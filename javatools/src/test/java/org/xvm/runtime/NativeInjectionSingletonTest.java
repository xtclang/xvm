package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the must-fix graduated by the 2026-08-25 manual lazy-cache completion sweep
 * (master-issue submissions row 25): native injection {@code ensure*} caches live on SHARED
 * template instances (a container delegates shared types to its parent, so one template serves
 * every injecting caller), their suppliers are non-idempotent - each cache miss calls
 * {@code Container.createServiceContext} - and the old plain double-read lazy writes let racing
 * first injections each create a service, with every loser staying registered forever (two
 * Consoles, two Clocks...). The fix is per-shape: DCL over volatile fields for the synchronous
 * sites, first-wins publication with loser-context termination for the construct-request sites,
 * and no frame-bound DeferredCallHandle is ever cached. This test races first injection on a
 * synchronous site and asserts ONE identity and EXACTLY ONE new service registration. Red on
 * the old shape: the latch-started racers each created a service context, so the registry grew
 * by more than one and callers observed different handles.
 */
public class NativeInjectionSingletonTest {
    @Test
    public void racingFirstInjectionsCreateExactlyOneService() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var template  = NativeTemplates.get(container).injector();

            int cBefore = container.getServices().size();

            try (var executor = Executors.newFixedThreadPool(8)) {
                var start   = new CountDownLatch(1);
                var futures = IntStream.range(0, 8)
                        .mapToObj(n -> executor.submit(() -> {
                            start.await();
                            return template.ensureInjector(null, null);
                        }))
                        .toList();
                start.countDown();

                var first = futures.get(0).get(30, TimeUnit.SECONDS);
                for (var future : futures) {
                    assertSame(first, future.get(30, TimeUnit.SECONDS),
                            "racing first injections must observe one Injector identity");
                }
            }

            assertEquals(cBefore + 1, container.getServices().size(),
                    "exactly one Injector service may be registered; a losing racer's duplicate"
                            + " context must never survive");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers (same discovery as ArrayViewGuardTest) ---------------------------------------

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var manualRepository = repositoryFor("manualTests/build/xtc/xdk/lib");
        if (manualRepository != null) {
            return manualRepository;
        }

        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(NativeInjectionSingletonTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.get(0);
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory()
                ? new DirRepository(directory, true)
                : null;
    }

    private static File checkoutFile(String path) {
        return checkoutRoot().resolve(path).toFile();
    }

    private static Path checkoutRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("javatools")) &&
                    Files.isDirectory(path.resolve("manualTests"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate checkout root");
    }
}
