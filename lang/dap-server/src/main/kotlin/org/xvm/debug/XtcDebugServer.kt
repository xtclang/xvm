package org.xvm.debug

import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.ExitedEventArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.ThreadsResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One Ecstasy Debug Adapter Protocol (DAP) session. A launch builds and runs its module through [runs], the
 * language server's embedded build and run, so a debug launch and a Run use the same build, worker and program
 * output. There is no runtime debugger yet (R7 in lang/doc/plans/plan-embedded-execution.md), so the session says
 * so instead of pretending: breakpoints are reported unverified with that reason, there are no threads, and only
 * the requests that work are advertised.
 *
 * The session owns the program it launches. Terminating or disconnecting stops it, also while it still builds;
 * its exit ends the session with `exited` and `terminated`; and the language server stops it when the session's
 * transport or the server itself goes away. A line typed in the client's debug console while the program runs is
 * the program's console input.
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

    companion object {
        private val logger = LoggerFactory.getLogger(XtcDebugServer::class.java)

        /** Why breakpoints are not set. */
        const val NOT_AVAILABLE = "The Ecstasy debugger is not available yet, so a program runs without stopping at breakpoints."

        /** A launch whose build fails runs nothing. */
        const val BUILD_FAILED = "Build failed; nothing was run."

        /** Why a debug console line is not evaluated. */
        const val NO_EVALUATION =
            "Expressions cannot be evaluated without the debugger; a line typed while the program runs is its console input."

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
        afterResponse.execute { client?.initialized() }
        val capabilities =
            Capabilities().apply {
                supportsConfigurationDoneRequest = true
                supportsTerminateRequest = true
            }
        return CompletableFuture.completedFuture(capabilities)
    }

    /**
     * DAP: setBreakpoints
     *
     * No breakpoint can be installed, so each is reported unverified with the reason, never as set.
     */
    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        val sourcePath = args.source?.path ?: args.source?.name ?: "<unknown>"
        logger.info("setBreakpoints: source={}, lines={}", sourcePath, args.breakpoints?.map { it.line }.orEmpty())
        val breakpoints =
            args.breakpoints
                ?.map { sourceBreakpoint ->
                    Breakpoint().apply {
                        isVerified = false
                        message = NOT_AVAILABLE
                        line = sourceBreakpoint.line
                        source = args.source
                    }
                }?.toTypedArray() ?: emptyArray()
        return CompletableFuture.completedFuture(SetBreakpointsResponse().apply { this.breakpoints = breakpoints })
    }

    /**
     * DAP: setExceptionBreakpoints
     *
     * Accepted and ignored: the adapter advertises no exception filters.
     */
    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments?): CompletableFuture<SetExceptionBreakpointsResponse> {
        logger.info("setExceptionBreakpoints: filters={}", args?.filters?.toList())
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
     * Builds the module and starts it, answering once it has started; a failed build answers with an error after
     * reporting the build's diagnostics as output. Arguments: `module` (a module name), `moduleUri` (a source URI of
     * the module, used in place of the name when given), `method`, `args` (an array, or a comma-separated string),
     * `cwd` or `workingDirectory`, and `noDebug`.
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
        console("Building and running ${target.moduleName ?: target.module} with the embedded compiler...")
        if (args?.get("noDebug") != true) {
            console(NOT_AVAILABLE)
        }
        val ready = configured.copy().completeOnTimeout(Unit, CONFIGURATION_WAIT_SECONDS, SECONDS)
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
                null
            }
    }

    /** DAP: attach. A session launches its program; there is nothing to attach to. */
    override fun attach(args: MutableMap<String, Any>?): CompletableFuture<Void> {
        logger.info("attach: refused: args={}", args)
        return failed("Attaching is not available; launch the module instead.")
    }

    /** DAP: threads. Without the debugger, a program's fibers are not shown, so there are none. */
    override fun threads(): CompletableFuture<ThreadsResponse> =
        CompletableFuture.completedFuture(ThreadsResponse().apply { threads = emptyArray() })

    /** DAP: pause. A program cannot be stopped without the debugger. */
    override fun pause(args: PauseArguments?): CompletableFuture<Void> = failed(NOT_AVAILABLE)

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
