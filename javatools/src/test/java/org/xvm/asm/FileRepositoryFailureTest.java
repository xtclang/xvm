package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void invalidHeaderPreservesItsCause() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");

        var repository = new FileRepository(file.toFile(), true);
        var failure = assertThrows(UncheckedIOException.class, repository::getModuleNames);
        assertTrue(failure.getMessage().contains(file.toString()));
        assertNotNull(failure.getCause());
        assertThrows(UncheckedIOException.class, repository::getModuleNames);
    }

    @Test
    void truncatedModulePreservesItsCauseAfterItsHeaderWasRead() throws IOException {
        Path file = directory.resolve("broken.xtc");
        new FileStructure("Broken").writeTo(file.toFile());
        byte[] contents = Files.readAllBytes(file);
        var input = new ByteArrayInputStream(contents);
        FileStructure.readFileInfo(input);
        Files.write(file, Arrays.copyOf(contents, contents.length - input.available()));

        var repository = new FileRepository(file.toFile(), true);
        assertTrue(repository.getModuleNames().contains("Broken"));
        var failure = assertThrows(UncheckedIOException.class,
                () -> repository.loadModule("Broken"));
        assertTrue(failure.getMessage().contains(file.toString()));
        assertNotNull(failure.getCause());
    }
}
