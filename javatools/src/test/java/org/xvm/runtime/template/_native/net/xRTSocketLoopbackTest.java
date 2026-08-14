package org.xvm.runtime.template._native.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Loopback TCP using the same {@link java.net.Socket} setup as {@link xRTSocket#connect}.
 * The Ecstasy {@code TestTcpClient} module exercises the native through {@code Network.connect}.
 */
class xRTSocketLoopbackTest {

    @Test
    void echoRoundTrip() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            CompletableFuture<byte[]> echoed = CompletableFuture.supplyAsync(() -> {
                try (Socket peer = server.accept()) {
                    byte[] buf = peer.getInputStream().readNBytes(4);
                    peer.getOutputStream().write(buf);
                    peer.getOutputStream().flush();
                    return buf;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            byte[] ping = {'p', 'i', 'n', 'g'};
            try (Socket sock = openLikeNative(new byte[] {127, 0, 0, 1}, port)) {
                sock.getOutputStream().write(ping);
                sock.getOutputStream().flush();
                byte[] got = sock.getInputStream().readNBytes(4);
                assertArrayEquals(ping, got);
            }
            assertArrayEquals(ping, echoed.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void connectToClosedPortFails() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = probe.getLocalPort();
        }
        assertThrows(IOException.class,
                () -> openLikeNative(new byte[] {127, 0, 0, 1}, port).close());
    }

    /**
     * Same bind/connect flags as {@link xRTSocket#connect}; IOException is what native maps to False.
     */
    private static Socket openLikeNative(byte[] remoteIp, int remotePort) throws IOException {
        Socket sock = new Socket();
        sock.setTcpNoDelay(true);
        sock.setKeepAlive(true);
        sock.connect(new InetSocketAddress(InetAddress.getByAddress(remoteIp), remotePort),
                xRTSocket.CONNECT_TIMEOUT_MS);
        return sock;
    }
}
