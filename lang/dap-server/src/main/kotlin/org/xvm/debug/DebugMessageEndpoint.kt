package org.xvm.debug

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.MessageIssueException
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.debug.DebugRemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints

/**
 * A DAP endpoint for a transport that carries one JSON message at a time rather than a byte stream, such as the
 * language server's `xtc/debugMessage` notifications. [receive] handles a message from the client, and [send]
 * gets each message for it, without the `Content-Length` framing of a stream. The endpoint is set up as lsp4j's
 * DAP launcher sets one up over streams.
 */
class DebugMessageEndpoint(
    server: XtcDebugServer,
    private val send: (String) -> Unit,
) {
    private val json: MessageJsonHandler
    private val endpoint: RemoteEndpoint

    init {
        val methods =
            ServiceEndpoints.getSupportedMethods(IDebugProtocolClient::class.java) +
                ServiceEndpoints.getSupportedMethods(server.javaClass)
        json = DebugMessageJsonHandler(methods)
        endpoint = DebugRemoteEndpoint({ message -> send(json.serialize(message)) }, ServiceEndpoints.toEndpoint(server))
        json.methodProvider = endpoint
        endpoint.jsonHandler = json
        server.connect(ServiceEndpoints.toServiceObject(endpoint, IDebugProtocolClient::class.java))
    }

    /** Handles one message from the client. A request's answer, like every event, goes out through [send]. */
    fun receive(message: String) {
        try {
            endpoint.consume(json.parseMessage(message))
        } catch (e: MessageIssueException) {
            endpoint.handle(e.rpcMessage, e.issues)
        }
    }
}
