package org.xvm.tool;

import org.junit.jupiter.api.Test;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.util.Severity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.xvm.util.Severity.*;

/**
 * Unit tests for Launcher error handling, logging, and abort mechanisms.
 * Tests the error accumulation, severity tracking, and checkErrors() behavior.
 */
class LauncherErrorHandlingTest {

    /**
     * Custom console that captures log output for testing.
     */
    private static class TestConsole implements Console {
        private final List<String> messages = new ArrayList<>();
        private final List<Severity> severities = new ArrayList<>();

        @Override
        public void out(Object o) {
            // Capture stdout
        }

        @Override
        public void err(Object o) {
            // Capture stderr
        }

        @Override
        public void log(Severity sev, String sMsg) {
            severities.add(sev);
            messages.add(sMsg);
        }

        public List<String> getMessages() {
            return messages;
        }

        public List<Severity> getSeverities() {
            return severities;
        }

        public void clear() {
            messages.clear();
            severities.clear();
        }
    }

    /**
     * Custom error listener that captures errors for testing.
     */
    private static class TestErrorListener implements ErrorListener {
        private final List<String> errors = new ArrayList<>();
        private final List<Severity> severities = new ArrayList<>();
        private Severity worstSeverity = Severity.NONE;

        @Override
        public boolean log(ErrorInfo err) {
            severities.add(err.getSeverity());
            errors.add(err.getCode() + ": " + err.toString());
            if (err.getSeverity().compareTo(worstSeverity) > 0) {
                worstSeverity = err.getSeverity();
            }
            return false; // Don't abort
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<Severity> getSeverities() {
            return severities;
        }

        public Severity getWorstSeverity() {
            return worstSeverity;
        }

        public void clear() {
            errors.clear();
            severities.clear();
            worstSeverity = Severity.NONE;
        }
    }

    /**
     * Test compiler that allows us to inject errors for testing.
     */
    private static class TestCompiler extends Compiler {
        public TestCompiler(CompilerOptions options, Console console, ErrorListener errListener) {
            super(options, console, errListener);
        }

        @Override
        protected int process() {
            // Override to prevent actual compilation
            return 0;
        }

        // Expose protected methods for testing
        public void testLog(Severity sev, String template, Object... params) {
            log(sev, template, params);
        }

        public void testLogWithThrowable(Severity sev, Throwable cause, String template, Object... params) {
            log(sev, cause, template, params);
        }

        public void testCheckErrors() {
            checkErrors();
        }

        public Severity testGetWorstSeverity() {
            return m_sevWorst;
        }

        public boolean testIsBadEnoughToAbort(Severity sev) {
            return isBadEnoughToAbort(sev);
        }
    }

    @Test
    void testInfoLogging() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .enableVerbose() // Enable verbose to see INFO messages
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(INFO, "Test info message");

        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().get(0).contains("Test info message"));
        assertEquals(INFO, console.getSeverities().get(0));
        assertEquals(INFO, compiler.testGetWorstSeverity());
    }

    @Test
    void testWarningLogging() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(WARNING, "Test warning: {}", "detail");

        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().get(0).contains("Test warning: detail"));
        assertEquals(WARNING, console.getSeverities().get(0));
        assertEquals(WARNING, compiler.testGetWorstSeverity());
    }

    @Test
    void testErrorLogging() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(ERROR, "Test error: {} at line {}", "syntax", 42);

        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().get(0).contains("Test error: syntax at line 42"));
        assertEquals(ERROR, console.getSeverities().get(0));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testFatalLogging() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(FATAL, "Fatal error occurred");

        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().get(0).contains("Fatal error occurred"));
        assertEquals(FATAL, console.getSeverities().get(0));
        assertEquals(FATAL, compiler.testGetWorstSeverity());
    }

    @Test
    void testSeverityTracking() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        // Log multiple messages with different severities
        compiler.testLog(INFO, "Info message");
        assertEquals(INFO, compiler.testGetWorstSeverity());

        compiler.testLog(WARNING, "Warning message");
        assertEquals(WARNING, compiler.testGetWorstSeverity());

        compiler.testLog(ERROR, "Error message");
        assertEquals(ERROR, compiler.testGetWorstSeverity());

        compiler.testLog(INFO, "Another info");
        assertEquals(ERROR, compiler.testGetWorstSeverity()); // Should remain ERROR

        // Only WARNING and ERROR are printed (INFO is filtered unless verbose)
        assertEquals(2, console.getMessages().size());
    }

    @Test
    void testCheckErrorsWithInfo() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(INFO, "Info message");

        // Should not throw - INFO is not bad enough to abort
        assertDoesNotThrow(() -> compiler.testCheckErrors());
    }

    @Test
    void testCheckErrorsWithWarning() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(WARNING, "Warning message");

        // Should not throw - WARNING is not bad enough to abort (in base Launcher)
        assertDoesNotThrow(() -> compiler.testCheckErrors());
    }

    @Test
    void testCheckErrorsWithError() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(ERROR, "Error message");

        // Should throw - ERROR is bad enough to abort
        assertThrows(LauncherException.class, () -> compiler.testCheckErrors());
    }

    @Test
    void testCheckErrorsWithFatal() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(FATAL, "Fatal error");

        // Should throw - FATAL is bad enough to abort
        assertThrows(LauncherException.class, () -> compiler.testCheckErrors());
    }

    @Test
    void testLogWithThrowable() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, "Failed to read file: {}", "test.x");

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().get(0);
        assertTrue(message.contains("Failed to read file: test.x"));
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testLogWithThrowableNoTemplate() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, null);

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().get(0);
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testLogWithThrowableEmptyTemplate() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, "");

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().get(0);
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testIsBadEnoughToAbort() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        // Test base Launcher behavior: ERROR and worse should abort
        assertFalse(compiler.testIsBadEnoughToAbort(Severity.NONE));
        assertFalse(compiler.testIsBadEnoughToAbort(INFO));
        assertFalse(compiler.testIsBadEnoughToAbort(WARNING));
        assertTrue(compiler.testIsBadEnoughToAbort(ERROR));
        assertTrue(compiler.testIsBadEnoughToAbort(FATAL));
    }

    @Test
    void testErrorAccumulation() {
        TestConsole console = new TestConsole();
        TestErrorListener errorListener = new TestErrorListener();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, errorListener);

        // Log multiple errors
        compiler.testLog(INFO, "Info 1");
        compiler.testLog(WARNING, "Warning 1");
        compiler.testLog(ERROR, "Error 1");
        compiler.testLog(WARNING, "Warning 2");
        compiler.testLog(ERROR, "Error 2");

        // Worst severity should be ERROR
        assertEquals(ERROR, compiler.testGetWorstSeverity());

        // Only WARNING and ERROR are printed to console (INFO filtered unless verbose)
        assertEquals(4, console.getMessages().size());

        // checkErrors should throw because worst is ERROR
        assertThrows(LauncherException.class, () -> compiler.testCheckErrors());
    }

    @Test
    void testLogAndAbortThrowsImmediately() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        // logAndAbort should throw immediately
        LauncherException ex = assertThrows(LauncherException.class, () -> {
            throw compiler.logAndAbort(ERROR, "Fatal error: {}", "test");
        });

        // Should have logged before throwing
        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().get(0).contains("Fatal error: test"));
    }

    @Test
    void testLogAndAbortWithThrowable() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("I/O error");

        // logAndAbort should throw immediately
        LauncherException ex = assertThrows(LauncherException.class, () -> {
            throw compiler.logAndAbort(FATAL, cause, "Failed: {}",  "operation");
        });

        // Should have logged before throwing
        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().get(0);
        assertTrue(message.contains("Failed: operation"));
        assertTrue(message.contains("I/O error"));
    }

    @Test
    void testMultipleCheckErrorsCalls() {
        TestConsole console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        // First checkpoint - should not throw
        compiler.testLog(INFO, "Info 1");
        assertDoesNotThrow(() -> compiler.testCheckErrors());

        // Second checkpoint - should not throw
        compiler.testLog(WARNING, "Warning 1");
        assertDoesNotThrow(() -> compiler.testCheckErrors());

        // Third checkpoint - should throw
        compiler.testLog(ERROR, "Error 1");
        assertThrows(LauncherException.class, () -> compiler.testCheckErrors());
    }

    @Test
    void testSeverityOrdering() {
        // Verify severity comparison works as expected
        assertTrue(INFO.compareTo(Severity.NONE) > 0);
        assertTrue(WARNING.compareTo(INFO) > 0);
        assertTrue(ERROR.compareTo(WARNING) > 0);
        assertTrue(FATAL.compareTo(ERROR) > 0);
    }
}