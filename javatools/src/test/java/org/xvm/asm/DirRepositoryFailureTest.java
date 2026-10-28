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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void corruptModuleRetainsItsFailureAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new DirRepository(directory.toFile(), true);

        assertTrue(repository.getReadFailures().isEmpty(), "inspection must not initiate a scan");
        assertTrue(repository.getModuleNames().isEmpty());
        assertNull(repository.loadModule("Broken"));
        var failures = repository.getReadFailures();
        assertEquals(Set.of(file.toFile()), failures.keySet());
        assertNotNull(failures.get(file.toFile()));
        assertThrows(UnsupportedOperationException.class, failures::clear);

        new FileStructure("Broken").writeTo(file.toFile());
        assertNotNull(repository.loadModule("Broken"));
        assertTrue(repository.getReadFailures().isEmpty());
        assertEquals(1, failures.size(), "an earlier snapshot must not change");
    }

    @Test
    void incompatibleCandidateDoesNotHideAUsableModuleInTheSameDirectory() throws IOException {
        Path old = directory.resolve("Old.xtc");
        writeUnsupportedModule(old, "Old");
        new FileStructure("Good").writeTo(directory.resolve("Good.xtc").toFile());
        var repository = new DirRepository(directory.toFile(), true);

        assertNotNull(repository.loadModule("Good"));
        assertEquals(Set.of("Good"), repository.getModuleNames());
        assertNull(repository.loadModule("Unknown"));
        assertTrue(repository.getReadFailures().get(old.toFile()).getMessage()
                .contains("Unsupported .xtc version"));
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
                ? new FileRepository(old.toFile(), true)
                : new DirRepository(first.toFile(), true);
        var repository = new LinkedRepository(candidate, new DirRepository(second.toFile(), true));

        assertNotNull(repository.loadModule("Good"));
        assertEquals(Set.of("Good"), repository.getModuleNames());
        assertTrue(repository.getAvailableVersions("Good").contains(version));
        assertNotNull(repository.loadModule("Good", version, true));
        var failures = repository.getReadFailures();
        assertEquals(Set.of(old.toFile()), failures.keySet());
        assertTrue(failures.get(old.toFile()).getMessage().contains("Unsupported .xtc version"));
        assertThrows(UnsupportedOperationException.class, failures::clear);
    }

    @Test
    void unsuccessfulLinkedSearchRetainsFailuresFromAllCandidates() throws IOException {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Path old = first.resolve("Old.xtc");
        Path broken = second.resolve("Broken.xtc");
        writeUnsupportedModule(old, "Old");
        Files.writeString(broken, "not a compiled module");
        var repository = new LinkedRepository(new DirRepository(first.toFile(), true),
                new DirRepository(second.toFile(), true));

        assertNull(repository.loadModule("Missing"));
        assertEquals(Set.of(old.toFile(), broken.toFile()), repository.getReadFailures().keySet());
    }

    @Test
    void cachedFailureIsRereadByANewRepositoryInstance() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        assertTrue(new DirRepository(directory.toFile(), true).getModuleNames().isEmpty());
        var repository = new DirRepository(directory.toFile(), true);

        assertNull(repository.loadModule("Broken"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));
        new FileStructure("Broken").writeTo(file.toFile());
        assertNotNull(repository.loadModule("Broken"));
        assertTrue(repository.getReadFailures().isEmpty());
    }

    @Test
    void failedCandidateIsRetriedEvenWhenRepairPreservesItsMetadata() throws IOException {
        Path file = directory.resolve("Good.xtc");
        new FileStructure("Good").writeTo(file.toFile());
        byte[] valid = Files.readAllBytes(file);
        writeUnsupportedModule(file, "Good");
        var timestamp = Files.getLastModifiedTime(file);
        var repository = new DirRepository(directory.toFile(), true);
        assertNull(repository.loadModule("Good"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));

        Files.write(file, valid);
        Files.setLastModifiedTime(file, timestamp);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (repository.loadModule("Good") == null) {
                Thread.sleep(10);
            }
        });
        assertTrue(repository.getReadFailures().isEmpty());
    }

    @Test
    void removingAnUnreadableFileClearsItsFailure() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new DirRepository(directory.toFile(), true);
        assertNull(repository.loadModule("Broken"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));

        Files.delete(file);
        assertTrue(repository.getModuleNames().isEmpty());
        assertTrue(repository.getReadFailures().isEmpty());
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
