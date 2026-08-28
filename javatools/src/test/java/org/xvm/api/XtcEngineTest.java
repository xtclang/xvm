package org.xvm.api;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Proves the embedding API works on master: compile Ecstasy source held in a String, then run the
 * compiled module in a nested container - all inside ONE JVM, with no process fork and no CLI.
 *
 * <p>This is the capability a fast Gradle plugin (and an LSP server) needs: today each compile/run
 * forks a JVM and re-bootstraps the runtime. A warm, resident engine removes both costs.</p>
 */
public class XtcEngineTest {
    /** Where the gradle build leaves the compiled XDK; TEST-ONLY discovery. */
    private static File xdkLib() {
        for (String s : List.of("xdk/build/install/xdk/lib", "../xdk/build/install/xdk/lib")) {
            File dir = new File(s);
            if (dir.isDirectory() && new File(dir, "ecstasy.xtc").isFile()) {
                return dir;
            }
        }
        return null;
    }

    private static File xdkJavatools() {
        for (String s : List.of("xdk/build/install/xdk/javatools", "../xdk/build/install/xdk/javatools")) {
            File dir = new File(s);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private static XtcEngine engine() {
        File lib = xdkLib();
        assumeTrue(lib != null, "a built XDK is required (./gradlew xdk:installDist)");
        File javatools = xdkJavatools();
        return javatools == null
                ? XtcEngine.builder().modulePath(lib).build()
                : XtcEngine.builder().modulePath(lib, javatools).build();
    }

    @Test
    public void compilesAndRunsAModuleHeldInAString() throws Exception {
        try (XtcEngine engine = engine()) {
            var result = engine.compile("TestPoc", """
                    module TestPoc {
                        Int run() {
                            return 42;
                        }
                    }
                    """);

            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());
            assertEquals(1, result.modules().size());

            // run it in a nested container and await the event-driven completion
            var control = engine.start(result, "TestPoc");
            control.completion().get(60, TimeUnit.SECONDS);

            assertFalse(control.running(), "the run should have finished");
            assertNotNull(control.whenStarted());
            assertTrue(control.whenStopped().isPresent(), "a finished run has a stop time");
            assertTrue(control.error().isEmpty(), () -> "run failed: " + control.error());
            assertEquals(42L, control.result().orElseThrow(),
                    "the module's Int run() result must reach the host");
        }
    }

    /**
     * The run-completion future must carry the module's actual {@code run()} return value.
     * Regression guard for two distinct defects fixed together: {@code callLater} hardcodes
     * {@code cReturns = 0} (so the future completed with an EMPTY tuple), and the native
     * instantiate-and-run op ignored the caller-designated return slot in favour of the stack
     * (so even with a return requested, the value landed where the future never reads).
     */
    @Test
    public void theRunResultReachesTheHost() throws Exception {
        try (XtcEngine engine = engine()) {
            for (long expected : new long[] {0L, 1L, 42L, 1234567890123L}) {
                String name = "Ret" + expected;
                var result = engine.compile(name,
                        "module " + name + " {\n"
                      + "    Int run() {\n"
                      + "        return " + expected + ";\n"
                      + "    }\n"
                      + "}\n");
                assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

                var control = engine.start(result, name);
                control.completion().get(60, TimeUnit.SECONDS);

                assertTrue(control.error().isEmpty(), () -> "run failed: " + control.error());
                assertEquals(expected, control.result().orElseThrow(),
                        "the exact Int returned by run() must reach the host");
            }
        }
    }

    /**
     * A void run() must complete cleanly and report NO result - "returned nothing" must not be
     * confused with "failed".
     */
    @Test
    public void aVoidRunCompletesWithNoResult() throws Exception {
        try (XtcEngine engine = engine()) {
            var result = engine.compile("VoidPoc",
                    "module VoidPoc {\n"
                  + "    void run() {\n"
                  + "        @Inject Console console;\n"
                  + "        console.print(\"void run executed\");\n"
                  + "    }\n"
                  + "}\n");
            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

            var control = engine.start(result, "VoidPoc");
            control.completion().get(60, TimeUnit.SECONDS);

            assertTrue(control.error().isEmpty(), () -> "run failed: " + control.error());
            assertFalse(control.running(), "the run should have finished");
            assertTrue(control.result().isEmpty(), "a void run() reports no result");
        }
    }

    @Test
    public void reportsCompileDiagnosticsAndStreamsThemToTheCaller() throws Exception {
        try (XtcEngine engine = engine()) {
            // the caller's own sink, exactly as an LSP server would supply
            var streamed = new ArrayList<String>();
            ErrorListener sink = err -> {
                streamed.add(err.getCode());
                return false;
            };

            var result = engine.compile(sink, new ToolApi.SourceUnit("BadPoc", """
                    module BadPoc {
                        void run() {
                            this_is_not_a_thing();
                        }
                    }
                    """));

            assertFalse(result.isSuccess(), "a broken module must not report success");
            assertFalse(result.diagnostics().isEmpty(), "the failure must be self-describing");
            assertTrue(result.diagnostics().stream()
                            .anyMatch(d -> d.severity().compareTo(Severity.ERROR) >= 0),
                    "at least one ERROR diagnostic expected");
            assertFalse(streamed.isEmpty(),
                    "diagnostics must also stream to the caller's ErrorListener as produced");
        }
    }

    @Test
    public void oneWarmEngineServesRepeatedCompileAndRunCycles() throws Exception {
        // the actual point of the API: pay the runtime bootstrap ONCE, not per invocation
        try (XtcEngine engine = engine()) {
            for (int i = 1; i <= 3; i++) {
                String name = "Repeat" + i;
                var result = engine.compile(name, """
                        module %s {
                            Int run() {
                                return %d;
                            }
                        }
                        """.formatted(name, i));
                assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

                var control = engine.start(result, name);
                control.completion().get(60, TimeUnit.SECONDS);
                assertTrue(control.error().isEmpty(),
                        () -> "warm run " + name + " failed: " + control.error());
                assertEquals((long) i, control.result().orElseThrow(),
                        "each warm run must return its own result");
            }
        }
    }

    /**
     * The engine must be usable through the {@link ToolApi} contract alone - that is the point of
     * naming the contract: a caller (or the upstream ToolConnector) can be written against the
     * interface and satisfied by any implementation.
     */
    @Test
    public void theEngineIsUsableThroughTheToolApiContractAlone() throws Exception {
        try (ToolApi api = engine()) {                       // <- interface type, not XtcEngine
            ToolApi.CompileResult result = api.compile(ErrorListener.BLACKHOLE,
                    new ToolApi.SourceUnit("ApiPoc",
                            "module ApiPoc {\n"
                          + "    Int run() {\n"
                          + "        return 7;\n"
                          + "    }\n"
                          + "}\n"));

            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());
            assertFalse(result.modules().isEmpty());

            ToolApi.RunControl control = api.start(result, "ApiPoc");
            control.completion().get(60, TimeUnit.SECONDS);

            assertTrue(control.error().isEmpty(), () -> "run failed: " + control.error());
            assertEquals(7L, control.result().orElseThrow());
        }
    }

    /**
     * The FAILURE path: a module that throws must complete the future EXCEPTIONALLY and surface the
     * error through the control handle. A test runner or build plugin depends on this - a run that
     * blows up must not look like a run that succeeded.
     */
    @Test
    public void aThrowingRunCompletesExceptionally() throws Exception {
        try (XtcEngine engine = engine()) {
            var result = engine.compile("ThrowPoc",
                    "module ThrowPoc {\n"
                  + "    void run() {\n"
                  + "        throw new IllegalState(\"deliberate POC failure\");\n"
                  + "    }\n"
                  + "}\n");
            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

            var control = engine.start(result, "ThrowPoc");
            try {
                control.completion().get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                // expected: the run threw
            }

            assertFalse(control.running(), "a failed run has still finished");
            assertTrue(control.error().isPresent(), "the failure must reach the host");
            assertTrue(control.result().isEmpty(), "a failed run has no result");
        }
    }

    /**
     * Several modules compiled in ONE request, resolving references among themselves - the shape a
     * build plugin needs when a project has interdependent modules.
     */
    @Test
    public void compilesMultipleModulesInOneRequest() throws Exception {
        try (XtcEngine engine = engine()) {
            var result = engine.compile(ErrorListener.BLACKHOLE,
                    new ToolApi.SourceUnit("AlphaPoc",
                            "module AlphaPoc {\n"
                          + "    Int run() {\n"
                          + "        return 1;\n"
                          + "    }\n"
                          + "}\n"),
                    new ToolApi.SourceUnit("BetaPoc",
                            "module BetaPoc {\n"
                          + "    Int run() {\n"
                          + "        return 2;\n"
                          + "    }\n"
                          + "}\n"));

            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());
            assertEquals(2, result.modules().size(), "both modules must be compiled");

            // and both are runnable from the one warm engine
            var a = engine.start(result, "AlphaPoc");
            a.completion().get(60, TimeUnit.SECONDS);
            assertEquals(1L, a.result().orElseThrow());

            var b = engine.start(result, "BetaPoc");
            b.completion().get(60, TimeUnit.SECONDS);
            assertEquals(2L, b.result().orElseThrow());
        }
    }

    /**
     * In-memory compile, then SYNC TO DISK as ordinary .xtc binaries, then load them back through a
     * plain DirRepository. "Compile directly to disk" is just compile-then-writeTo, and the output
     * must be indistinguishable from anything else the toolchain produced - which is exactly what a
     * Gradle plugin needs to emit build artifacts.
     */
    @Test
    public void compiledModulesCanBeSyncedToDiskAndReloaded(@TempDir Path tempDir) throws Exception {
        File out = tempDir.resolve("out").toFile();
        try (XtcEngine engine = engine()) {
            var result = engine.compile("DiskPoc",
                    "module DiskPoc {\n"
                  + "    Int run() {\n"
                  + "        return 5;\n"
                  + "    }\n"
                  + "}\n");
            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

            List<File> written = result.writeTo(out);
            assertEquals(1, written.size(), "one file per module");
            assertTrue(written.get(0).isFile(), "the .xtc must exist on disk");
            assertTrue(written.get(0).length() > 0, "and be non-empty");
        }

        // reload through an ordinary repository - nothing engine-specific about the output
        var repo = new DirRepository(out, true);
        assertTrue(repo.getModuleNames().stream().anyMatch(n -> n.startsWith("DiskPoc")),
                () -> "the written module must reload: " + repo.getModuleNames());
    }

    /**
     * Compile from ON-DISK source - the shape an LSP workspace and a build tool actually have
     * (directories of .x files, not one string). Goes through ModuleInfo into the SAME pipeline as
     * the in-memory path, and the result runs identically.
     */
    @Test
    public void compilesAModuleFromDisk(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("DiskSrc.x");
        Files.writeString(src,
                "module DiskSrc {\n"
              + "    Int run() {\n"
              + "        return 11;\n"
              + "    }\n"
              + "}\n");

        try (XtcEngine engine = engine()) {
            var result = engine.compile(src);

            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());
            assertEquals(1, result.modules().size());

            var control = engine.start(result, "DiskSrc");
            control.completion().get(60, TimeUnit.SECONDS);
            assertTrue(control.error().isEmpty(), () -> "run failed: " + control.error());
            assertEquals(11L, control.result().orElseThrow(),
                    "a module compiled from disk runs exactly like an in-memory one");
        }
    }

    /**
     * Pointing the tool at a path that is not a module is ordinary USER error: it must produce a
     * DIAGNOSTIC, not blow up. An LSP cannot die because someone opened the wrong directory.
     */
    @Test
    public void aPathThatIsNotAModuleIsADiagnosticNotACrash(@TempDir Path tempDir) throws Exception {
        Path notAModule = tempDir.resolve("empty-dir");
        Files.createDirectories(notAModule);

        try (XtcEngine engine = engine()) {
            var result = engine.compile(notAModule);       // must not throw

            assertFalse(result.isSuccess(), "a non-module path cannot compile successfully");
            assertFalse(result.diagnostics().isEmpty(), "and must say why");
        }
    }
}
