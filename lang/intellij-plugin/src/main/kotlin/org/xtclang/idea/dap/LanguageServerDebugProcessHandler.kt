package org.xtclang.idea.dap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import org.xtclang.idea.lsp.DebugMessageParams
import org.xtclang.idea.lsp.DebugSessionParams
import org.xtclang.idea.lsp.TestEvent
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The process LSP4IJ's DAP client debugs through, which is no operating-system process: the debug adapter is a
 * session in the Ecstasy language server. The client writes DAP messages to this process's input, and they go to
 * the session as `xtc/debugMessage` notifications; the session's messages come back as this process's output,
 * framed as an adapter on stdio writes them, which LSP4IJ's console does not show. The session starts with the
 * process, and messages written before it exists wait for it. Ending the process ends the session, which stops the program it launched, and the
 * process's exit code is the program's, from the session's `exited` event. A line typed in the session's console,
 * which IntelliJ writes to the same input, is what it is in a debug console: see [DebugConsoleLines]. The answer to a
 * `continue` reaches the client once the program stops or ends again, or the session ends, which Run to Cursor needs
 * with LSP4IJ 0.21.0.
 */
internal class LanguageServerDebugProcessHandler(
    private val sessions: DebugSessions,
    private val frameSources: (List<Path>) -> Unit = ::findFrameSources,
    /** Receives the program's output, from the session's `output` events, as a test run's Tests tab reads it. */
    private val output: (String) -> Unit = {},
    /** Receives the events of a test run's tests, from the session's `xtcTest` events, for its Tests tab. */
    private val tested: (TestEvent) -> Unit = {},
    private val server: () -> CompletableFuture<XtcLanguageServer>,
) : ProcessHandler(),
    DebugSessions.Session {
    private val lock = Any()
    private val waiting = mutableListOf<JsonObject>()
    private val continues = mutableListOf<JsonObject>()
    private var connection: Connection? = null
    private val finished = AtomicBoolean()
    private val console = DebugConsoleLines()

    @Volatile
    private var exitCode = 0

    private class Connection(
        val server: XtcLanguageServer,
        val sessionId: String,
    )

    private val input =
        object : OutputStream() {
            private val decoder = DapFrames.Decoder(::send) { line -> send(console.request(line)) }

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
        console.sent(message)
        send(message)
    }

    private fun send(message: JsonObject) {
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
        // a typed line's answer is console output, and the client sees neither the line nor the answer
        console.received(message)?.let(::deliver)
    }

    /** A message of the session, or the console output for a typed line, for the client and the Tests tab. */
    private fun deliver(message: JsonObject) {
        // the session's own event, for the Tests tab; LSP4IJ's DAP client knows only DAP's events
        if (message["type"]?.asString == "event" && message["event"]?.asString == DebugSessions.XTC_TEST) {
            message["body"]?.let(TestEvent::of)?.let(tested)
            return
        }
        if (message["type"]?.asString == "event" && message["event"]?.asString == "output") {
            message["body"]
                ?.asJsonObject
                ?.get("output")
                ?.asString
                ?.let(output)
        }
        if (message["type"]?.asString == "event" && message["event"]?.asString == "exited") {
            message["body"]
                ?.asJsonObject
                ?.get("exitCode")
                ?.asInt
                ?.let { exitCode = it }
        }
        // TODO LSP4IJ: UP65 — DAPStackFrame looks a frame's source up with LocalFileSystem.findFileByPath, which
        //  never refreshes, and keeps the null it gets. A library's sources are in a folder the server makes when it
        //  starts, which the VFS doesn't know if it had listed the parent before; let it find them first
        if (message["type"]?.asString == "response" && message["command"]?.asString == "stackTrace") {
            frameSources(framePaths(message))
        }
        // TODO LSP4IJ: UP44 — LSP4IJ 0.21.0 removes Run to Cursor's temporary breakpoint once the continue request is
        //  answered, not when the program stops there, so a program that takes longer than that round trip to reach
        //  the cursor runs past it. The client gets a continue's answer once the program next stops or ends, or the
        //  session does, which keeps the breakpoint until then, as LSP4IJ's fix (#1773) and VS Code do; nothing else
        //  in LSP4IJ waits for that answer: its other requests, a pause or a stop among them, go on meanwhile
        if (message["type"]?.asString == "response" && message["command"]?.asString == "continue" &&
            message["success"]?.asBoolean == true
        ) {
            synchronized(continues) { continues += message }
            return
        }
        if (message["type"]?.asString == "event" && message["event"]?.asString in RUN_ENDS) answerContinues()
        forward(message)
    }

    /** Gives the client [message], as an adapter on stdio writes it. */
    private fun forward(message: JsonObject) = notifyTextAvailable(DapFrames.frame(message.toString()), ProcessOutputTypes.STDOUT)

    /** Gives the client the answers to its continues that wait for the program to stop or end, in order. */
    private fun answerContinues() = synchronized(continues) { continues.toList().also { continues.clear() } }.forEach(::forward)

    override fun ended(reason: String) {
        if (finished.compareAndSet(false, true)) {
            forget()
            answerContinues()
            notifyTextAvailable("\n$reason\n", ProcessOutputTypes.STDERR)
            notifyProcessTerminated(1)
        }
    }

    override fun destroyProcessImpl() = finish { notifyProcessTerminated(exitCode) }

    override fun detachProcessImpl() = finish { notifyProcessDetached() }

    override fun detachIsDefault() = false

    override fun getProcessInput(): OutputStream = input

    /** Ends the session, which stops its program, then the process, whose client has every answer it waits for. */
    private fun finish(notify: () -> Unit) {
        if (finished.compareAndSet(false, true)) {
            forget()?.let { it.server.debugEnd(DebugSessionParams(it.sessionId)) }
            answerContinues()
            notify()
        }
    }

    private fun forget(): Connection? =
        synchronized(lock) {
            connection?.also { sessions.remove(it.sessionId) }
        }

    private companion object {
        /** The events after which a resumed program no longer runs: it stopped, or it ended. */
        val RUN_ENDS = setOf("stopped", "exited", "terminated")
    }
}

/** The files of a `stackTrace` response's frames whose source has a path, each once. */
internal fun framePaths(response: JsonObject): List<Path> =
    response["body"]
        ?.asJsonObject
        ?.getAsJsonArray("stackFrames")
        ?.mapNotNull { frame ->
            frame.asJsonObject["source"]
                ?.asJsonObject
                ?.get("path")
                ?.asString
                ?.let(Path::of)
        }.orEmpty()
        .distinct()

/**
 * Lets the VFS find each of [files] that is on disk but unknown to it, by refreshing the closest folder above it that
 * the VFS knows. Applying a refresh needs the EDT, and a debug message comes on the language server's listener thread,
 * so this waits a few seconds at most; then the frames go on as they would have.
 */
private fun findFrameSources(files: List<Path>) {
    val local = LocalFileSystem.getInstance()
    val folders =
        files
            .filter { local.findFileByNioFile(it) == null && Files.isRegularFile(it) }
            .mapNotNull { file -> generateSequence(file.parent) { it.parent }.firstNotNullOfOrNull(local::findFileByNioFile) }
            .distinct()
    if (folders.isEmpty()) return
    folders.forEach { (it as? NewVirtualFile)?.markDirty() }
    val refreshed = CompletableFuture<Unit>()
    RefreshQueue.getInstance().refresh(true, false, { refreshed.complete(Unit) }, folders)
    try {
        refreshed.get(5, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
