package org.xvm.xdk;

import com.sun.net.httpserver.HttpServer;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.net.http.HttpClient;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport;
import org.xvm.api.EmbeddingSupport.Control;
import org.xvm.api.InterpreterConnector;
import org.xvm.api.RunRequest;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.Return_0;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OwnedResource;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.net.xRTSocket;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native-handle assertions while the embedding host remains alive. Network access is supplied by
 * a test-only extension of the real runner's resource provider, not by changing its default policy.
 */
@Timeout(120)
class EmbeddingResourceOwnershipTest {
    @Test
    void parentReleaseClosesAnIdleNestedOwnersChannel(@TempDir Path root) throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            var module = compile(session, source("NestedResources.x"));
            for (int iteration = 0; iteration < 2; iteration++) {
                try (var execution = start(session, module, root, List.of())) {
                    execution.control.join();
                    assertFalse(execution.errors.hasSeriousErrors(), execution.output::toString);
                    var snapshot = snapshot(session);
                    assertEquals(1, snapshot.channels.size());
                    assertTrue(snapshot.channels.getFirst().isOpen());
                    assertEquals(1, snapshot.owners.size());
                    var child = snapshot.owners.getFirst();
                    var parent = child.f_parent;
                    assertEquals("NestedResources", child.getModule().getName());
                    assertEquals("NestedResources", parent.getModule().getName());
                    assertTrue(child.whenIdle().isDone());
                    // Inspect the strong ownership set; ordinary discovery and forced GC cannot
                    // establish whether an abandoned child has a deterministic lifetime owner.
                    assertTrue(retainedContainers(session).contains(child));
                    execution.control.close();
                    snapshot.assertClosed();
                    assertFalse(retainedContainers(session).contains(child));
                    assertFalse(retainedContainers(session).contains(parent));
                }
                assertHealthy(session);
            }
        }
    }

    @Test
    void undeliveredSocketsCloseBeforeTheirApplicationOwner() throws Exception {
        try (var session = networkSession();
             var listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10_000);
            var module = compile(session, source("NativeResources.x"));
            try (var execution = start(session, module, null, List.of("file", "cancel"))) {
                var owner = snapshot(session).owners.getFirst();
                var original = xRTSocket.INSTANCE;
                assertNotNull(original);
                try {
                    for (String outcome : List.of("ignored", "construct", "assign", "asyncAssign")) {
                        xRTSocket.INSTANCE = outcome.equals("construct")
                                ? new xRTSocket(nativeContainer(session), original.getStructure(), false) {
                                    @Override
                                    protected int constructSocket(Frame frame, OwnedResource<Socket> resource,
                                            byte[] local, int localPort, byte[] remote, int remotePort, int[] returns) {
                                        return failAsynchronously(frame);
                                    }
                                } : original;
                        var operation = new NativeFunctionHandle((frame, args, result) -> {
                            if (outcome.equals("assign") || outcome.equals("asyncAssign")) {
                                return frame.call(new FailingSocketAssignment(frame, listener.getLocalPort(),
                                        outcome.equals("asyncAssign")));
                            }
                            return xRTSocket.connect(frame, new byte[] {127, 0, 0, 1}, listener.getLocalPort(),
                                    null, 0, outcome.equals("ignored")
                                            ? new int[] {Op.A_IGNORE, Op.A_IGNORE}
                                            : new int[] {Op.A_STACK, Op.A_STACK});
                        });
                        var result = owner.getServiceContext().postRequest(null, operation, new ObjectHandle[0], 0);
                        try (Socket peer = listener.accept()) {
                            peer.setSoTimeout(10_000);
                            if (outcome.equals("ignored")) {
                                result.get(10, TimeUnit.SECONDS);
                            } else {
                                var failure = assertThrows(ExecutionException.class,
                                        () -> result.get(10, TimeUnit.SECONDS), outcome);
                                assertTrue(failure.getCause().toString().contains("expected socket"),
                                        failure::toString);
                            }
                            assertEquals(-1, peer.getInputStream().read(), outcome);
                            assertTrue(execution.control.running(), "Owner shutdown must not cause socket disposal");
                            assertTrue(snapshot(session).sockets.isEmpty(), outcome);
                        }
                    }
                } finally {
                    xRTSocket.INSTANCE = original;
                }
            }
            assertHealthy(session);
        }
    }

    /**
     * Exercise the real interpreter's asynchronous exception/continuation machinery, without
     * changing the production socket constructor to contain a test-only failure switch.
     */
    private static int failAsynchronously(Frame frame) {
        Op fail = new Op() {
            @Override
            public int process(Frame caller, int pc) {
                return caller.raiseException(xException.ioException(caller, "expected socket handoff failure"));
            }

            @Override
            public String toString() {
                return "FailSocketHandoff";
            }
        };
        return frame.call(frame.createNativeFrame(new Op[] {fail}, new ObjectHandle[0], Op.A_IGNORE, null));
    }

    private static class FailingSocketAssignment extends Frame {
        FailingSocketAssignment(Frame caller, int port, boolean asynchronous) {
            super(caller, new Op[] {new Op() {
                @Override
                public int process(Frame frame, int pc) {
                    return xRTSocket.connect(frame, new byte[] {127, 0, 0, 1}, port,
                            null, 0, new int[] {0, 1});
                }

                @Override
                public String toString() {
                    return "ConnectWithFailingAssignment";
                }
            }, Return_0.INSTANCE}, new ObjectHandle[2], Op.A_IGNORE, null);
            this.asynchronous = asynchronous;
        }

        @Override
        public int assignValues(int[] returns, ObjectHandle... values) {
            return asynchronous ? failAsynchronously(this)
                    : raiseException(xException.ioException(this, "expected socket assignment failure"));
        }

        private final boolean asynchronous;
    }

    @Test
    void timedOutControlFinishesHostCleanupWhenItsReleaseCompletes() throws Exception {
        var cleanup = new CompletableFuture<Void>();
        try (var session = EmbeddingSupport.create(repository())) {
            var module = compile(session, source("NativeResources.x"));
            try (var execution = start(session, module, null, List.of("file", "cancel"))) {
                var nativeOwner = nativeContainer(session);
                var owner = snapshot(session).owners.getFirst();
                var delegate = field(execution.control.getClass(), execution.control, "delegate");
                var temporary = (Path) field(delegate.getClass(), delegate, "temporary");
                var consoleId = (Long) field(delegate.getClass(), delegate, "consoleId");
                var names = (Map<?, ?>) field(NativeContainer.class, nativeOwner, "f_mapResourceNames");
                owner.acquireResource(Object::new, _ -> cleanup);
                try {
                    assertThrows(IllegalStateException.class, () -> execution.control.close(Duration.ZERO));
                    assertTrue(Files.isRegularFile(temporary.resolve("owned.dat")));
                    assertTrue(names.containsKey("console_" + consoleId));
                } finally {
                    cleanup.complete(null);
                }
                // Await the already-started release, without invoking close again to trigger cleanup.
                var release = (CompletableFuture<?>) field(delegate.getClass(), delegate, "releaseAttempt");
                release.get(10, TimeUnit.SECONDS);
                assertFalse(Files.exists(temporary));
                assertFalse(names.containsKey("console_" + consoleId));
            }
        } finally {
            cleanup.complete(null);
        }
    }

    @Test
    void runtimeCloseCompletesHostCleanupWhenTheRunnerCannotAcknowledgeRelease() throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            var module = compile(session, source("NativeResources.x"));
            var execution = start(session, module, null, List.of("file", "cancel"));
            var nativeOwner = nativeContainer(session);
            var delegate = field(execution.control.getClass(), execution.control, "delegate");
            var temporary = (Path) field(delegate.getClass(), delegate, "temporary");
            var consoleId = (Long) field(delegate.getClass(), delegate, "consoleId");
            var names = (Map<?, ?>) field(NativeContainer.class, nativeOwner, "f_mapResourceNames");
            ((InterpreterConnector) session.ensureConnector()).close();
            assertTrue(Files.exists(temporary));
            assertTrue(names.containsKey("console_" + consoleId));
            session.close();
            assertFalse(Files.exists(temporary));
            assertFalse(names.containsKey("console_" + consoleId));
            execution.close();
        }
        try (var replacement = EmbeddingSupport.create(repository())) {
            assertHealthy(replacement);
        }
    }

    @Test
    void failedSessionCloseRetainsOwnershipUntilNativeAndHostCleanupFinish() throws Exception {
        var cleanup = new CompletableFuture<Void>();
        var session = EmbeddingSupport.create(repository());
        try {
            var module = compile(session, source("NativeResources.x"));
            var execution = start(session, module, null, List.of("file", "cancel"));
            var nativeOwner = nativeContainer(session);
            var owner = snapshot(session).owners.getFirst();
            var delegate = field(execution.control.getClass(), execution.control, "delegate");
            var temporary = (Path) field(delegate.getClass(), delegate, "temporary");
            owner.acquireResource(Object::new, _ -> cleanup);
            assertThrows(IllegalStateException.class, () -> session.close(Duration.ZERO));
            assertFalse(nativeOwner.f_runtime.isTerminated());
            assertTrue(Files.exists(temporary));
            try (var replacement = EmbeddingSupport.create(repository())) {
                assertThrows(IllegalStateException.class, replacement::ensureConnector);
            }
            cleanup.complete(null);
            session.close();
            assertTrue(nativeOwner.f_runtime.isTerminated());
            assertFalse(Files.exists(temporary));
            execution.close();
        } finally {
            cleanup.complete(null);
            session.close();
        }
        try (var replacement = EmbeddingSupport.create(repository())) {
            assertHealthy(replacement);
        }
    }

    @Test
    void sequentialRequestsReleaseChannelsWatchesAndCallbacks(@TempDir Path root) throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            var module = compile(session, source("NativeResources.x"));
            for (String kind : List.of("file", "watch", "callbacks", "paused")) {
                for (String outcome : List.of("success", "failure", "cancel")) {
                    try (var execution = start(session, module, root, List.of(kind, outcome))) {
                        var snapshot = snapshot(session);
                        if (kind.equals("file")) {
                            assertEquals(1, snapshot.channels.size());
                            assertTrue(snapshot.channels.getFirst().isOpen());
                        }
                        if (kind.equals("watch")) {
                            assertEquals(1, watchCount(session, "subscriptions"));
                        }
                        if (!outcome.equals("cancel")) {
                            execution.control.join();
                            assertEquals(outcome.equals("failure"), execution.errors.hasSeriousErrors(),
                                    execution.output::toString);
                        }
                        execution.control.close();
                        snapshot.assertClosed();
                        assertEquals(0, watchCount(session, "subscriptions"));
                        assertEquals(0, watchCount(session, "f_mapWatches"));
                        assertNoCallbacks(session);
                        assertTrue(Files.isDirectory(root));
                    }
                    assertHealthy(session);
                }
            }
            assertTrue(Files.exists(root.resolve("owned.dat")));
        }
    }

    @Test
    void closingOneWatchOwnerPreservesTheOtherInEitherOrder(@TempDir Path root) throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            var module = compile(session, source("NativeResources.x"));
            for (boolean reverse : List.of(false, true)) {
                try (var first = start(session, module, root, List.of("watch", "cancel"));
                     var second = start(session, module, root, List.of("watch", "cancel"))) {
                    assertEquals(2, watchCount(session, "subscriptions"));
                    assertEquals(1, watchCount(session, "f_mapWatches"));
                    (reverse ? second : first).control.close();
                    assertEquals(1, watchCount(session, "subscriptions"));
                    assertEquals(1, watchCount(session, "f_mapWatches"));
                    (reverse ? first : second).control.close();
                    assertEquals(0, watchCount(session, "subscriptions"));
                    assertEquals(0, watchCount(session, "f_mapWatches"));
                }
            }
            assertHealthy(session);
        }
    }

    @Test
    void sequentialRequestsCloseSocketsIncludingBlockedReads(@TempDir Path root) throws Exception {
        try (var session = networkSession();
             var listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10_000);
            var module = compile(session, source("NativeResources.x"));
            for (String outcome : List.of("success", "failure", "cancel", "read")) {
                try (var execution = start(session, module, root,
                        List.of("socket", outcome, Integer.toString(listener.getLocalPort())));
                     Socket peer = listener.accept()) {
                    peer.setSoTimeout(10_000);
                    assertEquals(1, peer.getInputStream().read());
                    var snapshot = snapshot(session);
                    assertEquals(1, snapshot.sockets.size());
                    assertFalse(snapshot.sockets.getFirst().isClosed());
                    if (outcome.equals("success") || outcome.equals("failure")) {
                        execution.control.join();
                        assertEquals(outcome.equals("failure"), execution.errors.hasSeriousErrors(),
                                execution.output::toString);
                    }
                    execution.control.close();
                    snapshot.assertClosed();
                    assertEquals(-1, peer.getInputStream().read());
                }
                assertHealthy(session);
            }
        }
    }

    @Test
    void sequentialRequestsCloseHttpClientsAndServerBindings(@TempDir Path root) throws Exception {
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        peer.start();
        try (var session = networkSession()) {
            var module = compile(session, source("NativeResources.x"));
            for (String outcome : List.of("success", "failure", "cancel")) {
                try (var execution = start(session, module, root,
                        List.of("http", outcome, "http://127.0.0.1:" + peer.getAddress().getPort() + "/"))) {
                    var snapshot = snapshot(session);
                    assertEquals(1, snapshot.clients.size());
                    if (!outcome.equals("cancel")) {
                        execution.control.join();
                        assertEquals(outcome.equals("failure"), execution.errors.hasSeriousErrors(),
                                execution.output::toString);
                    }
                    execution.control.close();
                    snapshot.assertClosed();
                }
                assertHealthy(session);
            }
            // A bound server deliberately keeps its application alive until close or failure.
            for (String outcome : List.of("failure", "cancel", "close")) {
                try (var execution = start(session, module, root, List.of("server", outcome))) {
                    var snapshot = snapshot(session);
                    assertEquals(outcome.equals("close") ? 0 : 2, snapshot.listeners.size());
                    if (!outcome.equals("cancel")) {
                        execution.control.join();
                        assertEquals(outcome.equals("failure"), execution.errors.hasSeriousErrors(),
                                execution.output::toString);
                    }
                    execution.control.close();
                    snapshot.assertClosed();
                }
                assertHealthy(session);
            }
        } finally {
            peer.stop(0);
        }
    }

    private static EmbeddingSupport networkSession() throws Exception {
        // Compile the production runner with only its test provider expanded. No extra injection
        // is enabled for users, and all register/start/release and Task behavior stays unchanged.
        String runner = source("runner.x").replace("    package web   import web.xtclang.org;",
                "    package net import net.xtclang.org;\n    package web   import web.xtclang.org;");
        String insertion = "        Supplier getResource(Type type, String name) {";
        assertTrue(runner.contains(insertion));
        runner = runner.replace(insertion, insertion + "\n" + source("NetworkResources.x"));
        var modules = new BuildRepository();
        try (var compiler = EmbeddingSupport.create(repository())) {
            modules.storeModule(compile(compiler, runner));
        }
        return EmbeddingSupport.create(new LinkedRepository(modules, repository()));
    }

    private static Execution start(EmbeddingSupport session, ModuleStructure module, Path root,
                                   List<String> args) throws Exception {
        var modules = new BuildRepository();
        modules.storeModule(module);
        var errors = new ErrorList(25);
        var output = new StringWriter();
        var ready = new CountDownLatch(1);
        var console = new PrintWriter(output, true) {
            @Override
            public void println(char[] text) {
                super.println(text);
                if (new String(text).equals("ready")) {
                    ready.countDown();
                }
            }
        };
        var request = new RunRequest(modules, module.getName(), "run", args, console,
                root == null ? null : root.toFile(), false);
        Control control = session.run(request, errors);
        assertNotNull(control, () -> errors.getErrors().toString());
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS), () -> errors.getErrors() + "\n" + output);
            return new Execution(control, errors, output);
        } catch (Throwable failure) {
            control.close();
            throw failure;
        }
    }

    private record Execution(Control control, ErrorList errors, StringWriter output) implements AutoCloseable {
        @Override
        public void close() {
            control.close();
        }
    }

    private record Snapshot(List<FileChannel> channels, List<Socket> sockets, List<HttpClient> clients,
                            List<InetSocketAddress> listeners, List<Container> owners, List<Object> timers) {
        void assertClosed() throws Exception {
            assertTrue(channels.stream().noneMatch(FileChannel::isOpen));
            assertTrue(sockets.stream().allMatch(Socket::isClosed));
            assertTrue(clients.stream().allMatch(HttpClient::isTerminated), "HTTP clients still running");
            for (InetSocketAddress address : listeners) {
                try (var socket = new ServerSocket()) {
                    socket.setReuseAddress(true);
                    socket.bind(address);
                }
            }
            for (Object timer : timers) {
                assertTrue(((Set<?>) field(timer.getClass(), timer, "f_setAlarms")).isEmpty(),
                        "A completed request retained timer alarms");
            }
            for (Container owner : owners) {
                Set<?> remaining = (Set<?>) field(Container.class, owner, "ownedResources");
                assertTrue(remaining.isEmpty(), () -> owner + " retains " + remaining);
            }
        }
    }

    private static Snapshot snapshot(EmbeddingSupport session) throws Exception {
        var channels = new ArrayList<FileChannel>();
        var sockets = new ArrayList<Socket>();
        var clients = new ArrayList<HttpClient>();
        var listeners = new ArrayList<InetSocketAddress>();
        var owners = new ArrayList<Container>();
        var timers = new ArrayList<Object>();
        for (Container owner : nativeContainer(session).f_runtime.containers()) {
            for (ServiceContext context : owner.getServices()) {
                Object service = context.getService();
                if (service != null && service.getClass().getSimpleName().equals("TimerHandle")) {
                    timers.add(service);
                }
            }
            List<?> resources;
            synchronized (owner) {
                resources = List.copyOf((Set<?>) field(Container.class, owner, "ownedResources"));
            }
            boolean requestResource = false;
            for (Object resource : resources) {
                Object value;
                try {
                    value = ((OwnedResource<?>) resource).get();
                } catch (IllegalStateException alreadyClosing) {
                    continue;
                }
                if (value instanceof FileChannel channel) {
                    channels.add(channel);
                    requestResource = true;
                } else if (value instanceof Socket socket) {
                    sockets.add(socket);
                    requestResource = true;
                } else if (value.getClass().getSimpleName().equals("HttpClientPool")) {
                    for (HttpClient client : (HttpClient[]) field(value.getClass(), value, "clients")) {
                        if (client != null) {
                            clients.add(client);
                        }
                    }
                    requestResource = true;
                } else if (value.getClass().getSimpleName().equals("HttpServerResources")) {
                    for (String name : List.of("http", "https")) {
                        if (field(value.getClass(), value, name) instanceof HttpServer server) {
                            listeners.add(server.getAddress());
                        }
                    }
                    requestResource = true;
                }
            }
            if (requestResource) {
                owners.add(owner);
            }
        }
        return new Snapshot(channels, sockets, clients, listeners, owners, timers);
    }

    private static void assertNoCallbacks(EmbeddingSupport session) throws Exception {
        for (Container owner : nativeContainer(session).f_runtime.containers()) {
            for (ServiceContext context : owner.getServices()) {
                assertTrue(((Map<?, ?>) field(ServiceContext.class, context, "m_mapCallbacks")).isEmpty());
            }
        }
    }

    private static int watchCount(EmbeddingSupport session, String name) throws Exception {
        Object template = nativeContainer(session).getTemplate("_native.fs.OSStorage");
        Object daemon = field(template.getClass(), template, "watchDaemon");
        if (daemon == null) {
            return 0;
        }
        synchronized (daemon) {
            return ((Map<?, ?>) field(daemon.getClass(), daemon, name)).size();
        }
    }

    private static Object field(Class<?> declaringClass, Object target, String name) throws Exception {
        var field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NativeContainer nativeContainer(EmbeddingSupport session) throws Exception {
        var method = InterpreterConnector.class.getDeclaredMethod("getNativeContainer");
        method.setAccessible(true);
        return (NativeContainer) method.invoke(session.ensureConnector());
    }

    private static Set<?> retainedContainers(EmbeddingSupport session) throws Exception {
        var runtime = nativeContainer(session).f_runtime;
        var method = runtime.getClass().getDeclaredMethod("retainedContainers");
        method.setAccessible(true);
        return (Set<?>) method.invoke(runtime);
    }

    private static void assertHealthy(EmbeddingSupport session) {
        var connector = session.ensureConnector();
        var errors = new ErrorList(25);
        var module = compile(session, "module HealthyOwner { Int run() = 7; }");
        try (Control control = session.run(module, null, null, null, errors)) {
            assertNotNull(control);
            control.join();
            assertEquals(7L, control.result());
            assertFalse(errors.hasSeriousErrors());
        }
        assertSame(connector, session.ensureConnector());
    }

    private static String source(String name) throws Exception {
        try (var input = EmbeddingResourceOwnershipTest.class.getResourceAsStream("/ownership/" + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ModuleStructure compile(EmbeddingSupport session, String source) {
        var errors = new ErrorList(25);
        var module = session.compile(source, null, errors);
        assertNotNull(module, () -> errors.getErrors().toString());
        assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
        return module;
    }

    private static ModuleRepository repository() {
        Path installed = Path.of("build", "install", "xdk");
        assertTrue(Files.isRegularFile(installed.resolve("lib/ecstasy.xtc")));
        return new LinkedRepository(new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
    }
}
