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

        if (args.size > 0 && args[0] == "same-socket") {
            assert args.size == 3 as "usage: TestTcpClient same-socket <host> <port>";
            testSameSocket(insecureNetwork, args[1], new UInt16(args[2]));
            return;
        }

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

    /**
     * Verify that an asynchronous read does not prevent a subsequent write on the same socket.
     *
     * - Client sends "ping".
     * - Client starts an asynchronous read for "pong".
     * - Server receives "ping" but refuses to send pong until it receives next.
     * - While the read remains pending, the client writes next.
     * - Server receives "next" and sends "pong".
     * - The pending read completes.
     *
     * Start the peer in another terminal:
     *
     *     python3 - <<'PY'
     *     import socket
     *     server = socket.socket()
     *     server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
     *     server.bind(("127.0.0.1", 9998))
     *     server.listen(1)
     *     connection, _ = server.accept()
     *     assert connection.recv(4, socket.MSG_WAITALL) == b"ping"
     *     assert connection.recv(4, socket.MSG_WAITALL) == b"next"
     *     connection.sendall(b"pong")
     *     connection.close()
     *     server.close()
     *     PY
     *
     * Then run from the repository root:
     *
     *     xec -L manualTests/build/xtc/main/lib TestTcpClient same-socket 127.0.0.1 9998
     */
    void testSameSocket(Network network, String host, UInt16 port) {
        IPAddress ip = new IPAddress(host);
        if (Socket sock := network.connect((ip, port))) {
            try {
                using (new Timeout(Duration:5s)) {
                    BinaryInput  input  = sock.in;
                    BinaryOutput output = sock.out;

                    output.writeBytes("ping".utf8());

                    @Future Byte[] response = input.readBytes^(4);
                    assert !&response.assigned;

                    @Inject Clock clock;
                    Time start = clock.now;
                    output.writeBytes("next".utf8());
                    Duration elapsed = clock.now - start;
                    assert elapsed < Duration:1s as $"following write took {elapsed}";

                    Byte[] received = &response.get();
                    assert received.unpackUtf8() == "pong" as
                            $"expected pong; received {received.unpackUtf8()}";
                    console.print($"same-socket read/write completed after {elapsed}");
                }
            } catch (TimedOut e) {
                assert False as "full-duplex exchange timed out";
            } finally {
                sock.close();
            }
        } else {
            assert False as $"connect failed: {host}:{port}";
        }
    }
}
