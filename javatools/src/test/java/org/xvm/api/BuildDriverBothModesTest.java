package org.xvm.api;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The same build, scheduled two ways, must produce the same answer.
 *
 * <p>That is the whole claim of the driver's design - the dependency graph and the code are identical and only
 * the executor differs - so it is worth an assertion rather than an observation. A difference between the two
 * is a concurrency defect, and this is where it would show.</p>
 */
public class BuildDriverBothModesTest {
    @Test
    public void sequentialAndParallelAgree() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");
        // These build the XDK from source, repeatedly, on one engine. Without the heap for it the
        // failure is an OutOfMemoryError from inside a compile, which reads as a compiler defect
        // rather than as "you did not give this enough memory". Skip, and say which flag.
        assumeTrue(Runtime.getRuntime().maxMemory() > 4L << 30,
                "needs a large heap; run with -PtestMaxHeap=8g");

        Path root = XdkOutputs.root();
        var  nodes = XdkBuildHarnessTest.discoverForTest(root).stream()
                .map(n -> new BuildDriver.Module(n.moduleName(), n.label(), n.source(), n.resourceDirs(), n.deps()))
                .toList();
        assumeTrue(nodes.size() > 5, "expected the XDK library sources");

        ModuleRepository library = new LinkedRepository(true,
                new DirRepository(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("javatools_bridge/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/javatools").toFile(), true));

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {

            BuildDriver.Result sequential;
            try (var single = Executors.newSingleThreadExecutor()) {
                sequential = BuildDriver.build(engine, library, nodes, single, false);
            }
            BuildDriver.Result parallel;
            try (var virtual = Executors.newVirtualThreadPerTaskExecutor()) {
                parallel = BuildDriver.build(engine, library, nodes, virtual, false);
            }

            System.out.println("=== sequential ===");
            System.out.print(sequential.render());
            System.out.println("=== parallel ===");
            System.out.print(parallel.render());
            System.out.printf("speedup: %.2fx%n",
                    sequential.wall().toNanos() / (double) Math.max(parallel.wall().toNanos(), 1));

            assertTrue(sequential.isSuccess(), () -> "sequential build failed:\n" + sequential.render());
            assertTrue(parallel.isSuccess(), () -> "parallel build failed:\n" + parallel.render());
            assertEquals(labels(sequential), labels(parallel),
                    "the two schedules did not build the same set of modules");
        }
    }

    /** sorted, because completion ORDER legitimately differs between the two schedules */
    private static List<String> labels(BuildDriver.Result result) {
        return result.outcomes().stream()
                .filter(o -> o.status() == BuildDriver.Status.BUILT)
                .map(BuildDriver.Outcome::label).sorted().collect(Collectors.toList());
    }
}
