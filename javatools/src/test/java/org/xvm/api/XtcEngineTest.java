package org.xvm.api;


import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

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

            var result = engine.compile(sink, new XtcEngine.SourceUnit("BadPoc", """
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
}
