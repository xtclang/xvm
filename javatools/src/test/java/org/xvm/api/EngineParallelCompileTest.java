package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

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
     * @return true iff this failure was diagnosed and fixed, so its return is a regression
     *
     * <p>"Mack module (javatools_turtle) is missing" meant a compile got a library module whose
     * pool had no NakedRef type. Preparation had injected it, but preparation MODIFIES the module,
     * and an on-disk repository re-reads a modified module from disk - silently replacing the
     * prepared structure with a fresh one. Fixed by holding the prepared instances; see
     * XtcEngine.ensureLibraryPrepared.
     *
     * <p>OutOfMemoryError belongs here too: it was never a leak, it was Gradle's 512m default for
     * test workers, and -PtestMaxHeap exists so it cannot come back as a mystery.
     */
    private static boolean isFixedAndMustNotReturn(String failure) {
        return failure.contains("Mack module") || failure.contains("OutOfMemoryError");
    }

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
        var failures   = new TreeMap<String, Integer>();

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
            for (int iter = 1; iter <= iterations; iter++) {
                ExecutorService pool = Executors.newFixedThreadPool(8);
                var outcomes = new ConcurrentSkipListMap<String, String>();
                long t0 = System.nanoTime();
                var futures = new ArrayList<Future<?>>();
                for (File f : srcs) {
                    futures.add(pool.submit(() -> {
                        try {
                            var r = engine.compile(f.toPath());
                            outcomes.put(f.getName(), r.isSuccess() ? "OK"
                                    : "FAIL(" + r.diagnostics().size() + ") "
                                      + r.diagnostics().stream().map(String::valueOf)
                                            .collect(Collectors.joining("; ")));
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
                pool.shutdown();

                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.print("PAR caches after iter=" + iter + "\n" + engine.cacheReport());
                long ok = outcomes.values().stream().filter("OK"::equals).count();
                System.out.println("PAR iter=" + iter + " modules=" + srcs.size() + " ok=" + ok
                        + " notok=" + (srcs.size() - ok) + " wall=" + ms + " ms (8 threads)");
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

        // Assert what is FIXED; report what is not. Concurrent compilation against a shared library
        // is not correct yet - docs/reentrancy/plans/parallel-compiler-plan.md tracks the open
        // residuals (VERIFY-11 contribution cycles, ConcurrentModificationException, an NPE on
        // m_FVisited) - and asserting a clean sweep would leave this test permanently red, which
        // teaches everyone to ignore it. So it guards the classes that ARE resolved and prints the
        // rest, and the distribution above is the record of where the work stands.
        var regressions = failures.keySet().stream()
                .filter(EngineParallelCompileTest::isFixedAndMustNotReturn)
                .toList();
        assertEquals(List.of(), regressions, "a failure class that was fixed has come back");
    }
}
