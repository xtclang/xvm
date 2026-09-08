package org.xvm.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * "Compiled without errors" is not "compiled correctly".
 *
 * <p>The build harness reports 22 of 22 modules with no diagnostics, which says the compiler did not
 * complain - not that it produced the same thing the CLI produces. This compares the engine's output
 * against the artifacts the Gradle build wrote, structurally: same modules, and each with the same
 * shape of component tree.</p>
 */
@Tag("heavy")
public class XdkBuildOutputVerifyTest {
    @Test
    public void engineOutputMatchesTheBuiltXdkStructurally() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");
        // Belt and braces: the "heavy" tag keeps this out of an ordinary `test` run, and this
        // guard catches the case where someone runs the class directly (an IDE, or --tests) without
        // the heap. Without it the failure is an OutOfMemoryError from inside a compile, which
        // reads as a compiler defect rather than as "you did not give this enough memory".
        //
        // WHY it needs the memory: each compile holds its module's ConstantPool and the TypeInfo it
        // builds for every type it touches - and the XDK's own modules are the largest types in the
        // system. These tests then do that repeatedly on ONE engine, so the built modules
        // accumulate in a BuildRepository that is deliberately kept alive across compiles (that
        // reuse is what is under test). Peak live set is therefore the whole XDK plus the working
        // set of whichever compile is running, several times over in the parallel cases.
        assumeTrue(Runtime.getRuntime().maxMemory() > 4L << 30,
                "needs a large heap; run with -PtestMaxHeap=8g");

        Path root = XdkOutputs.root();
        ModuleRepository repoLibrary = new LinkedRepository(true,
                new DirRepository(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("javatools_bridge/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/javatools").toFile(), true));

        var nodes = XdkBuildHarnessTest.discoverForTest(root);
        assumeTrue(nodes.size() > 5, "expected the XDK library sources");

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build();
             var executor = Executors.newSingleThreadExecutor()) {

            var report = XdkBuildHarness.build(engine, repoLibrary, nodes, executor);
            assertEquals(nodes.size(), report.built(), () -> "build failed:\n" + report.render());

            var produced = XdkBuildHarness.lastOutput();
            var gradle   = new DirRepository(root.resolve("xdk/build/install/xdk/lib").toFile(), true);

            var mismatches = new ArrayList<String>();
            int compared   = 0;
            for (String name : produced.getModuleNames()) {
                ModuleStructure theirs = gradle.loadModule(name);
                if (theirs == null) {
                    continue;   // not every built module ships in xdk/lib
                }
                ModuleStructure ours = produced.loadModule(name);
                compared++;

                int ourCount   = countComponents(ours);
                int theirCount = countComponents(theirs);
                if (ourCount != theirCount) {
                    mismatches.add(String.format(
                            "%s: engine produced %d components, the build produced %d",
                            name, ourCount, theirCount));
                }
            }

            System.out.printf("compared %d modules against the Gradle-built XDK%n", compared);
            mismatches.forEach(m -> System.out.println("  MISMATCH " + m));
            assumeTrue(compared > 3, "expected several modules to be comparable");
            assertEquals(java.util.List.of(), mismatches,
                    "the engine's output differs structurally from the Gradle build's");
        }
    }

    /**
     * @return the number of components in the tree, which is a coarse but honest shape check: two
     *         compilers that disagree about what a module contains will disagree here
     */
    private static int countComponents(Component component) {
        int count = 1;
        for (Component child : component.children()) {
            count += countComponents(child);
        }
        return count;
    }
}
