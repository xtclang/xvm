package org.xvm.asm;


import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * A read-through {@link LinkedRepository} populates its FRONT repository with whatever it finds
 * further down the chain. That is a cache write, and it must not be able to change the answer to the
 * lookup.
 *
 * <p>It could. On a failed write the versioned {@code loadModule} broke out of the search, so a
 * module that WAS found came back as {@code null}. A read-only front repository - which a build
 * output directory routinely is - made that happen on every versioned load, and the only trace was a
 * line on stderr.
 *
 * <p>The unversioned overload had already been fixed; the versioned one was a copy that had not been.
 * Both now share one body, which is the real fix: the reason one was right and the other wrong is
 * that there were two of them.
 */
public class LinkedRepositoryReadThroughTest {
    @Test
    public void aFailedCacheWriteDoesNotHideAModuleThatWasFound() {
        ModuleStructure module = new FileStructure("readthrough.test.org").getModule();
        var chain = new LinkedRepository(true, new ReadOnlyRepo(), new HoldingRepo(module));

        assertNotNull(chain.loadModule("readthrough.test.org"),
                "unversioned: the front repository refused the write, but the module was found");
        assertNotNull(chain.loadModule("readthrough.test.org", Version.NONE, false),
                "versioned: this is the path that used to return null on a read-only front repo");
    }

    /**
     * When the front repository CAN be written, the caller gets the cached copy rather than the
     * repository's own instance - so that a compiler mutating what it was handed cannot reach back
     * into the repository the module came from.
     */
    @Test
    public void aSuccessfulCacheWriteServesTheCopy() {
        ModuleStructure module = new FileStructure("readthrough.copy.org").getModule();
        var front = new CollectingRepo();
        var chain = new LinkedRepository(true, front, new HoldingRepo(module));

        ModuleStructure served = chain.loadModule("readthrough.copy.org", Version.NONE, false);

        assertNotNull(served);
        assertNotNull(front.stored, "the front repository was populated");
        assertNotSame(module, served,
                "the caller must NOT be handed the source repository's own instance - a compiler "
                        + "mutating what it was given would reach back into that repository");
        assertSame(front.stored, served, "it is handed the copy that was cached");
    }

    /** A front repository that refuses every write, the way a read-only directory does. */
    private static final class ReadOnlyRepo implements ModuleRepository {
        @Override public Set<String> getModuleNames()                 { return Set.of(); }
        @Override public ModuleStructure loadModule(String sModule)    { return null; }
        @Override public void storeModule(ModuleStructure module) throws IOException {
            throw new IOException("read-only");
        }
    }

    /** A front repository that accepts a write and remembers what it was given. */
    private static final class CollectingRepo implements ModuleRepository {
        ModuleStructure stored;

        @Override public Set<String> getModuleNames()                 { return Set.of(); }
        @Override public ModuleStructure loadModule(String sModule)    { return null; }
        @Override public void storeModule(ModuleStructure module)      { stored = module; }
    }

    /** A back repository that holds exactly one module. */
    private record HoldingRepo(ModuleStructure module) implements ModuleRepository {
        @Override public Set<String> getModuleNames() { return Set.of(module.getName()); }

        @Override public ModuleStructure loadModule(String sModule) {
            return module.getName().equals(sModule) ? module : null;
        }

        /**
         * Overridden so the test exercises LinkedRepository rather than the interface's default
         * version matching, which would answer null for an unversioned module and hide the
         * behaviour under test.
         */
        @Override public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
            return loadModule(sModule);
        }

        @Override public void storeModule(ModuleStructure module) {}
    }
}
