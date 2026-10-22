package org.xvm.debug

import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.BreakpointEventArgumentsReason
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.ContinueResponse
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.NextArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.ScopePresentationHint
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.StackFrame
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.StepInArguments
import org.eclipse.lsp4j.debug.StepOutArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.Thread
import org.eclipse.lsp4j.debug.ThreadsResponse
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * One Ecstasy Debug Adapter Protocol (DAP) session. A launch builds and runs its module through [runs], the
 * language server's embedded build and run, so a debug launch and a Run use the same build, worker and program
 * output; the runtime debugger runs in that worker, beside the program.
 *
 * Breakpoints the client sets before the program starts are installed before its first line: they are answered
 * as pending, and `breakpoint` events report how they were installed. A stop is reported with every fiber
 * stopped; `threads` lists the fibers that wait, `stackTrace` their stacks, `scopes` a frame's local variables,
 * `variables` their values and the fields and elements of objects and arrays; `next`, `stepIn` and `stepOut` step a
 * fiber, and the program stops again with the reason `step`, and `continue` resumes it. The exception filters
 * stop the program where an exception that nothing catches, or any exception, is thrown, with the reason
 * `exception`; they are installed before the program's first line as breakpoints are. There is no pause or
 * evaluation yet (R7 in lang/doc/plans/plan-embedded-execution.md), so neither is advertised.
 *
 * The session owns the program it launches. Terminating or disconnecting stops it, also while it still builds or
 * is stopped; its exit ends the session with `exited` and `terminated`; and the language server stops it when the
 * session's transport or the server itself goes away. A line typed in the client's debug console while the program
 * runs is the program's console input.
 *
 * [afterResponse] runs a task after the message being handled has been answered, which is when the `initialized`
 * event may be sent.
 */
class XtcDebugServer(
    private val runs: DebugRuns,
    private val afterResponse: Executor,
) : IDebugProtocolServer {
    @Volatile
    private var client: IDebugProtocolClient? = null

    /** The run's ID with the language server's runs. */
    private val runId = UUID.randomUUID().toString()

    private val configured = CompletableFuture<Unit>()
    private val launched = AtomicBoolean()

    /** Completes when the launched program has exited, or will not run. */
    private val ended = CompletableFuture<Unit>()
    private val terminated = AtomicBoolean()

    /** False for a launch without debugging. */
    @Volatile
    private var debugging = true

    /** True once the launched program has started. */
    @Volatile
    private var started = false

    /** The client's breakpoints by source path, the sources as the client named them, and those installed at start. */
    private val breakpoints = ConcurrentHashMap<String, SourceBreakpoints>()
    private val sources = ConcurrentHashMap<String, Source>()

    @Volatile
    private var installed: Map<String, SourceBreakpoints> = emptyMap()

    /** The client's exception filters, and those installed at start. */
    @Volatile
    private var exceptionStops = ExceptionStops()

    @Volatile
    private var installedExceptions = ExceptionStops()
    private val lastBreakpoint = AtomicInteger()

    /** 0 for a client that counts lines and columns from 1, as DAP does by default, -1 for one that counts from 0. */
    @Volatile
    private var lineBase = 0

    @Volatile
    private var columnBase = 0

    companion object {
        private val logger = LoggerFactory.getLogger(XtcDebugServer::class.java)

        /** The exception filter that stops where an exception that nothing catches is thrown; on by default. */
        const val UNCAUGHT_EXCEPTIONS = "uncaught"

        /** The exception filter that stops where any exception is thrown. */
        const val ALL_EXCEPTIONS = "all"

        /** A breakpoint set before the program starts. */
        const val PENDING = "Installed when the program starts."

        /** A breakpoint of a launch without debugging. */
        const val NOT_DEBUGGING = "This program runs without debugging."

        /** A breakpoint the client gives no file for. */
        const val NO_SOURCE = "A breakpoint needs a source file."

        /** Why a fiber cannot be stepped. */
        const val NOT_STOPPED = "That thread is not stopped, so it cannot be stepped."

        /** Why a running program cannot be paused. */
        const val NO_PAUSE = "Pausing a running program is not available yet."

        /** A launch whose build fails runs nothing. */
        const val BUILD_FAILED = "Build failed; nothing was run."

        /** Why a debug console line is not evaluated. */
        const val NO_EVALUATION =
            "Expressions cannot be evaluated yet; a line typed while the program runs is its console input."

        /** How long a launch whose build has finished waits for the client to finish its configuration. */
        private const val CONFIGURATION_WAIT_SECONDS = 2L

        /** How long a disconnect waits for the program it stopped to report its exit. */
        private const val STOP_WAIT_SECONDS = 10L
    }

    /** Connect to the DAP client (IDE). */
    fun connect(client: IDebugProtocolClient) {
        this.client = client
    }

    /** The session's transport has gone: the program it launched stops. */
    fun close() {
        if (launched.get() && !ended.isDone) {
            logger.info("close: stopping run {}", runId)
            runs.stop(runId)
        }
    }

    /**
     * DAP: initialize
     *
     * Advertises the configuration handshake and terminate, then sends `initialized` once this is answered.
     */
    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        logger.info("initialize: clientID={}, adapterID={}", args.clientID, args.adapterID)
        lineBase = if (args.linesStartAt1 == false) -1 else 0
        columnBase = if (args.columnsStartAt1 == false) -1 else 0
        afterResponse.execute { client?.initialized() }
        val capabilities =
            Capabilities().apply {
                supportsConfigurationDoneRequest = true
                supportsTerminateRequest = true
                exceptionBreakpointFilters =
                    arrayOf(
                        ExceptionBreakpointsFilter().apply {
                            filter = UNCAUGHT_EXCEPTIONS
                            label = "Uncaught Exceptions"
                            description = "Stop where an exception that nothing catches is thrown."
                            default_ = true
                        },
                        ExceptionBreakpointsFilter().apply {
                            filter = ALL_EXCEPTIONS
                            label = "All Exceptions"
                            description = "Stop where any exception is thrown, also one that is caught."
                            default_ = false
                        },
                    )
            }
        return CompletableFuture.completedFuture(capabilities)
    }

    /**
     * DAP: setBreakpoints
     *
     * Replaces one file's breakpoints. While a debugged program runs, they are installed at once; before it starts
     * they are pending, and `breakpoint` events report how they were installed.
     */
    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        val path = args.source?.path
        val lines = args.breakpoints?.map { it.line } ?: args.lines?.toList().orEmpty()
        val requests = lines.map { BreakpointRequest(lastBreakpoint.incrementAndGet(), it - lineBase) }
        logger.info("setBreakpoints: source={}, lines={}", path, lines)
        if (path == null) {
            return CompletableFuture.completedFuture(unverified(requests, NO_SOURCE, args.source))
        }
        breakpoints[path] = SourceBreakpoints(path, requests)
        args.source?.let { sources[path] = it }
        return when {
            !debugging -> CompletableFuture.completedFuture(unverified(requests, NOT_DEBUGGING, args.source))

            !debuggedRunning() -> CompletableFuture.completedFuture(unverified(requests, PENDING, args.source))

            else ->
                runs.setBreakpoints(runId, breakpoints.getValue(path)).thenApply { states ->
                    SetBreakpointsResponse().apply { this.breakpoints = states.map { breakpoint(it, path) }.toTypedArray() }
                }
        }
    }

    /**
     * DAP: setExceptionBreakpoints
     *
     * Chooses the exception filters: [UNCAUGHT_EXCEPTIONS] and [ALL_EXCEPTIONS]. While a debugged program runs they
     * apply at once; before it starts, from its first line.
     */
    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments?): CompletableFuture<SetExceptionBreakpointsResponse> {
        val filters = args?.filters?.toSet().orEmpty()
        logger.info("setExceptionBreakpoints: filters={}", filters)
        exceptionStops = ExceptionStops(all = ALL_EXCEPTIONS in filters, uncaught = UNCAUGHT_EXCEPTIONS in filters)
        if (debuggedRunning()) runs.setExceptionStops(runId, exceptionStops)
        return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse())
    }

    /** DAP: configurationDone. A launched program starts after it, or after a short wait for it. */
    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        logger.info("configurationDone")
        configured.complete(Unit)
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: launch
     *
     * Builds the module and starts it, with the breakpoints set by then, answering once it has started; a failed
     * build answers with an error after reporting the build's diagnostics as output. Arguments: `module` (a module
     * name), `moduleUri` (a source URI of the module, used in place of the name when given), `method`, `args` (an
     * array, or a comma-separated string), `cwd` or `workingDirectory`, and `noDebug`.
     */
    override fun launch(args: MutableMap<String, Any>?): CompletableFuture<Void?> {
        logger.info("launch: args={}", args)
        val target =
            try {
                target(args.orEmpty())
            } catch (e: IllegalArgumentException) {
                return failed(e.message ?: "Invalid launch arguments")
            }
        if (!launched.compareAndSet(false, true)) {
            return failed("This debug session has already launched a program.")
        }
        debugging = args?.get("noDebug") != true
        console("Building and running ${target.moduleName ?: target.module} with the embedded compiler...")
        val ready =
            configured.copy().completeOnTimeout(Unit, CONFIGURATION_WAIT_SECONDS, SECONDS).thenApply {
                if (debugging) {
                    installed = breakpoints.toMap()
                    installedExceptions = exceptionStops
                    DebugSetup(installed.values.toList(), installedExceptions)
                } else {
                    null
                }
            }
        return runs
            .launch(runId, target, events, ready)
            .handle<Void?> { result, failure ->
                if (failure != null) {
                    end(sendTerminated = false)
                    val cause = (failure as? CompletionException)?.cause ?: failure
                    throw error("Run failed: ${cause.message}")
                }
                if (result.runId == null) {
                    end(sendTerminated = false)
                    if (result.buildReport.isNotEmpty()) {
                        output(OutputEventArgumentsCategory.STDERR, "${result.buildReport}\n")
                    }
                    throw error(BUILD_FAILED)
                }
                started = true
                if (debugging) installChanged()
                null
            }
    }

    /** DAP: attach. A session launches its program; there is nothing to attach to. */
    override fun attach(args: MutableMap<String, Any>?): CompletableFuture<Void> {
        logger.info("attach: refused: args={}", args)
        return failed("Attaching is not available; launch the module instead.")
    }

    /** DAP: threads. The fibers of the stopped program that wait; none while it runs. */
    override fun threads(): CompletableFuture<ThreadsResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(ThreadsResponse().apply { threads = emptyArray() })
        }
        return runs
            .threads(runId)
            .exceptionally { emptyList() }
            .thenApply { fibers ->
                ThreadsResponse().apply {
                    threads =
                        fibers
                            .map { fiber ->
                                Thread().apply {
                                    id = fiber.id
                                    name = fiber.name
                                }
                            }.toTypedArray()
                }
            }
    }

    /** DAP: stackTrace. A stopped fiber's stack, innermost frame first. */
    override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(
                StackTraceResponse().apply {
                    stackFrames = emptyArray()
                    totalFrames = 0
                },
            )
        }
        return runs.stackTrace(runId, args.threadId).thenApply { frames ->
            val first = args.startFrame ?: 0
            val levels = args.levels?.takeIf { it > 0 } ?: frames.size
            StackTraceResponse().apply {
                stackFrames =
                    frames
                        .drop(first)
                        .take(levels)
                        .map(::stackFrame)
                        .toTypedArray()
                totalFrames = frames.size
            }
        }
    }

    /** DAP: scopes. A stopped frame's local variables; a frame of a fiber that called the stopped one has none. */
    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(ScopesResponse().apply { scopes = emptyArray() })
        }
        return runs.scopes(runId, args.frameId).thenApply { states ->
            ScopesResponse().apply {
                scopes =
                    states
                        .map { state ->
                            Scope().apply {
                                name = state.name
                                presentationHint = ScopePresentationHint.LOCALS
                                variablesReference = state.variablesReference
                                isExpensive = false
                            }
                        }.toTypedArray()
            }
        }
    }

    /**
     * DAP: variables. A scope's variables, or a variable's fields or elements, with `start` and `count` taking a part
     * of them; none for a reference from before the program last resumed.
     */
    override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(VariablesResponse().apply { variables = emptyArray() })
        }
        return runs.variables(runId, args.variablesReference).thenApply { states ->
            val first = args.start ?: 0
            val count = args.count?.takeIf { it > 0 } ?: states.size
            VariablesResponse().apply {
                variables =
                    states
                        .drop(first)
                        .take(count)
                        .map { state ->
                            Variable().apply {
                                name = state.name
                                value = state.value
                                type = state.type.ifEmpty { null }
                                variablesReference = state.variablesReference
                            }
                        }.toTypedArray()
            }
        }
    }

    /** DAP: continue. Resumes every fiber of the stopped program. */
    override fun continue_(args: ContinueArguments?): CompletableFuture<ContinueResponse> {
        if (debuggedRunning()) {
            runs.resume(runId)
        }
        return CompletableFuture.completedFuture(ContinueResponse().apply { allThreadsContinued = true })
    }

    /** DAP: next. Steps the fiber over a call, to its next line; a return continues to the caller's next line. */
    override fun next(args: NextArguments): CompletableFuture<Void> = step(args.threadId, StepKind.OVER)

    /** DAP: stepIn. Steps the fiber to the next line it reaches, also in a method that it calls. */
    override fun stepIn(args: StepInArguments): CompletableFuture<Void> = step(args.threadId, StepKind.IN)

    /** DAP: stepOut. Steps the fiber until its method returns, to the caller's next line. */
    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> = step(args.threadId, StepKind.OUT)

    /** DAP: pause. Not available yet; a program stops only at its breakpoints and at `assert:debug`. */
    override fun pause(args: PauseArguments?): CompletableFuture<Void> = failed(NO_PAUSE)

    /**
     * DAP: evaluate
     *
     * A line typed in the debug console while the program runs is sent to the program's console; nothing else can
     * be evaluated.
     */
    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> {
        if (args.context != EvaluateArgumentsContext.REPL || args.frameId != null) {
            return failed(NO_EVALUATION)
        }
        if (!launched.get() || ended.isDone || !runs.input(runId, "${args.expression}\n")) {
            return failed("The program is not running, so it cannot read that line.")
        }
        return CompletableFuture.completedFuture(
            EvaluateResponse().apply {
                result = ""
                variablesReference = 0
            },
        )
    }

    /** DAP: terminate. Stops the program, whose exit then ends the session; with no program, it ends at once. */
    override fun terminate(args: TerminateArguments?): CompletableFuture<Void> {
        logger.info("terminate: restart={}", args?.restart)
        if (!launched.get() || ended.isDone || !runs.stop(runId)) {
            end(sendTerminated = true)
        }
        return CompletableFuture.completedFuture(null)
    }

    /** DAP: disconnect. Stops the program the session launched, and answers once it has exited. */
    override fun disconnect(args: DisconnectArguments?): CompletableFuture<Void?> {
        logger.info("disconnect: restart={}, terminateDebuggee={}", args?.restart, args?.terminateDebuggee)
        if (!launched.get() || ended.isDone) {
            return CompletableFuture.completedFuture(null)
        }
        runs.stop(runId)
        return ended.copy().completeOnTimeout(Unit, STOP_WAIT_SECONDS, SECONDS).thenApply<Void?> { null }
    }

    private val events =
        object : ProgramEvents {
            override fun output(text: String) = output(OutputEventArgumentsCategory.STDOUT, text)

            override fun exited(
                code: Long?,
                error: String?,
            ) {
                if (error != null) {
                    output(OutputEventArgumentsCategory.STDERR, "\n$error\n")
                }
                val exitCode = code?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: 1
                console("\nProcess finished with exit code $exitCode")
                client?.exited(ExitedEventArguments().apply { this.exitCode = exitCode })
                end(sendTerminated = true)
            }

            override fun breakpoints(
                path: String,
                states: List<BreakpointState>,
            ) = changed(path, states)

            override fun stopped(stop: StopState) {
                client?.stopped(
                    StoppedEventArguments().apply {
                        reason = stop.reason
                        description = stop.description
                        // the exception's type and text, which editors show beside the stop
                        text = stop.description.takeIf { stop.reason == StoppedEventArgumentsReason.EXCEPTION }
                        threadId = stop.threadId
                        allThreadsStopped = true
                        hitBreakpointIds = stop.breakpointIds.takeIf { it.isNotEmpty() }?.toTypedArray()
                    },
                )
            }
        }

    /** Steps a stopped fiber; the stop where the step ends is reported as a `stopped` event. */
    private fun step(
        threadId: Int,
        kind: StepKind,
    ): CompletableFuture<Void> {
        if (!debuggedRunning()) return failed(NOT_STOPPED)
        return runs.step(runId, threadId, kind).thenApply<Void> { stepped -> if (stepped) null else throw error(NOT_STOPPED) }
    }

    /** True while a debugged program runs, stopped or not. */
    private fun debuggedRunning() = debugging && started && !ended.isDone

    /** Installs the breakpoints and exception filters the client changed after the program's were taken, before it started. */
    private fun installChanged() {
        breakpoints.forEach { (path, current) ->
            if (installed[path] !== current) {
                runs.setBreakpoints(runId, current).thenAccept { states -> changed(path, states) }
            }
        }
        if (exceptionStops != installedExceptions) runs.setExceptionStops(runId, exceptionStops)
    }

    /** Tells the client how a file's breakpoints were installed. */
    private fun changed(
        path: String,
        states: List<BreakpointState>,
    ) {
        states.forEach { state ->
            client?.breakpoint(
                BreakpointEventArguments().apply {
                    reason = BreakpointEventArgumentsReason.CHANGED
                    breakpoint = breakpoint(state, path)
                },
            )
        }
    }

    private fun breakpoint(
        state: BreakpointState,
        path: String,
    ) = Breakpoint().apply {
        id = state.id
        isVerified = state.verified
        line = state.line + lineBase
        message = state.message
        source = sources[path] ?: Source().apply { this.path = path }
    }

    private fun unverified(
        requests: List<BreakpointRequest>,
        reason: String,
        file: Source?,
    ) = SetBreakpointsResponse().apply {
        breakpoints =
            requests
                .map { request ->
                    Breakpoint().apply {
                        id = request.id
                        isVerified = false
                        message = reason
                        line = request.line + lineBase
                        source = file
                    }
                }.toTypedArray()
    }

    private fun stackFrame(frame: FrameState) =
        StackFrame().apply {
            id = frame.id
            name = frame.name
            frame.path?.let { file ->
                source =
                    Source().apply {
                        path = file
                        name = File(file).name
                    }
            }
            line = frame.line + lineBase
            column = if (frame.path == null) 0 else 1 + columnBase
        }

    private fun end(sendTerminated: Boolean) {
        ended.complete(Unit)
        if (sendTerminated && terminated.compareAndSet(false, true)) {
            client?.terminated(TerminatedEventArguments())
        }
    }

    private fun console(line: String) = output(OutputEventArgumentsCategory.CONSOLE, "$line\n")

    private fun output(
        category: String,
        text: String,
    ) {
        client?.output(
            OutputEventArguments().apply {
                this.category = category
                output = text
            },
        )
    }

    private fun target(args: Map<String, Any>): LaunchTarget {
        val name = (args["module"] as? String)?.takeIf { it.isNotBlank() }
        val uri = (args["moduleUri"] as? String)?.takeIf { it.isNotBlank() }
        val module = uri ?: name ?: throw IllegalArgumentException("A launch requires a module")
        val arguments =
            when (val given = args["args"]) {
                null -> emptyList()
                is String -> given.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                is List<*> -> given.map { it.toString() }
                else -> throw IllegalArgumentException("Launch arguments must be an array or a comma-separated string")
            }
        return LaunchTarget(
            module = module,
            moduleName = name,
            method = (args["method"] as? String)?.takeIf { it.isNotBlank() },
            args = arguments,
            workingDirectory = ((args["workingDirectory"] ?: args["cwd"]) as? String)?.takeIf { it.isNotBlank() },
        )
    }

    private fun error(message: String) = ResponseErrorException(ResponseError(ResponseErrorCode.RequestFailed, message, null))

    private fun <T> failed(message: String): CompletableFuture<T> = CompletableFuture.failedFuture(error(message))
}
