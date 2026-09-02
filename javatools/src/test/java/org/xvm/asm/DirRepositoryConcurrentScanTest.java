package org.xvm.asm;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression / red-on-master proof for the DirRepository scan-cache race.
 *
 * <p>{@code DirRepository} caches its directory scan in a plain {@code HashMap modulesByFile} (a
 * non-final field reassigned by {@code rebuildCache}) and a plain {@code TreeMap modulesByName}
 * (rebuilt with {@code clear()} + {@code put()}). On master none of {@code loadModule}/
 * {@code getModuleNames}/{@code ensureCache}/{@code rebuildCache} is synchronized, so concurrent
 * callers race {@code clear()}/{@code put()} against {@code get()}/iteration. A fresh repository
 * starts with {@code lastScan == 0}, so the first concurrent access has every thread run
 * {@code rebuildCache} on the same maps at once. Two concurrent container-0 compiles reach exactly
 * this path.</p>
 *
 * <p><b>Proven red on master</b> {@code 82683bcd2}: this exact test throws
 * {@code java.util.ConcurrentModificationException} (verified in a master worktree with
 * master-built modules parsed into the cache). It passes here because
 * {@code DirRepository.loadModule}/{@code getModuleNames}/{@code storeModule} are {@code
 * synchronized} on this branch (the coarse per-repository lock is the correct granularity for a
 * lookup cache).</p>
 */
public class DirRepositoryConcurrentScanTest {
    private static final File LIB =
            new File("xdk/build/install/xdk/lib").isDirectory()
                    ? new File("xdk/build/install/xdk/lib")
                    : new File("../xdk/build/install/xdk/lib");

    @Test
    public void concurrentScanDoesNotCorruptTheCache() throws Exception {
        assumeTrue(LIB.isDirectory(), "need built .xtc modules to feed the repository");
        File[] xtc = LIB.listFiles((d, n) -> n.endsWith(".xtc"));
        assumeTrue(xtc != null && xtc.length >= 4, "need several .xtc modules");

        Path dir    = Files.createTempDirectory("dirrepo-race");
        int  copied = 0;
        for (File f : xtc) {
            if (f.getName().equals("ecstasy.xtc")) {
                continue;   // skip the big one to keep per-scan parse cost low
            }
            Files.copy(f.toPath(), dir.resolve(f.getName()));
            if (++copied >= 6) {
                break;
            }
        }
        assumeTrue(copied >= 4, "need several small .xtc modules");

        int threads = 8;
        var pool    = Executors.newFixedThreadPool(threads);
        var failure = new AtomicReference<Throwable>();

        try {
            for (int iter = 0; iter < 80 && failure.get() == null; iter++) {
                var repo    = new DirRepository(dir.toFile(), true);
                var start   = new CountDownLatch(1);
                var futures = new ArrayList<Future<?>>();
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        // First concurrent call -> every thread runs rebuildCache (clear()+put())
                        // on the shared modulesByName TreeMap; iterating the keySet view races it.
                        int seen = 0;
                        for (int i = 0; i < 8; i++) {
                            for (String name : repo.getModuleNames()) {
                                seen += name.length();
                            }
                        }
                        return seen;
                    }));
                }
                start.countDown();
                for (Future<?> f : futures) {
                    try {
                        f.get(20, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        failure.compareAndSet(null, e.getCause());
                    } catch (TimeoutException e) {
                        failure.compareAndSet(null, new AssertionError(
                                "a scan thread hung (corrupted HashMap infinite loop?)", e));
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        Throwable t = failure.get();
        if (t != null) {
            fail("concurrent DirRepository scan corrupted the cache: " + t, t);
        }
    }
}
