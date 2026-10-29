package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import org.xvm.debug.DebugSetup
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * A program that the launcher runs with `--debug`, which the language server's debugger attached to through the
 * program's debugger agent ([XtcDebugAgent]), over a socket: the program's one run, whose ID is [RUN], and its debug
 * events, which go to [listener] in order on a reader thread of their own. [module] is the program's module.
 */
internal class AttachedProgram private constructor(
    private val socket: Socket,
    private val lines: BufferedReader,
    private val listener: RunListener,
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
        val sent =
            deliver(
                RUN,
                JsonObject().apply {
                    addProperty("command", "debug")
                    addProperty("id", RUN)
                    add("modules", JsonArray().apply { modules.forEach(::add) })
                    add("breakpoints", RunWorkerDebug.sources(setup.breakpoints))
                    add("exceptions", RunWorkerDebug.exceptions(setup.exceptions))
                    if (setup.stopOnEntry) addProperty("stopOnEntry", true)
                    if (setup.functions.isNotEmpty()) add("functions", RunWorkerDebug.functions(setup.functions))
                },
            )
        if (!sent) started.completeExceptionally(IOException("The connection to the program was lost"))
        return started
    }

    /** Detaches from the program, which drops the debugger's breakpoints and runs on; the listener then hears of it. */
    fun detach() {
        detaching = true
        command("detach", RUN)
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
                val event = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: continue
                when (event["event"]?.asString) {
                    "started" -> started.complete(Unit)

                    "exit" -> {
                        started.complete(Unit)
                        if (over.compareAndSet(false, true)) listener.exited(event["code"]?.asLong, null)
                    }

                    "log" -> logger.info("attached program log: module={} text={}", module, event["text"]?.asString)

                    else ->
                        if (!debugEvent(event, listener)) {
                            logger.warn("attached program sent an unknown event: module={} event={}", module, line)
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

        /** How long attaching waits for the program to answer. */
        private val CONNECT_LIMIT = 10.seconds

        /**
         * Attaches to the program whose agent listens at [host] and [port], with its [token] if it has one; fails with
         * the reason if nothing listens there, or the program refuses the debugger or speaks another protocol. Blocks
         * until the program has answered.
         */
        fun attach(
            host: String,
            port: Int,
            token: String?,
            listener: RunListener,
        ): AttachedProgram {
            val socket = Socket()
            try {
                val limit = CONNECT_LIMIT.inWholeMilliseconds.toInt()
                socket.connect(InetSocketAddress(host, port), limit)
                socket.soTimeout = limit
                val out = PrintStream(socket.getOutputStream(), true, Charsets.UTF_8)
                out.println(
                    JsonObject()
                        .apply {
                            addProperty("command", "attach")
                            addProperty("id", RUN)
                            token?.let { addProperty("token", it) }
                        }.toString(),
                )
                val lines = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val ready =
                    lines.readLine()?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                        ?: throw IOException("The program at $host:$port did not answer as an Ecstasy program under --debug")
                ready["error"]?.asString?.let { throw IOException("The program refused the debugger: $it") }
                val protocol = ready["protocol"]?.asInt
                if (ready["event"]?.asString != "ready" || protocol != RunWorkerIdentity.PROTOCOL) {
                    throw IOException(
                        "The program's debugger agent speaks protocol ${protocol ?: "unknown"}, but the language server " +
                            "speaks ${RunWorkerIdentity.PROTOCOL}; run the program with this language server's jar as its agent",
                    )
                }
                socket.soTimeout = 0
                val module = ready["module"].asString
                logger.info("attached to a program: module={} pid={} at={}:{}", module, ready["pid"]?.asLong, host, port)
                return AttachedProgram(socket, lines, listener, module)
            } catch (failure: IOException) {
                runCatching { socket.close() }
                throw failure
            }
        }
    }
}
