package org.xvm.compiler2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.xvm.compiler2.parser.Diagnostic;
import org.xvm.compiler2.parser.DiagnosticSeverity;
import org.xvm.compiler2.parser.SourceText;
import org.xvm.compiler2.parser.TextSpan;

/**
 * Unified context for compilation phases (lexing, parsing, semantic analysis).
 * <p>
 * The context provides:
 * <ul>
 *   <li>Source text tracking with position information</li>
 *   <li>Diagnostic collection (errors, warnings, info)</li>
 *   <li>Logging via SLF4J-compatible API</li>
 *   <li>Compilation options and flags</li>
 * </ul>
 * <p>
 * This context is designed to be passed through all compilation phases
 * to ensure consistent error reporting and behavior.
 */
public final class CompilationContext {

    private final SourceText source;
    private final List<Diagnostic> diagnostics;
    private final Logger logger;
    private final CompilationOptions options;
    private boolean hasErrors;

    /**
     * Create a compilation context.
     *
     * @param source  the source text
     * @param logger  the logger
     * @param options compilation options
     */
    public CompilationContext(SourceText source, Logger logger, CompilationOptions options) {
        this.source = Objects.requireNonNull(source, "source");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.options = Objects.requireNonNull(options, "options");
        this.diagnostics = new ArrayList<>();
    }

    /**
     * Create a context with default options.
     *
     * @param source the source text
     * @param logger the logger
     */
    public CompilationContext(SourceText source, Logger logger) {
        this(source, logger, CompilationOptions.DEFAULT);
    }

    /**
     * Create a context with default logger and options.
     *
     * @param source the source text
     */
    public CompilationContext(SourceText source) {
        this(source, Logger.NULL, CompilationOptions.DEFAULT);
    }

    // -------------------------------------------------------------------------
    // Source access
    // -------------------------------------------------------------------------

    /**
     * @return the source text
     */
    public SourceText getSource() {
        return source;
    }

    /**
     * @return the source file name
     */
    public String getFileName() {
        return source.getFileName();
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    /**
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Log a trace message.
     */
    public void trace(String message) {
        logger.trace(message);
    }

    /**
     * Log a trace message with format arguments.
     */
    public void trace(String format, Object... args) {
        logger.trace(format, args);
    }

    /**
     * Log a debug message.
     */
    public void debug(String message) {
        logger.debug(message);
    }

    /**
     * Log a debug message with format arguments.
     */
    public void debug(String format, Object... args) {
        logger.debug(format, args);
    }

    /**
     * Log an info message.
     */
    public void info(String message) {
        logger.info(message);
    }

    /**
     * Log an info message with format arguments.
     */
    public void info(String format, Object... args) {
        logger.info(format, args);
    }

    /**
     * Log a warning message.
     */
    public void warn(String message) {
        logger.warn(message);
    }

    /**
     * Log a warning message with format arguments.
     */
    public void warn(String format, Object... args) {
        logger.warn(format, args);
    }

    /**
     * Log an error message.
     */
    public void error(String message) {
        logger.error(message);
    }

    /**
     * Log an error message with format arguments.
     */
    public void error(String format, Object... args) {
        logger.error(format, args);
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    /**
     * Report a diagnostic.
     *
     * @param diagnostic the diagnostic
     */
    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
        if (diagnostic.isError()) {
            hasErrors = true;
        }
        logger.debug("Diagnostic: {}", diagnostic);
    }

    /**
     * Report an error at the given span.
     *
     * @param code    the error code
     * @param message the error message
     * @param span    the source span
     */
    public void reportError(String code, String message, TextSpan span) {
        report(Diagnostic.error(code, message, span));
    }

    /**
     * Report an error at the given offset.
     *
     * @param code    the error code
     * @param message the error message
     * @param offset  the source offset
     * @param length  the length of the error span
     */
    public void reportError(String code, String message, int offset, int length) {
        report(Diagnostic.error(code, message, source.getSpan(offset, length)));
    }

    /**
     * Report a warning at the given span.
     *
     * @param code    the warning code
     * @param message the warning message
     * @param span    the source span
     */
    public void reportWarning(String code, String message, TextSpan span) {
        report(Diagnostic.warning(code, message, span));
    }

    /**
     * Report a warning at the given offset.
     *
     * @param code    the warning code
     * @param message the warning message
     * @param offset  the source offset
     * @param length  the length of the warning span
     */
    public void reportWarning(String code, String message, int offset, int length) {
        report(Diagnostic.warning(code, message, source.getSpan(offset, length)));
    }

    /**
     * @return true if any errors have been reported
     */
    public boolean hasErrors() {
        return hasErrors;
    }

    /**
     * @return the number of errors
     */
    public int getErrorCount() {
        return (int) diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
    }

    /**
     * @return the number of warnings
     */
    public int getWarningCount() {
        return (int) diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .count();
    }

    /**
     * @return an unmodifiable view of all diagnostics
     */
    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    /**
     * @return all error diagnostics
     */
    public List<Diagnostic> getErrors() {
        return diagnostics.stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    /**
     * @return all warning diagnostics
     */
    public List<Diagnostic> getWarnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Options
    // -------------------------------------------------------------------------

    /**
     * @return the compilation options
     */
    public CompilationOptions getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "CompilationContext[" + source.getFileName() + ", " +
                getErrorCount() + " errors, " + getWarningCount() + " warnings]";
    }
}
