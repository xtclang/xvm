/**
 * Represents a server socket. A server socket is simply a socket that can _accept_ incoming
 * connections. Each incoming connection, when it is accepted, becomes a [Socket].
 */
interface ServerSocket
        extends Closeable
    {
    /**
     * This is the [SocketAddress] that the socket originates from.
     */
    @RO SocketAddress fromAddress;

    /**
     * Obtain a Socket for a new inbound network connection.
     *
     * @return the newly created Socket
     *
     * @throws IOException if an I/O error occurs
     * TODO detail what exceptions are likely to occur and why
     */
    Socket accept();

    /**
     * Obtain a SocketChannel for a new inbound network connection.
     *
     * @return True iff there is an inbound socket attempting to connect
     * @return (conditional) the newly created SocketChannel
     *
     * @throws IOException if an I/O error occurs
     * TODO detail what exceptions are likely to occur and why
     */
    conditional SocketChannel accept();
    }
