package org.xvm.api;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileRepository;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingRepositoryFailureTest {
    @TempDir
    Path directory;

    @Test
    void incompleteSourcePreservesSeparateRepositoryReadReports() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var reports = new HashMap<File, IOException>();
        var support = new EmbeddingSupport().configure(new FileRepository(file.toFile(), true, reports::put), null);
        var errors = new ErrorList();

        var result = support.analyzeIncomplete(new Source("module Test { void run() { console."), null, errors);

        assertNull(result.pool());
        assertTrue(errors.hasError(Parser.UNEXPECTED_EOF));
        assertTrue(reports.containsKey(file.toFile()));
        assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
    }

    @Test
    void moduleCompilerDoesNotAttributeAnUnreadableFileToAMissingModule() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var reports = new HashMap<File, IOException>();
        var compiler = new ModuleCompiler(new FileRepository(file.toFile(), true, reports::put));
        var errors = new ErrorList();

        assertNull(compiler.compile("module Test {}", null, errors));
        assertTrue(errors.hasSeriousErrors());
        assertTrue(reports.containsKey(file.toFile()));
        assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
    }

    @Test
    void embeddingDoesNotAttributeAnUnreadableFileToAMissingModule() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var reports = new HashMap<File, IOException>();
        List.of(new FileRepository(file.toFile(), true, reports::put),
                new DirRepository(directory.toFile(), true, reports::put)).forEach(repository -> {
            reports.clear();
            var support = new EmbeddingSupport().configure(repository, null);
            var errors = new ErrorList();

            var result = support.compileModule(new Source("module Test {}"), null, errors);

            assertFalse(result.succeeded());
            assertTrue(errors.hasSeriousErrors());
            assertTrue(reports.containsKey(file.toFile()));
            assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
        });
    }
}
