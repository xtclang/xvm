package org.xtclang.idea.dap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import org.xtclang.idea.lsp.DebugMessageParams
import org.xtclang.idea.lsp.DebugSessionParams
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The process LSP4IJ's DAP client debugs through, which is no operating-system process: the debug adapter is a
 * session in the Ecstasy language server. The client writes DAP messages to this process's input, and they go to
 * the session as `xtc/debugMessage` notifications; the session's messages come back as this process's output,
 * framed as an adapter on stdio writes them, which LSP4IJ's console does not show. The session starts with the
 * process, and messages written before it exists wait for it. Ending the process ends the session, which stops the program it launched, and the
 * process's exit code is the program's, from the session's `exited` event.
 */
internal class LanguageServerDebugProcessHandler(
    private val sessions: DebugSessions,
    private val server: () -> CompletableFuture<XtcLanguageServer>,
) : ProcessHandler(),
    DebugSessions.Session {
    private val lock = Any()
    private val waiting = mutableListOf<JsonObject>()
    private var connection: Connection? = null
    private val finished = AtomicBoolean()

    @Volatile
    private var exitCode = 0

    private class Connection(
        val server: XtcLanguageServer,
        val sessionId: String,
    )

    private val input =
        object : OutputStream() {
            private val decoder = DapFrames.Decoder(::send)

            override fun write(byte: Int) = write(byteArrayOf(byte.toByte()), 0, 1)

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) = decoder.write(bytes, offset, length)
        }

    init {
        // the session starts with the process, when its listeners, LSP4IJ's and the console's, are attached
        addProcessListener(
            object : ProcessListener {
                override fun startNotified(event: ProcessEvent) = begin()
            },
        )
    }

    private fun begin() {
        server()
            .thenCompose { connected -> connected.debugStart().thenApply { Connection(connected, it.sessionId) } }
            .whenComplete { started, failure ->
                if (failure != null) {
                    val cause = (failure as? CompletionException)?.cause ?: failure
                    ended("The Ecstasy language server could not start a debug session: ${cause.message}")
                    return@whenComplete
                }
                synchronized(lock) {
                    if (finished.get()) {
                        started.server.debugEnd(DebugSessionParams(started.sessionId))
                        return@whenComplete
                    }
                    sessions.register(started.sessionId, this)
                    connection = started
                    waiting.forEach { started.server.debugMessage(DebugMessageParams(started.sessionId, it)) }
                    waiting.clear()
                }
            }
    }

    /** A message the client wrote, sent to the session or kept until it exists. */
    private fun send(json: String) {
        val message = JsonParser.parseString(json).asJsonObject
        synchronized(lock) {
            val started = connection
            if (started == null) {
                waiting += message
            } else {
                started.server.debugMessage(DebugMessageParams(started.sessionId, message))
            }
        }
    }

    override fun message(message: JsonObject) {
        if (message["type"]?.asString == "event" && message["event"]?.asString == "exited") {
            message["body"]
                ?.asJsonObject
                ?.get("exitCode")
                ?.asInt
                ?.let { exitCode = it }
        }
        notifyTextAvailable(DapFrames.frame(message.toString()), ProcessOutputTypes.STDOUT)
    }

    override fun ended(reason: String) {
        if (finished.compareAndSet(false, true)) {
            forget()
            notifyTextAvailable("\n$reason\n", ProcessOutputTypes.STDERR)
            notifyProcessTerminated(1)
        }
    }

    override fun destroyProcessImpl() = finish { notifyProcessTerminated(exitCode) }

    override fun detachProcessImpl() = finish { notifyProcessDetached() }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream = input

    /** Ends the session, which stops its program, then the process. */
    private fun finish(notify: () -> Unit) {
        if (finished.compareAndSet(false, true)) {
            forget()?.let { it.server.debugEnd(DebugSessionParams(it.sessionId)) }
            notify()
        }
    }

    private fun forget(): Connection? =
        synchronized(lock) {
            connection?.also { sessions.remove(it.sessionId) }
        }
}
