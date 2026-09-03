package org.xvm.api;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Does a repository hand back the SAME module instance over time?
 *
 * <p>Everything about serving one prepared library to many compiles rests on this. Preparation
 * injects the NakedRef type into each library module's {@code ConstantPool}; if a later
 * {@code loadModule} returns a different instance, that instance has a different pool, the
 * injection is not on it, and the compile fails with "Mack module (javatools_turtle) is missing".
 *
 * <p>{@code DirRepository} re-scans its directory once a second, so this deliberately waits past
 * that window - the interesting case is the one an interactive host hits constantly and a
 * single-shot CLI never does.
 */
public class LibraryInstanceStabilityTest {
    @Test
    public void sameInstanceAcrossRescan() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "needs XDK");
        Path root = XdkOutputs.root();
        File dir  = root.resolve("xdk/build/install/xdk/lib").toFile();
        assumeTrue(dir.isDirectory(), "needs the installed xdk lib dir");

        var repo = new DirRepository(dir, true);

        ModuleStructure first = repo.loadModule("ecstasy.xtclang.org");
        assumeTrue(first != null, "needs ecstasy in the lib dir");

        // past DirRepository's one-second scan window, so a re-scan definitely happens
        Thread.sleep(1_500);

        ModuleStructure second = repo.loadModule("ecstasy.xtclang.org");

        assertSame(first, second, "repository returned a different ModuleStructure after re-scan");
        assertSame(first.getConstantPool(), second.getConstantPool(),
                "repository returned a different ConstantPool after re-scan");
    }
}
