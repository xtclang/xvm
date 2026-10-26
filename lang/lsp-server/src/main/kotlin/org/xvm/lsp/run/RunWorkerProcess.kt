package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import org.xvm.debug.BreakpointState
import org.xvm.debug.ChildFilter
import org.xvm.debug.DebugSetup
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.concurrent.CancellationException
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
 * A worker receives nothing until it reports the identity the server expects (the protocol version and
 * the bundled XDK, see [RunWorkerIdentity]); one that reports another is ended and its run says why.
 * After each run a worker reports the heap it still uses, its threads, and what the run left behind;
 * one past [Limits.heap] or [Limits.threads], whose heap grew more than [Limits.growth] since its first
 * run, whose runtime pool grew, or that still holds a console or run of an ended program serves no
 * further run.
 */
internal class RunWorkerProcess(
    private val command: () -> List<String> = ::defaultCommand,
    private val limits: Limits = Limits(),
    expected: () -> RunWorkerIdentity = RunWorkerIdentity::current,
) : Closeable {
    /** Receives one run's events, in order, on its worker's reader thread. */
    interface Listener {
        fun output(text: String)

        fun exited(
            code: Long?,
            error: String?,
        )

        /** How one file's breakpoints were installed, before a run under the debugger started. */
        fun breakpoints(
            path: String,
            states: List<BreakpointState>,
        ) {}

        /** A run under the debugger stopped; it waits until [resume]. */
        fun stopped(stop: StopState) {}

        /** A logpoint of a debugged run logged a message. */
        fun logged(text: String) {}
    }

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
    )

    /**
     * How many programs run at once; how long a worker may take to start, to stop, or stay idle; and how much
     * heap in use (in bytes), growth of it since the worker's first run, and how many threads a worker may
     * keep after a run and still serve the next.
     */
    data class Limits(
        val running: Int = 4,
        val startup: Duration = 60.seconds,
        val stop: Duration = 10.seconds,
        val idle: Duration = 10.minutes,
        val heap: Long = 512L * MB,
        val growth: Long = 128L * MB,
        val threads: Int = 200,
    )

    /**
     * What a worker keeps after a run: the heap in use after a collection, the heap's maximum, its threads,
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

    /** One worker JVM and the run it hosts, if any. Guarded by [lock], apart from its reader thread. */
    private inner class Worker(
        val process: Process,
    ) {
        val input = PrintStream(process.outputStream, true, Charsets.UTF_8)
        val ready = CompletableFuture<Unit>()
        var run: Long? = null
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
    }

    private val lock = Any()
    private val workers = mutableListOf<Worker>()
    private val listeners = ConcurrentHashMap<Long, Listener>()
    private val hosts = ConcurrentHashMap<Long, Worker>()
    private val nextId = AtomicLong(1)

    /** Requests to a run's debugger that wait for the worker's reply, by request number. */
    private val pending = ConcurrentHashMap<Long, Pending>()
    private val nextRequest = AtomicLong(1)

    private class Pending(
        val run: Long,
        val reply: CompletableFuture<JsonObject>,
    )

    private val expected by lazy(expected)
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
                // a worker that is being ended or retired, or holds too much, is never reused
                val worker =
                    workers.firstOrNull { it.run == null && it.ended == null && it.worn == null && it.process.isAlive }
                        ?: if (workers.count { it.run != null } >= limits.running) {
                            return@synchronized "${limits.running} programs are already running; stop one to run another"
                        } else {
                            launch() ?: return@synchronized "The execution worker could not be started"
                        }
                worker.timer?.cancel(false)
                worker.timer = null
                worker.run = id
                hosts[id] = worker
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
                            request.debug?.let { setup ->
                                add("breakpoints", RunWorkerDebug.sources(setup.breakpoints))
                                add("exceptions", RunWorkerDebug.exceptions(setup.exceptions))
                                if (setup.stopOnEntry) addProperty("stopOnEntry", true)
                            }
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

    /** Replaces one file's breakpoints in a run under the debugger; answers how they were installed. */
    fun setBreakpoints(
        id: Long,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>> =
        request("setBreakpoints", id) {
            val source = RunWorkerDebug.source(breakpoints)
            source.keySet().forEach { add(it, source[it]) }
        }.thenApply { RunWorkerDebug.statesOf(it.getAsJsonArray("breakpoints")) }

    /** The fibers of a stopped run; none while it runs. */
    fun threads(id: Long): CompletableFuture<List<ThreadState>> =
        request("threads", id).thenApply { RunWorkerDebug.threadsOf(it.getAsJsonArray("threads")) }

    /** A stopped fiber's stack, innermost frame first. */
    fun stackTrace(
        id: Long,
        thread: Int,
    ): CompletableFuture<List<FrameState>> =
        request("stackTrace", id) { addProperty("thread", thread) }.thenApply { RunWorkerDebug.framesOf(it.getAsJsonArray("frames")) }

    /** A stopped frame's scopes. */
    fun scopes(
        id: Long,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> =
        request("scopes", id) { addProperty("frame", frame) }.thenApply { RunWorkerDebug.scopesOf(it.getAsJsonArray("scopes")) }

    /** The variables of a scope, or the fields or elements of a variable, as the waiting fiber read them. */
    fun variables(
        id: Long,
        reference: Int,
        hex: Boolean,
        filter: ChildFilter,
        start: Int,
        count: Int,
    ): CompletableFuture<List<VariableState>> =
        request("variables", id) {
            addProperty("reference", reference)
            addProperty("hex", hex)
            addProperty("filter", filter.name)
            addProperty("start", start)
            addProperty("count", count)
        }.thenApply { RunWorkerDebug.variablesOf(it.getAsJsonArray("variables")) }

    /** Replaces a debugged run's exception stops. */
    fun setExceptionStops(
        id: Long,
        stops: ExceptionStops,
    ) = command("exceptionStops", id) {
        val json = RunWorkerDebug.exceptions(stops)
        json.keySet().forEach { add(it, json[it]) }
    }

    /** Changes a variable of the stopped program to an expression's value; fails with the reason when it cannot. */
    fun setVariable(
        id: Long,
        reference: Int,
        name: String,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> =
        request("setVariable", id) {
            addProperty("reference", reference)
            addProperty("name", name)
            addProperty("expression", expression)
            addProperty("hex", hex)
        }.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }

    /** Evaluates an expression in a stopped frame; fails with the reason when it cannot. */
    fun evaluate(
        id: Long,
        frame: Int,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> =
        request("evaluate", id) {
            addProperty("frame", frame)
            addProperty("expression", expression)
            addProperty("hex", hex)
        }.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }

    /**
     * Runs code in a stopped frame, on the fiber that waits there, for at most [limit], and with [program], the whole
     * program meanwhile; fails with the reason when it cannot. Cancelling the answer ends the run: the worker is told,
     * and its late reply is dropped.
     */
    fun runCode(
        id: Long,
        frame: Int,
        expression: String,
        limit: Duration,
        program: Boolean,
        hex: Boolean,
    ): CompletableFuture<VariableState> {
        val (number, reply) =
            send("runCode", id) {
                addProperty("frame", frame)
                addProperty("expression", expression)
                addProperty("limit", limit.inWholeMilliseconds)
                addProperty("program", program)
                addProperty("hex", hex)
            }
        val answer = reply.thenApply { RunWorkerDebug.variableOf(it.getAsJsonObject("variable")) }
        answer.whenComplete { _, failure ->
            if (failure is CancellationException && pending.remove(number) != null) {
                command("cancel", id) { addProperty("request", number) }
            }
        }
        return answer
    }

    /** The exception a stopped run's fiber stopped it at; null when it stopped it for another reason. */
    fun exceptionInfo(
        id: Long,
        thread: Int,
    ): CompletableFuture<ExceptionInfoState?> =
        request("exceptionInfo", id) { addProperty("thread", thread) }
            .thenApply { reply -> reply.getAsJsonObject("exception")?.let(RunWorkerDebug::exceptionInfoOf) }

    /** Steps a stopped run's fiber; false when that fiber does not wait. */
    fun step(
        id: Long,
        thread: Int,
        kind: StepKind,
    ): CompletableFuture<Boolean> =
        request("step", id) {
            addProperty("thread", thread)
            addProperty("step", kind.name.lowercase())
        }.thenApply { it["stepped"].asBoolean }

    /** Pauses a debugged run at the next line one of its fibers reaches. */
    fun pause(id: Long) = command("pause", id)

    /** Resumes a stopped run. */
    fun resume(id: Long) = command("continue", id)

    /**
     * Sends a request to a run's debugger and answers with the worker's reply; fails when the run has no live
     * worker, ends before the reply, or its worker answers with an error.
     */
    private fun request(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit = {},
    ): CompletableFuture<JsonObject> = send(name, id, fill).second

    /** Sends a request as [request] does, and answers with its number too, by which a `cancel` names it. */
    private fun send(
        name: String,
        id: Long,
        fill: JsonObject.() -> Unit,
    ): Pair<Long, CompletableFuture<JsonObject>> {
        val number = nextRequest.getAndIncrement()
        val reply = CompletableFuture<JsonObject>()
        pending[number] = Pending(id, reply)
        val sent =
            synchronized(lock) {
                val worker = hosts[id]?.takeIf { it.process.isAlive }
                worker != null &&
                    worker.send(
                        JsonObject().apply {
                            addProperty("command", name)
                            addProperty("id", id)
                            addProperty("request", number)
                            fill()
                        },
                    )
            }
        if (!sent) pending.remove(number)?.reply?.completeExceptionally(IllegalStateException("The program is not running"))
        return number to reply
    }

    /** Fails the requests still waiting for a reply from a run that has ended. */
    private fun abandon(id: Long) {
        pending.entries.removeIf { (_, request) ->
            (request.run == id).also { if (it) request.reply.completeExceptionally(IllegalStateException("The program has ended")) }
        }
    }

    /** Process IDs of the live workers, for tests. */
    internal fun workerPids(): List<Long> = synchronized(lock) { workers.filter { it.process.isAlive }.map { it.process.pid() } }

    /** Runs the server still tracks, which is none once every run has ended; for tests. */
    internal fun tracked(): Int = listeners.size + hosts.size

    /** What each live worker kept after its last run, by process ID, for tests. */
    internal fun usage(): Map<Long, Usage> =
        synchronized(lock) {
            workers.filter { it.process.isAlive }.mapNotNull { worker -> worker.usage?.let { worker.process.pid() to it } }.toMap()
        }

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
                    worker.write(JsonObject().apply { addProperty("command", "shutdown") })
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

    /**
     * Delivers the worker's events until its output ends, then ends the runs it still hosts. Ending a worker
     * closes its streams, which ends the events as the worker's exit does.
     */
    private fun read(worker: Worker) {
        try {
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
                                abandon(id)
                                listeners.remove(id)?.exited(
                                    event["code"]?.asLong,
                                    event["error"]?.takeUnless { it.isJsonNull }?.asString,
                                )
                            }

                        "breakpoints" ->
                            id
                                ?.let(
                                    listeners::get,
                                )?.breakpoints(event["path"].asString, RunWorkerDebug.statesOf(event.getAsJsonArray("breakpoints")))

                        "stopped" -> id?.let(listeners::get)?.stopped(RunWorkerDebug.stopOf(event))

                        "logged" -> id?.let(listeners::get)?.logged(event["text"].asString)

                        "reply" ->
                            event["request"]?.asLong?.let(pending::remove)?.reply?.let { reply ->
                                val error = event["error"]?.asString
                                if (error == null) reply.complete(event) else reply.completeExceptionally(IllegalStateException(error))
                            }

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

                        "log" -> logger.info("execution worker: {}", event["text"]?.asString)

                        "ready" -> accept(worker, RunWorkerIdentity.of(event))

                        "started" -> logger.debug("execution worker: {}", line)

                        else -> logger.warn("execution worker sent an unknown event: {}", line)
                    }
                }
            }
        } catch (_: IOException) {
            // the stream was closed by ending the worker
        } finally {
            // a worker still running exits once its input ends
            worker.input.close()
            val runs =
                synchronized(lock) {
                    workers.remove(worker)
                    worker.timer?.cancel(false)
                    hosts.filterValues { it === worker }.keys.onEach(hosts::remove)
                }
            endRuns(runs, worker.ended ?: "The execution worker stopped unexpectedly")
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
        val pid = worker.process.pid()
        if (identity == expected) {
            logger.info("execution worker {} ready: {}", pid, identity)
            synchronized(lock) { if (!worker.release()) worker.end("The execution worker could not be started") }
        } else {
            val reported = identity?.toString() ?: "an earlier protocol"
            logger.error("execution worker {} runs {}, but the language server expects {}; ending it", pid, reported, expected)
            worker.end("The execution worker runs $reported, but the language server expects $expected; restart the language server")
        }
        worker.ready.complete(Unit)
    }

    /**
     * The worker reports what it kept after a run. Past a limit, it serves no further run: each limit bounds
     * one thing on its own, so what is left of ended programs, retained growth and the runtime's pool are
     * not hidden in the heap and thread totals.
     */
    private fun measured(
        worker: Worker,
        usage: Usage,
    ) {
        val pid = worker.process.pid()
        logger.info(
            "execution worker {} after run {}: {} MB heap in use of {} MB, {} threads ({} in the runtime's pool), {} consoles, {} runs",
            pid,
            usage.run,
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

                    usage.heapUsed > limits.heap -> "${usage.heapUsed / MB} MB heap in use after a run, over ${limits.heap / MB} MB"

                    usage.heapUsed - first.heapUsed > limits.growth ->
                        "${(usage.heapUsed - first.heapUsed) / MB} MB more heap in use than after its first run, over ${limits.growth / MB} MB"

                    usage.threads > limits.threads -> "${usage.threads} threads after a run, over ${limits.threads}"

                    usage.poolThreads > first.poolThreads ->
                        "its runtime pool grew from ${first.poolThreads} to ${usage.poolThreads} threads"

                    else -> null
                }
            if (worn == null || worker.ended != null) return
            logger.warn("execution worker {} keeps {}; it serves no further run", pid, worn)
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

        private const val MB = 1024L * 1024

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
