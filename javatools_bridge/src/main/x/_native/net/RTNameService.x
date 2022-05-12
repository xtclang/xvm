import libnet.NameService;
import libnet.IPAddress;

/**
 * Implements a native [NameService].
 */
@Concurrent
service RTNameService
        implements NameService
    {
    @Override
    conditional IPAddress[] resolve(String name)
        {TODO("Native");}

    @Override
    conditional String reverseLookup(IPAddress address)
        {
        return nativeLookup(address.bytes);
        }


    // ----- internal ------------------------------------------------------------------------------

    conditional String nativeLookup(Byte[] addressBytes)
        {TODO("Native");}
    }
