package org.xvm.compiler2.parser;

import java.util.Objects;

/**
 * An immutable diagnostic message (error, warning, info).
 * <p>
 * Diagnostics are created during parsing/compilation and collected
 * for reporting. They contain all information needed to display
 * the diagnostic to the user including source location.
 */
public record Diagnostic(
        DiagnosticSeverity severity,
        String code,
        String message,
        TextSpan span
) {
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        // span can be null for diagnostics without a specific location
    }

    /**
     * Create an error diagnostic.
     *
     * @param code    the error code (e.g., "XC0001")
     * @param message the error message
     * @param span    the source span (may be null)
     * @return the diagnostic
     */
    public static Diagnostic error(String code, String message, TextSpan span) {
        return new Diagnostic(DiagnosticSeverity.ERROR, code, message, span);
    }

    /**
     * Create a warning diagnostic.
     *
     * @param code    the warning code
     * @param message the warning message
     * @param span    the source span (may be null)
     * @return the diagnostic
     */
    public static Diagnostic warning(String code, String message, TextSpan span) {
        return new Diagnostic(DiagnosticSeverity.WARNING, code, message, span);
    }

    /**
     * Create an info diagnostic.
     *
     * @param code    the info code
     * @param message the info message
     * @param span    the source span (may be null)
     * @return the diagnostic
     */
    public static Diagnostic info(String code, String message, TextSpan span) {
        return new Diagnostic(DiagnosticSeverity.INFO, code, message, span);
    }

    /**
     * @return true if this is an error
     */
    public boolean isError() {
        return severity.isError();
    }

    /**
     * @return the location string (file:line:column) or null if no span
     */
    public String getLocationString() {
        if (span == null) {
            return null;
        }
        return span.getStartLocation().toDisplayString();
    }

    /**
     * Format this diagnostic for display.
     *
     * @return a formatted string like "error XC0001: message"
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        // Location
        if (span != null) {
            sb.append(span.getStartLocation().toDisplayString());
            sb.append(": ");
        }

        // Severity and code
        sb.append(severity.name().toLowerCase());
        sb.append(" ");
        sb.append(code);
        sb.append(": ");

        // Message
        sb.append(message);

        return sb.toString();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
