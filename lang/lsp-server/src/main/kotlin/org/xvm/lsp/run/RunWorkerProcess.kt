package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The language server's handle on the execution worker JVM ([RunWorker]). The worker starts on the first
 * run and is reused across runs; a worker that dies ends its runs with an error and is replaced by the next
 * run. Programs never execute in the language server process, so a hung or crashing program cannot take
 * editor features down with it.
 */
internal class RunWorkerProcess(
    private val command: () -> List<String> = ::defaultCommand,
) : Closeable {
    /** Receives one run's events, in order, on the worker's reader thread. */
    interface Listener {
        fun output(text: String)

        fun exited(
            code: Long?,
            error: String?,
        )
    }

    /** What to run: the directories holding the module and its dependencies, and its entry point. */
    data class Request(
        val modulePath: List<String>,
        val module: String,
        val method: String,
        val args: List<String>,
        val rootDir: String?,
    )

    private val lock = Any()
    private val listeners = ConcurrentHashMap<Long, Listener>()
    private val nextId = AtomicLong(1)
    private var process: Process? = null
    private var input: PrintStream? = null

    @Volatile
    private var closed = false

    /** Starts [request] and returns its run ID; the outcome is reported to [listener]. */
    fun start(
        request: Request,
        listener: Listener,
    ): Long {
        val id = nextId.getAndIncrement()
        listeners[id] = listener
        val sent =
            synchronized(lock) {
                check(!closed) { "The execution worker is closed" }
                ensureStarted()
                send(
                    JsonObject().apply {
                        addProperty("command", "run")
                        addProperty("id", id)
                        add("modulePath", JsonArray().apply { request.modulePath.forEach(::add) })
                        addProperty("module", request.module)
                        addProperty("method", request.method)
                        add("args", JsonArray().apply { request.args.forEach(::add) })
                        request.rootDir?.let { addProperty("rootDir", it) }
                    },
                )
            }
        if (!sent) listeners.remove(id)?.exited(null, "The execution worker could not be started")
        return id
    }

    /** Sends console input to the run; the program reads it line by line. */
    fun input(
        id: Long,
        text: String,
    ) = command("input", id) { addProperty("text", text) }

    /** Ends the run's console input; a program waiting for a line gets an empty one. */
    fun endInput(id: Long) = command("endInput", id)

    /** Stops the run; its listener still receives the final exit. */
    fun stop(id: Long) = command("stop", id)

    /** Sends a command for one run, when the worker is alive. */
    private fun command(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit = {},
    ) {
        synchronized(lock) {
            if (process?.isAlive == true) {
                send(
                    JsonObject().apply {
                        addProperty("command", name)
                        addProperty("id", id)
                        fill()
                    },
                )
            }
        }
    }

    override fun close() {
        val stopping =
            synchronized(lock) {
                closed = true
                send(JsonObject().apply { addProperty("command", "shutdown") })
                input?.close()
                process.also {
                    process = null
                    input = null
                }
            }
        stopping?.let {
            if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly()
        }
        endAll("The execution worker was shut down")
    }

    private fun ensureStarted() {
        if (process?.isAlive == true) return
        val started =
            ProcessBuilder(command())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        process = started
        input = PrintStream(started.outputStream, true, Charsets.UTF_8)
        Thread
            .ofPlatform()
            .name("xtc-run-worker-reader")
            .daemon(true)
            .start { read(started) }
        logger.info("execution worker started: pid {}", started.pid())
    }

    private fun send(message: JsonObject): Boolean {
        val stream = input ?: return false
        stream.println(message.toString())
        return !stream.checkError()
    }

    private fun read(worker: Process) {
        BufferedReader(InputStreamReader(worker.inputStream, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val event = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
                if (event == null) {
                    logger.warn("execution worker: {}", line)
                    continue
                }
                val id = event["id"]?.asLong
                when (event["event"]?.asString) {
                    "output" -> id?.let(listeners::get)?.output(event["text"].asString)

                    "exit" ->
                        id?.let(listeners::remove)?.exited(
                            event["code"]?.asLong,
                            event["error"]?.takeUnless { it.isJsonNull }?.asString,
                        )

                    "log" -> logger.info("execution worker: {}", event["text"]?.asString)

                    "ready", "started" -> logger.debug("execution worker: {}", line)

                    else -> logger.warn("execution worker sent an unknown event: {}", line)
                }
            }
        }
        synchronized(lock) {
            if (process === worker) {
                process = null
                input = null
            }
        }
        endAll("The execution worker stopped unexpectedly")
    }

    private fun endAll(reason: String) {
        listeners.keys.toList().forEach { id -> listeners.remove(id)?.exited(null, reason) }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(RunWorkerProcess::class.java)

        /** The worker runs from the language server's own classpath, on the same Java runtime. */
        fun defaultCommand(): List<String> =
            listOf(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-Dfile.encoding=UTF-8",
                "-cp",
                System.getProperty("java.class.path"),
                RunWorker::class.java.name,
            )
    }
}
