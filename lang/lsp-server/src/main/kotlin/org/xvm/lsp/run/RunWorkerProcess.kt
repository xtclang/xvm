package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The language server's handle on its execution worker JVMs ([RunWorker]). Each running program has a worker
 * of its own, so programs run side by side and a stuck one can be ended without touching the others. A worker
 * starts when no idle one is left and is reused for later runs; a worker that dies ends its run with an error.
 * Programs never execute in the language server process, so a hung or crashing program cannot take editor
 * features down with it.
 *
 * The workers are supervised: one that does not report ready within [Limits.startup] is ended and its run
 * fails; a program still running [Limits.stop] after Stop has its worker ended; one idle worker is kept for
 * the next run until it has been idle for [Limits.idle], and any other worker exits when its run ends.
 */
internal class RunWorkerProcess(
    private val command: () -> List<String> = ::defaultCommand,
    private val limits: Limits = Limits(),
) : Closeable {
    /** Receives one run's events, in order, on its worker's reader thread. */
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

    /** How many programs run at once, and how long a worker may take to start, to stop, or stay idle. */
    data class Limits(
        val running: Int = 4,
        val startup: Duration = 60.seconds,
        val stop: Duration = 10.seconds,
        val idle: Duration = 10.minutes,
    )

    /** One worker JVM and the run it hosts, if any. Guarded by [lock], apart from its reader thread. */
    private inner class Worker(
        val process: Process,
    ) {
        val input = PrintStream(process.outputStream, true, Charsets.UTF_8)
        val ready = CompletableFuture<Unit>()
        var run: Long? = null
        var timer: ScheduledFuture<*>? = null

        /** Why the server ended this worker, reported to its run instead of an unexpected stop. */
        @Volatile var ended: String? = null

        fun send(message: JsonObject): Boolean {
            input.println(message.toString())
            return !input.checkError()
        }

        /** Ends the process; its reader thread then reports [reason] to the run. */
        fun end(reason: String) {
            ended = ended ?: reason
            process.destroyForcibly()
        }
    }

    private val lock = Any()
    private val workers = mutableListOf<Worker>()
    private val listeners = ConcurrentHashMap<Long, Listener>()
    private val hosts = ConcurrentHashMap<Long, Worker>()
    private val nextId = AtomicLong(1)
    private val timers =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "xtc-run-worker-timers").apply { isDaemon = true } }

    @Volatile
    private var closed = false

    /** Starts [request] and returns its run ID; the outcome is reported to [listener]. */
    fun start(
        request: Request,
        listener: Listener,
    ): Long {
        val id = nextId.getAndIncrement()
        listeners[id] = listener
        val refused =
            synchronized(lock) {
                check(!closed) { "The execution worker is closed" }
                // a worker that is being ended or retired is never reused
                val worker =
                    workers.firstOrNull { it.run == null && it.ended == null && it.process.isAlive }
                        ?: if (workers.count { it.run != null } >= limits.running) {
                            return@synchronized "${limits.running} programs are already running; stop one to run another"
                        } else {
                            launch() ?: return@synchronized "The execution worker could not be started"
                        }
                worker.timer?.cancel(false)
                worker.timer = null
                worker.run = id
                hosts[id] = worker
                // commands queue in the worker's input until it is ready, so they keep their order
                val sent =
                    worker.send(
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
                if (sent) null else "The execution worker could not be started".also { worker.end(it) }
            }
        if (refused != null) {
            hosts.remove(id)
            listeners.remove(id)?.exited(null, refused)
        }
        return id
    }

    /** Sends console input to the run; the program reads it line by line. */
    fun input(
        id: Long,
        text: String,
    ) = command("input", id) { addProperty("text", text) }

    /** Ends the run's console input; a program waiting for a line gets an empty one. */
    fun endInput(id: Long) = command("endInput", id)

    /**
     * Stops the run; its listener still receives the final exit. A program that is still running after
     * [Limits.stop] has its worker ended, which ends the run.
     */
    fun stop(id: Long) {
        command("stop", id)
        val worker = hosts[id] ?: return
        after(limits.stop) {
            if (hosts[id] === worker) {
                logger.warn("run {} did not stop within {}; ending its execution worker", id, limits.stop)
                worker.end("The program did not stop within ${limits.stop}, so its execution worker was ended")
            }
        }
    }

    /** Process IDs of the live workers, for tests. */
    internal fun workerPids(): List<Long> = synchronized(lock) { workers.filter { it.process.isAlive }.map { it.process.pid() } }

    /** Sends a command for one run, when its worker is alive. */
    private fun command(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit = {},
    ) {
        synchronized(lock) {
            val worker = hosts[id]?.takeIf { it.process.isAlive } ?: return
            worker.send(
                JsonObject().apply {
                    addProperty("command", name)
                    addProperty("id", id)
                    fill()
                },
            )
        }
    }

    override fun close() {
        val stopping =
            synchronized(lock) {
                closed = true
                workers.toList().onEach { worker ->
                    worker.ended = "The execution worker was shut down"
                    worker.send(JsonObject().apply { addProperty("command", "shutdown") })
                    worker.input.close()
                }
            }
        stopping.forEach { if (!it.process.waitFor(5, TimeUnit.SECONDS)) it.process.destroyForcibly() }
        timers.shutdownNow()
        endRuns(listeners.keys.toList(), "The execution worker was shut down")
    }

    /** Starts a worker JVM, or returns null when it cannot be started. Called under [lock]. */
    private fun launch(): Worker? {
        val process =
            try {
                ProcessBuilder(command())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
            } catch (failure: Exception) {
                logger.error("execution worker could not be started", failure)
                return null
            }
        val worker = Worker(process)
        workers += worker
        Thread
            .ofPlatform()
            .name("xtc-run-worker-reader-${process.pid()}")
            .daemon(true)
            .start { read(worker) }
        after(limits.startup) {
            if (!worker.ready.isDone) {
                logger.warn("execution worker {} did not start within {}; ending it", process.pid(), limits.startup)
                worker.end("The execution worker did not start within ${limits.startup}")
            }
        }
        logger.info("execution worker started: pid {}", process.pid())
        return worker
    }

    private fun read(worker: Worker) {
        BufferedReader(InputStreamReader(worker.process.inputStream, Charsets.UTF_8)).useLines { lines ->
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
                        if (id != null) {
                            finished(worker, id)
                            listeners.remove(id)?.exited(
                                event["code"]?.asLong,
                                event["error"]?.takeUnless { it.isJsonNull }?.asString,
                            )
                        }

                    "log" -> logger.info("execution worker: {}", event["text"]?.asString)

                    "ready" -> worker.ready.complete(Unit).also { logger.debug("execution worker: {}", line) }

                    "started" -> logger.debug("execution worker: {}", line)

                    else -> logger.warn("execution worker sent an unknown event: {}", line)
                }
            }
        }
        val runs =
            synchronized(lock) {
                workers.remove(worker)
                worker.timer?.cancel(false)
                hosts.filterValues { it === worker }.keys.onEach(hosts::remove)
            }
        endRuns(runs, worker.ended ?: "The execution worker stopped unexpectedly")
    }

    /** The worker's run has ended: keep it for the next run if no other worker is idle, or let it go. */
    private fun finished(
        worker: Worker,
        id: Long,
    ) {
        synchronized(lock) {
            if (hosts.remove(id) == null || worker.run != id) return
            worker.run = null
            if (closed) return
            if (workers.any { it !== worker && it.run == null }) {
                retire(worker)
            } else {
                worker.timer = after(limits.idle) { synchronized(lock) { if (worker.run == null) retire(worker) } }
            }
        }
    }

    /** Asks an idle worker to exit; its reader thread then removes it. Called under [lock]. */
    private fun retire(worker: Worker) {
        worker.ended = "The execution worker was shut down"
        worker.send(JsonObject().apply { addProperty("command", "shutdown") })
        worker.input.close()
    }

    /** Runs [action] after [delay] on the timer thread; once the timers are shut down, nothing is scheduled. */
    private fun after(
        delay: Duration,
        action: () -> Unit,
    ): ScheduledFuture<*>? =
        try {
            timers.schedule(Runnable { action() }, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            null
        }

    private fun endRuns(
        ids: Collection<Long>,
        reason: String,
    ) {
        ids.forEach { id -> listeners.remove(id)?.exited(null, reason) }
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
