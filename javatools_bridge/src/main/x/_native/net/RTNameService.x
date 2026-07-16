import libnet.NameService;
import libnet.Network;
import libnet.IPAddress;

/**
 * Implements a native [NameService].
 */
@Concurrent
service RTNameService(Network network)
        implements NameService {

    @Override
    conditional IPAddress[] resolve(String name) {
        if (Byte[][] addressesBytes := nativeResolve(name)) {
            return True, addressesBytes.map(bytes -> new IPAddress(bytes)).toArray(Constant);
        }
        return False;
    }

    @Override
    conditional String reverseLookup(IPAddress address) {
        return nativeLookup(address.bytes);
    }

    @Override
    Record[] records(String name) {
        String[] fields  = nativeRecords(name);
        Record[] records = new Record[];

        for (Int index : 0 ..< fields.size/5) {
            Int offset = index * 5;
            records += new Record(fields[offset],
                    Duration.ofSeconds(new Int64(fields[offset+1])),
                    fields[offset+2], fields[offset+3], fields[offset+4]);
        }

        return records.toArray(Constant, inPlace=True);
    }

    @Override
    conditional String getData(String domain, String name, String type) {
        String recordName = name == "@" ? domain : $"{name}.{domain}";

        for (Record record : records(recordName)) {
            if (record.type == type) {
                return True, record.data;
            }
        }

        return False;
    }

    @Override
    String toString() {
        return "NameService";
    }


    // ----- internal ------------------------------------------------------------------------------

    conditional Byte[][] nativeResolve(String name) {TODO("Native");}

    conditional String nativeLookup(Byte[] addressBytes) {TODO("Native");}

    String[] nativeRecords(String name) {TODO("Native");}
}