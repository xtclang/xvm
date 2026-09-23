package org.xvm.runtime.template._native.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;

import java.io.IOException;

import java.net.CookieManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.FileStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OwnedResource;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class HttpResourceOwnershipTest {
    @Test
    void closesIdleClientsBeforeCompletingOwnerShutdown() throws Exception {
        var server = HttpServer.create(loopback(), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (var runtime = new Runtime()) {
            for (int i = 0; i < 3; i++) {
                var owner = owner(runtime);
                var resource = owner.acquireResource(
                        () -> new HttpClientPool(new CookieManager(), SSLContext.getDefault()),
                        OwnedResource::closeOnWorker);
                var pool = resource.get();
                HttpClient client = pool.selectClient(0);
                assertEquals(200, client.send(request(server), HttpResponse.BodyHandlers.discarding()).statusCode());
                owner.terminateServices().get(10, TimeUnit.SECONDS);
                assertTrue(client.isTerminated());
                assertThrows(IllegalStateException.class, () -> pool.selectClient(0));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelsActiveClientSendAndWaitsForClientTermination() throws Exception {
        var entered = new CountDownLatch(1);
        var server = HttpServer.create(loopback(), 0);
        server.createContext("/", exchange -> entered.countDown());
        server.start();
        try (var runtime = new Runtime()) {
            var owner = owner(runtime);
            var resource = owner.acquireResource(
                    () -> new HttpClientPool(new CookieManager(), SSLContext.getDefault()),
                    OwnedResource::closeOnWorker);
            HttpClient client = resource.get().selectClient(0);
            var response = client.sendAsync(request(server), HttpResponse.BodyHandlers.discarding());
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            owner.terminateServices().get(10, TimeUnit.SECONDS);
            assertTrue(client.isTerminated());
            assertThrows(ExecutionException.class, () -> response.get(10, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void closesFirstListenerWhenSecondBindFails() throws Exception {
        try (var runtime = new Runtime(); var occupied = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var owner = owner(runtime);
            var resource = owner.acquireResource(HttpServerResources::new, OwnedResource::closeOnWorker);
            var server = resource.get();
            server.bindHttp(loopback());
            var address = server.http().getAddress();
            assertThrows(IOException.class, () -> server.bindHttps(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), occupied.getLocalPort())));
            resource.closeAsync().get(10, TimeUnit.SECONDS);
            owner.terminateServices().get(10, TimeUnit.SECONDS);
            assertBindable(address);
        }
    }

    @Test
    void closesBothUnstartedListenersAfterTlsSetupFailure() throws Exception {
        try (var runtime = new Runtime()) {
            var owner = owner(runtime);
            var resource = owner.acquireResource(HttpServerResources::new, OwnedResource::closeOnWorker);
            var server = resource.get();
            server.bindHttp(loopback());
            server.bindHttps(loopback());
            var httpAddress = server.http().getAddress();
            var httpsAddress = server.https().getAddress();
            assertThrows(NullPointerException.class, () -> server.configureHttps(null));
            owner.terminateServices().get(10, TimeUnit.SECONDS);
            assertBindable(httpAddress);
            assertBindable(httpsAddress);
        }
    }

    @Test
    void closesUnfinishedExchangesListenersAndExecutorOnce() throws Exception {
        try (var runtime = new Runtime(); var client = HttpClient.newHttpClient()) {
            var owner = owner(runtime);
            var resource = owner.acquireResource(HttpServerResources::new, OwnedResource::closeOnWorker);
            var server = resource.get();
            var arrived = new CompletableFuture<Void>();
            var released = new AtomicInteger();
            server.bindHttp(loopback());
            server.bindHttps(loopback());
            server.configureHttps(new HttpsConfigurator(SSLContext.getDefault()));
            server.start(exchange -> {
                assertTrue(server.track(exchange));
                arrived.complete(null);
            }, released::incrementAndGet);
            var http = server.http();
            var https = server.https();
            var executor = (ExecutorService) http.getExecutor();
            var response = client.sendAsync(request(http), HttpResponse.BodyHandlers.discarding());
            arrived.get(10, TimeUnit.SECONDS);
            assertEquals(1, server.exchangeCount());
            resource.closeAsync().get(10, TimeUnit.SECONDS);
            owner.terminateServices().get(10, TimeUnit.SECONDS);
            assertEquals(0, server.exchangeCount());
            assertEquals(1, released.get());
            assertTrue(executor.isTerminated());
            assertThrows(ExecutionException.class, () -> response.get(10, TimeUnit.SECONDS));
            assertBindable(http.getAddress());
            assertBindable(https.getAddress());
        }
    }

    private static HttpRequest request(HttpServer server) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + server.getAddress().getPort() + "/")).build();
    }

    private static InetSocketAddress loopback() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    private static void assertBindable(InetSocketAddress address) throws Exception {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(address);
            assertFalse(socket.isClosed());
        }
    }

    private static Container owner(Runtime runtime) {
        return new Container(runtime, null, new FileStructure("test").getModuleId()) {
            @Override
            public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
