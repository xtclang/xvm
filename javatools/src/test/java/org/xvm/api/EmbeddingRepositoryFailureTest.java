package org.xvm.api;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileRepository;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void incompleteSourceDoesNotHideAnUnexpectedRepositoryFailure() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var support = new EmbeddingSupport().configure(new FileRepository(file.toFile(), true), null);
        var errors = new ErrorList();

        var result = support.analyzeIncomplete(new Source("module Test { void run() { console."), null, errors);

        assertTrue(result.pool().isEmpty());
        assertTrue(errors.hasError(Parser.UNEXPECTED_EOF));
        assertTrue(errors.hasError("EMB-5"));
        assertTrue(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
    }

    @Test
    void corruptRepositoryReachesTheEmbeddingListener() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        List.of(new FileRepository(file.toFile(), true),
                new DirRepository(directory.toFile(), true)).forEach(repository -> {
            var support = new EmbeddingSupport().configure(repository, null);
            var errors = new ErrorList();

            var result = support.compileModule(new Source("module Test {}"), null, errors);

            assertFalse(result.succeeded());
            assertTrue(errors.getErrors().stream().anyMatch(error -> error.getCode().equals("EMB-5")));
            assertTrue(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
        });
    }
}
