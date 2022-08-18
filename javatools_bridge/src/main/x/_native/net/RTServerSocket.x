import ecstasy.io.IOClosed;
import ecstasy.io.Channel;

import libnet.IPAddress;
import libnet.Socket;
import libnet.SocketAddress;
import libnet.ServerSocket;

/**
 * Implements a native [ServerSocket].
 */
service RTServerSocket(SocketAddress localAddress)
        implements ServerSocket
    {
    /**
     * Constructor from native land.
     *
     * @param name            the name of this network interface
     * @param addressesBytes  the byte array for each address of this network interface
     */
    construct(Byte[] localAddressBytes, UInt16 localPort)
        {
        construct RTServerSocket((new IPAddress(localAddressBytes), localPort));
        }

    // ----- Socket methods ------------------------------------------------------------------------

    @Override
    public/private SocketAddress localAddress;

    @Override
    Socket accept()
        {TODO("Native");}

    @Override
    void close(Exception? cause = Null)
        {TODO("Native");}

    @Override
    String toString()
        {
        return "ServerSocket";
        }


    // ----- internal ------------------------------------------------------------------------------

    // TODO
    }