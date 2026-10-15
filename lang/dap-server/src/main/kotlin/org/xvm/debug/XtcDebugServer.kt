package org.xvm.debug

import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.ThreadsResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Placeholder Ecstasy Debug Adapter Protocol (DAP) server. There is no runtime debugger yet, so it says so
 * instead of pretending: breakpoints are reported unverified with that reason, launch and attach fail with
 * it, and there are no threads. A real implementation needs session-scoped runtime debugging (R6/R7 in
 * lang/doc/plans/plan-embedded-execution.md).
 *
 * DAP is a separate protocol from LSP, specifically for debugging. LSP4J provides the
 * `org.eclipse.lsp4j.debug` module with the server-side DAP API.
 */
class XtcDebugServer : IDebugProtocolServer {
    private var client: IDebugProtocolClient? = null

    companion object {
        private val logger = LoggerFactory.getLogger(XtcDebugServer::class.java)

        /** Shown to the user for every request that would need a debugger. */
        const val NOT_AVAILABLE = "Ecstasy debugging is not available yet; run the module without debugging instead."
    }

    /** Connect to the DAP client (IDE). */
    fun connect(client: IDebugProtocolClient) {
        this.client = client
        logger.info("connect: Connected to debug client")
    }

    /**
     * DAP: initialize
     *
     * Called first by the client. Advertises nothing beyond the configuration handshake.
     */
    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        logger.info("initialize: clientID={}, adapterID={}", args.clientID, args.adapterID)
        val capabilities =
            Capabilities().apply {
                supportsConfigurationDoneRequest = true
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
    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture<SetExceptionBreakpointsResponse> {
        logger.info("setExceptionBreakpoints: filters={}", args.filters?.toList())
        return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse())
    }

    /**
     * DAP: configurationDone
     *
     * Signals that the client has finished sending initial configuration requests.
     */
    override fun configurationDone(args: ConfigurationDoneArguments): CompletableFuture<Void> {
        logger.info("configurationDone")
        return CompletableFuture.completedFuture(null)
    }

    /** DAP: launch. Fails with the reason, so the client ends the session instead of waiting on nothing. */
    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> {
        logger.info("launch: refused, no debugger: args={}", args)
        return unavailable()
    }

    /** DAP: attach. Fails with the reason, like [launch]. */
    override fun attach(args: MutableMap<String, Any>): CompletableFuture<Void> {
        logger.info("attach: refused, no debugger: args={}", args)
        return unavailable()
    }

    /** DAP: threads. Nothing runs under this adapter, so there are none. */
    override fun threads(): CompletableFuture<ThreadsResponse> =
        CompletableFuture.completedFuture(ThreadsResponse().apply { threads = emptyArray() })

    /**
     * DAP: disconnect
     *
     * Called when the client wants to disconnect from the debug session.
     */
    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        logger.info("disconnect: restart={}", args.restart)
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: terminate
     *
     * Called when the client wants to terminate the debuggee.
     */
    override fun terminate(args: TerminateArguments): CompletableFuture<Void> {
        logger.info("terminate: restart={}", args.restart)
        return CompletableFuture.completedFuture(null)
    }

    private fun unavailable(): CompletableFuture<Void> =
        CompletableFuture.failedFuture(ResponseErrorException(ResponseError(ResponseErrorCode.RequestFailed, NOT_AVAILABLE, null)))
}
