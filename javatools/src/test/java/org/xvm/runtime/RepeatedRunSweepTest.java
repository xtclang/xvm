package org.xvm.runtime;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Running one module twice under a single native plane leaves no container holding a reference to
 * an unrelated container's state.
 *
 * <p>This is the end-to-end form of {@link InjectedResourceOwnershipTest}, and the measurement that
 * issue 576 was filed on. Two runs of {@code TestFiles} produce two sibling containers; before the
 * injected filesystem resources were derived per asker, the reachability sweep of the first
 * reported exactly <strong>14</strong> foreign references, and of the second zero - the asymmetry
 * being the tell, since the first container is the one whose compositions everyone else borrowed.
 * It now reports zero for both.</p>
 *
 * <p>The whole-graph sweep is the point: it does not depend on anyone having enumerated the field
 * that leaks, so it catches a re-introduction through a path nobody thought to guard. That is also
 * why this test is worth its runtime despite the narrower unit test existing - the narrow one pins
 * the fix, this one pins the absence of the whole class of defect.</p>
 */
public class RepeatedRunSweepTest {
    @Test
    public void twoRunsOfOneModuleLeaveNoForeignReferences() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var  root       = XdkOutputs.root();
        File dirManual  = root.resolve("manualTests/build/xtc/main/lib").toFile();
        assumeTrue(dirManual.isDirectory(), "compiled manualTests modules are required");

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile(),
                            dirManual)
                .build()) {
            engine.run("TestFiles", "run").get();
            engine.run("TestFiles", "run").get();

            var listForeign = new ArrayList<String>();
            for (Container container : engine.diagnosticContainer().f_runtime.containers()) {
                OwnershipDiagnostics.sweepForeignReferences(container).violations()
                        .forEach(violation -> listForeign.add(container + ": " + violation));
            }

            assertEquals(List.of(), listForeign,
                    "a container holds a reference to an unrelated container's state after a"
                            + " second run on the same native plane");
        }
    }
}
