package org.xvm.api;


import java.util.List;
import java.util.Map;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static org.xvm.api.XtcEngineTest.xdkModulePath;


/**
 * Injections granted to one run must not be visible to another.
 *
 * <p>They very nearly were. {@code TaskResourceProvider} extends {@code BasicResourceProvider},
 * whose {@code String} case forwards to the parent - so without a per-task case, two runs asking for
 * the same injection name both resolve against container zero and see one value. The runner answers
 * {@code case (String, _)} from the task's own name/value arrays for exactly this reason.
 *
 * <p>The discriminator has to be OUTSIDE the module: two runs of one source cannot tell which run
 * they are. So this compiles two modules that each assert their own expected value, and runs each
 * with the injections it expects. If the values leaked, the second run would see the first's value
 * and its assert would fail - which surfaces here as an exceptionally-completed future, because
 * {@code resultOf} throws when the runner reports a failure.
 */
public class EnginePerRunInjectionTest {
    @Test
    public void twoRunsOfTheSameEngineEachSeeTheirOwnInjections() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var first  = compileEcho(engine, "InjectFirst",  "alpha");
            var second = compileEcho(engine, "InjectSecond", "beta");

            // first run establishes a value for the name "tag"
            engine.run(first, "InjectFirst", Map.of("tag", List.of("alpha")))
                    .get(30, TimeUnit.SECONDS);

            // second run asks for the SAME name and must get its own value, not the first's
            engine.run(second, "InjectSecond", Map.of("tag", List.of("beta")))
                    .get(30, TimeUnit.SECONDS);
        }
    }

    /**
     * The negative half: if a run is given the wrong value its assert fails, so the test above is
     * actually capable of detecting a leak rather than passing because nothing is checked.
     */
    @Test
    public void aRunGivenTheWrongValueFails() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        try (var engine = XtcEngine.builder().modulePath(xdkModulePath()).build()) {
            var module = compileEcho(engine, "InjectMismatch", "expected");

            var future = engine.run(module, "InjectMismatch", Map.of("tag", List.of("something-else")));

            assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS),
                    "a module whose injected value is wrong must fail the run");
        }
    }

    /**
     * A module that asserts its injected {@code tag} equals the value baked into its source.
     */
    private static XtcEngine.CompileResult compileEcho(XtcEngine engine, String sName, String sExpect) {
        var compiled = engine.compile(sName, """
                module %s {
                    void run() {
                        @Inject String tag;
                        assert tag == "%s";
                    }
                }
                """.formatted(sName, sExpect));
        assertTrue(compiled.isSuccess(), () -> "diagnostics: " + compiled.diagnostics());
        return compiled;
    }
}
