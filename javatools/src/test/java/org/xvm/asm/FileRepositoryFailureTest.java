package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void invalidHeaderRetainsItsCauseAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new FileRepository(file.toFile(), true);

        assertTrue(repository.getReadFailures().isEmpty(), "inspection must not initiate a read");
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
    void truncatedModuleRetainsItsCauseAfterItsHeaderWasRead() throws IOException {
        Path file = directory.resolve("broken.xtc");
        new FileStructure("Broken").writeTo(file.toFile());
        byte[] contents = Files.readAllBytes(file);
        var input = new ByteArrayInputStream(contents);
        FileStructure.readFileInfo(input);
        Files.write(file, Arrays.copyOf(contents, contents.length - input.available()));
        var repository = new FileRepository(file.toFile(), true);

        assertTrue(repository.getModuleNames().contains("Broken"));
        assertNull(repository.loadModule("Broken"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));
        assertNull(repository.loadModule("Broken"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));

        Files.write(file, contents);
        assertNotNull(repository.loadModule("Broken"));
        assertTrue(repository.getReadFailures().isEmpty());
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
        var repository = new FileRepository(file.toFile(), true);
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
        var repository = new FileRepository(file.toFile(), true);
        assertNull(repository.loadModule("Broken"));
        assertNotNull(repository.getReadFailures().get(file.toFile()));

        Files.delete(file);
        assertTrue(repository.getModuleNames().isEmpty());
        assertTrue(repository.getReadFailures().isEmpty());
    }
}
