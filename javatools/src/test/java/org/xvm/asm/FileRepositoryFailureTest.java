package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises failed module reads and recovery after normal file replacements. Ensures unchanged failures
 * stay cached and quiet, while write errors still reach the writer.
 */
class FileRepositoryFailureTest extends RepositoryFailureTestSupport {
    @TempDir
    Path directory;

    @Test
    void invalidHeaderIsReportedAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true, errors);

        assertTrue(output().isEmpty(), "construction must not initiate a read");
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
    void truncatedPayloadIsReportedAfterItsHeaderWasRead() throws Exception {
        Path file = directory.resolve("broken.xtc");
        clearOutput();
        new FileStructure("Broken").writeTo(file.toFile());
        byte[] contents = Files.readAllBytes(file);
        var input = new ByteArrayInputStream(contents);
        FileStructure.readFileInfo(input);
        Files.write(file, Arrays.copyOf(contents, contents.length - input.available()));
        var repository = new FileRepository(file.toFile(), true, errors);

        assertTrue(repository.getModuleNames().contains("Broken"));
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().contains(file.toString()));
        clearOutput();
        Thread.sleep(80); // Past the burst interval; the unchanged bad payload remains unavailable.
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().isEmpty());

        Files.write(file, contents);
        awaitModule(repository, "Broken");
        assertTrue(output().isEmpty());
    }

    @Test
    void unchangedFailureIsNotReadAgainWhenTheBurstIntervalExpires() throws Exception {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true, errors);
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().contains(file.toString()));
        clearOutput();

        Thread.sleep(80); // Advance past the production burst-cache interval.
        for (int i = 0; i < 3; ++i) {
            assertTrue(repository.getModuleNames().isEmpty());
            assertNull(repository.loadModule("Broken"));
        }
        assertTrue(output().isEmpty(), "unchanged failures must not cause repeated reads or reports");

        new FileStructure("Repaired").writeTo(file.toFile());
        awaitModule(repository, "Repaired");
        assertTrue(output().isEmpty());
    }

    @Test
    void removedCandidatesProduceNoFurtherReports() throws IOException {
        Path file = directory.resolve("Broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true, errors);
        assertNull(repository.loadModule("Broken"));
        assertTrue(output().contains(file.toString()));

        clearOutput();
        Files.delete(file);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!repository.getModuleNames().isEmpty()) Thread.sleep(10);
        });
        assertTrue(output().isEmpty());
    }

    @Test
    void failedWritesStillReachTheWriter() throws IOException {
        Path file = directory.resolve("Old.xtc");
        Files.writeString(file, "unreadable candidate");
        byte[] original = Files.readAllBytes(file);
        var repository = new FileRepository(file.toFile(), true, errors);

        assertNull(repository.loadModule("Missing"));
        assertTrue(output().contains(file.toString()));
        clearOutput();
        assertThrows(IOException.class, () -> repository.storeModule(new FileStructure("Good").getModule()));
        assertTrue(output().isEmpty(), "write failures must be thrown, not printed");
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    private static void awaitModule(ModuleRepository repository, String name) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (repository.loadModule(name) == null) Thread.sleep(10);
        });
    }
}
