package org.xvm.asm;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Captures the repositories' existing console reports without adding a reporting API for tests.
 * Isolation keeps temporary process-wide output redirection separate from other test classes.
 */
@Isolated
abstract class RepositoryFailureTestSupport {
    @BeforeEach
    void captureOutput() {
        original = System.out;
        captured = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        System.setOut(captured);
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(original);
        captured.close();
    }

    String output() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    void clearOutput() {
        bytes.reset();
    }

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private PrintStream original;
    private PrintStream captured;
}
