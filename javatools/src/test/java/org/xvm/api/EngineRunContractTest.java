package org.xvm.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The contract a resident host depends on: compile and run repeatedly in one warm JVM without the
 * runs interfering, and without the shared plane growing without bound.
 *
 * <p>These are the scenarios the {@code cpurdy/LSPAPI} branch reproduces in {@code LspTest}, written
 * as tests rather than as a reproducer. That distinction is the point of this class:
 * {@code LspTest} has no JUnit, is referenced by no build file or workflow, signals failure by
 * throwing {@code IllegalStateException}, and - in the case that matters most - prints the shared
 * pool size twelve times and never checks it. A number nothing asserts cannot fail a build, which
 * is how unbounded growth stays invisible until a host dies of it.
 *
 * <p>See {@code docs/reentrancy/lspapi-integration-analysis.md} Part 5b.
 */
public class EngineRunContractTest {
    /** A module that prints one line, parameterised so each is distinct. */
    private static String helloModule(String name) {
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        console.print("hello from %s");
                    }
                }
                """.formatted(name, name);
    }

    // ----- repeated sequential runs stay correct on one warm engine ------------------------------

    /**
     * Their {@code testRunLatency}, with the timing kept as a report and the CORRECTNESS asserted.
     * Five sequential runs of one module through one engine: every run must complete, and none may
     * fail because an earlier one left something behind.
     */
    @Test
    public void repeatedSequentialRunsAllCompleteOnOneWarmEngine() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(XtcEngineTest.xdkModulePath()).build()) {
            var compiled = engine.compile("Quick", helloModule("Quick"));
            assertTrue(compiled.isSuccess(),
                    () -> "compile should succeed, diagnostics: " + compiled.diagnostics());

            var elapsed = new ArrayList<Long>();
            for (int run = 1; run <= 5; run++) {
                long t0 = System.nanoTime();
                CompletableFuture<?> future = engine.run(compiled, "Quick");
                future.get(30, TimeUnit.SECONDS);
                elapsed.add((System.nanoTime() - t0) / 1_000_000);

                int runNumber = run;
                assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                        () -> "run " + runNumber + " of 5 must complete cleanly on the warm engine");
            }
            System.out.println("sequential run latencies (ms): " + elapsed);
        }
    }

    // ----- an application exception must reach the host -----------------------------------------

    /**
     * Their {@code testRunException}. A module that throws must not fail silently: the host has to
     * be able to tell a crashed run from a successful one, or a tool reports success for a program
     * that died.
     */
    @Test
    public void anApplicationExceptionIsVisibleToTheHost() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(XtcEngineTest.xdkModulePath()).build()) {
            var compiled = engine.compile("Crasher", """
                    module Crasher {
                        void run() {
                            @Inject Console console;
                            console.print("Crasher.run about to throw");
                            throw new IllegalState("deliberate failure the host must learn about");
                        }
                    }
                    """);
            assertTrue(compiled.isSuccess(),
                    () -> "compile should succeed, diagnostics: " + compiled.diagnostics());

            CompletableFuture<?> future = engine.run(compiled, "Crasher");
            Throwable failure = null;
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failure = e.getCause();
            }

            // Asserting the OBSERVABLE contract, not a particular mechanism: the host must be able
            // to distinguish this from a clean run. If a future implementation reports the failure
            // through a Control or a listener instead, this assertion is what has to be updated -
            // deliberately, rather than a run that crashed being silently indistinguishable.
            assertNotNull(failure,
                    "a module that throws must not look like a successful run to the host");
        }
    }

    // ----- the shared plane must not grow without bound ------------------------------------------

    /**
     * Their {@code testPoolGrows}, with an assertion. Each run uses a DISTINCT parameterized type,
     * so every one interns novel constants into the shared native pool - the "many different generic
     * shapes over one long session" case a resident host actually faces.
     *
     * <p>The bound is deliberately generous. The claim being defended is not "growth is zero" -
     * interning genuinely reaches the shared plane - but that growth per run FALLS as the shapes
     * become familiar, rather than continuing linearly forever. A linear-forever pool is how a host
     * dies after enough requests, and it is exactly what a printed number never catches.
     */
    @Test
    public void theSharedPlaneStopsGrowingAsShapesRepeat() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        String[] elemTypes = {"Int8", "Int16", "Int32", "Int64", "UInt8", "UInt16"};

        try (var engine = XtcEngine.builder().modulePath(XtcEngineTest.xdkModulePath()).build()) {
            var sizes = new ArrayList<Integer>();
            for (int i = 0; i < 12; i++) {
                String name = "Grow" + i;
                var compiled = engine.compile(name, """
                        module %s {
                            void run() {
                                %s[] values = [];
                                @Inject Console console;
                                console.print(values.size);
                            }
                        }
                        """.formatted(name, elemTypes[i % elemTypes.length]));
                assertTrue(compiled.isSuccess(),
                        () -> "compile of " + name + " failed: " + compiled.diagnostics());

                engine.run(compiled, name).get(30, TimeUnit.SECONDS);
                sizes.add(engine.diagnosticContainer().getConstantPool().size());
            }

            System.out.println("native pool sizes across runs: " + sizes);

            // Growth over the SECOND half must not exceed growth over the first: once the generic
            // shapes have been seen, repeating them must cost progressively less.
            int firstHalf  = sizes.get(5) - sizes.getFirst();
            int secondHalf = sizes.getLast() - sizes.get(5);
            assertTrue(secondHalf <= firstHalf,
                    () -> "the shared native pool is still growing as fast at the end as at the"
                            + " start, which is unbounded growth rather than warm-up: sizes="
                            + sizes);
        }
    }
}
