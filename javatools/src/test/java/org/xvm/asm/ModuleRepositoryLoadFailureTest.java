package org.xvm.asm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the requested-module load boundary of {@link FileRepository}, {@link DirRepository}, and
 * {@link LinkedRepository}. Master printed {@code "Error loading module from file..."} to stdout
 * and returned {@code null} when a module file existed but could not be read, so a corrupt module
 * was indistinguishable from a missing module for every caller: hosts reported "module not found"
 * with the real cause visible only as console text. Best-effort scanning
 * ({@code getModuleNames()}) must still skip broken candidates, but a requested load must fail
 * with the retained cause.
 */
class ModuleRepositoryLoadFailureTest {
    @TempDir
    File dir;

    /**
     * A requested load of the module a broken file should contain must throw with the retained
     * cause. On master this test fails because {@code loadModule} printed and returned null.
     */
    @Test
    void fileRepositoryRequestedLoadFailsWithCause() throws IOException {
        var file = brokenModuleFile("foo");
        var repo = new FileRepository(file, true);

        var failure = assertThrows(ModuleLoadException.class, () -> repo.loadModule("foo"),
                "a requested load of a corrupt module must not report the module as missing");
        assertTrue(failure.getMessage().contains("foo"), failure.getMessage());
        assertNotNull(failure.getCause(), "the underlying load failure must be retained");

        assertThrows(ModuleLoadException.class,
                () -> new FileRepository(file, true).loadModule("foo.example.org"),
                "a qualified module name must match the file's simple name");
    }

    /**
     * Scanning and unrelated module names keep the old best-effort behavior: no exception, and a
     * module the file cannot possibly hold stays a plain "not found".
     */
    @Test
    void fileRepositoryScanAndUnrelatedNamesStayBestEffort() throws IOException {
        var repo = new FileRepository(brokenModuleFile("foo"), true);

        assertTrue(repo.getModuleNames().isEmpty(), "scanning must skip broken candidates");
        assertNull(repo.loadModule("bar"), "an unrelated module name is a plain miss");
    }

    /**
     * Same contract for directory repositories: broken candidate files are skipped by scans but
     * surface their cause on a requested load that names them.
     */
    @Test
    void dirRepositoryRequestedLoadFailsWithCause() throws IOException {
        brokenModuleFile("foo");
        var repo = new DirRepository(dir, true);

        assertTrue(repo.getModuleNames().isEmpty(), "scanning must skip broken candidates");
        assertNull(repo.loadModule("bar"), "an unrelated module name is a plain miss");

        var failure = assertThrows(ModuleLoadException.class, () -> repo.loadModule("foo"));
        assertTrue(failure.getMessage().contains("foo"), failure.getMessage());
        assertNotNull(failure.getCause(), "the underlying load failure must be retained");
    }

    /**
     * A linked repository is a search: a broken candidate must not hide a good copy in a later
     * repository, and only a search that ends unsatisfied rethrows the retained failure.
     */
    @Test
    void linkedRepositorySearchPrefersGoodCopyAndRethrowsOnlyOnMiss() throws IOException {
        var fileBroken = brokenModuleFile("foo");
        var moduleGood = new FileStructure("foo.example.org").getModule();
        var repoGood   = new StubRepository(moduleGood);

        var linked = new LinkedRepository(new FileRepository(fileBroken, true), repoGood);
        assertSame(moduleGood, linked.loadModule("foo.example.org"),
                "a broken earlier candidate must not hide a good copy in a later repository");

        var linkedBrokenOnly = new LinkedRepository(new FileRepository(fileBroken, true));
        assertThrows(ModuleLoadException.class, () -> linkedBrokenOnly.loadModule("foo"),
                "an unsatisfied search with a broken candidate must surface the failure");
        assertThrows(ModuleLoadException.class,
                () -> new LinkedRepository(new FileRepository(fileBroken, true))
                        .loadModule("foo", null, false),
                "the versioned search path must surface the failure the same way");
    }

    private File brokenModuleFile(String sName) throws IOException {
        Path path = new File(dir, sName + ".xtc").toPath();
        Files.writeString(path, "this is not an XVM module file", UTF_8);
        return path.toFile();
    }

    /**
     * Minimal in-memory repository holding one module.
     */
    private static final class StubRepository implements ModuleRepository {
        StubRepository(ModuleStructure module) {
            f_module = module;
        }

        @Override
        public Set<String> getModuleNames() {
            return Collections.singleton(f_module.getIdentityConstant().getName());
        }

        @Override
        public VersionTree<Boolean> getAvailableVersions(String sModule) {
            return new VersionTree<>();
        }

        @Override
        public ModuleStructure loadModule(String sModule) {
            return f_module.getIdentityConstant().getName().equals(sModule) ? f_module : null;
        }

        @Override
        public void storeModule(ModuleStructure module) {
            throw new UnsupportedOperationException();
        }

        private final ModuleStructure f_module;
    }
}
