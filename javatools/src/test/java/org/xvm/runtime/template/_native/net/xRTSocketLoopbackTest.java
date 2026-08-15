package org.xvm.runtime.template._native.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit checks for the helpers {@link xRTSocket#connect} / read / write actually call.
 * Full {@code Network.connect} coverage is the Ecstasy {@code TestTcpClient} module.
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
            try (Socket sock = xRTSocket.openConnectedSocket(
                    new byte[] {127, 0, 0, 1}, port, null, 0)) {
                xRTSocket.writeBytes(sock, ping, 0, ping.length);
                assertArrayEquals(ping, xRTSocket.readBytes(sock, 4));
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
        assertThrows(IOException.class, () ->
                xRTSocket.openConnectedSocket(new byte[] {127, 0, 0, 1}, port, null, 0));
    }

    @Test
    void bindFailureClosesBeforeConnect() throws Exception {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = taken.getLocalPort();
            assertThrows(IOException.class, () ->
                    xRTSocket.openConnectedSocket(
                            new byte[] {127, 0, 0, 1}, 1,
                            new byte[] {127, 0, 0, 1}, port));
        }
    }

    @Test
    void writeRangeRejectsBadOffsetAndCount() {
        int nLength = 3;
        assertNull(xRTSocket.invalidWriteRange(nLength, 0, 0));
        assertNull(xRTSocket.invalidWriteRange(nLength, 0, 3));
        assertNull(xRTSocket.invalidWriteRange(nLength, 2, 1));

        assertTrue(xRTSocket.invalidWriteRange(nLength, -1, 1) != null);
        assertTrue(xRTSocket.invalidWriteRange(nLength, 0, -1) != null);
        assertTrue(xRTSocket.invalidWriteRange(nLength, 0, 4) != null);
        assertTrue(xRTSocket.invalidWriteRange(nLength, 3, 1) != null);
        assertTrue(xRTSocket.invalidWriteRange(nLength, Integer.MAX_VALUE + 1L, 1) != null);
        assertTrue(xRTSocket.invalidWriteRange(nLength, 0, Integer.MAX_VALUE + 1L) != null);
    }
}
