/**
 * Represents a socket.
 */
interface Socket
        extends Closeable
    {
    /**
     * This is the remote [SocketAddress] to which the underlying socket is connected; it is an
     * address and port somewhere on the network (even including this machine).
     */
    @RO SocketAddress remoteAddress;

    /**
     * This is the local [SocketAddress] at which the underlying socket is connected; it is an
     * address and port on this machine.
     */
    @RO SocketAddress localAddress;

    /**
     * A `Socket Channel` represents an operating system socket, with the ability to read and write
     * in the manner of a [Channel](ecstasy.io.Channel). The `Socket Channel` provides a
     * non-blocking I/O capability.
     */
    interface Channel
            extends ecstasy.io.Channel
        {
        }

    /**
     * Obtain the `Channel` for the `Socket`, which provides an advanced non-blocking read and
     * write API. The caller to the socket may use **either** the `channel`, or the [in] and [out]
     * streams, but not both; an attempt to use both for I/O will result in an exception.
     */
    @RO Channel channel;

    /**
     * Obtain the blocking, stream-like [BinaryInput] to read from the socket.
     */
    @RO BinaryInput in;

    /**
     * Obtain the blocking, stream-like [BinaryOutput] to write to the socket.
     */
    @RO BinaryOutput out;

    /**
     * Indicates that this socket will not be read from. All received socket data that has not
     * already been read and all subsequently received data will be silently discarded.
     */
    void shutdownInput();

    /**
     * Indicates that this socket will not be written to. Attempts to send additional data will
     * result in an exception, but (in the absence of network failure) previously sent data will
     * still be sent.
     */
    void shutdownOutput();

    // TODO options: (and/or some of these could be on the channel)
    // SO_TIMEOUT, SO_LINGER, TCP_NODELAY, SO_SNDBUF, SO_RCVBUF, SO_OOBINLINE,
    // SO_REUSEADDR/SO_REUSEPORT, SO_KEEPALIVE, SO_RCVTIMEO/SO_SNDTIMEO, IPTOS_*, etc.
    }
