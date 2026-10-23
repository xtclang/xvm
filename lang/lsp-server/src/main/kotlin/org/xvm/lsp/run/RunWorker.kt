package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleRepository
import org.xvm.debug.BreakpointState
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.runtime.SessionDebugger
import org.xvm.util.Severity
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer
import java.lang.management.ManagementFactory
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Entry point of the execution worker JVM. The language server starts it on demand and exchanges one
 * JSON object per line over stdin and stdout. Program output, including anything the runtime writes to
 * the process streams directly, travels as events and so can never corrupt the protocol. Stdin closing,
 * which is also what happens when the language server exits or dies, stops any run and ends the worker.
 *
 * Commands: `run` (`id`, `modulePath`, `module`, `method`, `args`, `rootDir`, and `breakpoints`, `exceptions` and
 * `stopOnEntry` to run under the debugger), `input` (`id`, `text`), `endInput` (`id`), `stop` (`id`), `shutdown`;
 * for a run under the debugger, `exceptionStops` (`id`, `all`, `uncaught`, `allTypes`, `uncaughtTypes`), `pause`
 * (`id`), `evaluate` (`id`, `request`, `frame`, `expression`), `exceptionInfo` (`id`, `request`, `thread`),
 * `setBreakpoints` (`id`, `request`, `path`, `breakpoints`), `threads` (`id`, `request`), `stackTrace` (`id`,
 * `request`, `thread`), `scopes` (`id`, `request`, `frame`), `variables` (`id`, `request`, `reference`), `step`
 * (`id`, `request`, `thread`, `step`: `over`, `in` or `out`) and `continue` (`id`). The debug data's JSON forms are
 * in [RunWorkerDebug].
 * Events: `ready` (`pid`, `protocol`, `xdk`; see [RunWorkerIdentity]), `started` (`id`), `output` (`id`,
 * `text`), `exit` (`id`, `code`, `error`), `usage` (`id`, `heapUsed`, `heapMax`, `threads`, `poolThreads`,
 * `consoles`, `runs`, sent after the run's exit), `log` (`text`); for a run under the debugger, `breakpoints` (`id`,
 * `path`, `breakpoints`: how a file's breakpoints were installed before the program started), `stopped` (`id`,
 * `reason`, `description`, `thread`, `breakpoints`), `logged` (`id`, `text`: a logpoint's message) and `reply`
 * (`id`, `request`, and the answer or `error`).
 */
object RunWorker {
    /** The error of a request for the debugger of a run without one, or of one that ended. */
    private const val NOT_DEBUGGED = "The program is not running under the debugger"

    @JvmStatic
    fun main(args: Array<String>) {
        val channel = RunWorkerChannel(PrintStream(FileOutputStream(FileDescriptor.out), false, Charsets.UTF_8))
        val runs = Runs(channel)
        System.setOut(PrintStream(StrayOutput(runs::stray), true, Charsets.UTF_8))
        System.setErr(PrintStream(StrayOutput(runs::stray), true, Charsets.UTF_8))
        XdkLibraries.configure()
        channel.send("ready") {
            addProperty("pid", ProcessHandle.current().pid())
            RunWorkerIdentity.current().addTo(this)
        }

        BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val command = JsonParser.parseString(line).asJsonObject
                when (command["command"]?.asString) {
                    "run" -> runs.start(command)
                    "input" -> runs.input(command["id"].asLong, command["text"].asString)
                    "endInput" -> runs.endInput(command["id"].asLong)
                    "stop" -> runs.stop(command["id"].asLong)
                    "setBreakpoints" -> runs.setBreakpoints(command)
                    "threads" -> runs.threads(command)
                    "stackTrace" -> runs.stackTrace(command)
                    "scopes" -> runs.scopes(command)
                    "variables" -> runs.variables(command)
                    "evaluate" -> runs.evaluate(command)
                    "exceptionInfo" -> runs.exceptionInfo(command)
                    "step" -> runs.step(command)
                    "exceptionStops" -> runs.exceptionStops(command)
                    "pause" -> runs.pause(command["id"].asLong)
                    "continue" -> runs.resume(command["id"].asLong)
                    "shutdown" -> break
                    else -> channel.log("Unknown worker command: $line")
                }
            }
        }
        runs.stopAll()
        // runtime service threads are not daemons, so a finished worker exits explicitly
        exitProcess(0)
    }

    /**
     * One run at a time, each a fresh application container in the shared runtime; the server starts a
     * worker for each program it runs, so a second run here is refused only if a client bypasses it.
     */
    private class Runs(
        private val channel: RunWorkerChannel,
    ) {
        private val active = ConcurrentHashMap<Long, EmbeddingSupport.Control>()
        private val inputs = ConcurrentHashMap<Long, ConsoleInput>()

        /** The runtime debugger of each run started with breakpoints. */
        private val debuggers = ConcurrentHashMap<Long, SessionDebugger>()

        /** The running program's output, which text the runtime prints directly joins in order. */
        @Volatile
        private var output: OutputBatches? = null

        /** Text the runtime prints to System.out or System.err: shown with the running program, or logged. */
        fun stray(text: String) {
            output?.write(text) ?: channel.log(text.trimEnd())
        }

        fun start(command: JsonObject) {
            val id = command["id"].asLong
            if (active.isNotEmpty()) {
                channel.exit(id, null, "Another program is still running")
                return
            }
            val output = OutputBatches({ channel.output(id, it) })
            val console = PrintWriter(output, true)
            val errors = RunErrors(console)
            // registered before the run starts: commands are read in order, so input for this run
            // can only arrive after this
            val input = ConsoleInput().also { inputs[id] = it }
            val control =
                try {
                    val repositories = command.strings("modulePath").map { ModuleRepository.of(File(it), true) }
                    val debugger =
                        command.getAsJsonArray("breakpoints")?.let { breakpoints ->
                            val exceptions = RunWorkerDebug.exceptionsOf(command["exceptions"])
                            val stopOnEntry = command["stopOnEntry"]?.asBoolean == true
                            debugger(id, repositories, RunWorkerDebug.sourcesOf(breakpoints), exceptions, stopOnEntry, output)
                        }
                    EmbeddingSupport.instance().runMethod(
                        LinkedRepository(*repositories.toTypedArray()),
                        command["module"].asString,
                        null,
                        EmbeddingSupport.EntryPoint(command.string("method") ?: "run", command.strings("args")),
                        console,
                        // the editors always show what is typed: the echo request is not passed on to them yet
                        EmbeddingSupport.ConsoleInput.of(BufferedReader(input)),
                        command.string("rootDir")?.let(::File),
                        debugger,
                        errors,
                    )
                } catch (failure: Exception) {
                    inputs.remove(id)
                    debuggers.remove(id)
                    channel.exit(id, null, failure.toString())
                    return
                }
            if (control == null) {
                inputs.remove(id)
                debuggers.remove(id)
                channel.exit(id, null, errors.failure() ?: "The module could not be started")
                return
            }

            active[id] = control
            this.output = output
            channel.send("started") { addProperty("id", id) }
            Thread
                .ofPlatform()
                .name("xtc-run-$id")
                .daemon(true)
                .start {
                    try {
                        control.join()
                        control.close()
                    } finally {
                        this.output = null
                        output.close()
                        inputs.remove(id)?.end()
                        debuggers.remove(id)
                        active.remove(id)
                        // counted before the exit, so the next run, which may start right after it, is not
                        val kept = Kept(poolThreads(), EmbeddingSupport.instance().registeredConsoles(), active.size + inputs.size)
                        channel.exit(id, control.result(), errors.failure())
                        channel.usage(id, kept)
                    }
                }
        }

        fun input(
            id: Long,
            text: String,
        ) {
            inputs[id]?.offer(text)
        }

        fun endInput(id: Long) {
            inputs[id]?.end()
        }

        fun stop(id: Long) {
            // a program waiting for a line gets the end of input, so nothing stays blocked on it
            inputs[id]?.end()
            // closing kills the task and waits for it, so keep the command reader responsive; it also
            // releases a program its debugger holds stopped
            active[id]?.let { control -> Thread.ofVirtual().start { control.close() } }
        }

        /**
         * The runtime debugger of a run started with breakpoints: it knows the program's own modules, so the
         * breakpoints in their files are verified, and installs the breakpoints, reporting each file's, the exception
         * stops and the stop on entry before the program starts. Its stops are reported as `stopped` events, after the
         * program's output so far.
         */
        private fun debugger(
            id: Long,
            repositories: List<ModuleRepository>,
            sources: List<SourceBreakpoints>,
            exceptions: ExceptionStops,
            stopOnEntry: Boolean,
            output: OutputBatches,
        ): SessionDebugger {
            val debugger =
                SessionDebugger(
                    object : SessionDebugger.Listener {
                        override fun stopped(stop: SessionDebugger.Stop) {
                            output.sendNow()
                            channel.send("stopped") {
                                addProperty("id", id)
                                RunWorkerDebug.stop(
                                    this,
                                    StopState(stop.reason(), stop.description(), stop.threadId(), stop.breakpointIds()),
                                )
                            }
                        }

                        override fun logged(message: String) {
                            // after what the program printed before it reached the logpoint
                            output.sendNow()
                            channel.send("logged") {
                                addProperty("id", id)
                                addProperty("text", message)
                            }
                        }
                    },
                )
            repositories.forEach { repository ->
                repository.moduleNames.forEach { name -> repository.loadModule(name)?.let(debugger::addModule) }
            }
            sources.forEach { source ->
                channel.send("breakpoints") {
                    addProperty("id", id)
                    addProperty("path", source.path)
                    add("breakpoints", RunWorkerDebug.states(install(debugger, source)))
                }
            }
            debugger.setExceptionStops(exceptions.all, exceptions.allTypes, exceptions.uncaught, exceptions.uncaughtTypes)
            // after the modules are known, so it stops in the program's code, not in the libraries before it
            if (stopOnEntry) debugger.stopOnEntry()
            debuggers[id] = debugger
            return debugger
        }

        fun setBreakpoints(command: JsonObject) =
            reply(command) { debugger -> add("breakpoints", RunWorkerDebug.states(install(debugger, RunWorkerDebug.sourceOf(command)))) }

        fun threads(command: JsonObject) =
            reply(command) { debugger ->
                add("threads", RunWorkerDebug.threads(debugger.threads().map { ThreadState(it.id(), it.name()) }))
            }

        fun stackTrace(command: JsonObject) =
            reply(command) { debugger ->
                val frames = debugger.stackTrace(command["thread"].asInt).map { FrameState(it.id(), it.name(), it.path(), it.line()) }
                add("frames", RunWorkerDebug.frames(frames))
            }

        fun scopes(command: JsonObject) =
            reply(command) { debugger ->
                val scopes = debugger.scopes(command["frame"].asInt).map { ScopeState(it.name(), it.variablesReference()) }
                add("scopes", RunWorkerDebug.scopes(scopes))
            }

        /**
         * Answers once the waiting fiber that holds the variables has read them on its own thread, so the commands
         * that follow, such as `continue`, do not wait for the read.
         */
        fun variables(command: JsonObject) {
            val read =
                debuggers[command["id"].asLong]?.variables(command["reference"].asInt)
                    ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
            read.whenComplete { variables, failure ->
                answer(command) {
                    if (failure != null) {
                        addProperty("error", reason(failure))
                    } else {
                        add("variables", RunWorkerDebug.variables(variables.map(::state)))
                    }
                }
            }
        }

        /** Answers, as `variables` does, with an expression's value, or the reason it cannot be evaluated. */
        fun evaluate(command: JsonObject) {
            val read =
                debuggers[command["id"].asLong]?.evaluate(command["frame"].asInt, command["expression"].asString)
                    ?: return answer(command) { addProperty("error", NOT_DEBUGGED) }
            read.whenComplete { variable, failure ->
                answer(command) {
                    if (failure != null) {
                        addProperty("error", reason(failure))
                    } else {
                        add("variable", RunWorkerDebug.variable(state(variable)))
                    }
                }
            }
        }

        private fun state(variable: SessionDebugger.Variable) =
            VariableState(variable.name(), variable.value(), variable.type(), variable.variablesReference())

        /** A failed read's reason, as the runtime debugger put it. */
        private fun reason(failure: Throwable): String {
            val cause = (failure as? CompletionException)?.cause ?: failure
            return cause.message ?: cause.toString()
        }

        /** Answers whether the fiber was stepped; one that does not wait is not, and the program stays stopped. */
        fun step(command: JsonObject) =
            reply(command) { debugger ->
                val step = SessionDebugger.Step.valueOf(command["step"].asString.uppercase())
                addProperty("stepped", debugger.step(command["thread"].asInt, step))
            }

        fun exceptionStops(command: JsonObject) {
            val stops = RunWorkerDebug.exceptionsOf(command)
            debuggers[command["id"].asLong]?.setExceptionStops(stops.all, stops.allTypes, stops.uncaught, stops.uncaughtTypes)
        }

        /** Answers with the exception the fiber stopped the program at; none when it stopped it for another reason. */
        fun exceptionInfo(command: JsonObject) =
            reply(command) { debugger ->
                debugger.exceptionInfo(command["thread"].asInt)?.let { info ->
                    add(
                        "exception",
                        RunWorkerDebug.exceptionInfo(ExceptionInfoState(info.type(), info.text(), info.stackTrace(), info.uncaught())),
                    )
                }
            }

        fun pause(id: Long) {
            debuggers[id]?.pause()
        }

        fun resume(id: Long) {
            debuggers[id]?.resume()
        }

        private fun install(
            debugger: SessionDebugger,
            source: SourceBreakpoints,
        ): List<BreakpointState> =
            debugger
                .setBreakpoints(
                    source.path,
                    source.breakpoints.map { SessionDebugger.Line(it.id, it.line, it.condition, it.hitCondition, it.logMessage) },
                ).map { BreakpointState(it.id(), it.line(), it.verified(), it.message()) }

        /** Answers a request for a run's debugger; a run without one, or one that ended, answers with an error. */
        private fun reply(
            command: JsonObject,
            fill: JsonObject.(SessionDebugger) -> Unit,
        ) {
            val debugger = debuggers[command["id"].asLong]
            answer(command) { if (debugger == null) addProperty("error", NOT_DEBUGGED) else fill(debugger) }
        }

        /** Sends a reply; its content is made first, since asking the debugger must not wait while holding the channel. */
        private fun answer(
            command: JsonObject,
            fill: JsonObject.() -> Unit,
        ) {
            val content = JsonObject().apply(fill)
            channel.send("reply") {
                addProperty("id", command["id"].asLong)
                addProperty("request", command["request"].asLong)
                content.entrySet().forEach { (name, value) -> add(name, value) }
            }
        }

        /** Threads of the runtime's own worker pool, which is fixed in size. */
        private fun poolThreads(): Int = Thread.getAllStackTraces().keys.count { it.threadGroup?.name == "XVM" }

        fun stopAll() {
            inputs.values.forEach(ConsoleInput::end)
            active.values.forEach { runCatching { it.close() } }
        }

        private fun ErrorList.failure(): String? =
            errors
                .filter { it.severity.isAtLeast(Severity.ERROR) }
                .joinToString("\n") { it.messageText }
                .ifEmpty { null }
    }

    /**
     * Console input for one run: text the client sends, in order, until the client ends it or the run
     * stops. A read waits for text; after the end, it returns end of input.
     */
    private class ConsoleInput : Reader() {
        private val lock = ReentrantLock()
        private val available = lock.newCondition()
        private val pending = StringBuilder()
        private var ended = false

        fun offer(text: String) =
            lock.withLock {
                if (!ended) {
                    pending.append(text)
                    available.signalAll()
                }
            }

        fun end() =
            lock.withLock {
                ended = true
                available.signalAll()
            }

        override fun read(
            buffer: CharArray,
            offset: Int,
            length: Int,
        ): Int =
            lock.withLock {
                try {
                    while (pending.isEmpty() && !ended) available.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
                if (pending.isEmpty()) return -1
                val count = minOf(length, pending.length)
                pending.getChars(0, count, buffer, offset)
                pending.delete(0, count)
                count
            }

        override fun close() = end()
    }

    /** Text the runtime prints to the process streams, passed on a line at a time, or in bounded pieces. */
    private class StrayOutput(
        private val text: (String) -> Unit,
    ) : OutputStream() {
        private val line = ByteArrayOutputStream()

        @Synchronized
        override fun write(byte: Int) {
            line.write(byte)
            if (byte == '\n'.code || line.size() >= OutputBatches.LIMIT) flush()
        }

        @Synchronized
        override fun flush() {
            if (line.size() == 0) return
            val pending = line.toString(Charsets.UTF_8)
            line.reset()
            text(pending)
        }
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.strings(name: String): List<String> = getAsJsonArray(name)?.map { it.asString }.orEmpty()
}

/**
 * One run's program output, sent in batches so that a program printing in a loop sends a few events rather
 * than one per print: text goes out [delay] after it is written, or as soon as [limit] characters wait. No
 * more than [limit] characters ever wait, so a program printing faster than the server reads blocks in its
 * print until the server catches up, and its output never piles up in the worker. A flush does not end a
 * batch; [close] sends the rest.
 */
internal class OutputBatches(
    private val send: (String) -> Unit,
    private val limit: Int = LIMIT,
    private val delay: Duration = DELAY,
) : Writer() {
    private val pending = StringBuilder()
    private var scheduled = false

    @Synchronized
    override fun write(
        buffer: CharArray,
        offset: Int,
        length: Int,
    ) {
        var from = offset
        val end = offset + length
        while (from < end) {
            val count = minOf(end - from, limit - pending.length)
            pending.appendRange(buffer, from, from + count)
            from += count
            if (pending.length >= limit) sendPending(all = false)
        }
        if (pending.isNotEmpty() && !scheduled) {
            scheduled = true
            timers.schedule(
                {
                    synchronized(this) {
                        scheduled = false
                        sendPending(all = false)
                    }
                },
                delay.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    override fun flush() {}

    /** Sends what waits at once: when the program stops at a breakpoint, its output so far comes before the stop. */
    @Synchronized
    fun sendNow() = sendPending(all = false)

    /** Sends everything still waiting; the run's exit follows its output. */
    @Synchronized
    override fun close() = sendPending(all = true)

    /** Sends what waits, keeping a trailing high surrogate for the next batch unless [all]. */
    private fun sendPending(all: Boolean) {
        var count = pending.length
        // a surrogate pair stays in one event
        if (!all && count > 1 && pending[count - 1].isHighSurrogate()) count--
        if (count == 0) return
        val text = pending.substring(0, count)
        pending.delete(0, count)
        send(text)
    }

    companion object {
        /** The most characters one output event carries and a run's output holds while it waits. */
        const val LIMIT = 16_384

        /** How long written text waits for more before it is sent. */
        val DELAY = 20.milliseconds

        // the thread starts when a program first prints, on one of the runtime's pool threads, and would join
        // their group, which is what the worker reports as the runtime's pool; it has a group of its own
        private val timers =
            Executors.newSingleThreadScheduledExecutor(
                Thread
                    .ofPlatform()
                    .group(ThreadGroup("xtc-run-output"))
                    .name("xtc-run-output")
                    .daemon(true)
                    .factory(),
            )
    }
}

/**
 * What a worker reports in `ready`, and the server requires before it sends the worker anything: the
 * protocol both sides speak and the bundled XDK the worker runs, which must be the one the server compiles
 * with. They differ when the server's files are replaced while it runs, e.g. by an IDE plugin update.
 */
internal data class RunWorkerIdentity(
    val protocol: Int,
    val xdk: String,
) {
    fun addTo(event: JsonObject) {
        event.addProperty("protocol", protocol)
        event.addProperty("xdk", xdk)
    }

    override fun toString() = "protocol $protocol with XDK ${xdk.take(12)}"

    companion object {
        /** Raised whenever a command or event changes; the first protocol had no number. */
        const val PROTOCOL = 9

        /** This JVM's identity. */
        fun current() = RunWorkerIdentity(PROTOCOL, XdkLibraries.fingerprint)

        /** The identity a `ready` event reports, or null when it has none, as from a first-protocol worker. */
        fun of(event: JsonObject): RunWorkerIdentity? {
            val protocol = event["protocol"]?.takeUnless { it.isJsonNull }?.asInt ?: return null
            val xdk = event["xdk"]?.takeUnless { it.isJsonNull }?.asString ?: return null
            return RunWorkerIdentity(protocol, xdk)
        }
    }
}

/**
 * What a run reports. A warning, such as one for arguments the entry method does not take, is also
 * shown in the run's output when it is reported, which is before the program starts.
 */
private class RunErrors(
    private val console: PrintWriter,
) : ErrorList(100) {
    override fun log(err: ErrorListener.ErrorInfo) {
        if (err.severity == Severity.WARNING) console.println("Warning: ${err.messageText}")
        super.log(err)
    }
}

/**
 * What a run left behind: the runtime's pool threads, and the consoles and runs still registered for
 * programs that have ended, which should be none.
 */
internal data class Kept(
    val poolThreads: Int,
    val consoles: Int,
    val runs: Int,
)

/** Writes worker events, one JSON object per line, to the protocol stream. */
internal class RunWorkerChannel(
    private val out: PrintStream,
) {
    @Synchronized
    fun send(
        event: String,
        fill: JsonObject.() -> Unit = {},
    ) {
        out.println(JsonObject().apply { addProperty("event", event) }.apply(fill).toString())
        out.flush()
    }

    fun output(
        id: Long,
        text: String,
    ) = send("output") {
        addProperty("id", id)
        addProperty("text", text)
    }

    fun exit(
        id: Long,
        code: Long?,
        error: String?,
    ) = send("exit") {
        addProperty("id", id)
        code?.let { addProperty("code", it) }
        error?.let { addProperty("error", it) }
    }

    fun log(text: String) = send("log") { addProperty("text", text) }

    /**
     * Reports what the worker holds once a run has ended: the heap in use after a collection, the live
     * threads, and what the run [kept]. The server retires a worker whose runs leave too much behind.
     */
    fun usage(
        id: Long,
        kept: Kept,
    ) {
        System.gc()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        send("usage") {
            addProperty("id", id)
            addProperty("heapUsed", heap.used)
            addProperty("heapMax", heap.max)
            addProperty("threads", ManagementFactory.getThreadMXBean().threadCount)
            addProperty("poolThreads", kept.poolThreads)
            addProperty("consoles", kept.consoles)
            addProperty("runs", kept.runs)
        }
    }
}
