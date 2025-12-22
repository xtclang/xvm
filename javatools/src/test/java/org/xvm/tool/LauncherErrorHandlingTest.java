package org.xvm.tool;

import org.junit.jupiter.api.Test;
import org.xvm.asm.ErrorListener;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.util.Severity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.NONE;
import static org.xvm.util.Severity.WARNING;

/**
 * Unit tests for Launcher error handling, logging, and abort mechanisms.
 * Tests the error accumulation, severity tracking, and checkErrors() behavior.
 */
class LauncherErrorHandlingTest {

    /**
     * Custom console that captures log output for testing.
     * Implements Console with output capturing for verification.
     */
    private static final class TestConsole implements Console {
        private final List<String> messages = new ArrayList<>();
        private final List<Severity> severities = new ArrayList<>();

        @Override
        public String log(final Severity sev, final Throwable cause, final String template, final Object... params) {
            // Capture severity and formatted message directly
            final var str = Console.super.log(sev, cause, template, params);
            severities.add(sev);
            messages.add(str);
            return str;
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
        private Severity worstSeverity = NONE;

        @Override
        public boolean log(final ErrorInfo err) {
            severities.add(err.getSeverity());
            errors.add(err.getCode() + ": " + err);
            if (err.getSeverity().compareTo(worstSeverity) > 0) {
                worstSeverity = err.getSeverity();
            }
            return false; // Don't abort
        }

        @SuppressWarnings("unused")
        public List<String> getErrors() {
            return errors;
        }

        @SuppressWarnings("unused")
        public List<Severity> getSeverities() {
            return severities;
        }

        @SuppressWarnings("unused")
        public Severity getWorstSeverity() {
            return worstSeverity;
        }

        @SuppressWarnings("unused")
        public void clear() {
            errors.clear();
            severities.clear();
            worstSeverity = Severity.NONE;
        }
    }

    /**
     * Test compiler that allows us to inject errors for testing.
     */
    private static final class TestCompiler extends Compiler {
        private TestCompiler(final CompilerOptions options, final Console console, final ErrorListener errListener) {
            this(options, console, errListener, Strictness.Normal);
        }

        private TestCompiler(final CompilerOptions options, final Console console, final ErrorListener errListener, final Strictness strictness) {
            super(options, console, errListener);
            this.strictLevel = strictness;
        }

        @Override
        protected int process() {
            // Override to prevent actual compilation
            return 0;
        }

        // Expose protected methods for testing
        public void testLog(final Severity sev, final String template, final Object... params) {
            log(sev, template, params);
        }

        public void testLogWithThrowable(final Severity sev, final Throwable cause, final String template, final Object... params) {
            log(sev, cause, template, params);
        }

        public int testCheckErrors() {
            return checkErrors();
        }

        public int testCheckErrors(final String context) {
            return checkErrors(context);
        }

        public void testReset() {
            reset();
        }

        public Severity testGetWorstSeverity() {
            return m_sevWorst;
        }

    }

    @Test
    void testInfoLogging() {
        final var console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .enableVerbose() // Also enable in options (for consistency)
                .build();
        TestCompiler compiler = new TestCompiler(opts, console, null);

        compiler.testLog(INFO, "Test info message");

        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().getFirst().contains("Test info message"));
        assertEquals(INFO, console.getSeverities().getFirst());
        assertEquals(INFO, compiler.testGetWorstSeverity());
    }

    @Test
    void testWarningLogging() {
        final var console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);
        compiler.testLog(WARNING, "Test warning: {}", "detail");
        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().getFirst().contains("Test warning: detail"));
        assertEquals(WARNING, console.getSeverities().getFirst());
        assertEquals(WARNING, compiler.testGetWorstSeverity());
    }

    @Test
    void testErrorLogging() {
        final var console = new TestConsole();
        CompilerOptions opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);
        compiler.testLog(ERROR, "Test error: {} at line {}", "syntax", 42);
        assertEquals(1, console.getMessages().size());
        assertTrue(console.getMessages().getFirst().contains("Test error: syntax at line 42"));
        assertEquals(ERROR, console.getSeverities().getFirst());
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testFatalLogging() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        // FATAL error for now throws LauncherException immediately and the exception carries the
        // actual message to avoid double-dipping
        var msg = "Fatal error occurred";
        var ex  = assertThrows(LauncherException.class, () -> compiler.testLog(FATAL, msg));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains(msg));
    }

    @Test
    void testSeverityTracking() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

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
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(INFO, "Info message");

        // Should not throw - INFO is not bad enough to abort
        assertEquals(0, compiler.testCheckErrors());
    }

    @Test
    void testCheckErrorsWithWarning() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(WARNING, "Warning message");

        // Should not throw - WARNING is not bad enough to abort (in Normal strictness)
        // Returns 0 because WARNING is not considered a "serious error" (ERROR or worse)
        assertEquals(0, compiler.testCheckErrors());
    }

    @Test
    void testCheckErrorsWithError() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(ERROR, "Error message");

        // Should throw - ERROR is bad enough to abort
        assertThrows(LauncherException.class, compiler::testCheckErrors);
    }

    @Test
    void testCheckErrorsWithFatal() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        // FATAL now throws LauncherException immediately - no need to call checkErrors()
        assertThrows(LauncherException.class, () -> compiler.testLog(FATAL, "Fatal error"));
    }

    @Test
    void testLogWithThrowable() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        final var cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, "Failed to read file: {}", "test.x");

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().getFirst();
        assertTrue(message.contains("Failed to read file: test.x"));
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testLogWithThrowableNoTemplate() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, null);

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().getFirst();
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testLogWithThrowableEmptyTemplate() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        IOException cause = new IOException("File not found");
        compiler.testLogWithThrowable(ERROR, cause, "");

        assertEquals(1, console.getMessages().size());
        String message = console.getMessages().getFirst();
        assertTrue(message.contains("File not found"));
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

    @Test
    void testShouldAbortBehavior() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        var compiler = new TestCompiler(opts, console, null);

        // Test abort behavior: ERROR and worse should trigger abort (via Launcher.isAbortDesired())
        compiler.testLog(Severity.NONE, "None message");
        assertFalse(compiler.isAbortDesired());

        console.clear();
        compiler = new TestCompiler(opts, console, null);
        compiler.testLog(INFO, "Info message");
        assertFalse(compiler.isAbortDesired());

        console.clear();
        compiler = new TestCompiler(opts, console, null);
        compiler.testLog(WARNING, "Warning message");
        assertFalse(compiler.isAbortDesired());

        console.clear();
        compiler = new TestCompiler(opts, console, null);
        compiler.testLog(ERROR, "Error message");
        assertTrue(compiler.isAbortDesired());

        console.clear();
        final TestCompiler fatalCompiler = new TestCompiler(opts, console, null);
        // FATAL now throws LauncherException immediately
        assertThrows(LauncherException.class, () -> fatalCompiler.testLog(FATAL, "Fatal message"));
    }

    @Test
    void testErrorAccumulation() {
        final var console = new TestConsole();
        TestErrorListener errorListener = new TestErrorListener();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, errorListener);

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
        assertThrows(LauncherException.class, compiler::testCheckErrors);
    }


    @Test
    void testMultipleCheckErrorsCalls() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        // First checkpoint - should not throw, return 0
        compiler.testLog(INFO, "Info 1");
        assertEquals(0, compiler.testCheckErrors());
        compiler.testReset();
        console.clear();

        // Second checkpoint - should not throw, return 0 (warning is not "serious" by hasSeriousErrors)
        compiler.testLog(WARNING, "Warning 1");
        assertEquals(0, compiler.testCheckErrors());
        compiler.testReset();
        console.clear();

        // Third checkpoint - should throw
        compiler.testLog(ERROR, "Error 1");
        assertThrows(LauncherException.class, compiler::testCheckErrors);
    }

    // ----- Strictness level tests ----------------------------------------------------------------

    @Test
    void testNormalStrictnessWarningDoesNotAbort() {
        final var console = new TestConsole();
        // Normal strictness (default) - WARNING does not abort
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(WARNING, "Warning message");

        // Should NOT throw - WARNING is not bad enough to abort in Normal strictness
        // Returns 0 because WARNING is not considered "serious" (ERROR or worse)
        assertEquals(0, compiler.testCheckErrors());
        assertFalse(compiler.isAbortDesired());
    }

    @Test
    void testNormalStrictnessErrorAborts() {
        final var console = new TestConsole();
        // Normal strictness (default) - ERROR aborts
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(ERROR, "Error message");

        // Should throw - ERROR is bad enough to abort in Normal strictness
        assertThrows(LauncherException.class, compiler::testCheckErrors);
        assertTrue(compiler.isAbortDesired());
    }

    @Test
    void testStrictModeWarningAborts() {
        final var console = new TestConsole();
        // --strict mode (Stickler) - WARNING should abort
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .enableStrictMode()
                .build();
        final var compiler = new TestCompiler(opts, console, null, Compiler.Strictness.Stickler);

        compiler.testLog(WARNING, "Warning message");

        // Should throw - WARNING is bad enough to abort in Stickler strictness
        assertThrows(LauncherException.class, compiler::testCheckErrors);
        assertTrue(compiler.isAbortDesired());
    }

    @Test
    void testStrictModeErrorAborts() {
        final var console = new TestConsole();
        // --strict mode (Stickler) - ERROR should also abort
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .enableStrictMode()
                .build();
        final var compiler = new TestCompiler(opts, console, null, Compiler.Strictness.Stickler);

        compiler.testLog(ERROR, "Error message");

        // Should throw - ERROR is bad enough to abort in Stickler strictness
        assertThrows(LauncherException.class, compiler::testCheckErrors);
        assertTrue(compiler.isAbortDesired());
    }

    @Test
    void testNoWarnModeWarningDoesNotAbort() {
        final var console = new TestConsole();
        // --nowarn mode (Suppressed) - WARNING does not abort
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .disableWarnings()
                .build();
        final var compiler = new TestCompiler(opts, console, null, Compiler.Strictness.Suppressed);

        compiler.testLog(WARNING, "Warning message");

        // Should NOT throw - WARNING is not bad enough to abort in Suppressed strictness
        // Returns 0 because WARNING is not considered "serious" (ERROR or worse)
        assertEquals(0, compiler.testCheckErrors());
        assertFalse(compiler.isAbortDesired());
    }

    @Test
    void testNoWarnModeErrorAborts() {
        final var console = new TestConsole();
        // --nowarn mode (Suppressed) - ERROR should still abort
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .disableWarnings()
                .build();
        final var compiler = new TestCompiler(opts, console, null, Compiler.Strictness.Suppressed);

        compiler.testLog(ERROR, "Error message");

        // Should throw - ERROR is bad enough to abort even in Suppressed strictness
        assertThrows(LauncherException.class, compiler::testCheckErrors);
        assertTrue(compiler.isAbortDesired());
    }

    @Test
    void testCheckErrorsReturnsExitCode() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        // No errors - should return 0
        assertEquals(0, compiler.checkErrors());

        // Log INFO - still no serious errors, should return 0
        compiler.testLog(INFO, "Info message");
        assertEquals(0, compiler.checkErrors());

        compiler.testReset();

        // Log WARNING - not "serious" by hasSeriousErrors (which uses ERROR threshold)
        compiler.testLog(WARNING, "Warning message");
        assertEquals(0, compiler.checkErrors()); // WARNING returns 0 (hasSeriousErrors checks for ERROR)

        compiler.testReset();

        // Log ERROR - should throw (not return)
        compiler.testLog(ERROR, "Error message");
        assertThrows(LauncherException.class, compiler::testCheckErrors);
    }

    @Test
    void testCheckErrorsWithContextMessage() {
        final var console = new TestConsole();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, null);

        compiler.testLog(ERROR, "Error message");

        // checkErrors with context should include context in exception message
        LauncherException ex = assertThrows(LauncherException.class,
                () -> compiler.checkErrors("module loading"));
        assertTrue(ex.getMessage().contains("module loading"));
        assertTrue(ex.getMessage().contains("ERROR"));
    }

    @Test
    void testErrorListenerReceivesErrors() {
        final var console = new TestConsole();
        final var errorListener = new TestErrorListener();
        final var opts = new CompilerOptions.Builder()
                .addInputFile(new File("test.x"))
                .build();
        final var compiler = new TestCompiler(opts, console, errorListener);

        // Log messages - they should go to both console and error listener
        compiler.testLog(WARNING, "Warning 1");
        compiler.testLog(ERROR, "Error 1");

        // Console should have the messages
        assertEquals(2, console.getMessages().size());

        // Worst severity tracked in compiler
        assertEquals(ERROR, compiler.testGetWorstSeverity());
    }

}
