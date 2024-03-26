/**
 * Represents a network interface, sometimes referred to as a "NIC" (Network Interface Card). Each
 * entry displayed by the UNIX "ifconfig" command is a network interface.
 */
interface NetworkInterface {
    /**
     * The name of the NetworkInterface, as it is know within the [Network]. Examples might be "lo0"
     * and "en0".
     */
    @RO String name;

    /**
     * True if the NetworkInterface is running. In Linux/UNIX, this indicates that the interface is
     * both `UP` and `RUNNING`.
     */
    @RO Boolean running;

    /**
     * An array of [IPAddress] that are assigned to this NetworkInterface. The array may be empty.
     */
    @RO immutable IPAddress[] addresses;

    /**
     * The Maximum Transmission Unit (MTU) is the number of bytes that the interface is capable of
     * fitting into a packet. This number includes various headers and checksums, so the amount of
     * data that is transmittable in a packet should be assumed to be less than the MTU.
     */
    // REVIEW defer: @RO Int MTU;

    /**
     * The MAC or other hardware address, if one is available. If the MAC address is not available,
     * then the byte array will be empty.
     */
    // REVIEW defer: @RO immutable Byte[] MAC;

    /**
     * True iff the `NetworkInterface` is a "loop back" interface.
     */
    // REVIEW defer: @RO Boolean loopback;

    /**
     * True if the `NetworkInterface` is a Point-to-Point Protocol (PPP) interface.
     */
    // REVIEW defer: @RO Boolean pointToPoint;

    /**
     * Determine if this `NetworkInterface` is virtualized, and if so, obtain its virtualized
     * interfaces.
     */
    // REVIEW defer: conditional NetworkInterface[] virtualized();
    // REVIEW is there any reason to allow the other direction? i.e. virtualized interface to real?

    /**
     * Create a stream-based socket that connects to the specified address.
     *
     *     @Future @Conditional Tuple<Boolean, Socket> result = iface.connect(addr);
     *     &result.passTo(t -> {
     *         if (Socket socket := t) {
     *             ...
     *         }
     *     });
     *
     * @param remoteAddress  the [SocketAddress] to connect to
     * @param localAddress   (optional) the local [SocketAddress] to connect from; `Null` implies
     *                       "any"
     *
     * @return `True` iff the specified address(es) could be connected
     * @return (conditional) the Socket connecting the specified address(es)
     */
    conditional Socket connect(SocketAddress remoteAddress, SocketAddress? localAddress=Null);

    /**
     * Create a [ServerSocket] that can accept incoming socket connections.
     *
     *     @Future @Conditional Tuple<Boolean, ServerSocket> result = iface.listen(addr);
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
