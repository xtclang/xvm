package org.xvm.runtime;


import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Two runs of one module, on one engine, can be given different injected values.
 *
 * <p>This is the property a resident host needs and a process-wide configuration cannot offer: the
 * upstream {@code LspSupport} is a singleton configured once, so every run it starts sees the same
 * host settings. Here the values are registered on the run's own container through
 * {@code NestedContainer.registerHostResource}, so they take precedence over the native plane,
 * they die with the container, and one run cannot see another's.</p>
 *
 * <p>The module asserts the value it was given, so a run that received the wrong one - or the
 * other run's - fails rather than reporting a value nobody checks.</p>
 */
public class PerRunInjectionTest {
    @Test
    public void twoRunsOfOneModuleSeeTheirOwnInjections() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        String sSource = """
                module InjectedProbe {
                    @Inject("label") String label;
                    void run() {
                        @Inject Console console;
                        console.print($"label={label}");
                        assert label == "first" || label == "second";
                    }
                }
                """;

        var root = XdkOutputs.root();
        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
            var result = engine.compile("InjectedProbe", sSource);
            assertTrue(result.isSuccess(), () -> "compile failed: " + result.diagnostics());

            assertNotNull(engine.run(result, "InjectedProbe",
                    Map.of("label", List.of("first"))).get());
            assertNotNull(engine.run(result, "InjectedProbe",
                    Map.of("label", List.of("second"))).get());
        }
    }
}
