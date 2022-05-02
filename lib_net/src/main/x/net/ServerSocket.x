/**
 * Represents a server socket. A server socket is simply a socket that _listens for_ and can
 * _accept_ incoming connections. Each incoming connection, when it is accepted, becomes a [Socket].
 */
interface ServerSocket
        extends Closeable
    {
    /**
     * This is the local [SocketAddress] at which the underlying listening socket is bound; it is an
     * address and port on this machine.
     */
    @RO SocketAddress localAddress;

    /**
     * Await an incoming [Socket] connection.
     *
     * To make the operation non-blocking, use the `@Future` annotation on the result; for example:
     *
     *     @Future Socket socket = serverSocket.accept();
     *     &socket.thenDo(() -> { ... });
     *
     * @return the newly created [Socket]
     *
     * @throws IllegalState  if this socket has been closed
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the ServerSocket is closed before the accept operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a connection is being
     *                       awaited, if a [Timeout] exists
     */
    Socket accept();

    // TODO options (backlog etc.)
    }
