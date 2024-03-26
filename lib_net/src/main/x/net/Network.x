import crypto.Algorithms;

/**
 * Represents the network.
 *
 * There are two common injections supported in Ecstasy for accessing a `Network`. One is a "plain
 * text" (unencrypted and insecure) network, while the second provides Transport Layer Security
 * (TLS):
 *
 *     @Inject Network insecureNetwork;
 *     @Inject Network secureNetwork;
 *
 * The names are intended to clearly specify the risk.
 */
interface Network {
    /**
     * The [NameService] for the network. This allows names to be resolved to addresses, and vice
     * versa.
     */
    @RO NameService nameService;

    /**
     * The [NetworkInterface] objects that provide access to the network.
     */
    @RO NetworkInterface[] interfaces;

//    /**
//     * Create a Transport Layer Security (TLS) secured network from this network, by providing the
//     * necessary secure algorithms and keys.
//     *
//     * @param secureAlgorithms  the [Algorithms] available negotiate secure connections
//     * @param secureKeys        the source for keys required by the secure algorithms
//     */
//    Network secure(Algorithms secureAlgorithms, KeyStore? secureKeys);

    /**
     * Determine whether the network provides Transport Layer Security (TLS), also referred to by
     * the older term "Secure Sockets Layer" (SSL).
     *
     * @return True iff the network is secured by Transport Layer Security (TLS)
     * @return (conditional) the [Algorithms] that are available to the TLS network in order to
     *         negotiate a secure connection
     */
    conditional Algorithms isSecure();

    /**
     * Obtain the default [NetworkInterface].
     *
     * REVIEW (not available in native land)
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
    conditional NetworkInterface interfaceByName(String name) {
        for (NetworkInterface nic : interfaces) {
            if (nic.name == name) {
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
    conditional NetworkInterface interfaceByAddress(IPAddress address) {
        for (NetworkInterface nic : interfaces) {
            if (nic.addresses.contains(address)) {
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
     *     // a conditional tuple is returned
     *     network.connect^(addr).passTo(tuple -> {
     *         if (Socket socket := tuple) {
     *             ...
     *         }
     *     });
     *
     *     @Future @Conditional Tuple<Boolean, Socket> result = network.connect(addr);
     *     &result.passTo(t -> {
     *         if (Socket socket := t) {
     *             ...
     *         }
     *     });
     *
     * @param remoteAddress  the [SocketAddress] to connect to
     * @param localAddress   (optional) the local [SocketAddress] to connect from; `Null` implies "any"
     *
     * @return `True` iff the specified address(es) could be connected
     * @return (conditional) the Socket connecting the specified address(es)
     */
    conditional Socket connect(SocketAddress remoteAddress, SocketAddress? localAddress=Null);

    /**
     * Create a [ServerSocket] that can accept incoming socket connections.
     *
     *     @Future @Conditional Tuple<Boolean, ServerSocket> result = network.listen(addr);
     *     &result.passTo(t -> {
     *         if (Socket socket := t) {
     *             ...
     *         }
     *     });
     *
     * @param localAddress  the [SocketAddress] to listen for connections on
     *
     * @return `True` iff the specified address could be listened on
     * @return (conditional) the ServerSocket listening on the specified address
     */
    conditional ServerSocket listen(SocketAddress localAddress);
}
