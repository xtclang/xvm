/**
 * Manual check that {@code Network.connect} can open a TCP client and read/write bytes.
 *
 * Listen/accept is not implemented, so start a tiny echo server first:
 *
 *     python3 -c 'import socket;s=socket.socket();s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(("127.0.0.1",9999));s.listen(1);c,_=s.accept();c.sendall(c.recv(64));c.close();s.close()'
 *
 * From the xvm repo root, select this checkout's installed XDK once:
 *
 *     export XDK_HOME="$PWD/xdk/build/install/xdk"
 *
 * Then run the test directly:
 *
 *     xec -L manualTests/build/xtc/main/lib TestTcpClient 127.0.0.1 9999
 */
module TestTcpClient {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.IPAddress;
    import net.Network;
    import net.Socket;

    void run(String[] args = ["127.0.0.1", "9999"]) {
        @Inject Network insecureNetwork;

        String host = args.size > 0 ? args[0] : "127.0.0.1";
        UInt16 port = new UInt16(args.size > 1 ? args[1] : "9999");

        testPing(insecureNetwork, host, port);
    }

    void testPing(Network network, String host, UInt16 port) {
        IPAddress ip = new IPAddress(host);
        if (Socket sock := network.connect((ip, port))) {
            try {
                Byte[] ping = "ping".utf8();
                sock.out.writeBytes(ping);
                Byte[] got = sock.in.readBytes(ping.size);
                assert got.unpackUtf8() == "ping";
                console.print($"ok {sock.localAddress} -> {sock.remoteAddress}");
            } finally {
                sock.close();
            }
        } else {
            console.print($"connect failed: {host}:{port}");
            assert False as "Network.connect returned False (is an echo server listening?)";
        }
    }
}
