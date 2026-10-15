package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import org.xtclang.idea.lsp.RunParams
import org.xtclang.idea.lsp.RunResponse
import org.xtclang.idea.lsp.StopRunParams
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * An embedded run as a process: the language server builds the module and runs it in its execution worker,
 * and output and the exit arrive as notifications for the run ID chosen here. Stop asks the server to stop
 * the run, which still reports the exit; a stop during the build stops the run as soon as it starts.
 */
internal class EmbeddedRunProcessHandler(
    private val sessions: EmbeddedRunSessions,
    private val server: () -> CompletableFuture<XtcLanguageServer>,
    request: RunParams,
    private val label: String,
) : ProcessHandler(),
    EmbeddedRunSessions.Session {
    private val runId = UUID.randomUUID().toString()
    private val request = request.copy(runId = runId)
    private val connection = AtomicReference<XtcLanguageServer?>()
    private val started = AtomicBoolean()
    private val stopping = AtomicBoolean()
    private val finished = AtomicBoolean()

    init {
        addProcessListener(
            object : ProcessListener {
                override fun startNotified(event: ProcessEvent) = begin()
            },
        )
    }

    private fun begin() {
        // registered before the request, because output can arrive before the response
        sessions.register(runId, this)
        system("Building and running $label with the embedded compiler...\n")
        server()
            .thenCompose { connected ->
                connection.set(connected)
                // stopped while connecting: never start the run
                if (finished.get()) CompletableFuture.completedFuture(NOT_STARTED) else connected.run(request)
            }.whenComplete { response: RunResponse?, failure ->
                when {
                    failure != null ->
                        exited(
                            null,
                            "Run failed: ${(if (failure is CompletionException) failure.cause else failure)?.message}",
                        )

                    response == null || response === NOT_STARTED -> Unit

                    response.runId == null -> buildFailed(response)

                    else -> {
                        started.set(true)
                        if (stopping.get()) stop()
                    }
                }
            }
    }

    private fun buildFailed(response: RunResponse) {
        EmbeddedExecution.describe(response.build).forEach { system("$it\n") }
        exited(null, "Build failed; nothing was run.")
    }

    override fun output(text: String) {
        notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
    }

    override fun exited(
        exitCode: Long?,
        error: String?,
    ) {
        if (finished.compareAndSet(false, true)) {
            sessions.remove(runId)
            error?.let { notifyTextAvailable("\n$it\n", ProcessOutputTypes.STDERR) }
            notifyProcessTerminated(exitCode?.toInt() ?: 1)
        }
    }

    /** Without a connection or an active run there is nothing left to wait for. */
    private fun stop() {
        val connected = connection.get() ?: return exited(null, "Stopped.")
        connected
            .stopRun(StopRunParams(runId))
            .whenComplete { active, failure -> if (failure != null || active != true) exited(null, "Stopped.") }
    }

    override fun destroyProcessImpl() {
        stopping.set(true)
        if (started.get() || connection.get() == null) {
            stop()
        } else {
            system("Stopping when the build finishes...\n")
        }
    }

    /** The run continues in the worker; this console just stops following it. */
    override fun detachProcessImpl() {
        if (finished.compareAndSet(false, true)) {
            sessions.remove(runId)
            notifyProcessDetached()
        }
    }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream? = null

    private fun system(text: String) = notifyTextAvailable(text, ProcessOutputTypes.SYSTEM)

    private companion object {
        val NOT_STARTED = RunResponse()
    }
}
