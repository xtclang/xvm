package org.xvm.debug

import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.BreakpointEventArgumentsReason
import org.eclipse.lsp4j.debug.CancelArguments
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.CompletionItem
import org.eclipse.lsp4j.debug.CompletionItemType
import org.eclipse.lsp4j.debug.CompletionsArguments
import org.eclipse.lsp4j.debug.CompletionsResponse
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.ContinueResponse
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.ExceptionBreakMode
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter
import org.eclipse.lsp4j.debug.ExceptionDetails
import org.eclipse.lsp4j.debug.ExceptionInfoArguments
import org.eclipse.lsp4j.debug.ExceptionInfoResponse
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.InvalidatedAreas
import org.eclipse.lsp4j.debug.InvalidatedEventArguments
import org.eclipse.lsp4j.debug.LoadedSourcesArguments
import org.eclipse.lsp4j.debug.LoadedSourcesResponse
import org.eclipse.lsp4j.debug.Module
import org.eclipse.lsp4j.debug.ModulesArguments
import org.eclipse.lsp4j.debug.ModulesResponse
import org.eclipse.lsp4j.debug.NextArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.RestartFrameArguments
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.ScopePresentationHint
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.StackFrame
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.StepInArguments
import org.eclipse.lsp4j.debug.StepInTarget
import org.eclipse.lsp4j.debug.StepInTargetsArguments
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.StepOutArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.Thread
import org.eclipse.lsp4j.debug.ThreadEventArguments
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason
import org.eclipse.lsp4j.debug.ThreadsResponse
import org.eclipse.lsp4j.debug.ValueFormat
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablePresentationHint
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter
import org.eclipse.lsp4j.debug.VariablesResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * One Ecstasy Debug Adapter Protocol (DAP) session. A launch builds and runs its module through [runs], the
 * language server's embedded build and run, so a debug launch and a Run use the same build, worker and program
 * output; the runtime debugger runs in that worker, beside the program.
 *
 * Breakpoints the client sets before the program starts are installed before its first line: they are answered
 * as pending, and `breakpoint` events report how they were installed. A stop is reported with every fiber
 * stopped; `threads` lists the program's fibers, `stackTrace` their stacks, `scopes` a frame's local variables,
 * `variables` their values and the fields and elements of objects and arrays; `next`, `stepIn` and `stepOut` step a
 * fiber, and the program stops again with the reason `step`; `restartFrame` makes a frame's caller call it again,
 * stopping with the reason `restart`; and `continue` resumes it. A breakpoint may have a
 * condition, an expression whose Boolean value must be True, a hit condition (`N` for the Nth hit, `>= N`, `> N` or
 * `% N`), and a log message, which makes it a logpoint: its message, with each `{expression}` replaced by its value,
 * goes to the console, and the program runs on. A function breakpoint names a method, alone or after its class's, as
 * `twice` or `Point.move`, and stops at the first line of the program's methods of that name, with the reason
 * `function breakpoint`. The exception filters stop the program where an exception that nothing catches,
 * or any exception, is thrown, with the reason `exception`, and each may name exception classes, separated by
 * commas, to stop only for them and the classes that extend them; `exceptionInfo` tells of the exception. They are
 * installed before the program's first line as breakpoints are. `pause` stops a running
 * program at the next line one of its fibers reaches, and a launch with `stopOnEntry` stops it at its first line,
 * with the reason `entry`. `evaluate` in a stopped frame evaluates expressions, such as `point.x + 1` or
 * `ints[i] > 0`, for watches and hovers without running the program's code: variables, their fields and elements,
 * literals, and operators that the runtime computes natively. A line typed in the console is evaluated so too, and
 * what that refuses, such as a call, runs as code on the stopped fiber, for at most [CONSOLE_LIMIT]; `cancel` ends it
 * sooner, and once code has run, `invalidated` tells a client that takes it to read the variables again
 * (lang/doc/plans/plan-bounded-evaluation.md, tier 2). A launch with `evaluationRunsProgram` lets the whole program run
 * while such code does, for a program whose code waits on work that its other fibers do; they then wait at their next
 * line once it ends, and `invalidated` asks for everything again, since the stop's threads and stacks may have moved.
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

    /** The launched module's name, which [RUNNING_THREAD] shows. */
    @Volatile
    private var programName = "program"

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

    /** The client's function breakpoints, and those installed at start. */
    @Volatile
    private var functionBreakpoints: List<FunctionBreakpointRequest> = emptyList()

    @Volatile
    private var installedFunctions: List<FunctionBreakpointRequest> = emptyList()
    private val lastBreakpoint = AtomicInteger()

    /** 0 for a client that counts lines and columns from 1, as DAP does by default, -1 for one that counts from 0. */
    @Volatile
    private var lineBase = 0

    @Volatile
    private var columnBase = 0

    /** True for a client that takes the `invalidated` event. */
    @Volatile
    private var invalidates = false

    /** True for a launch that lets the whole program run while code from the console does. */
    @Volatile
    private var evaluationRunsProgram = false

    /**
     * True to show integers in hexadecimal for a `variables` or `evaluate` request that names no `format`: the session's
     * own choice, which `xtc/valueFormat` sets, since VS Code never sends a format (UP38).
     */
    @Volatile
    private var hexValues = false

    /** The console evaluations in progress, by their request's `seq`, which `cancel` names. */
    private val evaluations = ConcurrentHashMap<Int, Evaluation>()

    /** The `seq` of the request being handled on this thread, as [receiving] gives it; null when not known. */
    private val handling = ThreadLocal<Int?>()

    companion object {
        private val logger = LoggerFactory.getLogger(XtcDebugServer::class.java)

        /** The exception filter that stops where an exception that nothing catches is thrown; on by default. */
        const val UNCAUGHT_EXCEPTIONS = "uncaught"

        /** The exception filter that stops where any exception is thrown. */
        const val ALL_EXCEPTIONS = "all"

        /** An exception class's name, as an exception filter's condition names it. */
        private val EXCEPTION_TYPE = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** What an exception filter's condition is. */
        const val EXCEPTION_TYPES =
            "Exception classes, separated by commas, such as IllegalArgument, OutOfBounds; " +
                "the filter then stops only at them and at the classes that extend them."

        /** The answer to `exceptionInfo` for a fiber that did not stop the program at an exception. */
        const val NO_EXCEPTION = "The thread did not stop the program at an exception."

        /** A breakpoint set before the program starts. */
        const val PENDING = "Installed when the program starts."

        /** A breakpoint of a launch without debugging. */
        const val NOT_DEBUGGING = "This program runs without debugging."

        /** A breakpoint the client gives no file for. */
        const val NO_SOURCE = "A breakpoint needs a source file."

        /** Why a fiber cannot be stepped. */
        const val NOT_STOPPED = "That thread is not stopped, so it cannot be stepped."

        /** Why a frame cannot restart: its fiber does not wait at a line, or the frame says it cannot (`canRestart`). */
        const val NOT_RESTARTABLE = "That frame cannot be restarted."

        /**
         * The thread a debugged program shows while it runs and none of its fibers is stopped, named after its module:
         * the one an editor's Pause acts on, which pauses the whole program. Fiber IDs count up from 0.
         */
        const val RUNNING_THREAD = Int.MAX_VALUE

        /** Why a program that does not run under the debugger cannot be paused. */
        const val NOT_PAUSABLE = "The program is not running under the debugger, so it cannot be paused."

        /** A launch whose build fails runs nothing. */
        const val BUILD_FAILED = "Build failed; nothing was run."

        /**
         * How long code typed in the console may run on the stopped fiber (decided 2026-10-23): longer than any
         * expression needs, short enough that a loop does not leave the console waiting.
         */
        val CONSOLE_LIMIT = 2.seconds

        /** The answer to an evaluation that `cancel` ended, as DAP names it. */
        const val CANCELLED = "cancelled"

        /** Why a debug console line is not evaluated. */
        const val NO_EVALUATION =
            "Nothing is evaluated while the program runs; a line typed in the console then is its input."

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
        invalidates = args.supportsInvalidatedEvent == true
        afterResponse.execute { client?.initialized() }
        val capabilities =
            Capabilities().apply {
                supportsConfigurationDoneRequest = true
                supportsTerminateRequest = true
                supportsEvaluateForHovers = true
                supportsConditionalBreakpoints = true
                supportsHitConditionalBreakpoints = true
                supportsFunctionBreakpoints = true
                supportsLogPoints = true
                supportsExceptionInfoRequest = true
                supportsExceptionFilterOptions = true
                supportsCancelRequest = true
                supportsValueFormattingOptions = true
                supportsSetVariable = true
                supportsCompletionsRequest = true
                supportsModulesRequest = true
                supportsLoadedSourcesRequest = true
                supportsStepInTargetsRequest = true
                supportsRestartFrame = true
                completionTriggerCharacters = arrayOf(".")
                exceptionBreakpointFilters =
                    arrayOf(
                        ExceptionBreakpointsFilter().apply {
                            filter = UNCAUGHT_EXCEPTIONS
                            label = "Uncaught Exceptions"
                            description = "Stop where an exception that nothing catches is thrown."
                            default_ = true
                            supportsCondition = true
                            conditionDescription = EXCEPTION_TYPES
                        },
                        ExceptionBreakpointsFilter().apply {
                            filter = ALL_EXCEPTIONS
                            label = "All Exceptions"
                            description = "Stop where any exception is thrown, also one that is caught."
                            default_ = false
                            supportsCondition = true
                            conditionDescription = EXCEPTION_TYPES
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
        val requests =
            args.breakpoints?.map {
                BreakpointRequest(lastBreakpoint.incrementAndGet(), it.line - lineBase, it.condition, it.hitCondition, it.logMessage)
            }
                ?: lines.map { BreakpointRequest(lastBreakpoint.incrementAndGet(), it - lineBase) }
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
     * DAP: setFunctionBreakpoints
     *
     * Replaces the function breakpoints, by a method's name, which stop at the first line of the program's methods of that
     * name. Each is answered at the line of the first such method, or unverified when none has the name. They are
     * installed as breakpoints are: at once while a debugged program runs, and before it starts, pending.
     */
    override fun setFunctionBreakpoints(args: SetFunctionBreakpointsArguments): CompletableFuture<SetFunctionBreakpointsResponse> {
        val requests =
            args.breakpoints.orEmpty().map {
                FunctionBreakpointRequest(lastBreakpoint.incrementAndGet(), it.name.orEmpty(), it.condition, it.hitCondition)
            }
        logger.info("setFunctionBreakpoints: names={}", requests.map { it.name })
        functionBreakpoints = requests
        val states =
            when {
                !debugging -> CompletableFuture.completedFuture(requests.map { BreakpointState(it.id, 0, false, NOT_DEBUGGING) })
                !debuggedRunning() -> CompletableFuture.completedFuture(requests.map { BreakpointState(it.id, 0, false, PENDING) })
                else -> runs.setFunctionBreakpoints(runId, requests)
            }
        return states.thenApply { installed ->
            SetFunctionBreakpointsResponse().apply { breakpoints = installed.map(::functionBreakpoint).toTypedArray() }
        }
    }

    /**
     * DAP: setExceptionBreakpoints
     *
     * Chooses the exception filters: [UNCAUGHT_EXCEPTIONS] and [ALL_EXCEPTIONS], by `filters`, or by `filterOptions`
     * with a condition that names exception classes ([EXCEPTION_TYPES]); a blank condition means any exception. A
     * condition that is not names leaves its filter off, and the answer says why. While a debugged program runs they
     * apply at once; before it starts, from its first line.
     */
    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments?): CompletableFuture<SetExceptionBreakpointsResponse> {
        val filters = args?.filters?.toList().orEmpty()
        val options = args?.filterOptions?.toList().orEmpty()
        logger.info("setExceptionBreakpoints: filters={}, options={}", filters, options.map { it.filterId to it.condition })
        val types = mutableMapOf<String, List<String>>()
        val answers = filters.map { Breakpoint().apply { isVerified = true } }.toMutableList()
        for (option in options) {
            val named = exceptionTypes(option.condition)
            answers.add(
                Breakpoint().apply {
                    isVerified = named != null
                    message = if (named == null) "\"${option.condition}\" is not exception classes. $EXCEPTION_TYPES" else null
                },
            )
            if (named != null) types[option.filterId] = named
        }
        val chosen = filters.toSet() + types.keys
        exceptionStops =
            ExceptionStops(
                all = ALL_EXCEPTIONS in chosen,
                uncaught = UNCAUGHT_EXCEPTIONS in chosen,
                allTypes = types[ALL_EXCEPTIONS].takeUnless { ALL_EXCEPTIONS in filters }.orEmpty(),
                uncaughtTypes = types[UNCAUGHT_EXCEPTIONS].takeUnless { UNCAUGHT_EXCEPTIONS in filters }.orEmpty(),
            )
        if (debuggedRunning()) runs.setExceptionStops(runId, exceptionStops)
        return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse().apply { breakpoints = answers.toTypedArray() })
    }

    /**
     * DAP: exceptionInfo
     *
     * The exception a stopped fiber stopped the program at: its type, its text, the stack trace it carries, and
     * whether it stopped as uncaught (`unhandled`) or as any thrown one (`always`).
     */
    override fun exceptionInfo(args: ExceptionInfoArguments): CompletableFuture<ExceptionInfoResponse> {
        if (!debuggedRunning()) return failed(NOT_STOPPED)
        return runs.exceptionInfo(runId, args.threadId).thenApply { info ->
            info ?: throw error(NO_EXCEPTION)
            ExceptionInfoResponse().apply {
                exceptionId = info.type
                description = info.text
                breakMode = if (info.uncaught) ExceptionBreakMode.UNHANDLED else ExceptionBreakMode.ALWAYS
                details =
                    ExceptionDetails().apply {
                        typeName = info.type
                        message = info.text
                        stackTrace = info.stackTrace
                    }
            }
        }
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
     * array, or a comma-separated string), `cwd` or `workingDirectory`, `noDebug`, `stopOnEntry`, which stops the
     * program at its first line, and `evaluationRunsProgram`, which lets the whole program run while code from the
     * console does.
     */
    override fun launch(args: Map<String, Any>?): CompletableFuture<Void?> {
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
        evaluationRunsProgram = args?.get("evaluationRunsProgram") == true
        programName = target.moduleName ?: target.module.substringAfterLast('/').substringBefore('.')
        val stopOnEntry = args?.get("stopOnEntry") == true
        console("Building and running ${target.moduleName ?: target.module} with the embedded compiler...")
        val ready =
            configured.copy().completeOnTimeout(Unit, CONFIGURATION_WAIT_SECONDS, SECONDS).thenApply {
                if (debugging) {
                    installed = breakpoints.toMap()
                    installedExceptions = exceptionStops
                    installedFunctions = functionBreakpoints
                    DebugSetup(installed.values.toList(), installedExceptions, stopOnEntry, installedFunctions)
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
                if (debugging) {
                    client?.thread(
                        ThreadEventArguments().apply {
                            reason = ThreadEventArgumentsReason.STARTED
                            threadId = RUNNING_THREAD
                        },
                    )
                }
                if (debugging) installChanged()
                null
            }
    }

    /** DAP: attach. A session launches its program; there is nothing to attach to. */
    override fun attach(args: Map<String, Any>?): CompletableFuture<Void> {
        logger.info("attach: refused: args={}", args)
        return failed("Attaching is not available; launch the module instead.")
    }

    /** DAP: threads. The fibers of the stopped program, those that wait at a line first; while it runs, [RUNNING_THREAD]. */
    override fun threads(): CompletableFuture<ThreadsResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(ThreadsResponse().apply { threads = emptyArray() })
        }
        return runs
            .threads(runId)
            .exceptionally { emptyList() }
            .thenApply { waiting ->
                val fibers = waiting.ifEmpty { listOf(ThreadState(RUNNING_THREAD, programName)) }
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

    /** DAP: modules. The debugged program's modules, its own and the libraries it uses, a page at a time. */
    override fun modules(args: ModulesArguments?): CompletableFuture<ModulesResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(
                ModulesResponse().apply {
                    modules = emptyArray()
                    totalModules = 0
                },
            )
        }
        return runs.modules(runId).exceptionally { emptyList() }.thenApply { all ->
            val first = args?.startModule ?: 0
            val count = args?.moduleCount?.takeIf { it > 0 } ?: all.size
            ModulesResponse().apply {
                modules =
                    all
                        .drop(first)
                        .take(count)
                        .map { module ->
                            Module().apply {
                                setId(module.name)
                                name = module.name
                                path = module.sourceDir
                                version = module.version
                                isUserCode = module.own
                            }
                        }.toTypedArray()
                totalModules = all.size
            }
        }
    }

    /**
     * DAP: loadedSources. The source files of the program's modules: those of its own where they were compiled from, and
     * a bundled library's in the source bundled with it, as its frames are.
     */
    override fun loadedSources(args: LoadedSourcesArguments?): CompletableFuture<LoadedSourcesResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(LoadedSourcesResponse().apply { sources = emptyArray() })
        }
        return runs.modules(runId).exceptionally { emptyList() }.thenApply { modules ->
            LoadedSourcesResponse().apply {
                sources =
                    modules
                        .flatMap { module ->
                            module.sources.mapNotNull { source ->
                                (runs.librarySource(module.name, source) ?: module.sourceDir?.let { it + source })?.let { file ->
                                    Source().apply {
                                        path = file
                                        name = File(file).name
                                    }
                                }
                            }
                        }.toTypedArray()
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
        val filter =
            when (args.filter) {
                VariablesArgumentsFilter.NAMED -> ChildFilter.NAMED
                VariablesArgumentsFilter.INDEXED -> ChildFilter.INDEXED
                else -> ChildFilter.ALL
            }
        val hex = args.format?.hex ?: hexValues
        return runs.variables(runId, args.variablesReference, hex, filter, args.start ?: 0, args.count ?: 0).thenApply { states ->
            VariablesResponse().apply {
                variables =
                    states
                        .map { state ->
                            Variable().apply {
                                name = state.name
                                value = state.value
                                type = state.type.ifEmpty { null }
                                variablesReference = state.variablesReference
                                // a client that pages, such as VS Code, reads a large array's elements a part at a time
                                indexedVariables = state.elements.takeIf { it > 0 }
                                if (state.lazy) {
                                    presentationHint = VariablePresentationHint().apply { lazy = true }
                                }
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

    /**
     * DAP: stepIn. Steps the fiber to the next line it reaches, also in a method that it calls; with a `targetId`, into
     * that call of its line, which [stepInTargets] gave.
     */
    override fun stepIn(args: StepInArguments): CompletableFuture<Void> {
        val target = args.targetId ?: return step(args.threadId, StepKind.IN)
        if (!debuggedRunning()) return failed(NOT_STOPPED)
        return runs.stepInto(runId, args.threadId, target).thenApply<Void> { stepped -> if (stepped) null else throw error(NOT_STOPPED) }
    }

    /** DAP: stepInTargets. The calls that a stopped frame's line makes, in the order of its code, which stepIn goes into. */
    override fun stepInTargets(args: StepInTargetsArguments): CompletableFuture<StepInTargetsResponse> {
        if (!debuggedRunning()) {
            return CompletableFuture.completedFuture(StepInTargetsResponse().apply { targets = emptyArray() })
        }
        return runs.stepInTargets(runId, args.frameId).exceptionally { emptyList() }.thenApply { calls ->
            StepInTargetsResponse().apply {
                targets =
                    calls
                        .map { call ->
                            StepInTarget().apply {
                                id = call.id
                                label = call.label
                            }
                        }.toTypedArray()
            }
        }
    }

    /**
     * DAP: restartFrame. The frame's caller calls it again, once its fiber has left it and the frames above it without
     * running more of their code; the program stops again, with the reason `restart`, at the call's first line. Only a
     * frame with `canRestart` can: one that the program's own code called, of a fiber that waits at a line.
     */
    override fun restartFrame(args: RestartFrameArguments): CompletableFuture<Void> {
        if (!debuggedRunning()) return failed(NOT_RESTARTABLE)
        return runs
            .restartFrame(runId, args.frameId)
            .thenApply<Void> { restarted -> if (restarted) null else throw error(NOT_RESTARTABLE) }
    }

    /** DAP: stepOut. Steps the fiber until its method returns, to the caller's next line. */
    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> = step(args.threadId, StepKind.OUT)

    /**
     * DAP: pause. The program stops, with the reason `pause`, at the next line any of its fibers reaches; one whose
     * fibers all wait, for input say, stops when one of them next runs. A stopped program stays as it is.
     */
    override fun pause(args: PauseArguments?): CompletableFuture<Void> {
        if (!debuggedRunning() || !runs.pause(runId)) return failed(NOT_PAUSABLE)
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: evaluate
     *
     * In a stopped frame, a watch, a hover or a line typed in the debug console evaluates an expression, such as
     * `point.x + 1` or `ints[i] > 0`, without running the program's code; what cannot be evaluated fails with the
     * reason. What that refuses in a console line, such as a call, runs as code on the stopped fiber, for at most
     * [CONSOLE_LIMIT], and answers with its value's `toString()`; `cancel` ends it sooner, with [CANCELLED]. Once
     * code has run, `invalidated` tells a client that takes it that the variables may have changed.
     * Without a frame, as while the program runs, a line typed in the debug console is the program's console input.
     */
    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> {
        val frameId = args.frameId
        if (frameId != null && debuggedRunning()) {
            val hex = args.format?.hex ?: hexValues
            val read = runs.evaluate(runId, frameId, args.expression, hex)
            val value =
                if (args.context == EvaluateArgumentsContext.REPL) {
                    // a line typed in the console is code, which runs, unless reading it as a path does without
                    // running any: that changes nothing, and shows a value as the Variables view does
                    val evaluation = Evaluation()
                    val seq = handling.get()?.also { evaluations[it] = evaluation }
                    read
                        .exceptionallyCompose {
                            evaluation.start {
                                val program = evaluationRunsProgram
                                runs.run(runId, frameId, args.expression, CONSOLE_LIMIT, program, hex).also { run ->
                                    run.whenComplete { _, _ -> ran(program) }
                                }
                            }
                        }.whenComplete { _, _ -> seq?.let(evaluations::remove) }
                } else {
                    read
                }
            return value.handle { state, failure ->
                if (failure != null) {
                    val cause = (failure as? CompletionException)?.cause ?: failure
                    throw error(if (cause is CancellationException) CANCELLED else cause.message ?: cause.toString())
                }
                EvaluateResponse().apply {
                    result = state.value
                    type = state.type.ifEmpty { null }
                    variablesReference = state.variablesReference
                    indexedVariables = state.elements.takeIf { it > 0 }
                }
            }
        }
        if (args.context != EvaluateArgumentsContext.REPL) {
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

    /**
     * DAP: setVariable
     *
     * Changes a stopped frame's local, an object's field or an array's element, which `variablesReference` read, to
     * the value of an expression, such as `5`, `n * 2` or `"text"`, evaluated without running the program's code; what
     * cannot take the value, as a variable of another type or a field of an immutable object, fails with the reason.
     * The answer is the variable's new value, which the client shows; the program goes on with it once it resumes.
     */
    override fun setVariable(args: SetVariableArguments): CompletableFuture<SetVariableResponse> {
        if (!debuggedRunning()) return failed("The program is not stopped, so its variables cannot be changed.")
        val hex = args.format?.hex ?: hexValues
        return runs.setVariable(runId, args.variablesReference, args.name, args.value, hex).handle { state, failure ->
            if (failure != null) {
                val cause = (failure as? CompletionException)?.cause ?: failure
                throw error(cause.message ?: cause.toString())
            }
            SetVariableResponse().apply {
                value = state.value
                type = state.type.ifEmpty { null }
                variablesReference = state.variablesReference
                indexedVariables = state.elements.takeIf { it > 0 }
            }
        }
    }

    /**
     * DAP: completions
     *
     * Completes the name typed in the debug console where its cursor is, at `column` of `line` of `text`, in the
     * stopped frame: after a dot, with the members of the value before it, which is evaluated without running the
     * program's code, otherwise with the frame's variables, `this`, and the members of `this`. The items are the names
     * that start with what is typed of the name, which they replace: `length` characters before the cursor. Without a
     * frame, or one of the stopped program's, there are none.
     */
    override fun completions(args: CompletionsArguments): CompletableFuture<CompletionsResponse> {
        val frameId = args.frameId
        if (frameId == null ||
            !debuggedRunning()
        ) {
            return CompletableFuture.completedFuture(CompletionsResponse().apply { targets = emptyArray() })
        }
        val text = args.text
        val lines = text.split('\n')
        val line = ((args.line ?: (1 + lineBase)) - lineBase - 1).coerceIn(0, lines.lastIndex)
        val offset = lines.take(line).sumOf { it.length + 1 } + (args.column - columnBase - 1).coerceIn(0, lines[line].length)
        return runs.completions(runId, frameId, text, offset).handle { state, failure ->
            if (failure != null) logger.debug("completions: none, {}", failure.message)
            // no start: DAP measures it in the client's column base, but adapters send it 0-based and LSP4IJ and VS
            // Code read it so, although both say columns start at 1 (UP43); with the length alone, both replace the
            // typed part before the cursor
            val typed = state?.length?.takeIf { it > 0 }
            CompletionsResponse().apply {
                targets =
                    state
                        ?.items
                        .orEmpty()
                        .map { item ->
                            CompletionItem().apply {
                                label = item.label
                                type = CompletionItemType.valueOf(item.kind.uppercase())
                                length = typed
                            }
                        }.toTypedArray()
            }
        }
    }

    /**
     * `xtc/valueFormat`, a request of this adapter's own: shows integers in hexadecimal ([ValueFormat.hex]), or in
     * decimal, for the `variables` and `evaluate` requests that name no `format`, until it is set again. VS Code's
     * Variables view sets it from the Ecstasy extension's "Show Values as Hexadecimal" item, since VS Code never sends a
     * format itself (UP38); a client that takes `invalidated` is then told to read the variables again.
     */
    @JsonRequest("xtc/valueFormat")
    fun valueFormat(args: ValueFormat?): CompletableFuture<Void> {
        hexValues = args?.hex == true
        if (invalidates && debuggedRunning()) {
            client?.invalidated(InvalidatedEventArguments().apply { areas = arrayOf(InvalidatedAreas.VARIABLES) })
        }
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: cancel
     *
     * Ends a console evaluation that runs code, which `requestId` names, before its time limit; it then answers with
     * [CANCELLED]. Any other request is answered too soon to be cancelled, so its cancel does nothing.
     */
    override fun cancel(args: CancelArguments?): CompletableFuture<Void> {
        args?.requestId?.let(evaluations::get)?.cancel()
        return CompletableFuture.completedFuture(null)
    }

    /**
     * Handles one request, whose `seq` is [seq], with [handle]: the endpoint calls each request's method through this,
     * so that the method knows which request it answers, and a later `cancel` can name it.
     */
    internal fun <T> receiving(
        seq: Int?,
        handle: () -> T,
    ): T {
        handling.set(seq)
        try {
            return handle()
        } finally {
            handling.remove()
        }
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

            override fun functionBreakpoints(states: List<BreakpointState>) = functionsChanged(states)

            override fun logged(text: String) = output(OutputEventArgumentsCategory.CONSOLE, "$text\n")

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

    /**
     * Code has run on a stopped fiber: what it changed may show in the variables, which the client reads again, and
     * after a run of the whole [program], also the threads and stacks, since its other fibers moved.
     */
    private fun ran(program: Boolean) {
        if (invalidates) {
            val area = if (program) InvalidatedAreas.ALL else InvalidatedAreas.VARIABLES
            client?.invalidated(InvalidatedEventArguments().apply { areas = arrayOf(area) })
        }
    }

    /** True while a debugged program runs, stopped or not. */
    private fun debuggedRunning() = debugging && started && !ended.isDone

    /** Installs the breakpoints, function breakpoints and exception filters the client changed after the program's were taken, before it started. */
    private fun installChanged() {
        breakpoints.forEach { (path, current) ->
            if (installed[path] !== current) {
                runs.setBreakpoints(runId, current).thenAccept { states -> changed(path, states) }
            }
        }
        if (exceptionStops != installedExceptions) runs.setExceptionStops(runId, exceptionStops)
        if (functionBreakpoints !== installedFunctions) {
            runs.setFunctionBreakpoints(runId, functionBreakpoints).thenAccept(::functionsChanged)
        }
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

    /** Tells the client how the function breakpoints were installed. */
    private fun functionsChanged(states: List<BreakpointState>) {
        states.forEach { state ->
            client?.breakpoint(
                BreakpointEventArguments().apply {
                    reason = BreakpointEventArgumentsReason.CHANGED
                    breakpoint = functionBreakpoint(state)
                },
            )
        }
    }

    /** A function breakpoint, at the first line of the first method of its name if one has it. */
    private fun functionBreakpoint(state: BreakpointState) =
        Breakpoint().apply {
            id = state.id
            isVerified = state.verified
            message = state.message
            state.path?.let { path ->
                line = state.line + lineBase
                source = sources[path] ?: Source().apply { this.path = path }
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
            // a bundled library's frame is shown in the source its code was compiled from
            val shown = frame.module?.let { module -> frame.source?.let { runs.librarySource(module, it) } } ?: frame.path
            shown?.let { file ->
                source =
                    Source().apply {
                        path = file
                        name = File(file).name
                    }
            }
            line = frame.line + lineBase
            column = if (shown == null) 0 else 1 + columnBase
            canRestart = frame.restartable
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

    /** The exception classes an exception filter's condition names; none for a blank one, and null if it is not names. */
    private fun exceptionTypes(condition: String?): List<String>? {
        if (condition.isNullOrBlank()) return emptyList()
        val names = condition.split(',').map { it.trim() }
        return names.takeIf { it.all(EXCEPTION_TYPE::matches) }
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

    /**
     * A console evaluation in progress, which `cancel` ends: before its code starts to run, it then never does, and
     * while it runs, the run ends.
     */
    private class Evaluation {
        @Volatile
        private var cancelled = false

        @Volatile
        private var run: CompletableFuture<*>? = null

        /** Starts the evaluation's run, unless it was cancelled first. */
        fun <T> start(run: () -> CompletableFuture<T>): CompletableFuture<T> {
            if (cancelled) return CompletableFuture.failedFuture(CancellationException())
            return run().also {
                this.run = it
                // a cancel between the check and now has no run to end yet
                if (cancelled) it.cancel(false)
            }
        }

        fun cancel() {
            cancelled = true
            run?.cancel(false)
        }
    }

    private fun <T> failed(message: String): CompletableFuture<T> = CompletableFuture.failedFuture(error(message))
}
