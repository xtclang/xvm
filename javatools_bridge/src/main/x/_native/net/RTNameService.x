import libnet.NameService;
import libnet.Network;
import libnet.IPAddress;

/**
 * Implements a native [NameService].
 */
@Concurrent
service RTNameService(Network network)
        implements NameService
    {
    @Override
    conditional IPAddress[] resolve(String name)
        {
        if (Byte[][] addressesBytes := nativeResolve(name))
            {
            return True, addressesBytes.map(bytes -> new IPAddress(bytes)).toArray(Constant);
            }
        return False;
        }

    @Override
    conditional String reverseLookup(IPAddress address)
        {
        return nativeLookup(address.bytes);
        }


    // ----- internal ------------------------------------------------------------------------------

    conditional Byte[][] nativeResolve(String name)
        {TODO("Native");}

    conditional String nativeLookup(Byte[] addressBytes)
        {TODO("Native");}
    }
