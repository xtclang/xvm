package org.xvm.api;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xvm.test.XdkOutputs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Sequential compiles of the whole manualTests suite through ONE warm engine. */
public class EngineSuiteCompileTest {
    @Test public void sequential() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "needs XDK");
        Path root = XdkOutputs.root();
        File dir  = root.resolve("manualTests/src/main/x").toFile();
        List<File> srcs = new ArrayList<>(Arrays.asList(
                dir.listFiles(f -> f.getName().endsWith(".x"))));
        srcs.sort(java.util.Comparator.comparing(File::getName));

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
            int ok = 0, bad = 0, threw = 0;
            long t0 = System.nanoTime();
            for (File f : srcs) {
                long s = System.nanoTime();
                String outcome;
                try {
                    var r = engine.compile(f.toPath());
                    outcome = r.isSuccess() ? "OK" : "FAIL(" + r.diagnostics().size() + ")";
                    if (r.isSuccess()) { ok++; } else { bad++; }
                } catch (Throwable t) {
                    outcome = "THREW " + t.getClass().getSimpleName() + ": " + t.getMessage();
                    threw++;
                }
                System.out.println(String.format("SEQ %-28s %6d ms  %s",
                        f.getName(), (System.nanoTime() - s) / 1_000_000, outcome));
            }
            System.out.println(String.format("SEQ TOTAL modules=%d ok=%d fail=%d threw=%d wall=%d ms",
                    srcs.size(), ok, bad, threw, (System.nanoTime() - t0) / 1_000_000));

            // A bad input must produce diagnostics, never an exception out of the compiler. This is
            // the invariant that "Cannot find a module" used to break: it left a compiler short of
            // the stage the next phase asserted, and generateCode threw IllegalStateException.
            assertEquals(0, threw, "compiling a module threw instead of reporting diagnostics");
            assertTrue(ok >= 40, "expected at least 40 of the suite to compile, got " + ok);
        }
    }
}
