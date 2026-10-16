package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.vfs.encoding.EncodingManager
import org.xtclang.idea.lsp.RunInputParams
import org.xtclang.idea.lsp.RunParams
import org.xtclang.idea.lsp.RunResponse
import org.xtclang.idea.lsp.StopRunParams
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * An embedded run as a process: the language server builds the module and runs it in its execution worker,
 * and output and the exit arrive as notifications for the run ID chosen here. Stop asks the server to stop
 * the run, which still reports the exit; a stop during the build stops the run as soon as it starts. Lines
 * typed into the console go to the program, including during the build; Send EOF ends its input.
 */
internal class EmbeddedRunProcessHandler(
    private val sessions: EmbeddedRunSessions,
    private val server: () -> CompletableFuture<XtcLanguageServer>,
    request: RunParams,
    private val label: String,
    /** Shown after the first line, e.g. that the project's Gradle settings do not apply. */
    private val notice: String? = null,
    /** The console writes typed input in its configured encoding. */
    private val consoleCharset: () -> Charset = { EncodingManager.getInstance().defaultConsoleEncoding },
) : ProcessHandler(),
    EmbeddedRunSessions.Session {
    private val runId = UUID.randomUUID().toString()
    private val request = request.copy(runId = runId)
    private val connection = AtomicReference<XtcLanguageServer?>()
    private val started = AtomicBoolean()
    private val stopping = AtomicBoolean()
    private val finished = AtomicBoolean()

    /** Completes once the run request is sent, so no input can reach the server before its run. */
    private val runRequested = CompletableFuture<XtcLanguageServer>()
    private val inputLock = Any()
    private var inputs: CompletableFuture<*> = runRequested
    private val input by lazy { RunConsoleInput(consoleCharset(), ::sendInput) }

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
        notice?.let { system("$it\n") }
        server()
            .thenCompose { connected ->
                connection.set(connected)
                // stopped while connecting: never start the run
                if (finished.get()) {
                    CompletableFuture.completedFuture(NOT_STARTED)
                } else {
                    connected.run(request).also { runRequested.complete(connected) }
                }
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

    override fun getProcessInput(): OutputStream = input

    /** Sends in order, after the run request; a run that is already over just answers false. */
    private fun sendInput(
        text: String?,
        end: Boolean,
    ) {
        synchronized(inputLock) {
            inputs =
                inputs
                    .handle { _, _ -> null }
                    .thenCompose { runRequested }
                    .thenCompose { server -> server.runInput(RunInputParams(runId, text, end)) }
        }
    }

    private fun system(text: String) = notifyTextAvailable(text, ProcessOutputTypes.SYSTEM)

    private companion object {
        val NOT_STARTED = RunResponse()
    }
}

/**
 * The run console's input. IntelliJ writes each line the user enters, line end included, and closes the
 * stream for Send EOF. Complete lines are forwarded as they arrive; the end of input follows any rest.
 */
internal class RunConsoleInput(
    private val charset: Charset,
    private val send: (text: String?, end: Boolean) -> Unit,
) : OutputStream() {
    private val pending = ByteArrayOutputStream()
    private var closed = false

    @Synchronized
    override fun write(byte: Int) {
        if (closed) return
        pending.write(byte)
        if (byte == '\n'.code) {
            send(pending.toString(charset), false)
            pending.reset()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        send(pending.takeIf { it.size() > 0 }?.toString(charset), true)
        pending.reset()
    }
}
