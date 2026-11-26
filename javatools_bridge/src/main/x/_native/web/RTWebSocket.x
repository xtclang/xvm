import libweb.WebSocket;

/**
 * The native WebSocket implementation.
 */
service RTWebSocket<Message extends String|Byte[]>(RTServer server, Object context,
        String? protocol, Int maxMessageSize, WebSocket<Message> endpoint)
        extends WebSocket<Message> {
    construct(RTServer server, Object context, String? protocol, Int maxMessageSize,
            WebSocket<Message> endpoint) {
        construct WebSocket(maxMessageSize, protocol);

        this.server   = server;
        this.context  = context;
        this.endpoint = endpoint;
    }

    /**
     * True once local closure or a transport close event prevents further message delivery and
     * duplicate close notifications.
     */
    private Boolean closed = False;

    /**
     * True once the disconnect callback has been delivered or is owned by a local disconnect.
     */
    private Boolean disconnectNotified = False;

    // ----- WebSocket operation called by the underlying socket -----------------------------------

    @Override
    Boolean open.get() = !closed && server.isWebSocketOpen(context);

    @Override
    void send(Message message) = server.sendWebSocket(context, message);

    @Override
    conditional Message receive() = False;

    @Override
    void disconnect(UInt16 code = WebSocket.NormalClosure, String reason = "") {
        assert:arg (code >= NormalClosure && code <= UnsupportedData ||
                code >= InvalidPayload && code <= BadGateway || code >= 3000 && code <= 4999) &&
                reason.utf8().size <= 123 as "Invalid WebSocket close status or reason";

        // the application's disconnect() invokes its own onDisconnect() callback
        closed             = True;
        disconnectNotified = True;
        server.closeWebSocket(context, code, reason);
    }

    // ----- WebSocket operation called by the native code -----------------------------------------

    @Override
    void onConnect() {
        try {
            endpoint.connect(this);
        } catch (Exception e) {
            fail(InternalError, "WebSocket onConnect failed");
        }
    }

    @Override
    void onMessage(String|Byte[] message) {
        assert !closed;
        assert message.is(Message);

        try {
            endpoint.onMessage(message);
        } catch (Exception e) {
            fail(InternalError, "WebSocket onMessage failed");
        }
    }

    @Override
    void onError(UInt16 errorCode) {
        if (disconnectNotified) {
            return;
        }
        closed = True;
        try {
            endpoint.onError(errorCode);
        } catch (Exception ignore) {
            // allow disconnect notification to proceed if the error callback fails
        }
    }

    /**
     * Report transport closure once, after previously queued message callbacks.
     */
    @Override
    void onDisconnect(UInt16 code, String reason) {
        if (disconnectNotified) {
            return;
        }
        closed             = True;
        disconnectNotified = True;
        try {
            endpoint.onDisconnect(code, reason);
        } catch (Exception ignore) {
            // not our problem anymore
        }
    }

    // ----- internal helpers ----------------------------------------------------------------------

    private void fail(UInt16 code, String reason) {
        if (closed) {
            return;
        }
        closed = True;
        try {
            server.closeWebSocket(context, code, reason);
        } catch (Exception ignore) {}
        try {
            onError(code);
        } catch (Exception ignore) {}
        onDisconnect(code, reason);
    }
}
