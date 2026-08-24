package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runnable, output-first demonstrations of the world-state snapshot ("X-ray") format: what a
 * whole-world dump looks like for sequential and for multiple live containers, and what the
 * cross-world diff and ownership validation report. The assertions keep the demos honest; the
 * printed output is the point. Run with test stdout enabled to see the dumps:
 *
 * <pre>
 *   ./gradlew :javatools:test --tests org.xvm.runtime.WorldSnapshotDemoTest \
 *       -Porg.xtclang.java.test.stdout=true
 * </pre>
 *
 * How to read the output:
 * - "world: N container(s)" heads each snapshot; each container section lists its identity
 *   (the stable identity hash the diff keys on), name, and per-container state sweep.
 * - "ownership: valid" is the cross-container validation verdict - every reachable handle,
 *   pool, and template checked against the container that owns it; a violation prints the
 *   offending owner path instead.
 * - A diff renders "added / removed / retained" by container identity. After a completed
 *   sequential run, a RETAINED container from the previous world is the leak signal: the old
 *   world is still reachable from the new one.
 */
public class WorldSnapshotDemoTest {
    /**
     * Sequential-run demo: world before, world after a second run starts, and the diff between
     * them. The first container is deliberately kept reachable, so the diff shows it as
     * RETAINED - exactly what a leaked world looks like after a completed run.
     */
    @Test
    public void sequentialRunsWorldDumpDemo() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            banner("SEQUENTIAL RUNS: run 1 boots its world");
            var containerA = NativeContainer.create(runtime, systemRepository());
            var world1 = OwnershipDiagnostics.snapshotWorld(runtime);
            System.out.println(world1.render());
            assertTrue(world1.isValid(), () -> world1.validation().message());

            banner("SEQUENTIAL RUNS: run 2 boots; run 1's container still reachable");
            var containerB = NativeContainer.create(runtime, systemRepository());
            var world2 = OwnershipDiagnostics.snapshotWorld(runtime);
            System.out.println(world2.render());
            assertTrue(world2.isValid(), () -> world2.validation().message());

            banner("DIFF world2 - world1: 'retained' is the sequential-run leak signal");
            var diff = world2.diffFrom(world1);
            System.out.println(diff.render());
            System.out.println();
            System.out.println("retained container identity "
                    + diff.retained().get(0).identity()
                    + " is run 1's world surviving into run 2: after a completed run this"
                    + " means the old world is still reachable and its container, pools,"
                    + " and handles cannot be collected");
            assertEquals(1, diff.added().size());
            assertEquals(1, diff.retained().size());
            assertEquals(0, diff.removed().size());
            assertEquals(System.identityHashCode(containerA),
                    diff.retained().get(0).identity());
            assertEquals(System.identityHashCode(containerB),
                    diff.added().get(0).identity());
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * Multiple-container demo: two live containers in one world, the full dump listing both,
     * and the cross-container ownership sweep validating that neither world's state leaked
     * into the other.
     */
    @Test
    public void multipleContainersWorldDumpDemo() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var containerA = NativeContainer.create(runtime, systemRepository());
            var containerB = NativeContainer.create(runtime, systemRepository());

            banner("MULTIPLE CONTAINERS: one world, two containers, one consistency sweep");
            var world = OwnershipDiagnostics.snapshotWorld(runtime);
            System.out.println(world.render());
            System.out.println();
            System.out.println("'ownership: valid' above is the cross-container consistency"
                    + " check: every handle, pool, and template reachable from either"
                    + " container was verified to belong to it - a foreign-owner reference"
                    + " (container A state reachable from container B) would print the"
                    + " owner path here and fail this test");

            assertEquals(2, world.containers().size());
            assertTrue(world.isValid(), () -> world.validation().message());
            assertTrue(world.containers().stream()
                            .anyMatch(c -> c.identity() == System.identityHashCode(containerA)));
            assertTrue(world.containers().stream()
                            .anyMatch(c -> c.identity() == System.identityHashCode(containerB)));
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static void banner(String text) {
        System.out.println();
        System.out.println("========================================================================");
        System.out.println("== " + text);
        System.out.println("========================================================================");
    }

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
                .map(WorldSnapshotDemoTest::repositoryFor)
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
