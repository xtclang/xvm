import libcrypto.Algorithms;

import libnet.NameService;
import libnet.Network;
import libnet.NetworkInterface;
import libnet.IPAddress;
import libnet.Socket;
import libnet.SocketAddress;
import libnet.ServerSocket;

/**
 * Implements a native [Network].
 */
service RTNetwork(Boolean secure)
        implements Network
    {
    @Override
    @RO NameService nameService.get()
        {TODO("Native");}

    @Override
    @RO NetworkInterface[] interfaces.get()
        {TODO("Native");}

    @Override
    conditional Algorithms isSecure()
        {TODO("Native");}

    @Override
    conditional NetworkInterface defaultInterface()
        {TODO("Native");}

    @Override
    conditional NetworkInterface interfaceByName(String name)
        {TODO("Native");}

    @Override
    conditional NetworkInterface interfaceByAddress(IPAddress address)
        {
        return nativeNicByAddress(address.bytes);
        }

    @Override
    conditional Socket connect(SocketAddress remoteAddress, SocketAddress? localAddress=Null)
        {
        return nativeConnect(remoteAddress[0].bytes, remoteAddress[1],
                localAddress?[0].bytes : [], localAddress?[1] : 0);
        }

    @Override
    conditional ServerSocket listen(SocketAddress localAddress)
        {
        return nativeListen(localAddress[0].bytes, localAddress[1]);
        }


    // ----- internal ------------------------------------------------------------------------------

    conditional NetworkInterface nativeNicByAddress(Byte[] addressBytes)
        {TODO("Native");}

    conditional Socket nativeConnect(Byte[] remoteAddressBytes, UInt16 remotePort, Byte[] localAddressBytes, UInt16 localPort)
        {TODO("Native");}

    conditional ServerSocket nativeListen(Byte[] localAddressBytes, UInt16 localPort)
        {TODO("Native");}
    }
