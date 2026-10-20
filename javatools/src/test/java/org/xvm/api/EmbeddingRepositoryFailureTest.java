package org.xvm.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileRepository;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that repository console reports remain separate from source diagnostics while embedding
 * hosts compile complete or incomplete source. Isolation prevents temporary console capture from
 * affecting other tests; the original stream is restored after each test.
 */
@Isolated
class EmbeddingRepositoryFailureTest {
    @TempDir
    Path directory;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PrintStream original;
    private PrintStream captured;

    @BeforeEach
    void captureRepositoryOutput() {
        original = System.out;
        captured = new PrintStream(output, true, StandardCharsets.UTF_8);
        System.setOut(captured);
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(original);
        captured.close();
    }

    @Test
    void incompleteSourcePreservesSeparateRepositoryReadReports() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var support = new EmbeddingSupport().configure(new FileRepository(file.toFile(), true), null);
        var errors = new ErrorList();

        var result = support.analyzeIncomplete(new Source("module Test { void run() { console."), null, errors);

        assertNull(result.pool());
        assertTrue(errors.hasError(Parser.UNEXPECTED_EOF));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains(file.toString()));
        assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
    }

    @Test
    void moduleCompilerDoesNotAttributeAnUnreadableFileToAMissingModule() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        var compiler = new ModuleCompiler(new FileRepository(file.toFile(), true));
        var errors = new ErrorList();

        assertNull(compiler.compile("module Test {}", null, errors));
        assertTrue(errors.hasSeriousErrors());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains(file.toString()));
        assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
    }

    @Test
    void embeddingDoesNotAttributeAnUnreadableFileToAMissingModule() throws IOException {
        Path file = directory.resolve("broken.xtc");
        Files.writeString(file, "not a compiled module");
        List.of(new FileRepository(file.toFile(), true),
                new DirRepository(directory.toFile(), true)).forEach(repository -> {
            output.reset();
            var support = new EmbeddingSupport().configure(repository, null);
            var errors = new ErrorList();

            var result = support.compileModule(new Source("module Test {}"), null, errors);

            assertFalse(result.succeeded());
            assertTrue(errors.hasSeriousErrors());
            assertTrue(output.toString(StandardCharsets.UTF_8).contains(file.toString()));
            assertFalse(errors.getErrors().stream().anyMatch(error -> error.getMessage().contains("broken.xtc")));
        });
    }
}
