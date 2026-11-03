package org.xtclang.idea.dap

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.xtclang.idea.lsp.DebugTaskParams
import org.xtclang.idea.lsp.DebugTaskResult
import org.xtclang.idea.run.XtcRunConfiguration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

/**
 * Runs the task that a Gradle-mode or command-line debug session's launch asks for (`xtc/debugTask`): the module's
 * Gradle run, in a JVM of its own, or `xtc run`, with the debug options that make the launcher run the program under the
 * session's debugger, which the program then connects to. The task's output goes to the session's console, as an
 * embedded debugged run's does; the server cancels the task when the session stops, which ends it, and the program with
 * it.
 */
internal object DebugTasks {
    private val logger = logger<DebugTasks>()

    /** The `buildMode` of a command-line launch, whose task is `xtc run`. */
    const val COMMAND_LINE = "commandLine"

    /** How a line of a test run's output that is xunit's TeamCity service message starts. */
    private const val SERVICE_MESSAGE = "##teamcity["

    fun run(
        project: Project,
        params: DebugTaskParams,
    ): CompletableFuture<DebugTaskResult> {
        val command = commandLine(params).withWorkDirectory(project.basePath?.let { Path(it).toFile() })
        logger.info("Running under the debugger: ${command.commandLineString}")
        val handler =
            try {
                OSProcessHandler(command)
            } catch (e: ExecutionException) {
                return CompletableFuture.failedFuture(e)
            }
        val result = CompletableFuture<DebugTaskResult>()
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    val category =
                        when {
                            // a test run's service messages are for the session's Tests tab, not its console
                            params.tests != null && event.text.startsWith(SERVICE_MESSAGE) -> "telemetry"

                            outputType == ProcessOutputTypes.STDOUT -> "stdout"

                            outputType == ProcessOutputTypes.STDERR -> "stderr"

                            else -> return
                        }
                    DebugSessions.getInstance(project).output(params.sessionId, event.text, category)
                }

                override fun processTerminated(event: ProcessEvent) {
                    result.complete(DebugTaskResult(event.exitCode))
                }
            },
        )
        result.whenComplete { _, failure -> if (failure is CancellationException) handler.destroyProcess() }
        handler.startNotify()
        return result
    }

    /**
     * The task's command line: for a command-line launch, `xtc run` as a command-line Run runs the module, with
     * `--debug`; otherwise the Gradle wrapper's, the module's run with the debug property.
     */
    internal fun commandLine(params: DebugTaskParams): GeneralCommandLine {
        val tests = params.tests ?: return runCommandLine(params)
        return if (params.buildMode == COMMAND_LINE) {
            XtcRunConfiguration.xtcTestCommandLine(params.module, tests, params.debug)
        } else {
            GeneralCommandLine(XtcRunConfiguration.gradleWrapper()).apply {
                addParameters("-q", "testXtc", "--mode=ATTACHED", "--module=${params.module}")
                addParameters(XtcRunConfiguration.gradleTestOptions(tests))
                addParameter("-PxtcDebug=${params.debug}")
            }
        }
    }

    /** A debugged run's command line, `xtc run`'s or the Gradle run's, as [commandLine] has it for a launch without tests. */
    private fun runCommandLine(params: DebugTaskParams): GeneralCommandLine =
        if (params.buildMode == COMMAND_LINE) {
            XtcRunConfiguration.xtcCommandLine(params.module, params.method, params.args, params.debug)
        } else {
            GeneralCommandLine(XtcRunConfiguration.gradleWrapper()).apply {
                addParameters("-q", "runXtc", "--mode=ATTACHED", "--module=${params.module}")
                params.method?.let { addParameter("--method=$it") }
                params.args.takeIf { it.isNotEmpty() }?.let { addParameter("--args=${it.joinToString(",")}") }
                addParameter("-PxtcDebug=${params.debug}")
            }
        }
}
