package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
 * Exercises failed module reads and recovery after normal file replacements. Ensures unchanged failures
 * stay cached and quiet, while write errors remain separate from read observations.
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
        awaitModule(repository, "Broken");
        assertTrue(reports.isEmpty());
    }

    @Test
    void truncatedPayloadIsReportedAfterItsHeaderWasRead() throws Exception {
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
        reports.clear();
        Thread.sleep(80); // Past the burst interval; the unchanged bad payload remains unavailable.
        assertNull(repository.loadModule("Broken"));
        assertTrue(reports.isEmpty());

        Files.write(file, contents);
        awaitModule(repository, "Broken");
        assertTrue(reports.isEmpty());
    }

    @Test
    void unchangedFailureIsNotReadAgainWhenTheBurstIntervalExpires() throws Exception {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var attempts = new ArrayList<File>();
        var repository = new FileRepository(file.toFile(), true, (candidate, cause) -> attempts.add(candidate));
        assertNull(repository.loadModule("Broken"));
        assertEquals(List.of(file.toFile()), attempts);

        Thread.sleep(80); // Advance past the production burst-cache interval.
        for (int i = 0; i < 3; ++i) {
            assertTrue(repository.getModuleNames().isEmpty());
            assertNull(repository.loadModule("Broken"));
        }
        assertEquals(1, attempts.size(), "unchanged failures must not cause repeated reads or reports");

        new FileStructure("Repaired").writeTo(file.toFile());
        awaitModule(repository, "Repaired");
        assertEquals(1, attempts.size());
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
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!repository.getModuleNames().isEmpty()) Thread.sleep(10);
        });
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

    private static void awaitModule(ModuleRepository repository, String name) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (repository.loadModule(name) == null) Thread.sleep(10);
        });
    }
}
