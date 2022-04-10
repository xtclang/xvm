/**
 * Represents a name service, such as DNS.
 */
interface NameService
    {
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
    }
