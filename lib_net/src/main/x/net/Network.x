import crypto.Algorithms;

/**
 * Represents the network.
 *
 * TODO document injection names
 *
 *     @Inject Network network;
 *     @Inject Network secure;
 */
interface Network
    {
    /**
     * The [NameService] for the network. This allows names to be resolved to addresses, and vice
     * versa.
     */
    @RO NameService nameService;

    /**
     * The [NetworkInterface] objects that provide access to the network.
     */
    @RO NetworkInterface[] interfaces;

    /**
     * REVIEW
     */
    Network secure(Algorithms secureAlgorithms);

    /**
     * REVIEW
     */
    conditional Algorithms isSecure();

    /**
     * Obtain the default [NetworkInterface].
     *
     * @return `True` iff there is a default `NetworkInterface`
     * @return (conditional) the default `NetworkInterface`
     */
    conditional NetworkInterface defaultInterface();

    /**
     * Obtain the [NetworkInterface] that has the specified name.
     *
     * @param name  the name of the `NetworkInterface`
     *
     * @return `True` iff there is a `NetworkInterface` that has the specified name
     * @return (conditional) the `NetworkInterface`  with the specified name
     */
    conditional NetworkInterface interfaceByName(String name)
        {
        for (NetworkInterface nic : interfaces)
            {
            if (nic.name == name)
                {
                return True, nic;
                }
            }
        return False;
        }

    /**
     * Obtain a [NetworkInterface] that is bound to the specified `IPAddress`. It is possible that
     * more than one interface is bound to the specified `IPAddress`, in which case one of those
     * interfaces will be returned, but the manner in which that one interface is selected is
     * unspecified.
     *
     * @param address  the `IPAddress` potentially bound to at least one `NetworkInterface`
     *
     * @return `True` iff there is at least one `NetworkInterface` that has the specified
     *         `IPAddress` bound to it
     * @return (conditional) a `NetworkInterface` with the specified address
     */
    conditional NetworkInterface interfaceByAddress(IPAddress address)
        {
        for (NetworkInterface nic : interfaces)
            {
            if (nic.addresses.contains(address))
                {
                return True, nic;
                }
            }
        return False;
        }

    /**
     * Create a stream-based socket that connects to the specified address. The determination of
     * what [NetworkInterface] to use is automatic, and in the case when more than one network
     * interface could be used, the selection criteria used to choose one is undefined.
     *
     * @param to    the [SocketAddress] to connect to
     * @param from  (optional) the local [SocketAddress] to connect from; `Null` implies "any"
     */
    conditional Socket connect(SocketAddress to, SocketAddress? from=Null);

    /**
     * Create a [ServerSocket] that can accept incoming socket connections.
     *
     * @param address  the [SocketAddress] to listen for connections on
     */
    conditional ServerSocket listen(SocketAddress address);
    }
