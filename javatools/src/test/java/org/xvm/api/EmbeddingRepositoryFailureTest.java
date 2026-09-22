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
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void corruptRepositoryReachesTheEmbeddingListener() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        for (var repository : List.of(new FileRepository(file.toFile(), true),
                new DirRepository(directory.toFile(), true))) {
            var support = new EmbeddingSupport().configure(repository, null);
            var errors = new ErrorList();

            var result = support.compileModule(new Source("module Test {}"), null, errors);

            assertFalse(result.succeeded());
            assertTrue(errors.getErrors().stream().anyMatch(error -> error.getCode().equals("EMB-5")));
            assertTrue(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
        }
    }
}
