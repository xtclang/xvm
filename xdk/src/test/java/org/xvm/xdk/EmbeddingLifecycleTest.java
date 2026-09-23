package org.xvm.xdk;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.BindException;
import java.net.ServerSocket;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.EmbeddingSupport;
import org.xvm.api.EmbeddingSupport.Control;
import org.xvm.api.RunRequest;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.BuildRepository;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.TestRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embedding lifecycle tests using the distribution provisioned by the XDK test task.
 */
@Timeout(60)
class EmbeddingLifecycleTest {
    @Test
    void jitReusesRuntimeWithFreshStaticsArgumentsAndConsoles() {
        try (var session = EmbeddingSupport.create(repository(),
                Path.of("build", "install", "xdk", "javatools", "javatools-jitbridge.jar"))) {
            ModuleStructure module = compile(session, """
                    module RepeatedJit {
                        static Counter counter = new CounterService();
                        Int evaluate(String[] args) {
                            @Inject Console console;
                            console.print(args[0]);
                            return counter.next();
                        }
                        interface Counter {
                            Int next();
                        }
                        service CounterService implements Counter {
                            Int count;
                            @Override
                            Int next() = ++count;
                        }
                    }
                    """);
            var first = new StringWriter();
            var second = new StringWriter();
            var connector = session.ensureConnector(RunRequest.Backend.JIT);
            assertEquals(1L, runJit(session, module, "evaluate", List.of("first"), first, Map.of()));
            assertEquals(1L, runJit(session, module, "evaluate", List.of("second"), second, Map.of()));
            assertEquals("first" + System.lineSeparator(), first.toString());
            assertEquals("second" + System.lineSeparator(), second.toString());
            assertSame(connector, session.ensureConnector(RunRequest.Backend.JIT));

            ModuleStructure replacement = compile(session,
                    "module RepeatedJit { Int run() = 4294967297; }");
            assertEquals(4294967297L, runJit(session, replacement, "run", List.of(), new StringWriter(), Map.of()));
            assertEquals(1L, runJit(session, module, "evaluate", List.of("original"), new StringWriter(), Map.of()));

            // The compiler, interpreter and JIT can be used in the same owned session.
            assertEquals(7L, run(session, compile(session, "module Interpreted { Int run() = 7; }")));
            assertSame(connector, session.ensureConnector(RunRequest.Backend.JIT));
        }
    }

    @Test
    void jitFailureDoesNotContaminateTheNextRequest() {
        try (var session = EmbeddingSupport.create(repository(),
                Path.of("build", "install", "xdk", "javatools", "javatools-jitbridge.jar"))) {
            ModuleStructure broken = compile(session, "module BrokenJit { void run() { assert False; } }");
            var modules = new BuildRepository();
            modules.storeModule(broken);
            var errors = new ErrorList(25);
            var output = new StringWriter();
            var request = new RunRequest(modules, broken.getName(), "run", List.of(),
                    new PrintWriter(output), null, false, Map.of(), RunRequest.Backend.JIT);
            try (Control control = session.run(request, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertNull(control.result());
                assertTrue(errors.hasSeriousErrors());
                assertFalse(control.running());
                assertNotNull(control.whenStopped());
            }
            assertFalse(output.toString().isEmpty());
            assertEquals(0L, runJit(session, compile(session, "module HealthyJit { void run() {} }"),
                    "run", List.of(), new StringWriter(), Map.of()));
        }
    }

    @Test
    void jitInjectionsBelongToEachRequest() {
        try (var session = EmbeddingSupport.create(repository(),
                Path.of("build", "install", "xdk", "javatools", "javatools-jitbridge.jar"))) {
            ModuleStructure module = compile(session, """
                    module InjectedJit {
                        Int run(String[] args) {
                            @Inject("sample") String sample;
                            @Inject("values") List<String> values;
                            assert sample == args[0];
                            assert values[0] == sample;
                            return values.size;
                        }
                    }
                    """);
            assertEquals(2L, runJit(session, module, "run", List.of("first"), new StringWriter(),
                    Map.of("sample", List.of("old", "first"), "values", List.of("first", "value"))));
            assertEquals(1L, runJit(session, module, "run", List.of("second"), new StringWriter(),
                    Map.of("sample", List.of("second"), "values", List.of("second"))));
        }
    }

    @Test
    void jitSessionCloseStopsItsWorkerAndPreservesInterruption() throws Exception {
        try (var session = EmbeddingSupport.create(repository(),
                Path.of("build", "install", "xdk", "javatools", "javatools-jitbridge.jar"))) {
            var console = new BlockingJitConsole(true);
            try (Control control = startBlockedJit(session, console)) {
                try {
                    console.entered.await();
                    Thread.currentThread().interrupt();
                    session.close();
                    assertTrue(Thread.currentThread().isInterrupted());
                    assertEquals(0L, console.interrupted.getCount());
                    assertFalse(control.running());
                    assertNotNull(control.whenStopped());
                } finally {
                    Thread.interrupted();
                    console.release.countDown();
                }
            }
        }
    }

    @Test
    void jitFailedCloseDoesNotPretendItsWorkerStopped() throws Exception {
        try (var session = EmbeddingSupport.create(repository(),
                Path.of("build", "install", "xdk", "javatools", "javatools-jitbridge.jar"))) {
            var console = new BlockingJitConsole(false);
            try (Control control = startBlockedJit(session, console)) {
                try {
                    console.entered.await();
                    assertThrows(IllegalStateException.class, () -> control.close(Duration.ZERO));
                    assertTrue(control.running());
                    assertNull(control.whenStopped());
                    assertThrows(IllegalStateException.class,
                            () -> session.ensureConnector(RunRequest.Backend.JIT));
                } finally {
                    console.release.countDown();
                    control.join();
                }
                assertFalse(control.running());
            }
        }
    }

    private static Control startBlockedJit(EmbeddingSupport session, PrintWriter console) {
        ModuleStructure module = compile(session, """
                module BlockedJit {
                    void run() {
                        @Inject Console console;
                        console.print("entered");
                    }
                }
                """);
        var modules = new BuildRepository();
        modules.storeModule(module);
        var errors = new ErrorList(25);
        var request = new RunRequest(modules, module.getName(), "run", List.of(),
                console, null, false, Map.of(), RunRequest.Backend.JIT);
        Control control = session.run(request, errors);
        assertNotNull(control, () -> errors.getErrors().toString());
        return control;
    }

    private static class BlockingJitConsole extends PrintWriter {
        BlockingJitConsole(boolean stopOnInterrupt) {
            super(new StringWriter());
            this.stopOnInterrupt = stopOnInterrupt;
        }

        @Override
        public void print(Object value) {
            entered.countDown();
            while (true) {
                try {
                    release.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    if (stopOnInterrupt) {
                        return;
                    }
                }
            }
        }

        private final boolean stopOnInterrupt;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
    }

    private static long runJit(EmbeddingSupport session, ModuleStructure module, String method,
                              List<String> args, StringWriter output, Map<String, List<String>> injections) {
        var modules = new BuildRepository();
        modules.storeModule(module);
        var errors = new ErrorList(25);
        var request = new RunRequest(modules, module.getName(), method, args,
                new PrintWriter(output), null, false, injections, RunRequest.Backend.JIT);
        try (Control control = session.run(request, errors)) {
            assertNotNull(control, () -> errors.getErrors().toString());
            control.join();
            assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors() + "\n" + output);
            assertNotNull(control.result());
            return control.result();
        }
    }

    @Test
    void xunitUsesRequestInjectionsAndReportsFailures(@TempDir Path root) throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            ModuleStructure module = compile(session, """
                    module EmbeddedTests {
                        package xunit import xunit.xtclang.org;
                        class Fixture {
                            @Test
                            void verifiesRequest() {
                                @Inject("sample") String sample;
                                @Inject Directory testOutputRoot;
                                testOutputRoot.fileFor("marker.txt").ensure();
                                assert sample == "pass";
                            }
                        }
                    }
                    """);
            var modules = new BuildRepository();
            modules.storeModule(module);
            for (int index = 0; index < 3; index++) {
                int expected = index == 0 ? 1 : 0;
                String outputDirectory = "report-" + index;
                var output = new StringWriter();
                var errors = new ErrorList(25);
                var request = new RunRequest(modules, TestRunner.XUNIT_MODULE, "run", List.of(),
                        new PrintWriter(output), root.toFile(), true, Map.of(
                            TestRunner.XUNIT_MODULE_ARG, List.of(module.getName()),
                            TestRunner.XUNIT_MODULE_VERSION_ARG, List.of(),
                            TestRunner.XUNIT_TEST_OUTPUT_DIR, List.of(outputDirectory),
                            "sample", List.of(expected == 0 ? "pass" : "fail")));
                try (Control control = session.run(request, errors)) {
                    assertNotNull(control, () -> errors.getErrors().toString());
                    control.join();
                    assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors() + "\n" + output);
                    assertEquals((long) expected, control.result(), output::toString);
                }
                assertTrue(output.toString().contains("verifiesRequest"), output::toString);
                assertTrue(output.toString().contains("failed=" + expected), output::toString);
                assertTrue(Files.isRegularFile(root.resolve(outputDirectory).resolve("test-output/marker.txt")));
            }
        }
    }

    @Test
    void sessionCloseStopsFilesystemWatchers(@TempDir Path root) throws Exception {
        Set<Thread> previous = runtimeThreads();
        try (var session = EmbeddingSupport.create(repository())) {
            ModuleStructure module = compile(session, """
                    module Watching {
                        void run() {
                            @Inject Directory rootDir;
                            rootDir.watch(new ecstasy.fs.FileWatcher() {}.makeImmutable());
                        }
                    }
                    """);
            var errors = new ErrorList(25);
            try (Control control = session.run(module, null, root.toFile(), null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
            }
        }
        assertWorkersStopped(previous);
    }

    @Test
    void fileCompilationIncludesNestedSourcesResourcesAndFreshOutputs(@TempDir Path root) throws Exception {
        Path source = Files.createDirectory(root.resolve("src"));
        Path children = Files.createDirectory(source.resolve("Sample"));
        Path resources = Files.createDirectory(root.resolve("resources"));
        Path output = Files.createDirectory(root.resolve("out"));
        Path module = source.resolve("Sample.x");
        Path helper = children.resolve("Helper.x");
        Files.writeString(module, """
                module Sample.example.org {
                    Int evaluate(String[] args) {
                        String payload = $./message.txt;
                        assert payload == "payload";
                        @Inject Directory curDir;
                        curDir.fileFor("marker.txt").ensure();
                        return new Helper().value + args.size;
                    }
                }
                """);
        Files.writeString(helper, "const Helper { Int value.get() = 3; }");
        Files.writeString(resources.resolve("message.txt"), "payload");
        var timestamp = Files.getLastModifiedTime(helper);
        var options = CompilerOptions.builder().addInputFile(module.toFile())
                .addResourceLocation(resources.toFile()).setOutputLocation(output.toFile())
                .setModuleVersion("1.2.3").forceRebuild(true).build();
        var errors = new ErrorList(25);
        try (var session = EmbeddingSupport.create(repository())) {
            Set<Thread> previous = runtimeThreads();
            assertEquals(0, session.compile(options, null, errors), () -> errors.getErrors().toString());
            assertEquals(previous, runtimeThreads());
            for (int expected : List.of(5, 6)) {
                var modules = new DirRepository(output.toFile(), true);
                assertEquals(new Version("1.2.3"), modules.loadModule("Sample.example.org").getVersion());
                var request = new RunRequest(modules, "Sample.example.org", "evaluate", List.of("a", "b"),
                        new PrintWriter(new StringWriter()), root.toFile(), true);
                try (Control control = session.run(request, errors)) {
                    assertNotNull(control, () -> errors.getErrors().toString());
                    control.join();
                    assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
                    assertEquals((long) expected, control.result());
                }
                assertTrue(Files.isRegularFile(root.resolve("marker.txt")));
                if (expected == 5) {
                    Files.writeString(helper, "const Helper { Int value.get() = 4; }");
                    Files.setLastModifiedTime(helper, timestamp);
                    assertEquals(0, session.compile(options, null, errors), () -> errors.getErrors().toString());
                }
            }
        }
    }

    @Test
    void nestedModuleResolutionUsesTheRequestRepository(@TempDir Path root) throws Exception {
        Path output = Files.createDirectory(root.resolve("out"));
        Path dependency = root.resolve("Dependency.x");
        Path consumer = root.resolve("Consumer.x");
        Path loader = root.resolve("Loader.x");
        Files.writeString(consumer, """
                module Consumer {
                    package dependency import Dependency;
                    Int run() = dependency.answer();
                }
                """);
        Files.writeString(loader, """
                module Loader {
                    import ecstasy.mgmt.*;
                    Int run() {
                        @Inject("repository") ModuleRepository repository;
                        val template = repository.getResolvedModule("Consumer");
                        val container = new Container(template, Lightweight, repository,
                                new PassThroughResourceProvider());
                        Tuple result = container.invoke("run", ());
                        return result[0].as(Int);
                    }
                }
                """);
        var options = CompilerOptions.builder().addInputFile(dependency.toFile())
                .addInputFile(consumer.toFile()).addInputFile(loader.toFile())
                .setOutputLocation(output.toFile()).forceRebuild(true).build();
        try (var session = EmbeddingSupport.create(repository())) {
            for (int expected : List.of(41, 42)) {
                Files.writeString(dependency, "module Dependency { Int answer() = " + expected + "; }");
                var errors = new ErrorList(25);
                assertEquals(0, session.compile(options, null, errors), () -> errors.getErrors().toString());
                var modules = new DirRepository(output.toFile(), true);
                var request = new RunRequest(modules, "Loader", "run", List.of(),
                        new PrintWriter(new StringWriter()), root.toFile(), true);
                try (Control control = session.run(request, errors)) {
                    assertNotNull(control, () -> errors.getErrors().toString());
                    control.join();
                    assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
                    assertEquals((long) expected, control.result());
                }
            }
        }
    }

    @Test
    void legacyFileCompilationLoadsASourceTree(@TempDir Path root) throws Exception {
        Path source = root.resolve("Sample.x");
        Path children = Files.createDirectory(root.resolve("Sample"));
        Files.writeString(source, "module Sample { Int run() = new Helper().value; }");
        Files.writeString(children.resolve("Helper.x"), "const Helper { Int value.get() = 12; }");
        var output = new BuildRepository();
        var errors = new ErrorList(25);
        try (var session = EmbeddingSupport.create(repository())) {
            assertTrue(session.compile(source.toFile(), null, output, errors), () -> errors.getErrors().toString());
            assertEquals(12L, run(session, output.loadModule("Sample")));
        }
    }

    @Test
    void headlessSessionReusesRuntimeAndIsolatesRepeatedModules() throws Exception {
        Set<Thread> previous = runtimeThreads();
        try (ServerSocket http = occupyPort(8080);
             ServerSocket https = occupyPort(8090);
             EmbeddingSupport session = EmbeddingSupport.create(repository())) {
            ModuleStructure first = compile(session, """
                    module Repeated {
                        Int run() = Counter.next();
                        static service Counter {
                            Int value = 0;
                            Int next() = ++value;
                        }
                    }
                    """);
            assertEquals(previous, runtimeThreads(), "Compilation must not start an XVM");

            try (var ignore = ConstantPool.withPool(first.getConstantPool())) {
                assertEquals(1L, run(session, first));
                assertSame(first.getConstantPool(), ConstantPool.getCurrentPool());
                var connector = session.ensureConnector();
                assertEquals(1L, run(session, first), "Application singletons must be fresh");
                assertSame(connector, session.ensureConnector());

                var errors = new ErrorList(25);
                assertNull(session.compile("module Broken { void run( }", null, errors));
                assertTrue(errors.hasSeriousErrors());
                assertSame(first.getConstantPool(), ConstantPool.getCurrentPool());

                ModuleStructure replacement = compile(session,
                        "module Repeated { Int run() = 42; }");
                assertEquals(42L, run(session, replacement));
                assertEquals(1L, run(session, first), "The caller's original module stays intact");
                assertSame(first.getConstantPool(), ConstantPool.getCurrentPool());
            }
        }
        assertWorkersStopped(previous);
    }

    @Test
    void failedRequestDoesNotContaminateTheNextRequest() {
        try (EmbeddingSupport session = EmbeddingSupport.create(repository())) {
            ModuleStructure broken = compile(session,
                    "module Broken { void run() { throw new IllegalState(\"expected failure\"); } }");
            var output = new StringWriter();
            var errors = new ErrorList(25);
            try (Control control = session.run(broken, new PrintWriter(output, true), null, null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertNull(control.result());
                assertTrue(errors.hasSeriousErrors());
            }
            assertTrue(output.toString().contains("expected failure"));
            assertEquals(7L, run(session, compile(session, "module Healthy { Int run() = 7; }")));
        }
    }

    @Test
    void failedRequestCanCloseWithAnOutstandingServiceCall() throws Exception {
        try (var session = EmbeddingSupport.create(repository())) {
            ModuleStructure module = compile(session, """
                    module Outstanding {
                        void run() {
                            Worker.awaitRelease^();
                            throw new IllegalState("expected failure");
                        }
                        static service Worker {
                            void awaitRelease() {
                                @Inject Console console;
                                @Inject Timer timer;
                                @Future Tuple done;
                                timer.schedule(Duration:1H, () -> { done = (); });
                                console.print("waiting");
                                return done;
                            }
                        }
                    }
                    """);
            var ready = new CountDownLatch(1);
            var console = new PrintWriter(new StringWriter(), true) {
                @Override
                public void println(char[] text) {
                    super.println(text);
                    if (new String(text).equals("waiting")) {
                        ready.countDown();
                    }
                }
            };
            var errors = new ErrorList(25);
            try (Control control = session.run(module, console, null, null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Worker never reached its timer wait");
                control.join();
                assertNull(control.result());
                assertTrue(errors.hasSeriousErrors());
            }
            assertEquals(7L, run(session, compile(session, "module Healthy { Int run() = 7; }")));
        }
    }

    @Test
    void joinReportsAnUnhandledBackgroundFailure() {
        try (EmbeddingSupport session = EmbeddingSupport.create(repository())) {
            ModuleStructure module = compile(session, """
                    module Background {
                        Int run() {
                            Worker.fail^();
                            return 7;
                        }
                        static service Worker {
                            void fail() {
                                throw new IllegalState("background failure");
                            }
                        }
                    }
                    """);
            var errors = new ErrorList(25);
            var output = new StringWriter();
            try (Control control = session.run(module, new PrintWriter(output, true), null, null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertNull(control.result(), "The entry result must not hide a background failure");
                assertTrue(errors.hasSeriousErrors());
            }
            assertTrue(output.toString().contains("background failure"));
            assertEquals(9L, run(session, compile(session, "module Next { Int run() = 9; }")));
        }
    }

    @Test
    void requestClosePreservesCallerRootAndDeletesOwnedRoot(@TempDir Path root) throws Exception {
        try (EmbeddingSupport session = EmbeddingSupport.create(repository())) {
            ModuleStructure module = compile(session, """
                    module Files {
                        void run() {
                            @Inject Directory rootDir;
                            rootDir.fileFor("marker.txt").ensure();
                        }
                    }
                    """);
            var errors = new ErrorList(25);
            try (Control control = session.run(module, null, root.toFile(), null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
            }
            assertTrue(Files.isRegularFile(root.resolve("marker.txt")));

            String marker = root.getFileName() + ".txt";
            ModuleStructure temporaryModule = compile(session, """
                    module Temporary {
                        void run() {
                            @Inject Directory rootDir;
                            rootDir.fileFor("%s").ensure();
                        }
                    }
                    """.formatted(marker));
            Path temporary;
            try (Control control = session.run(temporaryModule, null, null, null, errors)) {
                assertNotNull(control, () -> errors.getErrors().toString());
                control.join();
                assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
                temporary = requestDirectoryContaining(marker);
                control.close();
                control.close();
            }
            assertFalse(Files.exists(temporary));
        }
    }

    @Test
    void sessionCloseCancelsWaitingRequestsAndAllowsANewSession() throws Exception {
        Set<Thread> previous = runtimeThreads();
        EmbeddingSupport session = EmbeddingSupport.create(repository());
        Control control;
        try (session) {
            ModuleStructure waiting = compile(session, """
                    module Waiting {
                        void run() {
                            @Inject Console console;
                            @Inject Timer timer;
                            @Future Tuple done;
                            timer.schedule(Duration:1H, () -> { done = (); });
                            console.print("waiting");
                            return done;
                        }
                    }
                    """);
            var ready = new CountDownLatch(1);
            var console = new PrintWriter(new StringWriter(), true) {
                @Override
                public void println(char[] text) {
                    super.println(text);
                    if (new String(text).equals("waiting")) {
                        ready.countDown();
                    }
                }
            };
            var errors = new ErrorList(25);
            control = session.run(waiting, console, null, null, errors);
            assertNotNull(control, () -> errors.getErrors().toString());
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Request never reached its timer wait");
            Thread.currentThread().interrupt();
            try {
                assertThrows(IllegalStateException.class, control::join);
                assertTrue(Thread.currentThread().isInterrupted());
                session.close();
                assertTrue(Thread.currentThread().isInterrupted(), "Cleanup must preserve interruption");
            } finally {
                Thread.interrupted();
            }
        }
        assertFalse(control.running());
        control.close();
        session.close();
        assertThrows(IllegalStateException.class, session::ensureConnector);
        assertThrows(IllegalStateException.class,
                () -> session.compile("module Closed {}", null, new ErrorList(25)));
        assertWorkersStopped(previous);

        try (EmbeddingSupport next = EmbeddingSupport.create(repository())) {
            assertEquals(9L, run(next, compile(next, "module Next { Int run() = 9; }")));
        }
        assertWorkersStopped(previous);
    }

    private static ModuleRepository repository() {
        Path installed = Path.of("build", "install", "xdk");
        assertTrue(Files.isRegularFile(installed.resolve("lib/ecstasy.xtc")),
                "The XDK test task must provision its distribution");
        return new LinkedRepository(
                new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
    }

    private static ModuleStructure compile(EmbeddingSupport session, String source) {
        var errors = new ErrorList(25);
        ModuleStructure module = session.compile(source, null, errors);
        assertNotNull(module, () -> errors.getErrors().toString());
        assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
        return module;
    }

    private static long run(EmbeddingSupport session, ModuleStructure module) {
        var errors = new ErrorList(25);
        try (Control control = session.run(module, new PrintWriter(new StringWriter()), null, null, errors)) {
            assertNotNull(control, () -> errors.getErrors().toString());
            control.join();
            assertFalse(errors.hasSeriousErrors(), () -> errors.getErrors().toString());
            assertNotNull(control.result());
            return control.result();
        }
    }

    private static ServerSocket occupyPort(int port) throws Exception {
        try {
            return new ServerSocket(port);
        } catch (BindException e) {
            // An already occupied port provides the same headless-startup check.
            return null;
        }
    }

    private static Path requestDirectoryContaining(String marker) throws Exception {
        try (var entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries.filter(path -> path.getFileName().toString().startsWith("xvm-embedding-"))
                    .filter(path -> Files.isRegularFile(path.resolve(marker)))
                    .findFirst().orElseThrow();
        }
    }

    private static Set<Thread> runtimeThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("XvmWorker@")
                        || thread.getName().equals("WatchServiceDaemon")
                        || thread.getName().equals("FileSystemWatcher")
                        || thread.getName().equals("ecstasy:LocalClock"))
                .collect(Collectors.toSet());
    }

    private static void assertWorkersStopped(Set<Thread> previous) throws InterruptedException {
        for (Thread thread : runtimeThreads()) {
            if (!previous.contains(thread)) {
                thread.join(1_000);
                assertFalse(thread.isAlive(), () -> "Embedding worker still alive: " + thread);
            }
        }
    }
}
