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
import org.eclipse.lsp4j.debug.Thread
import org.eclipse.lsp4j.debug.ThreadsResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Stub XTC Debug Adapter Protocol (DAP) server.
 *
 * Implements [IDebugProtocolServer] to receive breakpoint notifications and other
 * debug requests from IDEs. Currently logs all requests as a foundation for future
 * debugger integration.
 *
 * DAP is a separate protocol from LSP, specifically for debugging. LSP4J provides
 * the `org.eclipse.lsp4j.debug` module with the server-side DAP API.
 */
class XtcDebugServer : IDebugProtocolServer {
    private var client: IDebugProtocolClient? = null

    companion object {
        private val logger = LoggerFactory.getLogger(XtcDebugServer::class.java)
        private const val MAIN_THREAD_ID = 1
    }

    /**
     * Connect to the DAP client (IDE).
     */
    fun connect(client: IDebugProtocolClient) {
        this.client = client
        logger.info("connect: Connected to debug client")
    }

    /**
     * DAP: initialize
     *
     * Called first by the client. Returns server capabilities.
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
     * Called when the user sets or changes breakpoints in a source file.
     * Logs the source file and line numbers, and returns them as verified.
     */
    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        val sourcePath = args.source?.path ?: args.source?.name ?: "<unknown>"
        val lines = args.breakpoints?.map { it.line } ?: emptyList()
        logger.info("setBreakpoints: source={}, lines={}", sourcePath, lines)

        val breakpoints =
            args.breakpoints
                ?.mapIndexed { index, sourceBreakpoint ->
                    Breakpoint().apply {
                        id = index + 1
                        isVerified = true
                        line = sourceBreakpoint.line
                        source = args.source
                    }
                }?.toTypedArray() ?: emptyArray()

        val response =
            SetBreakpointsResponse().apply {
                this.breakpoints = breakpoints
            }
        return CompletableFuture.completedFuture(response)
    }

    /**
     * DAP: setExceptionBreakpoints
     *
     * Called when the user configures exception breakpoint filters.
     */
    @Suppress("ktlint:standard:function-signature")
    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture<SetExceptionBreakpointsResponse> {
        logger.info("setExceptionBreakpoints: filters={}", args.filters?.toList())
        return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse())
    }

    /**
     * DAP: configurationDone
     *
     * Signals that the client has finished sending initial configuration requests
     * (breakpoints, exception breakpoints, etc.).
     */
    override fun configurationDone(args: ConfigurationDoneArguments): CompletableFuture<Void> {
        logger.info("configurationDone")
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: launch
     *
     * Called when the client wants to launch the debuggee.
     * Sends an `initialized` event back to the client.
     */
    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> {
        logger.info("launch: args={}", args)
        client?.initialized()
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: attach
     *
     * Called when the client wants to attach to an already running debuggee.
     * Sends an `initialized` event back to the client.
     */
    override fun attach(args: MutableMap<String, Any>): CompletableFuture<Void> {
        logger.info("attach: args={}", args)
        client?.initialized()
        return CompletableFuture.completedFuture(null)
    }

    /**
     * DAP: threads
     *
     * Returns the list of threads. Currently returns a single dummy thread.
     */
    override fun threads(): CompletableFuture<ThreadsResponse> {
        logger.info("threads")
        val response =
            ThreadsResponse().apply {
                threads =
                    arrayOf(
                        Thread().apply {
                            id = MAIN_THREAD_ID
                            name = "main"
                        },
                    )
            }
        return CompletableFuture.completedFuture(response)
    }

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
}
