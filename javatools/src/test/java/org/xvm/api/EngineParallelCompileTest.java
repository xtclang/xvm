package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.xvm.test.XdkOutputs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Concurrent compiles through ONE warm engine. Reports what breaks. */
public class EngineParallelCompileTest {
    @Test public void parallel() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "needs XDK");
        Path root = XdkOutputs.root();
        File dir  = root.resolve("manualTests/src/main/x").toFile();
        // exclude the two known-bad inputs so the signal is about concurrency
        Set<String> skip = Set.of("container.x", "errors.x", "literals.x"); // Dec28 compiles now
        List<File> srcs = Arrays.stream(dir.listFiles(f -> f.getName().endsWith(".x")))
                .filter(f -> !skip.contains(f.getName()))
                .sorted(Comparator.comparing(File::getName)).toList();

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
          for (int iter = 1; iter <= 3; iter++) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            var outcomes = new ConcurrentSkipListMap<String, String>();
            long t0 = System.nanoTime();
            var futures = new ArrayList<Future<?>>();
            for (File f : srcs) {
                futures.add(pool.submit(() -> {
                    try {
                        var r = engine.compile(f.toPath());
                        outcomes.put(f.getName(), r.isSuccess() ? "OK"
                                : "FAIL(" + r.diagnostics().size() + ")\n    "
                                  + r.diagnostics().stream().map(String::valueOf)
                                        .collect(java.util.stream.Collectors.joining("\n    ")));
                    } catch (Throwable t) {
                        var sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw));
                        outcomes.put(f.getName(), "THREW " + t.getClass().getSimpleName()
                                + ": " + String.valueOf(t.getMessage()).split("\n")[0]
                                + "\n" + sw);
                    }
                }));
            }
            for (var fut : futures) { fut.get(300, TimeUnit.SECONDS); }
            pool.shutdown();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            long ok = outcomes.values().stream().filter("OK"::equals).count();
            System.out.println("PAR iter=" + iter + " modules=" + srcs.size() + " ok=" + ok
                    + " notok=" + (srcs.size() - ok) + " wall=" + ms + " ms (8 threads)");
            outcomes.forEach((k, v) -> { if (!"OK".equals(v)) System.out.println("PAR " + k + " -> " + v); });
            assertEquals(srcs.size(), ok, "a concurrent compile did not match the sequential result"
                    + " on iteration " + iter + ": " + outcomes);
            outcomes.clear();
          }
        }
    }
}
