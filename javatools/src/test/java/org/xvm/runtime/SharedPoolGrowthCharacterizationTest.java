package org.xvm.runtime;


import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Characterizes the #2 shared-pool memory leak (issue 543 §1c): does the SHARED native
 * ConstantPool grow, unbounded, as a long-running host runs many DISTINCT-typed modules over one
 * warm plane? This is the acceptance gate for the frozen-base+annex Phase C
 * (constant-pool-freeze-annex-design.md): once interning is routed into an evictable per-run annex,
 * the shared base must stay bounded and this characterization becomes a bounded-growth regression
 * test. Until then it is a measurement, printed for the record - it asserts nothing about the
 * magnitude, only reports it.
 */
public class SharedPoolGrowthCharacterizationTest {
    private static final int RUNS = 12;

    @Test
    public void measuresSharedNativePoolGrowthOverDistinctRuns() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        Path dirWork = Files.createTempDirectory("pool-growth");
        Path dirOut  = Files.createDirectory(dirWork.resolve("lib"));

        var runtime = new Runtime();
        try {
            runtime.start();
            var          containerNative = NativeContainer.create(runtime, systemRepo(), ErrorListener.RUNTIME);
            ConstantPool sharedPool      = containerNative.getConstantPool();

            int   baseline = sharedPool.size();
            int[] sizes    = new int[RUNS];

            for (int i = 0; i < RUNS; i++) {
                String sModule = "PoolGrowth" + i;
                EmbeddingTestSupport.compile(dirWork, dirOut, sModule, distinctTupleModule(sModule, i + 1));
                runNested(containerNative, dirOut, sModule);
                sizes[i] = sharedPool.size();
            }

            // characterization output (visible in the test's captured stdout)
            System.out.println("=== shared native ConstantPool.size() across " + RUNS
                    + " distinct-typed runs ===");
            System.out.println("baseline (before any run): " + baseline);
            int prev = baseline;
            for (int i = 0; i < RUNS; i++) {
                System.out.printf("after run %2d (arity %2d): size=%d  (+%d)%n",
                        i, i + 1, sizes[i], sizes[i] - prev);
                prev = sizes[i];
            }
            int totalGrowth = sizes[RUNS - 1] - baseline;
            System.out.println("total growth over " + RUNS + " distinct runs: " + totalGrowth
                    + " constants (" + String.format("%.1f", (double) totalGrowth / RUNS)
                    + " per run). Monotone growth here = the #2 leak; a flat tail = bounded.");

            // PIN the #2 leak. Run 0 is one-time warmup (first-touch TypeInfo for Console/Tuple/etc.);
            // the STEADY-STATE tail (runs 1..N-1) is the leak: each DISTINCT-typed run still adds
            // permanent constants to the SHARED pool that never evict. Measured ~1-2 constants per
            // distinct type - small per run, but monotone and unbounded (a very-long-lived host that
            // sees many distinct types climbs without limit). When the frozen-base+annex Phase C
            // evicts the per-run annex, this tail goes FLAT and this assertion must be flipped to
            // assert bounded (sizes[RUNS-1] == sizes[1]).
            int steadyStateGrowth = sizes[RUNS - 1] - sizes[1];
            assertTrue(steadyStateGrowth > 0,
                    "expected the shared pool to leak (grow monotonically with distinct types) "
                    + "pre-Phase-C; steady-state growth was " + steadyStateGrowth + " over "
                    + (RUNS - 2) + " post-warmup runs");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers ------------------------------------------------------------------------------

    /** A module whose run() touches a DISTINCT parameterized tuple type (arity n), to provoke novel
     *  interning into shared pools rather than reusing the same types every run. */
    private static String distinctTupleModule(String sModule, int n) {
        var elems = new StringBuilder();
        var vals  = new StringBuilder();
        for (int i = 0; i < n; i++) {
            elems.append(i == 0 ? "Int" : ", Int");
            vals.append(i == 0 ? "0" : ", 0");
        }
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        Tuple<%s> t = (%s);
                        console.print(t.size);
                    }
                }
                """.formatted(sModule, elems, vals);
    }

    private static void runNested(NativeContainer containerNative, Path dirOut, String sModule)
            throws Exception {
        ModuleRepository repository = new LinkedRepository(
                new DirRepository(dirOut.toFile(), true), systemRepo());
        ModuleStructure moduleApp = repository.loadModule(sModule);
        FileStructure   structApp = containerNative.createFileStructure(moduleApp);
        ModuleConstant  idMissing = structApp.linkModules(repository, true);
        assertNull(idMissing, "app module must link");

        var containerNested = NestedContainer.createForHost(
                containerNative, structApp.getModuleId(), List.of());
        containerNested.runModule("run").get(30, TimeUnit.SECONDS);
    }

    private static ModuleRepository systemRepo() {
        return EmbeddingTestSupport.systemRepository();
    }
}
