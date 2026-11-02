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
 * Runs the Gradle task that a Gradle-mode debug session's launch asks for (`xtc/debugTask`): the module's run, in a
 * JVM of its own, with the debug properties that make the launcher run the program under the session's debugger,
 * which the program then connects to. The task's output goes to the session's console, as an embedded debugged run's
 * does; the server cancels the task when the session stops, which ends Gradle, and the program with it.
 */
internal object DebugTasks {
    private val logger = logger<DebugTasks>()

    fun run(
        project: Project,
        params: DebugTaskParams,
    ): CompletableFuture<DebugTaskResult> {
        val command = commandLine(params).withWorkDirectory(project.basePath?.let { Path(it).toFile() })
        logger.info("Running Gradle under the debugger: ${command.commandLineString}")
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
                        when (outputType) {
                            ProcessOutputTypes.STDOUT -> "stdout"
                            ProcessOutputTypes.STDERR -> "stderr"
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

    /** The Gradle wrapper's command line for the task: the module's run, with the debug properties. */
    internal fun commandLine(params: DebugTaskParams): GeneralCommandLine =
        GeneralCommandLine(XtcRunConfiguration.gradleWrapper()).apply {
            addParameters("-q", "runXtc", "--mode=ATTACHED", "--module=${params.module}")
            params.method?.let { addParameter("--method=$it") }
            params.args.takeIf { it.isNotEmpty() }?.let { addParameter("--args=${it.joinToString(",")}") }
            addParameter("-PxtcDebug=${params.debug}")
            params.agent?.let { addParameter("-PxtcDebugAgent=$it") }
        }
}
