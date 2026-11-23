import ecstasy.io.IOException;

import libweb.WebSocket;

/**
 * The service boundary for one upgraded connection. Native I/O suspends the calling fiber.
 */
@Concurrent
service RTWebSocket<Message extends String|Byte[]>(RTServer server, Object context,
        String? protocol, WebSocket<Message> endpoint)
        implements WebSocket<Message> {
    @Override
    Boolean open.get() = server.isWebSocketOpen(context);

    /**
     * Start delivery after the dispatcher has assigned the application's socket implementation.
     */
    @Override
    void onConnect() {
        Exception? cause = Null;
        UInt16     code   = NormalClosure;
        String     reason = "";
        try {
            endpoint.onConnect();
            while (val message := server.receiveWebSocket(context)) {
                if (!message.is(Message)) {
                    code   = UnsupportedData;
                    reason = "Unexpected WebSocket message type";
                    cause  = new IOException(reason);
                    break;
                }
                handle(message);
            }
            if (cause == Null) {
                code   = server.getWebSocketCloseCode(context);
                reason = server.getWebSocketCloseReason(context);
                if (code != NormalClosure && code != GoingAway) {
                    cause = new IOException($"WebSocket closed with status {code}");
                }
            }
        } catch (Exception e) {
            cause  = e;
            code   = server.getWebSocketCloseCode(context);
            reason = e.message;
            if (code == NormalClosure || code == GoingAway) {
                code = InternalError;
            }
        } finally {
            try {
                if (open) {
                    disconnect(code);
                }
            } catch (Exception e) {
                cause  = cause ?: e;
                code   = InternalError;
                reason = cause.message;
            }
            endpoint.socketImpl = Null;
            if (cause != Null) {
                try {
                    onError(code);
                } catch (Exception ignore) {}
            }
            onDisconnect(code, reason);
        }
    }

    @Override
    void handle(Message message) = endpoint.handle(message);

    @Override
    void onError(UInt16 errorCode) = endpoint.onError(errorCode);

    @Override
    void onDisconnect(UInt16 code, String reason) = endpoint.onDisconnect(code, reason);

    @Override
    void send(Message message) = server.sendWebSocket(context, message);

    @Override
    void disconnect(UInt16 code = WebSocket.NormalClosure, String reason = "") {
        server.closeWebSocket(context, code, reason);
    }
}
