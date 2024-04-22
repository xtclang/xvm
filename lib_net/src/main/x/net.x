/**
 * The Ecstasy standard module for basic networking support.
 */
module net.xtclang.org {
    package crypto import crypto.xtclang.org;

    /**
     * A host address is specified as a host name or IP address, either in the form of a `String` or
     * an [IPAddress].
     */
    typedef String|IPAddress as Host;

    /**
     * A combination of an address and a port number, in which the address is specified as a host
     * name or IP address, either in the form of a `String` or an [IPAddress].
     */
    typedef Tuple<Host, UInt16> as HostPort;

    /**
     * A combination of an address and a port number, in which the address is specified as an
     * [IPAddress].
     */
    typedef Tuple<IPAddress, UInt16> as SocketAddress;
}
