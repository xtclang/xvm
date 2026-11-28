/**
 * A bidirectional connection that exchanges WebSocket messages between two peers (typically a
 * "client" and a "server".
 */
@Abstract service WebSocket<Message extends String|Byte[]>
        implements Closeable {

    /**
     * Construct a WebSocket service with the subprotocol and incoming message size limit to use
     * when a connection is established. The subprotocol is negotiated during the opening handshake.
     *
     * @param maxMessageSize  (optional) the maximum size, in bytes, of a complete incoming text or
     *                        binary message, including all fragments
     * @param protocol        (optional) the application subprotocol to negotiate; for a server
     *                        endpoint, the client must offer this value in its
     *                        `Sec-WebSocket-Protocol` header
     * @param idleTimeout     (optional) the transport idle timeout, at least one second
     *
     * @see [RFC 6455 §4.2.2](https://www.rfc-editor.org/rfc/rfc6455.html#section-4.2.2)
     * @see [RFC 6455 §10.4](https://www.rfc-editor.org/rfc/rfc6455.html#section-10.4)
     */
    construct(Int maxMessageSize = 64ki, String? protocol = Null,
              Duration idleTimeout = Duration:5m) {
        assert:arg idleTimeout >= Second as "Idle timeout too small";

        this.maxMessageSize = maxMessageSize;
        this.protocol       = protocol;
        this.idleTimeout    = idleTimeout;
    }

    // ----- customizable WebSocket actions --------------------------------------------------------

    /**
     * Invoked when the WebSocket connects to a peer.
     */
    void onConnect() {}

    /**
     * Invoked when the WebSocket receives a Message to process. Each implementation of WebSocket
     * must provide an implementation of this method.
     *
     * @param msg  the Message received from the peer
     */
    @Abstract void onMessage(Message msg);

    /**
     * Invoked when an error condition is reported to the WebSocket.
     */
    void onError(UInt16 errorCode) {}

    /**
     * Invoked when the WebSocket connects.
     */
    void onDisconnect(UInt16 code, String reason) {}

    // ----- capabilities intrinsic to a WebSocket service -----------------------------------------

    /**
     * An underlying WebSocket that, if present, this WebSocket delegates operations to.
     */
    protected WebSocket!? underlying.set(WebSocket!? socket) {
        if (socket == Null) {
            assert !underlying?.open;
        } else {
            assert underlying == Null;
        }
        super(socket);
    }

    /**
     * Connect this WebSocket implementation to an underlying WebSocket.
     *
     * @param underlying  the WebSocket to delegate operations to
     */
    void connect(WebSocket! underlying) {
        assert this.underlying == Null;
        this.underlying = underlying;
        onConnect^();
    }

    /**
     * Indicates whether the WebSocket is open or not.
     */
    @RO Boolean open.get() = underlying?.open : False;

    /**
     * How long the WebSocket transport may be idle before it closes.
     */
    public/private Duration idleTimeout;

    /**
     * The maximum message size for this WebSocket.
     */
    Int maxMessageSize {
        Int get() {
            return underlying?.maxMessageSize : super();
        }

        void set(Int maxMessageSize) {
             underlying?.maxMessageSize = maxMessageSize;
             super(maxMessageSize);
        }
    }

    /**
     * The protocol name in use by the WebSocket.
     */
    String? protocol {
        String? get() {
            return underlying?.protocol : super();
        }

        void set(String? protocol) {
             underlying?.protocol = protocol;
             super(protocol);
        }
    }

    /**
     * Receive the next message from the client if one has already arrived. This allows a stream
     * (e.g. chunked upload) to be efficiently processed if a sequence of messages have arrived by
     * the time that the first one gets processed. This method returns immediately with `False` if
     * no next Message has already arrived.
     *
     * Once this method returns `False`, it is expected to continue to return `False` repeatedly,
     * forever. Do not poll this method; both the client and server web API designs support
     * event-driven programming, and the blocking/polling model must never be used.
     *
     * @return `True` iff a Message was already queued for delivery to this WebSocket
     * @return (conditional) the previously queued Message
     */
    conditional Message receive() = underlying?.receive() : False;

    /**
     * Submit a `Message` to be sent to the other end of the WebSocket.
     */
    void send(Message msg) = underlying?.send(msg) : assert;

    /**
     * Instruct the WebSocket to disconnect.
     * TODO param doc
     */
    void disconnect(UInt16 code = NormalClosure, String reason = "") {
        if (open) {
            try {
                underlying?.disconnect(code, reason);
                onDisconnect(code, reason);
            } finally {
                underlying = Null;
            }
        }
    }

    @Override
    void close(Exception? cause = Null) {
        if (open) {
            disconnect(cause == Null ? NormalClosure : InternalError);
        }
    }

    // ----- WebSocket error codes -----------------------------------------------------------------

    static UInt16 NormalClosure    = 1000;
    static UInt16 GoingAway        = 1001;
    static UInt16 ProtocolError    = 1002;
    static UInt16 UnsupportedData  = 1003;
    static UInt16 InvalidPayload   = 1007;
    static UInt16 PolicyViolation  = 1008;
    static UInt16 MessageTooBig    = 1009;
    static UInt16 MissingExtension = 1010;
    static UInt16 InternalError    = 1011;
    static UInt16 ServiceRestart   = 1012;
    static UInt16 TryAgainLater    = 1013;
    static UInt16 BadGateway       = 1014;
}
