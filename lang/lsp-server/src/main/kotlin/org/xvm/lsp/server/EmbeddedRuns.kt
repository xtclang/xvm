package org.xvm.lsp.server

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.debug.BreakpointState
import org.xvm.debug.ChildFilter
import org.xvm.debug.CompletionsState
import org.xvm.debug.DebugSetup
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.ModuleState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepKind
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildResult
import org.xvm.lsp.adapter.xdk.XdkBuiltModule
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.run.RunWorkerProcess
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/** `xtc/build` parameters: a module name or root URI, or every source module when [module] is absent. */
data class BuildParams(
    val module: String? = null,
    val rebuild: Boolean? = null,
)

/** `xtc/build` result. Diagnostics are grouped by document, in the shape of `publishDiagnostics`. */
data class BuildResponse(
    val succeeded: Boolean,
    val cancelled: Boolean,
    val modules: List<XdkBuiltModule>,
    val diagnostics: List<PublishDiagnosticsParams>,
    val outputDirectory: String?,
)

/**
 * `xtc/run` parameters. [module] is a module name or root URI; a client that sends a root URI also sends the
 * module's name as [moduleName], which identifies the module when the URI no longer does, as after its root file
 * was renamed or moved. The client chooses [runId] so it can attach its console before any output arrives;
 * [args] are passed to the entry method as given, never shell-parsed.
 */
data class RunParams(
    val module: String? = null,
    val method: String? = null,
    val args: List<String>? = null,
    val workingDirectory: String? = null,
    val runId: String? = null,
    val moduleName: String? = null,
)

/** `xtc/run` result: the build outcome, and the run's ID when the build succeeded and the run started. */
data class RunResponse(
    val runId: String?,
    val build: BuildResponse,
)

/** A Run lens of a document: the module it runs and the zero-based line it is on. */
data class RunLens(
    val module: String,
    val line: Int,
)

data class StopRunParams(
    val runId: String? = null,
)

/**
 * `xtc/runInput` parameters: [text] for the program's console, sent as typed, with line ends; [end] ends
 * the input, after which a program waiting for a line gets an empty one.
 */
data class RunInputParams(
    val runId: String? = null,
    val text: String? = null,
    val end: Boolean? = null,
)

data class RunOutputParams(
    val runId: String,
    val text: String,
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String,
    val exitCode: Long?,
    val error: String?,
)

/** Client side of the embedded execution notifications. */
interface XtcLanguageClient : LanguageClient {
    @JsonNotification("xtc/runOutput")
    fun runOutput(params: RunOutputParams)

    @JsonNotification("xtc/runExited")
    fun runExited(params: RunExitedParams)

    /** One DAP message from a debug session to the client; see [DebugSessions]. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams) {}
}

/**
 * Embedded build and run for IDE clients: builds through the compiler adapter's serialized queue, then runs
 * the built module in the execution worker JVM. Clients that do not implement [XtcLanguageClient] can still
 * build; runs then have nowhere to report.
 */
internal class EmbeddedRuns(
    private val adapter: Adapter,
    private val client: () -> LanguageClient?,
    private val worker: RunWorkerProcess = RunWorkerProcess(),
) : Closeable {
    private val workerIds = ConcurrentHashMap<String, Long>()

    /** The sources each run under the debugger was compiled from, by run ID, while it runs. */
    private val compiled = ConcurrentHashMap<String, CompiledSources>()

    /** Input typed while a run is still building, forwarded in order when the program starts. */
    private val waiting = HashMap<String, WaitingInput>()
    private val inputLock = Any()

    private class WaitingInput {
        val text = StringBuilder()
        var ended = false

        /** Stopped before the program started, which it then never does. */
        var stopped = false
    }

    fun build(params: BuildParams): CompletableFuture<BuildResponse> =
        compiler()?.buildAsync(params.module, params.rebuild == true)?.thenApply(::response) ?: unsupported()

    fun run(params: RunParams): CompletableFuture<RunResponse> {
        val runId = params.runId ?: UUID.randomUUID().toString()
        return launch(
            params.copy(runId = runId),
            object : RunWorkerProcess.Listener {
                override fun output(text: String) {
                    (client() as? XtcLanguageClient)?.runOutput(RunOutputParams(runId, text))
                }

                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    (client() as? XtcLanguageClient)?.runExited(RunExitedParams(runId, code, error))
                }
            },
        )
    }

    /**
     * Builds the module and, if the build succeeds, runs it once [ready] completes, as `xtc/run` does at once and a
     * debug session does after its client's configuration. [ready]'s value is the debugger's setup before the
     * program's first line, or null to run it without the debugger. [events] receives the program's output and its
     * single exit, also when the run is stopped before the program starts, and a debugged run's breakpoints and stops.
     */
    internal fun launch(
        params: RunParams,
        events: RunWorkerProcess.Listener,
        ready: CompletionStage<out DebugSetup?> = CompletableFuture.completedFuture(null),
    ): CompletableFuture<RunResponse> {
        val module = params.module ?: return CompletableFuture.failedFuture(IllegalArgumentException("A run requires a module"))
        val compiler = compiler() ?: return unsupported()
        val runId = params.runId ?: UUID.randomUUID().toString()
        synchronized(inputLock) { waiting[runId] = WaitingInput() }
        return compiler
            .buildAsync(module, false, params.moduleName)
            .thenCompose { result ->
                val build = response(result)
                val target = result.modules.lastOrNull()
                if (!result.succeeded || target == null) {
                    logger.info(
                        "run not started: run={} build={} outcome={}",
                        runId,
                        result.id,
                        if (result.cancelled) "cancelled" else "failed",
                    )
                    synchronized(inputLock) { waiting.remove(runId) }
                    return@thenCompose CompletableFuture.completedFuture(RunResponse(null, build))
                }
                ready.thenApply { setup ->
                    start(runId, params, result, target.name, setup, events)
                    RunResponse(runId, build)
                }
            }.whenComplete { _, failure -> if (failure != null) synchronized(inputLock) { waiting.remove(runId) } }
    }

    private fun start(
        runId: String,
        params: RunParams,
        result: XdkBuildResult,
        module: String,
        setup: DebugSetup?,
        events: RunWorkerProcess.Listener,
    ) {
        val stoppedEarly = synchronized(inputLock) { waiting[runId]?.stopped == true && waiting.remove(runId) != null }
        if (stoppedEarly) {
            logger.info("run stopped before it started: run={} build={}", runId, result.id)
            events.exited(null, STOPPED_BEFORE_START)
            return
        }
        // the debugger places breakpoints on the lines the program was compiled from, which the editor may have changed
        // since; one on a line changed since has no code, and says so before the program starts
        val sources =
            setup
                ?.let {
                    CompiledSources(
                        result.compiled,
                    ) { file -> compiler()?.sourceText(file) }
                }?.also { compiled[runId] = it }
        val placed =
            setup?.copy(
                breakpoints =
                    setup.breakpoints.map { file ->
                        val lines = sources?.lines(file.path) ?: LineMap.SAME
                        refused(file, lines).takeIf { it.isNotEmpty() }?.let { events.breakpoints(file.path, it) }
                        placed(file, lines)
                    },
            )
        logger.info(
            "run starting: run={} build={} artifact={} debug={}",
            runId,
            result.id,
            result.modules.lastOrNull { it.name == module }?.logged ?: module,
            setup != null,
        )
        // a run can end before start returns, e.g. while another program still runs
        val ended = AtomicBoolean()
        val id =
            worker.start(
                RunWorkerProcess.Request(
                    result.modulePath,
                    module,
                    params.method ?: "run",
                    params.args.orEmpty(),
                    params.workingDirectory ?: result.workingDirectory,
                    placed,
                    runId,
                ),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) = events.output(text)

                    override fun breakpoints(
                        path: String,
                        states: List<BreakpointState>,
                    ) {
                        val lines = sources?.lines(path) ?: LineMap.SAME
                        events.breakpoints(path, states.map { it.copy(line = lines.now(it.line)) })
                    }

                    override fun functionBreakpoints(states: List<BreakpointState>) =
                        events.functionBreakpoints(states.map { state -> now(state) { path -> sources?.lines(path) ?: LineMap.SAME } })

                    override fun stopped(stop: StopState) = events.stopped(stop)

                    override fun logged(text: String) = events.logged(text)

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        synchronized(inputLock) {
                            ended.set(true)
                            waiting.remove(runId)
                            workerIds.remove(runId)
                        }
                        compiled.remove(runId)
                        events.exited(code, error)
                    }
                },
            )
        // input typed while the module built goes to the program, unless the run was stopped meanwhile
        val stop =
            synchronized(inputLock) {
                if (!ended.get()) workerIds[runId] = id
                val typed = waiting.remove(runId)?.takeUnless { ended.get() } ?: return@synchronized false
                if (!typed.stopped) {
                    if (typed.text.isNotEmpty()) worker.input(id, typed.text.toString())
                    if (typed.ended) worker.endInput(id)
                }
                typed.stopped
            }
        if (stop) worker.stop(id)
    }

    /** Forwards console input to the run, or holds it while the run is still building. */
    fun input(params: RunInputParams): Boolean {
        val runId = params.runId ?: return false
        synchronized(inputLock) {
            val id = workerIds[runId]
            if (id != null) {
                params.text?.takeIf { it.isNotEmpty() }?.let { worker.input(id, it) }
                if (params.end == true) worker.endInput(id)
                return true
            }
            val typed = waiting[runId] ?: return false
            params.text?.let(typed.text::append)
            if (params.end == true) typed.ended = true
            return true
        }
    }

    /**
     * Stops the run, or keeps it from starting while it still builds; the client still receives its `xtc/runExited`,
     * unless the build fails. False when no such run is active.
     */
    fun stop(params: StopRunParams): Boolean {
        val runId = params.runId ?: return false
        val id =
            synchronized(inputLock) {
                workerIds[runId] ?: waiting[runId]?.let { typed ->
                    typed.stopped = true
                    return true
                }
            } ?: return false
        worker.stop(id)
        return true
    }

    /** Replaces the exception stops of a running program under the debugger. False when no such run is active. */
    internal fun setExceptionStops(
        runId: String,
        stops: ExceptionStops,
    ): Boolean {
        val id = workerIds[runId] ?: return false
        worker.setExceptionStops(id, stops)
        return true
    }

    /**
     * Replaces one file's breakpoints in a running program under the debugger, on the lines the program was compiled
     * from; one on a line changed since is not installed, and its state says why.
     */
    internal fun setBreakpoints(
        runId: String,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>> {
        val lines = lines(runId, breakpoints.path)
        val refused = refused(breakpoints, lines).associateBy { it.id }
        return debugged(runId) { worker.setBreakpoints(it, placed(breakpoints, lines)) }.thenApply { states ->
            val installed = states.associateBy { it.id }
            // in the order they were asked for, on their lines now
            breakpoints.breakpoints.mapNotNull { request ->
                refused[request.id] ?: installed[request.id]?.let { it.copy(line = lines.now(it.line)) }
            }
        }
    }

    /** Replaces the function breakpoints of a running program under the debugger, each on its line now. */
    internal fun setFunctionBreakpoints(
        runId: String,
        breakpoints: List<FunctionBreakpointRequest>,
    ): CompletableFuture<List<BreakpointState>> =
        debugged(runId) { worker.setFunctionBreakpoints(it, breakpoints) }.thenApply { states ->
            states.map { state -> now(state) { path -> lines(runId, path) } }
        }

    /** A function breakpoint on the line of its method's file where that line is now, which may have changed since the build. */
    private fun now(
        state: BreakpointState,
        lines: (String) -> LineMap,
    ) = state.path?.let { path -> state.copy(line = lines(path).now(state.line)) } ?: state

    /** The fibers of a stopped program under the debugger. */
    internal fun threads(runId: String): CompletableFuture<List<ThreadState>> = debugged(runId, worker::threads)

    /** The calls that the line of a stopped frame of a program under the debugger makes. */
    internal fun stepInTargets(
        runId: String,
        frame: Int,
    ): CompletableFuture<List<StepTargetState>> = debugged(runId) { worker.stepInTargets(it, frame) }

    /** Steps a stopped fiber of a program under the debugger into one of the calls of its line. */
    internal fun stepInto(
        runId: String,
        thread: Int,
        target: Int,
    ): CompletableFuture<Boolean> = debugged(runId) { worker.stepInto(it, thread, target) }

    /** The modules of a program under the debugger, its own and the libraries it uses. */
    internal fun modules(runId: String): CompletableFuture<List<ModuleState>> = debugged(runId, worker::modules)

    /** A stopped fiber's stack, with each frame's line where it is now in its file, which may have changed since the build. */
    internal fun stackTrace(
        runId: String,
        thread: Int,
    ): CompletableFuture<List<FrameState>> =
        debugged(runId) { worker.stackTrace(it, thread) }.thenApply { frames ->
            val maps = HashMap<String?, LineMap>()
            frames.map { frame -> frame.copy(line = maps.getOrPut(frame.path) { lines(runId, frame.path) }.now(frame.line)) }
        }

    /** The line map of a file of a run under the debugger, between the text it was compiled from and its text now. */
    private fun lines(
        runId: String,
        path: String?,
    ): LineMap = compiled[runId]?.lines(path) ?: LineMap.SAME

    /** The file's breakpoints on their compiled lines, without those on lines changed since the build. */
    private fun placed(
        breakpoints: SourceBreakpoints,
        lines: LineMap,
    ): SourceBreakpoints =
        breakpoints.copy(
            breakpoints =
                breakpoints.breakpoints.mapNotNull { request ->
                    lines.compiled(request.line)?.let { request.copy(line = it) }
                },
        )

    /** The states of the file's breakpoints on lines changed since the build, which the program has no code for. */
    private fun refused(
        breakpoints: SourceBreakpoints,
        lines: LineMap,
    ): List<BreakpointState> =
        breakpoints.breakpoints
            .filter { lines.compiled(it.line) == null }
            .map { BreakpointState(it.id, it.line, false, CHANGED_SINCE_BUILD) }

    /** A stopped frame's scopes. */
    internal fun scopes(
        runId: String,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> = debugged(runId) { worker.scopes(it, frame) }

    /**
     * The variables of a scope, or the fields or elements of a variable, with integers in hexadecimal if [hex]: those
     * [filter] takes, from [start], [count] of them, or all from there for 0.
     */
    internal fun variables(
        runId: String,
        reference: Int,
        hex: Boolean,
        filter: ChildFilter,
        start: Int,
        count: Int,
    ): CompletableFuture<List<VariableState>> = debugged(runId) { worker.variables(it, reference, hex, filter, start, count) }

    /** Evaluates an expression in a frame of a stopped program under the debugger, an integer in hexadecimal if [hex]. */
    internal fun evaluate(
        runId: String,
        frame: Int,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { worker.evaluate(it, frame, expression, hex) }

    /** Changes a variable of a stopped program under the debugger to an expression's value, an integer in hexadecimal if [hex]. */
    internal fun setVariable(
        runId: String,
        reference: Int,
        name: String,
        expression: String,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { worker.setVariable(it, reference, name, expression, hex) }

    /** The names that complete the name typed at [offset] in [text], in a frame of a stopped program under the debugger. */
    internal fun completions(
        runId: String,
        frame: Int,
        text: String,
        offset: Int,
    ): CompletableFuture<CompletionsState> = debugged(runId) { worker.completions(it, frame, text, offset) }

    /**
     * Runs code in a frame of a stopped program under the debugger, for at most [limit], and with [program], the whole
     * program meanwhile, its integer value in hexadecimal if [hex]; cancelling it ends the run.
     */
    internal fun runCode(
        runId: String,
        frame: Int,
        expression: String,
        limit: Duration,
        program: Boolean,
        hex: Boolean,
    ): CompletableFuture<VariableState> = debugged(runId) { worker.runCode(it, frame, expression, limit, program, hex) }

    /** The exception a fiber of a stopped program under the debugger stopped it at; null for another stop. */
    internal fun exceptionInfo(
        runId: String,
        thread: Int,
    ): CompletableFuture<ExceptionInfoState?> = debugged(runId) { worker.exceptionInfo(it, thread) }

    /** Steps a fiber of a stopped program under the debugger; false when that fiber does not wait. */
    internal fun step(
        runId: String,
        thread: Int,
        kind: StepKind,
    ): CompletableFuture<Boolean> = debugged(runId) { worker.step(it, thread, kind) }

    /** Pauses a running program under the debugger. False when no such run is active. */
    internal fun pause(runId: String): Boolean {
        val id = workerIds[runId] ?: return false
        worker.pause(id)
        return true
    }

    /** Resumes a stopped program under the debugger. False when no such run is active. */
    internal fun resume(runId: String): Boolean {
        val id = workerIds[runId] ?: return false
        worker.resume(id)
        return true
    }

    private fun <T> debugged(
        runId: String,
        request: (Long) -> CompletableFuture<T>,
    ): CompletableFuture<T> =
        workerIds[runId]?.let(request) ?: CompletableFuture.failedFuture(IllegalStateException("The program is not running"))

    /** Runs still tracked, here and by the execution workers, which is none once every run has ended; for tests. */
    internal fun tracked(): Int = synchronized(inputLock) { workerIds.size + waiting.size } + compiled.size + worker.tracked()

    /** Process IDs of the live execution workers, for tests. */
    internal fun workerPids(): List<Long> = worker.workerPids()

    override fun close() = worker.close()

    private fun compiler(): XdkAdapter? = adapter as? XdkAdapter

    private fun <T> unsupported(): CompletableFuture<T> =
        CompletableFuture.failedFuture(
            UnsupportedOperationException("Embedded build and run require the compiler language service"),
        )

    private fun response(result: XdkBuildResult) =
        BuildResponse(
            succeeded = result.succeeded,
            cancelled = result.cancelled,
            modules = result.modules,
            diagnostics =
                result.diagnostics
                    .groupBy { it.location.uri }
                    .map { (uri, diagnostics) -> PublishDiagnosticsParams(uri, diagnostics.map { it.toLsp(uri) }) },
            outputDirectory = result.modulePath.firstOrNull(),
        )

    companion object {
        private val logger = LoggerFactory.getLogger(EmbeddedRuns::class.java)

        /** The exit of a run stopped while its module was still building. */
        const val STOPPED_BEFORE_START = "Stopped before the program started."

        /** Why a breakpoint on a line changed since the program was built is not installed. */
        const val CHANGED_SINCE_BUILD =
            "This line changed after the program was built, so the running program has no code for it. Debug again to stop here."
    }
}
