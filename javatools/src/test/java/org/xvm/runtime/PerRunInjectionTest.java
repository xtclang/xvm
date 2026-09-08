package org.xvm.runtime;


import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;
import org.xvm.api.XtcEngine.Injection;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Two runs on one engine must each see their own injected values.
 *
 * <p>This is the property a resident host needs and a process-wide configuration cannot offer.
 * {@code TaskResourceProvider} extends {@code BasicResourceProvider}, whose {@code String} case
 * forwards to the parent - so without the runner's per-task {@code case (String, _)}, two runs
 * asking for the same name both resolve against container zero and see one value.
 *
 * <p><b>The discriminator has to be outside the module.</b> An earlier version of this test ran one
 * module twice and asserted {@code label == "first" || label == "second"}, which cannot fail on a
 * leak: if the second run saw the first's value, {@code "first"} still satisfies the disjunction. A
 * module cannot tell which run it is, so the expectation has to be baked into the source - hence two
 * modules, each asserting its own value, each run with the injections it expects.
 */
public class PerRunInjectionTest {
    @Test
    public void twoRunsOnOneEngineEachSeeTheirOwnInjections() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        try (var engine = engine()) {
            var first  = compileExpecting(engine, "InjectFirst",  "first");
            var second = compileExpecting(engine, "InjectSecond", "second");

            assertNotNull(engine.run(first, "InjectFirst", new Injection("label", "first")).get());

            // the same NAME again, and it must resolve to this run's value rather than the first's
            assertNotNull(engine.run(second, "InjectSecond", new Injection("label", "second")).get());
        }
    }

    /**
     * What makes the test above capable of failing: a run given the wrong value does fail. Without
     * this, a leak - which delivers exactly that wrong value - could pass unnoticed.
     */
    @Test
    public void aRunGivenTheWrongValueFails() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        try (var engine = engine()) {
            var module = compileExpecting(engine, "InjectMismatch", "expected");

            var future = engine.run(module, "InjectMismatch", new Injection("label", "not-expected"));

            assertThrows(ExecutionException.class, future::get,
                    "a module whose injected value is wrong must fail the run");
        }
    }

    private static XtcEngine engine() {
        var root = XdkOutputs.root();
        return XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build();
    }

    private static XtcEngine.CompileResult compileExpecting(XtcEngine engine, String sName,
                                                            String sExpect) {
        var result = engine.compile(sName, """
                module %s {
                    @Inject("label") String label;
                    void run() {
                        assert label == "%s";
                    }
                }
                """.formatted(sName, sExpect));
        assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());
        return result;
    }
}
