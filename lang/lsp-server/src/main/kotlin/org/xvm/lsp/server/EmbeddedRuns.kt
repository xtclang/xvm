package org.xvm.lsp.server

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.xvm.debug.BreakpointState
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
            events.exited(null, STOPPED_BEFORE_START)
            return
        }
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
                    setup,
                ),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) = events.output(text)

                    override fun breakpoints(
                        path: String,
                        states: List<BreakpointState>,
                    ) = events.breakpoints(path, states)

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

    /** Replaces one file's breakpoints in a running program under the debugger. */
    internal fun setBreakpoints(
        runId: String,
        breakpoints: SourceBreakpoints,
    ): CompletableFuture<List<BreakpointState>> = debugged(runId) { worker.setBreakpoints(it, breakpoints) }

    /** The fibers of a stopped program under the debugger. */
    internal fun threads(runId: String): CompletableFuture<List<ThreadState>> = debugged(runId, worker::threads)

    /** A stopped fiber's stack. */
    internal fun stackTrace(
        runId: String,
        thread: Int,
    ): CompletableFuture<List<FrameState>> = debugged(runId) { worker.stackTrace(it, thread) }

    /** A stopped frame's scopes. */
    internal fun scopes(
        runId: String,
        frame: Int,
    ): CompletableFuture<List<ScopeState>> = debugged(runId) { worker.scopes(it, frame) }

    /** The variables of a scope, or the fields or elements of a variable. */
    internal fun variables(
        runId: String,
        reference: Int,
    ): CompletableFuture<List<VariableState>> = debugged(runId) { worker.variables(it, reference) }

    /** Evaluates a path to a value in a frame of a stopped program under the debugger. */
    internal fun evaluate(
        runId: String,
        frame: Int,
        expression: String,
    ): CompletableFuture<VariableState> = debugged(runId) { worker.evaluate(it, frame, expression) }

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
    internal fun tracked(): Int = synchronized(inputLock) { workerIds.size + waiting.size } + worker.tracked()

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
        /** The exit of a run stopped while its module was still building. */
        const val STOPPED_BEFORE_START = "Stopped before the program started."
    }
}
