package org.xvm.asm;

import org.xvm.test.XdkOutputs;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A failed read-through cache write must not hide a module that was found.
 *
 * <p>{@code LinkedRepository} answers a hit in a later repository by cloning the module into the
 * front one, so the compiler can mutate it. When that write fails - a read-only front repository is
 * the ordinary case for a chain assembled out of build output directories - the module had been
 * found and was still reported as absent, with the reason going to {@code System.err}.</p>
 */
public class LinkedRepositoryReadThroughTest {
    /**
     * A front repository that holds nothing and refuses every write, which is what a read-only
     * build output directory behaves like.
     */
    private static final class UnwritableRepository
            implements ModuleRepository {
        @Override
        public Set<String> getModuleNames() {
            return Set.of();
        }

        @Override
        public ModuleStructure loadModule(String sModule) {
            return null;
        }

        @Override
        public void storeModule(ModuleStructure module) throws IOException {
            throw new IOException("read-only repository");
        }
    }

    @Test
    public void aFailedCacheWriteStillServesTheModuleItFound() {
        File dirSource = XdkOutputs.root().resolve("lib_ecstasy/build/xtc/main/lib").toFile();
        assumeTrue(dirSource.isDirectory(), "needs compiled lib_ecstasy");

        var repoSource = new DirRepository(dirSource, true);
        assumeTrue(repoSource.loadModule(Constants.ECSTASY_MODULE) != null,
                "the source repository must actually hold the module");

        // read-through ON, and the front repository cannot be written: the module is found in
        // repos[1] and the attempt to cache it into repos[0] fails
        var repo = new LinkedRepository(true, new UnwritableRepository(), repoSource);

        assertNotNull(repo.loadModule(Constants.ECSTASY_MODULE),
                "a module found in a later repository must still be served when the read-through"
                        + " copy into the front repository cannot be written");
    }

}
