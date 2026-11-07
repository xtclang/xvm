package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import org.xvm.debug.DebugSetup
import org.xvm.debug.ProgramEvents
import org.xvm.runtime.debug.DebugProtocol
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A program that the launcher runs with `--debug`, which the language server's debugger attached to through the
 * program's debugger agent (javatools' `DebugAgent`), over a socket that the debugger opened ([attach]) or the program did
 * ([accept]): the program's one run, whose ID is [RUN], and its debug events, which go to [listener] in order on a
 * reader thread of their own. [module] is the program's module.
 */
internal class AttachedProgram private constructor(
    private val socket: Socket,
    private val lines: BufferedReader,
    private val listener: ProgramEvents,
    val module: String,
) : RunDebugClient(),
    Closeable {
    private val out = PrintStream(socket.getOutputStream(), true, Charsets.UTF_8)

    /** Completes once the program has taken the debugger's setup. */
    private val started = CompletableFuture<Unit>()

    /** True once the listener has had the run's last event, the program's exit or the detach. */
    private val over = AtomicBoolean()

    @Volatile
    private var detaching = false

    override fun deliver(
        id: Long,
        message: JsonObject,
    ): Boolean =
        synchronized(out) {
            out.println(message.toString())
            !out.checkError()
        }

    /**
     * Sets the program's debugger up as [setup] asks, with the program's own modules named [modules] besides its
     * main one, and completes once the program has taken it: how the breakpoints were installed goes to the listener
     * first. A program that waits for a debugger before its first line then starts.
     */
    fun debug(
        modules: List<String>,
        setup: DebugSetup,
    ): CompletableFuture<Unit> {
        Thread
            .ofPlatform()
            .name("xtc-attached-reader-$module")
            .daemon(true)
            .start(::read)
        val sent = deliver(RUN, DebugProtocol.json(DebugProtocol.Debug(RUN, modules, setup.record())))
        if (!sent) started.completeExceptionally(IOException("The connection to the program was lost"))
        return started
    }

    /** Detaches from the program, which drops the debugger's breakpoints and runs on; the listener then hears of it. */
    fun detach() {
        detaching = true
        command(DebugProtocol.Detach(RUN))
        runCatching { socket.shutdownOutput() }
    }

    /** Ends the connection; the program runs on, as after a detach. */
    override fun close() {
        detaching = true
        runCatching { socket.close() }
    }

    /** Delivers the program's events until the connection ends, which is the run's end for the listener. */
    private fun read() {
        try {
            for (line in lines.lineSequence()) {
                val json = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: continue
                when (val event = runCatching { DebugProtocol.event(json) }.getOrNull()) {
                    is DebugProtocol.Started -> started.complete(Unit)

                    is DebugProtocol.Exit -> {
                        started.complete(Unit)
                        if (over.compareAndSet(false, true)) listener.exited(event.code(), null)
                    }

                    is DebugProtocol.Log -> logger.info("attached program log: module={} text={}", module, event.text())

                    null -> logger.warn("attached program sent an unknown event: module={} event={}", module, line)

                    else ->
                        if (!debugEvent(event, listener)) {
                            logger.warn("attached program sent an unexpected event: module={} event={}", module, line)
                        }
                }
            }
        } catch (_: IOException) {
            // the connection closed
        } finally {
            runCatching { socket.close() }
            abandon(RUN)
            val lost = "The connection to the program was lost"
            started.completeExceptionally(IOException(lost))
            if (over.compareAndSet(false, true)) listener.detached(if (detaching) null else lost)
        }
    }

    companion object {
        /** The ID of an attached program's run, which is its only one. */
        const val RUN = 1L

        private val logger = LoggerFactory.getLogger(AttachedProgram::class.java)

        /** How long attaching waits for the program to answer, unless a test lifts it. */
        internal val CONNECT_LIMIT = 10.seconds

        /**
         * Attaches to the program whose agent listens at [host] and [port], with its [token] if it has one; fails with
         * the reason if nothing listens there within [limit], or the program refuses the debugger or speaks another
         * protocol. Blocks until the program has answered.
         */
        fun attach(
            host: String,
            port: Int,
            token: String?,
            listener: ProgramEvents,
            limit: Duration = CONNECT_LIMIT,
        ): AttachedProgram {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), limit.inWholeMilliseconds.toInt())
            } catch (failure: IOException) {
                runCatching { socket.close() }
                throw failure
            }
            return answer(socket, "at $host:$port", token, listener, limit)
        }

        /**
         * Takes the connection of a program that the launcher runs with `--debug connect:` and [server]'s port, with
         * the [token] the program was given; fails as [attach] does, or when [server] is closed first. Blocks until a
         * program has connected and answered, within [limit] once it has connected.
         */
        fun accept(
            server: ServerSocket,
            token: String?,
            listener: ProgramEvents,
            limit: Duration = CONNECT_LIMIT,
        ): AttachedProgram = answer(server.accept(), "that connected to port ${server.localPort}", token, listener, limit)

        /**
         * Introduces the debugger to the program [where] at the other end of [socket], and waits for its answer for at
         * most [limit].
         */
        private fun answer(
            socket: Socket,
            where: String,
            token: String?,
            listener: ProgramEvents,
            limit: Duration,
        ): AttachedProgram {
            try {
                socket.soTimeout = limit.inWholeMilliseconds.toInt()
                val out = PrintStream(socket.getOutputStream(), true, Charsets.UTF_8)
                out.println(DebugProtocol.json(DebugProtocol.Attach(RUN, token)))
                val lines = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val answer =
                    lines.readLine()?.let { runCatching { DebugProtocol.event(JsonParser.parseString(it).asJsonObject) }.getOrNull() }
                        ?: throw IOException("The program $where did not answer as an Ecstasy program under --debug")
                if (answer is DebugProtocol.Refused) throw IOException("The program refused the debugger: ${answer.error()}")
                val ready = answer as? DebugProtocol.Ready
                if (ready?.protocol() != RunWorkerIdentity.PROTOCOL) {
                    throw IOException(
                        "The program's debugger agent speaks protocol ${ready?.protocol() ?: "unknown"}, but the language server " +
                            "speaks ${RunWorkerIdentity.PROTOCOL}; run the program with an XDK of the language server's version",
                    )
                }
                socket.soTimeout = 0
                val module = ready.module()
                logger.info("attached to a program: module={} pid={} {}", module, ready.pid(), where)
                return AttachedProgram(socket, lines, listener, module)
            } catch (failure: IOException) {
                runCatching { socket.close() }
                throw failure
            }
        }
    }
}
