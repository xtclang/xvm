package org.xvm.runtime;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * An injected filesystem resource belongs to the container that asked for it.
 *
 * <p>The defect this pins (issue #576) needs two containers that are siblings under one native
 * plane - neither is the other's ancestor - and it reproduces on unmodified master as soon as a
 * host builds that topology, which master's own CLI never does but its public {@code MainContainer}
 * constructor permits. {@code NativeContainer} cached {@code curDir} and friends in single instance
 * fields, filled by whichever container asked first, and served that same handle to every later
 * one. The handle carries a {@code TypeComposition}, and a composition belongs to exactly one
 * container.</p>
 *
 * <p>Masking is not a repair here: it rebuilds the outer handle but leaves the handles in its
 * fields pointing at the original owner's compositions, which is why the fix derives the value per
 * asker instead. The {@code OSStorage} service behind it stays plane-wide - a service handle is
 * legitimate cross-container currency, and giving each container its own starts a second storage
 * service, which is what broke {@code runner.x} on an earlier attempt.</p>
 */
public class InjectedResourceOwnershipTest {
    @Test
    public void siblingContainersDoNotShareAnInjectedResource() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        String sSource = """
                module InjectProbe {
                    @Inject Directory curDir;
                    @Inject Directory tmpDir;
                    @Inject Directory homeDir;
                    void run() {
                        // @Inject is lazy - the resource is only derived when the field is read
                        assert curDir.name.size >= 0;
                        assert tmpDir.name.size >= 0;
                        assert homeDir.name.size >= 0;
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
            var result = engine.compile("InjectProbe", sSource);
            assertTrue(result.isSuccess(), () -> "compile failed: " + result);

            // two runs of the same module under ONE native plane: the containers are siblings
            engine.run(result, "InjectProbe").get();
            engine.run(result, "InjectProbe").get();

            var listBad = new ArrayList<String>();
            List<Container> listRun = runContainersOf(engine.diagnosticContainer());
            // A run container with no cache of its own means the resources went back to being
            // cached plane-wide on the native container - the shape this test exists to reject.
            assertTrue(listRun.size() >= 2,
                    "expected each of the two run containers to hold its own injected resources,"
                            + " found " + listRun.size() + " that do; if this is 0 the resources"
                            + " are being cached on the native container again");

            for (Container container : listRun) {
                container.ensureNativeResourceCache().forEach((sName, handle) -> {
                    Container ownerComp = handle.getComposition().getContainer();
                    if (ownerComp != container) {
                        listBad.add(sName + " served to " + container
                                + " carries a composition owned by " + ownerComp);
                    }
                });
            }
            assertEquals(List.of(), listBad,
                    "an injected resource carries a composition owned by another container");
        }
    }

    private static List<Container> runContainersOf(Container containerNative) {
        var list = new ArrayList<Container>();
        containerNative.f_runtime.containers().forEach(container -> {
            if (container != containerNative
                    && !container.ensureNativeResourceCache().isEmpty()) {
                list.add(container);
            }
        });
        return list;
    }
}
