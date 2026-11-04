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
import org.xtclang.idea.lsp.TestEvent
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
        val sessions = DebugSessions.getInstance(project)
        // a test run's events are lines of its output, for the session's Tests tab rather than its console
        val lines = params.tests?.let { TestLines(params.sessionId, sessions) }
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    when (outputType) {
                        ProcessOutputTypes.STDOUT -> lines?.feed(event.text) ?: sessions.output(params.sessionId, event.text, "stdout")
                        ProcessOutputTypes.STDERR -> sessions.output(params.sessionId, event.text, "stderr")
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    lines?.flush()
                    result.complete(DebugTaskResult(event.exitCode))
                }
            },
        )
        result.whenComplete { _, failure -> if (failure is CancellationException) handler.destroyProcess() }
        handler.startNotify()
        return result
    }

    /**
     * A test task's standard output for the debug session [sessionId], in whole lines: each that is one of xunit's
     * events goes to the session as that, and the rest as its output.
     */
    private class TestLines(
        private val sessionId: String,
        private val sessions: DebugSessions,
    ) {
        private val partial = StringBuilder()

        fun feed(text: String) {
            val lines =
                synchronized(partial) {
                    partial.append(text)
                    val end = partial.lastIndexOf("\n") + 1
                    partial.substring(0, end).also { partial.delete(0, end) }
                }
            lines.lineSequence().filter { it.isNotEmpty() }.forEach(::line)
        }

        fun flush() {
            val rest = synchronized(partial) { partial.toString().also { partial.setLength(0) } }
            if (rest.isNotEmpty()) line(rest)
        }

        private fun line(text: String) {
            TestEvent.parse(text)?.let { sessions.tested(sessionId, it) } ?: sessions.output(sessionId, "$text\n", "stdout")
        }
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
