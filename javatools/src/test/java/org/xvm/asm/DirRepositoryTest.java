package org.xvm.asm;


import java.io.File;
import java.io.IOException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for a directory-backed module repository.
 */
public class DirRepositoryTest {
    @Test
    public void testConventionalFileNameAvoidsFullScan()
            throws IOException {
        writeModule("test.xtc", "test.example.org");
        writeModule("unrelated.xtc", "unrelated.example.org");

        TrackingDirRepository repository = new TrackingDirRepository(tempDir.toFile());
        ModuleStructure       module     = repository.loadModule("test.example.org");

        assertNotNull(module);
        assertEquals("test.example.org", module.getName());
        assertFalse(repository.fullScan);
    }

    @Test
    public void testNonstandardFileNameFallsBackToFullScan()
            throws IOException {
        writeModule("nonstandard.xtc", "test.example.org");

        TrackingDirRepository repository = new TrackingDirRepository(tempDir.toFile());
        ModuleStructure module = repository.loadModule("test.example.org");

        assertNotNull(module);
        assertEquals("test.example.org", module.getName());
        assertTrue(repository.fullScan);
    }

    @Test
    public void testDirectLookupModeAvoidsFallbackScan()
            throws IOException {
        writeModule("nonstandard.xtc", "test.example.org");

        TrackingDirRepository repository = new TrackingDirRepository(tempDir.toFile(), false);
        assertNull(repository.loadModule("test.example.org"));
        assertFalse(repository.fullScan);

        assertTrue(repository.getModuleNames().contains("test.example.org"));
        assertNotNull(repository.loadModule("test.example.org"));
    }

    private void writeModule(String fileName, String moduleName)
            throws IOException {
        new FileStructure(moduleName).writeTo(tempDir.resolve(fileName).toFile());
    }

    private static class TrackingDirRepository
            extends DirRepository {
        private TrackingDirRepository(File dir) {
            this(dir, true);
        }

        private TrackingDirRepository(File dir, boolean fScanFallback) {
            super(dir, true, fScanFallback);
        }

        @Override
        protected void ensureCache() {
            fullScan = true;
            super.ensureCache();
        }

        private boolean fullScan;
    }

    @TempDir
    private Path tempDir;
}