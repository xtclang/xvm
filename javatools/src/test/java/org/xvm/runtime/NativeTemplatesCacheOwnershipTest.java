package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;

import org.xvm.runtime.template.reflect.xPackage;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Values cached by key are owned by the container that cached them, and the ownership sweep can see
 * them.
 *
 * <p>The second half matters as much as the first. Moving owner-bearing state into a keyed table is
 * only an improvement if the existing diagnostics still reach it - a cache the sweep cannot walk
 * would hide exactly the kind of leak the table was introduced to make visible.</p>
 */
public class NativeTemplatesCacheOwnershipTest {
    @Test
    public void aCachedValueIsOwnedByItsContainerAndIsReachableBySweep() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, XdkOutputs.systemRepository(),
                    ErrorListener.RUNTIME);
            var templates = container.nativeTemplates();

            // plane-wide values are resolved during boot, before the pool is published, so that a
            // constant is never first registered into a pool the runtime can already see
            assertTrue(templates.resolvedKeys().size() > 0,
                    "the native container must warm its plane-wide values at boot");

            var reportBefore = OwnershipDiagnostics.sweepForeignReferences(container);
            assertTrue(reportBefore.isClean(), "baseline sweep: " + reportBefore.render());

            TypeComposition clz = xPackage.ensureListMapComposition(container);
            assertEquals(container, clz.getContainer(),
                    "a value cached by key must be owned by the container that cached it");
            assertTrue(templates.resolvedKeys().contains("ListMap<String,Class> composition"),
                    "the resolved key must be reported: " + templates.resolvedKeys());

            // the cache is reachable from the container, so the sweep walks it: resolving a key
            // adds objects to what the sweep sees, and the value is still owner-correct
            var reportAfter = OwnershipDiagnostics.sweepForeignReferences(container);
            assertTrue(reportAfter.isClean(), "sweep after caching: " + reportAfter.render());
            assertTrue(reportAfter.objectsVisited() > reportBefore.objectsVisited(),
                    "the keyed cache must be reachable by the sweep; object count went from "
                            + reportBefore.objectsVisited() + " to " + reportAfter.objectsVisited());
        } finally {
            runtime.shutdownXVM();
        }
    }
}
