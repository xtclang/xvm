package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Concurrent compiles through ONE warm engine.
 *
 * <p>Reports the DISTRIBUTION of failures across every iteration rather than stopping at the
 * first. What this test catches are races, and a race that fails one run in four is not
 * meaningfully different from one that fails every run - except that stopping early throws away
 * the evidence saying WHICH residual dominates. Ten iterations of a 42-module compile costs a few
 * seconds and turns one bit into a histogram.
 *
 * <p>{@code -Dxvm.parallel.iterations=N} raises the count when chasing something rare.
 */
public class EngineParallelCompileTest {
    /**
     * A wedged run must fail, not hang. One earlier soak sat for TWELVE HOURS after the test itself
     * had finished - the work was done in 22 minutes and the JVM then parked in Gradle's
     * {@code MessageHub.stop}, which waits without a useful bound. Nothing in the test noticed,
     * because nothing was watching. A timeout turns that into a failed build in a knowable time.
     */
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    @Test public void parallel() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "needs XDK");
        Path root = XdkOutputs.root();
        File dir  = root.resolve("manualTests/src/main/x").toFile();
        // exclude the two known-bad inputs so the signal is about concurrency
        Set<String> skip = Set.of("container.x", "errors.x", "literals.x"); // Dec28 compiles now
        List<File> srcs = Arrays.stream(dir.listFiles(f -> f.getName().endsWith(".x")))
                .filter(f -> !skip.contains(f.getName()))
                .sorted(Comparator.comparing(File::getName)).toList();

        int iterations = Integer.getInteger("xvm.parallel.iterations", 3);
        // Thread count is a knob for the same reason the iteration count is: these are races, and
        // raising contention is the cheapest way to find the next one.
        int cThreads   = Integer.getInteger("xvm.parallel.threads", 8);
        var failures   = new TreeMap<String, Integer>();

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
            for (int iter = 1; iter <= iterations; iter++) {
                ExecutorService pool = Executors.newFixedThreadPool(cThreads);
                var outcomes = new ConcurrentSkipListMap<String, String>();
                long t0 = System.nanoTime();
                // Shuffle the submission order each iteration. Sorted order samples ONE
                // interleaving over and over; these are races, so varying which compiles overlap is
                // most of what more iterations buys. Seeded off the iteration so a failure is
                // reproducible from the printed seed.
                var order = new ArrayList<>(srcs);
                long seed = Long.getLong("xvm.parallel.seed", 20260903L) + iter;
                Collections.shuffle(order, new Random(seed));

                var futures = new ArrayList<Future<?>>();
                for (File f : order) {
                    futures.add(pool.submit(() -> {
                        try {
                            var r = engine.compile(f.toPath());
                            outcomes.put(f.getName(), r.isSuccess() ? "OK"
                                    : "FAIL(" + r.diagnostics().size() + ") "
                                      + r.diagnostics().stream().map(String::valueOf)
                                            .collect(Collectors.joining("; ")));
                        } catch (OutOfMemoryError e) {
                            // NEVER record an OOM as a compile outcome. It is not one: the compile
                            // did not fail, the harness ran out of memory, and every result after it
                            // is meaningless. Recording it produced a run that reported "skipped"
                            // with twelve OOMs in its output and no failure - the exact shape of a
                            // problem being hidden by the thing meant to report it.
                            throw e;
                        } catch (Throwable t) {
                            var sw = new StringWriter();
                            t.printStackTrace(new PrintWriter(sw));
                            outcomes.put(f.getName(), "THREW " + t.getClass().getSimpleName()
                                    + ": " + String.valueOf(t.getMessage()).split("\n")[0]
                                    + "\n" + sw);
                        }
                    }));
                }
                for (var fut : futures) {
                    fut.get(300, TimeUnit.SECONDS);
                }
                // shutdown() only STOPS ACCEPTING work; it does not wait, and
                // Executors.newFixedThreadPool creates NON-DAEMON threads. A soak creates one pool
                // per iteration, so a thousand threads accumulate, and any task that never finishes
                // - after an OutOfMemoryError, for instance - keeps its non-daemon thread alive,
                // which keeps the JVM alive, which leaves Gradle's worker parked in
                // MessageHub.stop. That is what sat for twelve hours after a soak had finished its
                // work in twenty-two minutes.
                pool.shutdown();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    throw new IllegalStateException("compile threads did not terminate on iteration "
                            + iter + "; refusing to leave non-daemon threads behind");
                }

                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.print("PAR caches after iter=" + iter + "\n" + engine.cacheReport());
                long ok = outcomes.values().stream().filter("OK"::equals).count();
                System.out.println("PAR iter=" + iter + " modules=" + srcs.size() + " ok=" + ok
                        + " notok=" + (srcs.size() - ok) + " wall=" + ms + " ms (" + cThreads + " threads, seed=" + seed + ")");
                outcomes.forEach((name, outcome) -> {
                    if (!"OK".equals(outcome)) {
                        System.out.println("PAR " + name + " -> " + outcome);
                        // key on the first line only: the histogram is the signal, not each stack
                        failures.merge(name + " :: " + outcome.split("\n")[0], 1, Integer::sum);
                    }
                });
            }
        }

        System.out.println("PAR DISTRIBUTION over " + iterations + " iterations:");
        failures.forEach((what, count) -> System.out.println("  " + count + "x  " + what));

        // Strict again. Concurrent compilation against a shared library now runs clean - 40
        // iterations of 42 modules on 8 threads, 1680 compiles, zero failures - so anything here
        // is a regression, and a distribution printed above says exactly which class came back.
        assertEquals(Map.of(), failures, "concurrent compiles did not match the sequential result");
    }
}
