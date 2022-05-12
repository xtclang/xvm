import ecstasy.io.IOClosed;
import ecstasy.io.Channel;

import libnet.IPAddress;
import libnet.Socket;
import libnet.SocketAddress;
import libnet.ServerSocket;

/**
 * Implements a native [Socket].
 */
service RTSocket(SocketAddress localAddress, SocketAddress remoteAddress)
        implements Socket
    {
    /**
     * Constructor from native land.
     *
     * @param name            the name of this network interface
     * @param addressesBytes  the byte array for each address of this network interface
     */
    construct(Byte[] localAddressBytes, UInt16 localPort, Byte[] remoteAddressBytes, UInt16 remotePort)
        {
        construct RTSocket((new IPAddress(localAddressBytes), localPort),
                           (new IPAddress(remoteAddressBytes), remotePort));
        }

    /**
     * State of the socket IO.
     *
     * * None - no IO yet
     * * Sync - access to `in` and/or `out` has occurred
     * * Async - access to `channel` has occurred
     * * Closed - the socket has been closed
     */
    private enum IO {None, Sync, Async, Closed}

    /**
     * The "IO mode" of the socket. Once the socket goes into sync or async mode, it's not supposed
     * switch to the other.
     */
    private IO mode;

    /**
     * TODO
     */
    protected/private Channel rawChannel;


    // ----- Socket methods ------------------------------------------------------------------------

    @Override
    public/private SocketAddress localAddress;

    @Override
    public/private SocketAddress remoteAddress;

    @Override
    @Lazy public/private Channel channel.calc()
        {
        switch (mode)
            {
            case None:
            case Async:
                mode = Async;
                val channel = new SocketChannel(rawChannel);
                return &channel.maskAs(Socket.Channel);

            case Sync:
                throw new IllegalState("The Socket is already in synchronous I/O mode");

            case Closed:
                throw new IOClosed();
            }
        }

    @Override
    @Lazy BinaryInput in.calc()
        {
        switch (mode)
            {
            case None:
            case Sync:
                mode = Sync;
                val stream = new SocketInput();
                return &stream.maskAs(BinaryInput);

            case Async:
                throw new IllegalState("The Socket is already in asynchronous I/O mode");

            case Closed:
                throw new IOClosed();
            }
        }

    @Override
    @Lazy BinaryOutput out.calc()
        {
        switch (mode)
            {
            case None:
            case Sync:
                mode = Sync;
                val stream = new SocketOutput();
                return &stream.maskAs(BinaryOutput);

            case Async:
                throw new IllegalState("The Socket is already in asynchronous I/O mode");

            case Closed:
                throw new IOClosed();
            }
        }

    @Override
    void shutdownInput()
        {TODO("Native");}

    @Override
    void shutdownOutput()
        {TODO("Native");}

    @Override
    void close(Exception? cause = Null)
        {TODO("Native");}


    // ----- SocketChannel class -------------------------------------------------------------------

    /**
     * TODO
     */
    class SocketChannel(Channel rawChannel)
            delegates Channel(rawChannel)
        {
        // TODO
        }

    // ----- SocketInput class ---------------------------------------------------------------------

    /**
     * TODO
     */
    class SocketInput
            implements BinaryInput
        {
        // TODO
        }


    // ----- SocketInput class ---------------------------------------------------------------------

    /**
     * TODO
     */
    class SocketOutput
            implements BinaryOutput
        {
        // TODO
        }


    // ----- internal ------------------------------------------------------------------------------

    // TODO
    }