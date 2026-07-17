/**
 * Represents a name service, such as DNS.
 */
interface NameService {
    /**
     * Resolve the specified name to one or more `IPAddress`. This process is often called a
     * "name resolve" or a "DNS lookup".
     *
     * @param name  the name to look up
     *
     * @return True if the name could be resolved
     * @return (conditional) an array of one or more addresses that the name resolved to
     */
    conditional IPAddress[] resolve(String name);

    /**
     * Determine the name that the specified `IPAddress` corresponds to. This process is often
     * called a "reverse lookup".
     *
     * @param address  the IPAddress to perform a reverse-lookup on
     *
     * @return True if the address had a record
     * @return (conditional) the name that the address is associated with
     */
    conditional String reverseLookup(IPAddress address);

    /**
     * Get all DNS resource records for the specified owner name.
     *
     * @param owner  the absolute DNS name whose records are requested; for example,
     *               "acme.com" or "www.acme.com"
     *
     * @return a list of records (empty if none found)
     */
    Record[] records(String owner);

    /**
     * Get the first RDATA (record data) value for the specified record name and record type within
     * the specified domain.
     *
     * @param zone  the zone apex (e.g. "acme.com")
     * @param name  the name within the zone (e.g. "www" or "@")
     * @param type  the record type (e.g. "CNAME")
     *
     * @return True if the zone has at least one record with the specified name and type
     * @return (conditional) the record data
     */
    conditional String getData(String zone, String name, String type) {
        Record[] records = records(name == "@" ? zone : $"{name}.{zone}");

        if (Record record := records.any(r -> r.type == type)) {
            return True, record.data;
        }
        return False;
    }

    /**
     * The DNS resource record
     *
     * @see https://datatracker.ietf.org/doc/html/rfc1035
     */
    static const Record(String name, Duration TTL, String netClass, String type, String data);
}