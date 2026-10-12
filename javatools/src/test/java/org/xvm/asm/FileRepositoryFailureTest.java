package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises failed and repaired module-file reads, including repairs that preserve file metadata. Ensures
 * failure observations do not turn write errors into read reports or leave failed reads permanently cached.
 */
class FileRepositoryFailureTest {
    @TempDir
    Path directory;

    private final Map<File, IOException> reports = new HashMap<>();

    @Test
    void invalidHeaderIsReportedAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true, reports::put);

        assertTrue(reports.isEmpty(), "construction must not initiate a read");
        assertTrue(repository.getModuleNames().isEmpty());
        assertNull(repository.loadModule("Broken"));
        assertEquals(Set.of(file.toFile()), reports.keySet());
        assertNotNull(reports.get(file.toFile()));

        reports.clear();
        new FileStructure("Broken").writeTo(file.toFile());
        assertNotNull(repository.loadModule("Broken"));
        assertTrue(reports.isEmpty());
    }

    @Test
    void truncatedPayloadIsReportedAfterItsHeaderWasRead() throws IOException {
        Path file = directory.resolve("broken.xtc");
        reports.clear();
        new FileStructure("Broken").writeTo(file.toFile());
        byte[] contents = Files.readAllBytes(file);
        var input = new ByteArrayInputStream(contents);
        FileStructure.readFileInfo(input);
        Files.write(file, Arrays.copyOf(contents, contents.length - input.available()));
        var repository = new FileRepository(file.toFile(), true, reports::put);

        assertTrue(repository.getModuleNames().contains("Broken"));
        assertNull(repository.loadModule("Broken"));
        assertNotNull(reports.get(file.toFile()));
        assertNull(repository.loadModule("Broken"));
        assertNotNull(reports.get(file.toFile()));

        reports.clear();
        Files.write(file, contents);
        assertNotNull(repository.loadModule("Broken"));
        assertTrue(reports.isEmpty());
    }

    @Test
    void failedReadIsRetriedEvenWhenRepairPreservesItsMetadata() throws IOException {
        Path file = directory.resolve("Good.xtc");
        new FileStructure("Good").writeTo(file.toFile());
        byte[] valid = Files.readAllBytes(file);
        byte[] broken = valid.clone();
        broken[0] = 0; // Invalidate the magic value without changing the file size.
        Files.write(file, broken);
        var timestamp = Files.getLastModifiedTime(file);
        var repository = new FileRepository(file.toFile(), true, reports::put);
        assertNull(repository.loadModule("Good"));
        assertNotNull(reports.get(file.toFile()));

        reports.clear();
        Files.write(file, valid);
        Files.setLastModifiedTime(file, timestamp);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (repository.loadModule("Good") == null) {
                Thread.sleep(10);
            }
        });
        assertTrue(reports.isEmpty());
    }

    @Test
    void removedCandidatesProduceNoFurtherReports() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true, reports::put);
        assertNull(repository.loadModule("Broken"));
        assertNotNull(reports.get(file.toFile()));

        reports.clear();
        Files.delete(file);
        assertTrue(repository.getModuleNames().isEmpty());
        assertTrue(reports.isEmpty());
    }
    @Test
    void failedWritesAreThrownSeparatelyFromReadReports() throws IOException {
        Path file = directory.resolve("Old.xtc");
        Files.writeString(file, "unreadable candidate");
        byte[] original = Files.readAllBytes(file);
        var repository = new FileRepository(file.toFile(), true, reports::put);

        assertNull(repository.loadModule("Missing"));
        assertEquals(Set.of(file.toFile()), reports.keySet());
        reports.clear();
        assertThrows(IOException.class, () -> repository.storeModule(new FileStructure("Good").getModule()));
        assertTrue(reports.isEmpty(), "a write failure is not a read event");
        assertArrayEquals(original, Files.readAllBytes(file));
    }

}
