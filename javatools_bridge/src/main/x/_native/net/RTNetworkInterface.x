import libnet.NetworkInterface;
import libnet.IPAddress;
import libnet.Socket;
import libnet.SocketAddress;
import libnet.ServerSocket;

/**
 * Implements a native [NetworkInterface] (a.k.a. a "NIC").
 */
service RTNetworkInterface(String name, immutable IPAddress[] addresses)
        implements NetworkInterface {
    /**
     * Constructor from native land.
     *
     * @param name            the name of this network interface
     * @param addressesBytes  the byte array for each address of this network interface
     */
    construct(String name, Byte[][] addressesBytes) {
        IPAddress[] addresses = new IPAddress[];
        for (Byte[] addressBytes : addressesBytes) {
            addresses += new IPAddress(addressBytes);
        }
        construct RTNetworkInterface(name, addresses.freeze(True));
    }


    // ----- NetworkInterface methods --------------------------------------------------------------

    @Override
    public/private String name;

    @Override
    @RO Boolean running.get() {TODO("Native");}

    @Override
    public/private immutable IPAddress[] addresses;

    @Override
    conditional Socket connect(SocketAddress remoteAddress, SocketAddress? localAddress=Null) {
        return nativeConnect(remoteAddress[0].bytes, remoteAddress[1],
                localAddress?[0].bytes : [], localAddress?[1] : 0);
    }

    @Override
    conditional ServerSocket listen(SocketAddress localAddress) {
        return nativeListen(localAddress[0].bytes, localAddress[1]);
    }

    @Override
    String toString() {
        return "NetworkInterface";
    }


    // ----- internal ------------------------------------------------------------------------------

    conditional Socket nativeConnect(Byte[] remoteAddressBytes, UInt16 remotePort, Byte[] localAddressBytes, UInt16 localPort) {TODO("Native");}

    conditional ServerSocket nativeListen(Byte[] localAddressBytes, UInt16 localPort) {TODO("Native");}
}