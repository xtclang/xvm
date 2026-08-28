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
            // KNOWN GAP (tracked): the module's Int run() value does not reach the host - the
            // completion tuple comes back EMPTY, so result() is empty. Compile + run + completion
            // all work; only the return-value plumbing in Container.runModule is unfinished.
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
                assertFalse(control.running(), "each warm run must finish");
            }
        }
    }
}
