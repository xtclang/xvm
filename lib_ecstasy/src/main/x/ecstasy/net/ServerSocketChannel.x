/**
 * ServerSocketChannel provides the ability to accept inbound network connections.
 * TODO GG: move to "net" module
 */
interface ServerSocketChannel
        extends Closeable
    {
    /**
     * Obtain a SocketChannel for a new inbound network connection.
     *
     * @return the newly created SocketChannel
     *
     * @throws IOException if an I/O error occurs
     */
    SocketChannel accept();
    }