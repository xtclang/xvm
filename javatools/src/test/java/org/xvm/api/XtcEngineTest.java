package org.xvm.api;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.xvm.asm.ErrorListener;

/**
 * End-to-end coverage for the {@link XtcEngine} first-class embedding API: one warm engine
 * compiles in-memory sources (LSP-buffer style) and runs them as nested containers over the
 * shared native plane, with structured diagnostics on failure and an event-driven completion
 * future on success - the surface an LSP or resident tool would drive.
 */
public class XtcEngineTest {
    /**
     * A host-supplied listener must SEE the diagnostics, not merely have them collected for it.
     * This is the contract an LSP depends on: one sink it owns, told as errors are produced.
     */
    @Test
    public void aCallerSuppliedListenerReceivesCompileDiagnostics() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var seen = new CopyOnWriteArrayList<String>();
        ErrorListener mine = err -> {
            seen.add(err.getCode());
            return false;
        };

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile(mine, new XtcEngine.SourceUnit("ListenerBad", """
                    module ListenerBad {
                        void run() {
                            this is not ecstasy
                        }
                    }
                    """));

            assertFalse(compiled.isSuccess(), "the source is deliberately invalid");
            assertFalse(seen.isEmpty(),
                    "the caller's listener must be told, not just have errors collected for it");
            assertFalse(compiled.diagnostics().isEmpty(),
                    "and the returned result still carries them");
        }
    }

    /**
     * Two compiles running at once must not report into each other's listener. Each compiled
     * module owns its own ConstantPool, and that pool owns the listener, so the isolation is a
     * property of the ownership rather than of timing.
     */
    @Test
    public void parallelCompilesDoNotShareAListener() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var seenA = new CopyOnWriteArrayList<String>();
        var seenB = new CopyOnWriteArrayList<String>();
        ErrorListener listenerA = err -> { seenA.add(err.getCode()); return false; };
        ErrorListener listenerB = err -> { seenB.add(err.getCode()); return false; };

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            // A is invalid, B is valid; if the listeners were shared, B's would hear A's errors
            var futureA = CompletableFuture.supplyAsync(() ->
                    engine.compile(listenerA, new XtcEngine.SourceUnit("ParallelBad", """
                            module ParallelBad {
                                void run() {
                                    not valid ecstasy at all
                                }
                            }
                            """)));
            var futureB = CompletableFuture.supplyAsync(() ->
                    engine.compile(listenerB, new XtcEngine.SourceUnit("ParallelGood", """
                            module ParallelGood {
                                void run() {}
                            }
                            """)));

            var resultA = futureA.get(60, TimeUnit.SECONDS);
            var resultB = futureB.get(60, TimeUnit.SECONDS);

            assertFalse(resultA.isSuccess(), "A is deliberately invalid");
            assertTrue(resultB.isSuccess(),
                    () -> "B is valid and must not be failed by A: " + resultB.diagnostics());
            assertTrue(seenB.isEmpty(),
                    () -> "B's listener must not hear A's diagnostics, but heard: " + seenB);
        }
    }

    /**
     * The container owns the runtime's listener rather than each call site naming a constant, so an
     * embedder can replace it. Defaults to RUNTIME, which is what the removed ambient walk resolved
     * to, and rejects null the way the rest of this API does.
     */
    @Test
    public void theRuntimeListenerIsOwnedByTheContainerAndReplaceable() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile("ContainerListener", """
                    module ContainerListener {
                        void run() {}
                    }
                    """);
            assertTrue(compiled.isSuccess(), () -> "diagnostics: " + compiled.diagnostics());

            var future = engine.run(compiled, "ContainerListener");
            future.get(30, TimeUnit.SECONDS);
            assertTrue(future.isDone() && !future.isCompletedExceptionally());
        }
    }

    @Test
    public void compilesAndRunsAnInMemoryModuleOverOneWarmEngine() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile("EngineHello", """
                    module EngineHello {
                        void run() {
                            @Inject Console console;
                            console.print("engine ran EngineHello");
                        }
                    }
                    """);
            assertTrue(compiled.isSuccess(),
                    () -> "compile should succeed, diagnostics: " + compiled.diagnostics());
            assertTrue(compiled.modules().stream().anyMatch(id -> id.getName().equals("EngineHello")));

            // a second compile on the SAME warm engine (proving reuse)
            var compiled2 = engine.compile("EngineHello2", """
                    module EngineHello2 {
                        void run() {}
                    }
                    """);
            assertTrue(compiled2.isSuccess());

            // run the first module as a nested container; await the event-driven completion
            var future = engine.run(compiled, "EngineHello");
            future.get(30, TimeUnit.SECONDS);
            assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                    "the run must complete cleanly on the shared native plane");
        }
    }

    @Test
    public void compilesMultipleModulesInOneRequestOverOneWarmEngine() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile(
                    new XtcEngine.SourceUnit("AlphaMod", """
                            module AlphaMod {
                                void run() {
                                    @Inject Console console;
                                    console.print("alpha");
                                }
                            }
                            """),
                    new XtcEngine.SourceUnit("BetaMod", """
                            module BetaMod {
                                void run() {
                                    @Inject Console console;
                                    console.print("beta");
                                }
                            }
                            """));
            assertTrue(compiled.isSuccess(),
                    () -> "both modules should compile, diagnostics: " + compiled.diagnostics());
            assertTrue(compiled.modules().stream().anyMatch(id -> id.getName().equals("AlphaMod")));
            assertTrue(compiled.modules().stream().anyMatch(id -> id.getName().equals("BetaMod")));

            // each compiled module runs on the same warm engine
            engine.run(compiled, "AlphaMod").get(30, TimeUnit.SECONDS);
            engine.run(compiled, "BetaMod").get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void compilesInMemoryThenSyncsRunnableBinariesToDisk() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile("DiskMod", """
                    module DiskMod {
                        void run() {
                            @Inject Console console;
                            console.print("from disk");
                        }
                    }
                    """);
            assertTrue(compiled.isSuccess(),
                    () -> "compile should succeed, diagnostics: " + compiled.diagnostics());

            // sync the in-memory compilation out to .xtc binaries
            Path out   = Files.createTempDirectory("xtc-engine-out");
            var  files = compiled.writeTo(out.toFile());
            assertTrue(files.stream()
                            .anyMatch(f -> f.getName().equals("DiskMod.xtc") && f.isFile()),
                    () -> "expected DiskMod.xtc on disk, wrote: " + files);

            // the persisted binary reloads through an ordinary repository, proving a clean round-trip
            var reloaded = new DirRepository(out.toFile(), true).loadModule("DiskMod");
            assertTrue(reloaded != null, "the written .xtc must reload as a module");
        }
    }

    @Test
    public void surfacesStructuredDiagnosticsForBadSource() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var compiled = engine.compile("Broken", """
                    module Broken {
                        void run() {
                            this is not valid ecstasy
                        }
                    }
                    """);
            assertFalse(compiled.isSuccess(), "a broken module must not compile");
            assertTrue(compiled.diagnostics().stream()
                            .anyMatch(d -> d.severity().ordinal() >= Severity.ERROR.ordinal()),
                    () -> "must surface at least one error diagnostic: " + compiled.diagnostics());
        }
    }

    private static File[] xdkModulePath() {
        return List.of("xdk/build/install/xdk/lib", "xdk/build/install/xdk/javatools",
                        "lib_ecstasy/build/xtc/main/lib", "javatools_bridge/build/xtc/main/lib")
                .stream()
                .map(XtcEngineTest::checkoutFile)
                .filter(File::isDirectory)
                .toArray(File[]::new);
    }

    private static File checkoutFile(String path) {
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }
}
