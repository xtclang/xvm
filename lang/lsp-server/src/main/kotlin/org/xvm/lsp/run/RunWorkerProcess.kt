package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import org.xvm.debug.DebugSetup
import org.xvm.debug.ProgramEvents
import org.xvm.lsp.util.snapshot
import org.xvm.runtime.debug.DebugProtocol
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The language server's handle on its execution worker JVMs ([RunWorker]). Each running program has a worker
 * of its own, so programs run side by side and a stuck one can be ended without touching the others. A worker
 * starts when no idle one is left and is reused for later runs; a worker that dies ends its run with an error.
 * Programs never execute in the language server process, so a hung or crashing program cannot take editor
 * features down with it. A worker runs with the JVM options of the run it was started for, its Gradle project's
 * ([Request.jvmOptions]), and serves only runs with the same options: a run with others gets a worker of its own,
 * and an idle worker with other options is retired as it starts, so that one idle worker is kept, as before.
 *
 * The workers are supervised: one that does not report ready within [Limits.startup] is ended and its run
 * fails; a program still running [Limits.stop] after Stop has its worker ended; one idle worker is kept for
 * the next run until it has been idle for [Limits.idle], and any other worker exits when its run ends.
 * A worker receives nothing until it reports the identity the server expects (the protocol version and
 * the bundled XDK, see [RunWorkerIdentity]); one that reports another is ended and its run says why.
 * After each run a worker reports the heap the JVM left in use after its own latest collections, its
 * threads, and what the run left behind; one past [Limits.heap] or [Limits.threads], whose runtime pool
 * grew, or that still holds a console or run of an ended program serves no further run.
 *
 * A worker's events come over a loopback socket of their own ([connect]): the server listens at an ephemeral port
 * of 127.0.0.1 for each worker it starts, gives the worker the port and a random token in its environment, takes the
 * one connection that shows that token, and reads the events from it. Its commands go to its standard input. What the
 * worker's JVM itself writes to its standard output and error, as JVM options such as `-Xlog`, `-verbose` or
 * `-XX:+PrintCompilation` have it do, at any point of a line, can then not break a message: it is read apart, line by
 * line, and goes to the output of the run the worker hosts, between the program's lines, as a Gradle run shows it
 * ([stray]).
 */
internal class RunWorkerProcess(
    /** The command that starts a worker with the given JVM options. */
    private val command: (List<String>) -> List<String> = ::defaultCommand,
    private val limits: Limits = Limits(),
    expected: () -> RunWorkerIdentity = RunWorkerIdentity::current,
) : RunDebugClient(),
    Closeable {
    /**
     * What to run: the directories holding the module and its dependencies, and its entry point; with [breakpoints],
     * the run is under the runtime debugger, with those breakpoints installed before its first line.
     */
    data class Request(
        val modulePath: List<String>,
        val module: String,
        val method: String,
        val args: List<String>,
        val rootDir: String?,
        /** The debugger's setup, for a run under the debugger. */
        val debug: DebugSetup? = null,
        /** The ID the client knows the run by, which the logs give beside the worker's own run number. */
        val runId: String? = null,
        /** For a run under the debugger, the directory of each module's sources, by module name. */
        val sourceDirs: Map<String, String> = emptyMap(),
        /** The JVM options of the worker that runs it, which [JvmOptions] has checked; none for the worker's own. */
        val jvmOptions: List<String> = emptyList(),
    )

    /**
     * How many programs run at once; how long a worker may take to start, to stop, or stay idle; how much
     * heap in use after the JVM's collections (in bytes) and how many threads a worker may keep after a run and
     * still serve the next; and how long the code that computes a lazy variable's value may run in a debugged
     * program, [onDemand], the debugger's own `ON_DEMAND` when null, unless the run's debug setup sets its own.
     */
    data class Limits(
        val running: Int = 4,
        val startup: Duration = 60.seconds,
        val stop: Duration = 10.seconds,
        val idle: Duration = 10.minutes,
        val heap: Long = 512L * MB,
        val threads: Int = 200,
        val onDemand: Duration? = null,
    )

    /**
     * What a worker keeps after a run: the heap the JVM left in use after its latest collections, which may be
     * from before the run, the heap's maximum, its threads,
     * the threads of the runtime's own pool, and the consoles and runs it still holds for ended programs;
     * [run] is the run this report follows.
     */
    data class Usage(
        val heapUsed: Long,
        val heapMax: Long,
        val threads: Int,
        val poolThreads: Int = 0,
        val consoles: Int = 0,
        val runs: Int = 0,
        val run: Long? = null,
    )

    /**
     * One worker JVM and the run it hosts, if any; [generation] counts the workers this server started, from 1, and
     * [options] are the JVM options it runs with, on its [command] line. Guarded by [lock], apart from its reader thread.
     */
    private inner class Worker(
        val process: Process,
        val generation: Int,
        val options: List<String>,
        val command: List<String>,
    ) {
        val input = PrintStream(process.outputStream, true, Charsets.UTF_8)
        val ready = CompletableFuture<Unit>()
        var run: Long? = null

        /** The client's ID of the run the worker hosts, or hosted last. */
        var runId: String? = null
        var timer: ScheduledFuture<*>? = null

        /** Commands waiting for the worker to report the expected identity; null once it has. */
        private var held: MutableList<JsonObject>? = mutableListOf()

        /** What the worker kept after its first and its last run. */
        var first: Usage? = null
        var usage: Usage? = null

        /** Why the worker serves no further run once its current one ends. */
        var worn: String? = null

        /** Why the server ended this worker, reported to its run instead of an unexpected stop. */
        @Volatile var ended: String? = null

        /** Whether the worker hosts no run and is not being ended or retired. */
        fun idle() = run == null && ended == null

        /** Completes once its JVM's standard output and error have both ended, which they do as the process ends. */
        val drained = CompletableFuture<Unit>()

        /**
         * Guards the output of the run it hosts, so that its JVM's own lines and the program's keep their order and the
         * JVM's never split one of the program's: [lineOpen], [waiting].
         */
        val delivery = Any()

        /** Whether the hosted run's output so far ends inside a line of the program's, which its JVM's lines then wait out. */
        var lineOpen = false

        /** Its JVM's lines that wait for the program's line to end. */
        val waiting = ArrayDeque<String>()

        /** Its JVM's lines on its standard output outside a run, of which the log has the first [STRAYS_LOGGED]. */
        val strays = AtomicInteger()

        /**
         * Its JVM's lines on its standard error outside a run, of which the log has the first [STRAYS_LOGGED] and, once it
         * exits, the last of the others, [lastErrors], up to [STRAYS_LOGGED]: an idle worker's crash says why there.
         */
        val errors = AtomicInteger()
        val lastErrors = ArrayDeque<String>()

        /** Whether its JVM's own output went to the run it hosts, as when the JVM says why it cannot start; each run's own. */
        @Volatile var printed = false

        /** Sends [message], or holds it until the worker has reported the expected identity. */
        fun send(message: JsonObject): Boolean {
            held?.let {
                it += message
                return true
            }
            return write(message)
        }

        /** The worker reported the expected identity: sends what was held, in order. */
        fun release(): Boolean {
            val commands = held ?: return true
            held = null
            return commands.all(::write)
        }

        fun write(message: JsonObject): Boolean {
            input.println(message.toString())
            return !input.checkError()
        }

        /** Ends the process; its reader thread then reports [reason] to the run. */
        fun end(reason: String) {
            ended = ended ?: reason
            process.destroyForcibly()
        }

        /** The worker as the logs give it. */
        override fun toString() = "worker=${process.pid()} generation=$generation"
    }

    private val lock = Any()
    private val workers = mutableListOf<Worker>()

    /** The workers started so far, which numbers each one's generation. Guarded by [lock]. */
    private var generations = 0
    private val listeners = ConcurrentHashMap<Long, ProgramEvents>()
    private val hosts = ConcurrentHashMap<Long, Worker>()
    private val nextId = AtomicLong(1)

    private val expected by lazy(expected)
    private val timers =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "xtc-run-worker-timers").apply { isDaemon = true } }

    @Volatile
    private var closed = false

    /** Starts [request] and returns its run ID; the outcome is reported to [listener]. */
    fun start(
        request: Request,
        listener: ProgramEvents,
    ): Long {
        val id = nextId.getAndIncrement()
        listeners[id] = listener
        val refused =
            synchronized(lock) {
                check(!closed) { "The execution worker is closed" }
                // a worker that is being ended or retired, or holds too much, is never reused, nor one with other JVM options
                val worker =
                    workers.firstOrNull { it.idle() && it.worn == null && it.process.isAlive && it.options == request.jvmOptions }
                        ?: if (workers.count { it.run != null } >= limits.running) {
                            return@synchronized "${limits.running} programs are already running; stop one to run another"
                        } else {
                            val launched = launch(request.jvmOptions) ?: return@synchronized "The execution worker could not be started"
                            // an idle worker with other options serves none of these runs; the new one is kept idle after its run
                            workers.filter { it !== launched && it.idle() && it.options != launched.options }.forEach(::retire)
                            launched
                        }
                worker.timer?.cancel(false)
                worker.timer = null
                worker.run = id
                worker.printed = false
                worker.runId = request.runId
                hosts[id] = worker
                logger.info(
                    "run assigned: {} {} module={} method={}{}",
                    runFields(id, request.runId),
                    worker,
                    request.module,
                    request.method,
                    logged(request.jvmOptions),
                )
                // commands are held until the worker reports the expected identity, so they keep their order
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
                            request.debug?.let { add("setup", DebugProtocol.json(it.record(request.sourceDirs))) }
                            limits.onDemand?.let { addProperty("onDemandMillis", it.inWholeMilliseconds) }
                        },
                    )
                if (sent) null else "The execution worker could not be started".also { worker.end(it) }
            }
        if (refused != null) {
            logger.warn("run refused: {} reason={}", runFields(id, request.runId), refused)
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
                val runId = synchronized(lock) { worker.runId }
                logger.warn("run did not stop within {}; ending its worker: {} {}", limits.stop, runFields(id, runId), worker)
                worker.end("The program did not stop within ${limits.stop}, so its execution worker was ended")
            }
        }
    }

    /** Process IDs of the live workers, for tests. */
    internal fun workerPids(): List<Long> = synchronized(lock) { workers.filter { it.process.isAlive }.map { it.process.pid() } }

    /** The command line that started each live worker, by process ID, as [launch] built it; for tests. */
    internal fun workerCommands(): Map<Long, List<String>> =
        synchronized(lock) { workers.filter { it.process.isAlive }.associate { it.process.pid() to it.command } }

    /** Runs the server still tracks, which is none once every run has ended; for tests. */
    internal fun tracked(): Int = listeners.size + hosts.size

    /** What each live worker kept after its last run, by process ID, for tests. */
    internal fun usage(): Map<Long, Usage> =
        synchronized(lock) {
            workers.filter { it.process.isAlive }.mapNotNull { worker -> worker.usage?.let { worker.process.pid() to it } }.toMap()
        }

    /** Sends a message for one run to its worker, when the worker is alive. */
    override fun deliver(
        id: Long,
        message: JsonObject,
    ): Boolean = synchronized(lock) { hosts[id]?.takeIf { it.process.isAlive }?.send(message) == true }

    override fun close() {
        val stopping =
            synchronized(lock) {
                closed = true
                workers.toList().onEach { worker ->
                    worker.ended = "The execution worker was shut down"
                    worker.write(JsonObject().apply { addProperty("command", "shutdown") })
                    worker.input.close()
                }
            }
        stopping.forEach { if (!it.process.waitFor(5, TimeUnit.SECONDS)) it.process.destroyForcibly() }
        timers.shutdownNow()
        endRuns(listeners.keys.snapshot(), "The execution worker was shut down")
    }

    /** Starts a worker JVM with the JVM options [options], or returns null when it cannot be started. Called under [lock]. */
    private fun launch(options: List<String>): Worker? {
        val line = command(options)
        val token = UUID.randomUUID().toString()
        val server =
            try {
                ServerSocket(0, BACKLOG, InetAddress.getByName(RunWorker.LOOPBACK))
            } catch (failure: IOException) {
                logger.error("execution worker could not be started: no port for its events", failure)
                return null
            }
        val process =
            try {
                ProcessBuilder(line)
                    .apply {
                        environment()[RunWorker.PORT] = server.localPort.toString()
                        environment()[RunWorker.TOKEN] = token
                    }.start()
            } catch (failure: Exception) {
                runCatching { server.close() }
                logger.error("execution worker could not be started", failure)
                return null
            }
        val worker = Worker(process, ++generations, options, line)
        workers += worker
        // a worker that ends before it connects ends the wait for its connection
        process.onExit().thenRun { runCatching { server.close() } }
        val streams = listOf(Triple("output", process.inputStream, false), Triple("errors", process.errorStream, true))
        val open = AtomicInteger(streams.size)
        streams.forEach { (name, stream, errors) ->
            Thread
                .ofPlatform()
                .name("xtc-run-worker-$name-${process.pid()}")
                .daemon(true)
                .start {
                    try {
                        drain(worker, stream, errors)
                    } finally {
                        if (open.decrementAndGet() == 0) worker.drained.complete(Unit)
                    }
                }
        }
        Thread
            .ofPlatform()
            .name("xtc-run-worker-reader-${process.pid()}")
            .daemon(true)
            .start { read(worker, server, token) }
        after(limits.startup) {
            if (!worker.ready.isDone) {
                logger.warn("execution worker did not start within {}; ending it: {}", limits.startup, worker)
                worker.end("The execution worker did not start within ${limits.startup}")
            }
        }
        logger.info("execution worker started: {}{}", worker, logged(options))
        return worker
    }

    /**
     * Waits for the worker to connect ([connect]), then delivers its events until its connection ends, and ends the runs
     * it still hosts, once what its JVM printed last has gone to them. Ending a worker closes its connection, which ends
     * the events as the worker's exit does; one that ends before it connects ends the wait.
     */
    private fun read(
        worker: Worker,
        server: ServerSocket,
        token: String,
    ) {
        try {
            val socket = connect(worker, server, token) ?: return
            BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    val event = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
                    if (event == null) {
                        logger.warn("execution worker sent a line that is no event: {} text={}", worker, line)
                        continue
                    }
                    when (val typed = runCatching { DebugProtocol.event(event) }.getOrNull()) {
                        is DebugProtocol.Exit -> {
                            val id = typed.id()
                            val code = typed.code()
                            val error = typed.error()
                            val runId = synchronized(lock) { worker.runId.takeIf { worker.run == id } }
                            logger.info(
                                "run exited: {} {} {}",
                                runFields(id, runId),
                                worker,
                                code?.let { "code=$it" } ?: error?.let { "error=$it" } ?: "code=none",
                            )
                            finished(worker, id)
                            abandon(id)
                            listeners.remove(id)?.let { listener ->
                                // its JVM's lines that waited for the program's last line go first
                                synchronized(worker.delivery) {
                                    release(worker, listener)
                                    worker.lineOpen = false
                                }
                                listener.exited(code, error)
                            }
                        }

                        is DebugProtocol.Log -> logger.info("execution worker log: {} text={}", worker, typed.text())

                        is DebugProtocol.Ready -> accept(worker, RunWorkerIdentity.of(typed))

                        is DebugProtocol.Started -> logger.debug("execution worker event: {} {}", worker, line)

                        null -> workerEvent(worker, event, line)

                        else ->
                            if (!debugEvent(typed, (typed as? DebugProtocol.RunEvent)?.id()?.let(listeners::get))) {
                                logger.warn("execution worker sent an unexpected event: {} event={}", worker, line)
                            }
                    }
                }
            }
        } catch (_: IOException) {
            // the connection was closed by ending the worker
        } finally {
            // a worker still running exits once its input ends
            worker.input.close()
            // what its JVM printed last, as why it stopped, reaches its run ahead of the run's end: its streams end with it
            runCatching { worker.drained.get(limits.stop.inWholeMilliseconds, TimeUnit.MILLISECONDS) }
            synchronized(lock) { worker.run }?.let(listeners::get)?.let { synchronized(worker.delivery) { release(worker, it) } }
            if (worker.strays.get() > STRAYS_LOGGED) {
                logger.info(
                    "execution worker wrote {} lines to its standard output outside a run, of which the log has the first {}: {}",
                    worker.strays.get(),
                    STRAYS_LOGGED,
                    worker,
                )
            }
            synchronized(worker.lastErrors) { worker.lastErrors.toList() }.takeIf { it.isNotEmpty() }?.let { last ->
                logger.warn(
                    "execution worker wrote {} lines to its standard error outside a run, of which the last {} follow: {} text={}",
                    worker.errors.get(),
                    last.size,
                    worker,
                    last.joinToString("\n"),
                )
            }
            logger.info("execution worker exited: {} reason={}", worker, worker.ended ?: "it stopped on its own")
            val runs =
                synchronized(lock) {
                    workers.remove(worker)
                    worker.timer?.cancel(false)
                    hosts.filterValues { it === worker }.keys.onEach(hosts::remove)
                }
            endRuns(runs, worker.ended ?: unexpectedly(worker))
        }
    }

    /**
     * Waits for the worker to connect to [server], its port, and show [token] as the connection's first line, and returns
     * the connection; null once the worker has ended first, which closes [server], as its startup limit does. Each
     * connection has [TOKEN_WAIT] of its own to show the token, which the worker writes as it connects, while the server
     * goes on taking others ([admit]): one that sends nothing, or another token, is refused and holds up no other. Once
     * one shows it, [server] closes, so no further connection is taken.
     */
    private fun connect(
        worker: Worker,
        server: ServerSocket,
        token: String,
    ): Socket? {
        val connection = CompletableFuture<Socket?>()
        Thread
            .ofVirtual()
            .name("xtc-run-worker-accept-${worker.process.pid()}")
            .start {
                server.use {
                    while (!connection.isDone) {
                        val candidate =
                            try {
                                server.accept()
                            } catch (_: IOException) {
                                break
                            }
                        Thread.ofVirtual().start { admit(worker, candidate, token, connection, server) }
                    }
                }
                connection.complete(null)
            }
        return connection.join()
    }

    /**
     * Takes [candidate] as the worker's [connection] if it shows [token] within [TOKEN_WAIT], and closes [server]; else
     * refuses it, with a warning.
     */
    private fun admit(
        worker: Worker,
        candidate: Socket,
        token: String,
        connection: CompletableFuture<Socket?>,
        server: ServerSocket,
    ) {
        val shown =
            try {
                candidate.soTimeout = TOKEN_WAIT.inWholeMilliseconds.toInt()
                firstLine(candidate.getInputStream())
            } catch (_: IOException) {
                null
            }
        if (shown == token && runCatching { candidate.soTimeout = 0 }.isSuccess && connection.complete(candidate)) {
            runCatching { server.close() }
            return
        }
        if (shown != token) {
            logger.warn("execution worker connection refused: {} reason={}", worker, if (shown == null) "no token" else "a wrong token")
        }
        runCatching { candidate.close() }
    }

    /** The first line of [input], read byte by byte so that nothing after it is taken; null for none within [TOKEN_LIMIT]. */
    private fun firstLine(input: InputStream): String? {
        val line = StringBuilder()
        while (line.length < TOKEN_LIMIT) {
            when (val byte = input.read()) {
                -1 -> return null
                '\n'.code -> return line.toString()
                else -> line.append(byte.toChar())
            }
        }
        return null
    }

    /**
     * Reads what the worker's JVM itself writes to [stream], its standard output, or its standard error where [errors],
     * line by line, as [stray] takes it.
     */
    private fun drain(
        worker: Worker,
        stream: InputStream,
        errors: Boolean,
    ) {
        try {
            stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { stray(worker, it, errors) } }
        } catch (_: IOException) {
            // the stream was closed by ending the worker
        }
    }

    /**
     * A line that the worker's JVM itself wrote to its standard output or error, outside the protocol. It goes to the
     * output of the run the worker hosts, as a Gradle run shows it, between the program's lines: while the program's
     * output ends inside a line, it waits, up to [WAITING_LIMIT] lines. Outside a run, the log has the first
     * [STRAYS_LOGGED] lines of standard output at debug, and how many there were once the worker exits, and those of
     * standard error, [errors], as warnings, as a crash says why there: the first [STRAYS_LOGGED], and the last of the
     * others once the worker exits.
     */
    private fun stray(
        worker: Worker,
        line: String,
        errors: Boolean,
    ) {
        val listener = synchronized(lock) { worker.run }?.let(listeners::get)
        if (listener == null) {
            when {
                !errors -> {
                    if (worker.strays.getAndIncrement() < STRAYS_LOGGED) {
                        logger.debug("execution worker output outside a run: {} text={}", worker, line)
                    }
                }

                worker.errors.getAndIncrement() < STRAYS_LOGGED ->
                    logger.warn("execution worker errors outside a run: {} text={}", worker, line)

                else ->
                    synchronized(worker.lastErrors) {
                        worker.lastErrors += line
                        if (worker.lastErrors.size > STRAYS_LOGGED) worker.lastErrors.removeFirst()
                    }
            }
            return
        }
        worker.printed = true
        synchronized(worker.delivery) {
            if (worker.lineOpen && worker.waiting.size < WAITING_LIMIT) {
                worker.waiting += line
            } else {
                release(worker, listener)
                listener.output("$line\n")
            }
        }
    }

    /** The program's [text] for the run of [listener], then its JVM's lines that waited for the program's line to end. */
    private fun programOutput(
        worker: Worker,
        listener: ProgramEvents,
        text: String,
    ) {
        synchronized(worker.delivery) {
            listener.output(text)
            if (text.isNotEmpty()) worker.lineOpen = !text.endsWith('\n')
            if (!worker.lineOpen) release(worker, listener)
        }
    }

    /** Delivers the JVM's lines that wait, to [listener]. Called under the worker's [Worker.delivery]. */
    private fun release(
        worker: Worker,
        listener: ProgramEvents,
    ) {
        while (worker.waiting.isNotEmpty()) listener.output("${worker.waiting.removeFirst()}\n")
    }

    /**
     * Why [worker] stopped, which it did on its own: as it started, before it was ready, where the JVM options it was
     * given may be why, or later.
     */
    private fun unexpectedly(worker: Worker): String =
        when {
            worker.ready.isDone -> "The execution worker stopped unexpectedly"
            worker.options.isEmpty() -> "The execution worker stopped as it started"
            else -> "The execution worker stopped as it started, with the JVM options ${worker.options}"
        } + if (worker.printed) "; what its JVM printed, in the output above, says why" else ""

    /** One of the worker's own events of a run, its output or its memory once it has ended, which [line] carries. */
    private fun workerEvent(
        worker: Worker,
        event: JsonObject,
        line: String,
    ) {
        val id = event["id"]?.asLong
        when (event["event"]?.asString) {
            "output" -> id?.let(listeners::get)?.let { programOutput(worker, it, event["text"].asString) }

            "usage" ->
                measured(
                    worker,
                    Usage(
                        event["heapUsed"].asLong,
                        event["heapMax"].asLong,
                        event["threads"].asInt,
                        event["poolThreads"]?.asInt ?: 0,
                        event["consoles"]?.asInt ?: 0,
                        event["runs"]?.asInt ?: 0,
                        id,
                    ),
                )

            else -> logger.warn("execution worker sent an unknown event: {} event={}", worker, line)
        }
    }

    /**
     * The worker has started and reports its identity: it serves runs if that is the identity this server
     * expects, and is ended otherwise, before it receives anything.
     */
    private fun accept(
        worker: Worker,
        identity: RunWorkerIdentity?,
    ) {
        if (identity == expected) {
            logger.info("execution worker ready: {} identity={}", worker, identity)
            synchronized(lock) { if (!worker.release()) worker.end("The execution worker could not be started") }
        } else {
            val reported = identity?.toString() ?: "an earlier protocol"
            logger.error("execution worker runs {}, but the language server expects {}; ending it: {}", reported, expected, worker)
            worker.end("The execution worker runs $reported, but the language server expects $expected; restart the language server")
        }
        worker.ready.complete(Unit)
    }

    /**
     * The worker reports what it kept after a run. Past a limit, it serves no further run: each limit bounds
     * one thing on its own, so what is left of ended programs and the runtime's pool are not hidden in the heap
     * and thread totals. The heap is not compared with an earlier run's: each is what the JVM left after a
     * collection it ran when it chose to, so the difference between two says nothing about what runs retain.
     */
    private fun measured(
        worker: Worker,
        usage: Usage,
    ) {
        // the next run can start before this report arrives; its client ID is then not this run's
        val runId = synchronized(lock) { worker.runId.takeIf { worker.run == null || worker.run == usage.run } }
        logger.info(
            "execution worker after a run: {} {} heapAfterCollections={}MB heapMax={}MB threads={} poolThreads={} consoles={} runs={}",
            worker,
            usage.run?.let { runFields(it, runId) } ?: "workerRun=?",
            usage.heapUsed / MB,
            usage.heapMax / MB,
            usage.threads,
            usage.poolThreads,
            usage.consoles,
            usage.runs,
        )
        synchronized(lock) {
            val first = worker.first ?: usage.also { worker.first = it }
            worker.usage = usage
            val worn =
                when {
                    usage.consoles > 0 -> "${usage.consoles} consoles of ended programs still registered"

                    usage.runs > 0 -> "${usage.runs} ended runs still tracked"

                    usage.heapUsed > limits.heap -> "${usage.heapUsed / MB} MB heap in use after a collection, over ${limits.heap / MB} MB"

                    usage.threads > limits.threads -> "${usage.threads} threads after a run, over ${limits.threads}"

                    usage.poolThreads > first.poolThreads ->
                        "its runtime pool grew from ${first.poolThreads} to ${usage.poolThreads} threads"

                    else -> null
                }
            if (worn == null || worker.ended != null) return
            logger.warn("execution worker serves no further run: {} keeps={}", worker, worn)
            worker.worn = worn
            if (worker.run == null) retire(worker)
        }
    }

    /**
     * The worker's run has ended: keep it for the next run if no other worker is idle and it does not hold
     * too much, or let it go.
     */
    private fun finished(
        worker: Worker,
        id: Long,
    ) {
        synchronized(lock) {
            if (hosts.remove(id) == null || worker.run != id) return
            worker.run = null
            if (closed) return
            if (worker.worn != null || workers.any { it !== worker && it.run == null && it.ended == null }) {
                retire(worker)
            } else {
                worker.timer = after(limits.idle) { synchronized(lock) { if (worker.run == null) retire(worker) } }
            }
        }
    }

    /** Asks an idle worker to exit; its reader thread then removes it. Called under [lock]. */
    private fun retire(worker: Worker) {
        worker.ended = "The execution worker was shut down"
        worker.write(JsonObject().apply { addProperty("command", "shutdown") })
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
        ids.forEach { id ->
            abandon(id)
            listeners.remove(id)?.exited(null, reason)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(RunWorkerProcess::class.java)

        /** A run as the logs give it: the worker's run number, and the client's ID when it has one. */
        fun runFields(
            id: Long,
            runId: String?,
        ) = "workerRun=$id" + runId?.let { " run=$it" }.orEmpty()

        private const val MB = 1024L * 1024

        /** JVM options as the logs give them, after the rest of a line: nothing for none. */
        fun logged(options: List<String>) = if (options.isEmpty()) "" else " options=$options"

        /** How many of a worker's lines outside a run the log has; see [stray]. */
        private const val STRAYS_LOGGED = 20

        /** How many of a JVM's lines wait at most for the program's line to end; see [stray]. */
        private const val WAITING_LIMIT = 1000

        /** The longest token a connection may show; the server's are UUIDs. */
        private const val TOKEN_LIMIT = 200

        /** How long a connection to a worker's port has to show the token: the worker writes it as it connects. */
        private val TOKEN_WAIT = 5.seconds

        /** How many connections to a worker's port may wait to be taken, the worker's own among strays. */
        private const val BACKLOG = 8

        /**
         * The worker runs from the language server's own classpath, on the same Java runtime, with the JVM options
         * [options] after the server's own `-Dfile.encoding`, which one of theirs replaces, as the JVM takes the last.
         */
        fun defaultCommand(options: List<String>): List<String> =
            listOf(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-Dfile.encoding=UTF-8",
            ) + options +
                listOf(
                    "-cp",
                    System.getProperty("java.class.path"),
                    RunWorker::class.java.name,
                )
    }
}
