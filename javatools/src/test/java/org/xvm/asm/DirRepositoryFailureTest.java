package org.xvm.asm;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises directory repository reads with corrupt, incompatible and repaired candidates. Protects both
 * best-effort fallback to usable modules and metadata-based reuse of unavailable candidates.
 */
class DirRepositoryFailureTest extends RepositoryFailureTestSupport {
    @TempDir
    Path directory;

    @Test
    void unreadableCandidateIsReportedAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new DirRepository(directory.toFile(), true, errors);

        assertTrue(output().isEmpty(), "construction must not initiate a scan");
        assertTrue(repository.getModuleNames().isEmpty());
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().contains(file.toString()));
        assertEquals(1, reports.size());
        assertEquals("LAUNCHER-04", reports.getFirst().getCode());
        assertInstanceOf(Exception.class, reports.getFirst().getParams()[1]);
        assertFalse(errors.hasSeriousErrors(), "an unavailable candidate must not poison fallback");

        clearOutput();
        new FileStructure("Broken").writeTo(file.toFile());
        awaitModule(repository, "Broken");
        assertTrue(output().isEmpty());
    }

    @Test
    void incompatibleCandidateDoesNotHideAUsableModuleInTheSameDirectory() throws IOException {
        Path old = directory.resolve("Old.xtc");
        writeUnsupportedModule(old, "Old");
        new FileStructure("Good").writeTo(directory.resolve("Good.xtc").toFile());
        var repository = new DirRepository(directory.toFile(), true, errors);

        assertNotNull(repository.loadModule("Good"));
        assertEquals(Set.of("Good"), repository.getModuleNames());
        assertNull(repository.loadModule("Unknown"));
        assertTrue(output().contains("Unsupported .xtc version"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incompatibleCandidateDoesNotBlockALaterRepository(boolean singleFile) throws IOException {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Path old = first.resolve("Good.xtc");
        writeUnsupportedModule(old, "Good");
        var version = new Version("1.0");
        var good = new FileStructure("Good");
        good.getModule().setVersion(version);
        good.writeTo(second.resolve("Good.xtc").toFile());
        ModuleRepository candidate = singleFile
                ? new FileRepository(old.toFile(), true, errors)
                : new DirRepository(first.toFile(), true, errors);
        var repository = new LinkedRepository(candidate, new DirRepository(second.toFile(), true, errors));

        assertNotNull(repository.loadModule("Good"));
        assertEquals(Set.of("Good"), repository.getModuleNames());
        assertTrue(repository.getAvailableVersions("Good").contains(version));
        assertNotNull(repository.loadModule("Good", version, true));
        assertTrue(output().contains(old.toString()));
        assertTrue(output().contains("Unsupported .xtc version"));
    }

    @Test
    void cachedFailureIsReusedByANewRepositoryInstanceUntilFileChanges() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        assertTrue(new DirRepository(directory.toFile(), true, errors).getModuleNames().isEmpty());
        clearOutput();
        var repository = new DirRepository(directory.toFile(), true, errors);

        assertNull(repository.loadModule("Broken"));
        assertTrue(output().isEmpty(), "cached absence is not a new read attempt");
        new FileStructure("Broken").writeTo(file.toFile());
        awaitModule(repository, "Broken");
        assertTrue(output().isEmpty());
    }

    @Test
    void unchangedFailureIsNotReadAgainWhenTheScanIntervalExpires() throws Exception {
        Path file = directory.resolve("Old.xtc");
        writeUnsupportedModule(file, "Old");
        var repository = new DirRepository(directory.toFile(), true, errors);
        assertNull(repository.loadModule("Old"));
        assertTrue(output().contains(file.toString()));
        clearOutput();

        Thread.sleep(1100); // Advance past the production metadata-check interval.
        for (int i = 0; i < 3; ++i) {
            assertTrue(repository.getModuleNames().isEmpty());
            assertNull(repository.loadModule("Missing"));
        }
        assertTrue(output().isEmpty(), "unchanged candidates must not be read or reported repeatedly");

        new FileStructure("Good").writeTo(directory.resolve("Good.xtc").toFile());
        awaitModule(repository, "Good");
        assertTrue(output().isEmpty(), "a rebuild for another file must retain the failed entry");
        new FileStructure("Repaired").writeTo(file.toFile());
        awaitModule(repository, "Repaired");
        assertTrue(output().isEmpty());
    }

    @Test
    void removedCandidatesProduceNoFurtherReports() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new DirRepository(directory.toFile(), true, errors);
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().contains(file.toString()));

        clearOutput();
        Files.delete(file);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!repository.getModuleNames().isEmpty()) Thread.sleep(10);
        });
        assertTrue(output().isEmpty());
    }

    private static void awaitModule(ModuleRepository repository, String name) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (repository.loadModule(name) == null) Thread.sleep(10);
        });
    }

    private static void writeUnsupportedModule(Path file, String name) throws IOException {
        new FileStructure(name).writeTo(file.toFile());
        byte[] contents = Files.readAllBytes(file);
        // The binary header starts with three ints: magic, major version, minor version.
        int minor = FileStructure.getToolMinorVersion() - 1;
        assertFalse(FileStructure.isFileVersionSupported(FileStructure.getToolMajorVersion(), minor));
        ByteBuffer.wrap(contents).putInt(2 * Integer.BYTES, minor);
        Files.write(file, contents);
    }
}
