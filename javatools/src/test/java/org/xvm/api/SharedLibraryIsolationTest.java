package org.xvm.api;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The invariant a shared library has to satisfy: <b>nothing in it may refer to a per-request
 * constant.</b>
 *
 * <p>Serving one prepared library to many compiles is only sound if the library stays request-
 * independent. If a library structure retains a compile's constant, that entry outlives the request
 * that made it, and the next request reading it walks into a pool that is not its own - which is how
 * one compile ends up interning into another compile's pool while that pool is being written.
 *
 * <p>This asserts the invariant directly rather than inferring it from whether compiles happen to
 * pass, because the failure it guards against is silent for a long time and then produces a
 * malformed module far away from the cause.
 *
 * <p>Cross-MODULE references inside the library are NOT violations and are the normal case - a
 * flattened TypeInfo contains everything the type inherits, most of it declared in {@code ecstasy}.
 * An earlier version of this check counted those and reported hundreds of false positives; that they
 * were stable across iterations, where real pollution would grow with the number of requests, is
 * what gave it away.
 */
public class SharedLibraryIsolationTest {
    @Test
    public void libraryHoldsNothingFromAnyRequest() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "needs XDK");
        Path root = XdkOutputs.root();
        File dir  = root.resolve("manualTests/src/main/x").toFile();
        List<File> srcs = Arrays.stream(dir.listFiles(f -> f.getName().endsWith(".x")))
                .sorted(Comparator.comparing(File::getName)).limit(12).toList();

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {

            ExecutorService pool = Executors.newFixedThreadPool(6);
            var futures = new ArrayList<Future<?>>();
            for (File f : srcs) {
                futures.add(pool.submit(() -> {
                    try {
                        engine.compile(f.toPath());
                    } catch (Throwable ignoredHere) {
                        // Compile outcomes are EngineParallelCompileTest's subject. This test is
                        // about what the library retains afterwards, which is worth checking even -
                        // especially - when a compile failed part way through.
                    }
                }));
            }
            for (var fut : futures) {
                fut.get(300, TimeUnit.SECONDS);
            }
            pool.shutdown();

            // cacheReport prints "outsideLib=N refsOut=N" per library module; both must be zero.
            String report = engine.cacheReport();
            var violations = report.lines()
                    .filter(line -> line.contains("outsideLib=") || line.contains("refsOut="))
                    .filter(SharedLibraryIsolationTest::holdsRequestData)
                    .toList();

            assertEquals(List.of(), violations,
                    "a shared library structure retained a per-request constant:\n" + report);
        }
    }

    /**
     * @param line  one module's line from the cache report
     *
     * @return true iff the line reports a non-zero count of anything outside the library
     */
    private static boolean holdsRequestData(String line) {
        return !line.contains("outsideLib=0 ") || !line.contains("refsOut=0 ");
    }
}
