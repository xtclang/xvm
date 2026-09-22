package org.xvm.asm;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void corruptModuleIsReportedOnEveryAttemptAndCanBeRepaired() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var repository = new DirRepository(directory.toFile(), true);

        var failure = assertThrows(UncheckedIOException.class, repository::getModuleNames);
        assertTrue(failure.getMessage().contains(file.toString()));
        assertNotNull(failure.getCause());
        assertThrows(UncheckedIOException.class, () -> repository.loadModule("Broken"));

        new FileStructure("Broken").writeTo(file.toFile());
        assertNotNull(repository.loadModule("Broken"));
    }
}
