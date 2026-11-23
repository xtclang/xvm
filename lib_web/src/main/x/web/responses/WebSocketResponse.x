/**
 * An HTTP upgrade response carrying the application's WebSocket service. Interceptors can add
 * handshake headers and cookies before the dispatcher transfers the connection to the socket.
 */
@AutoFreezable
class WebSocketResponse(WebSocket<String|Byte[]> socket,
                        String?                  protocol,
                        Int                      maxMessageSize)
        extends SimpleResponse(socket.open ? Conflict : SwitchingProtocols) {

    construct(WebSocket<String|Byte[]> socket) {
        construct WebSocketResponse(socket, socket.protocol, socket.maxMessageSize);
    }
}
