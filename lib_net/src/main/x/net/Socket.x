/**
 * Represents a socket.
 */
interface Socket
        extends Closeable
    {
    /**
     * This is the [SocketAddress] that the socket originates from.
     */
    @RO SocketAddress fromAddress;

    /**
     * This is the [SocketAddress] that the socket connects to.
     */
    @RO SocketAddress toAddress;

    /**
     * Obtain the [SocketChannel] for the `Socket`, which provides an advanced non-blocking read and
     * write API.
     */
    @RO SocketChannel channel;

    /**
     * Obtain the stream-like [BinaryInput] to read from the socket.
     */
    @RO BinaryInput in;

    /**
     * Obtain the stream-like [BinaryOutput] to write to the socket.
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
