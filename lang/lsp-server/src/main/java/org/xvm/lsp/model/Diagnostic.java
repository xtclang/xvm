package org.xvm.lsp.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Immutable diagnostic (error, warning, hint).
 */
public record Diagnostic(
        @NonNull Location location,
        @NonNull Severity severity,
        @NonNull String message,
        @Nullable String code,
        @Nullable String source
) {
    public enum Severity {
        ERROR,
        WARNING,
        INFORMATION,
        HINT
    }

    public static Diagnostic error(
            final @NonNull Location location,
            final @NonNull String message) {
        return new Diagnostic(location, Severity.ERROR, message, null, "xtc");
    }

    public static Diagnostic warning(
            final @NonNull Location location,
            final @NonNull String message) {
        return new Diagnostic(location, Severity.WARNING, message, null, "xtc");
    }

    public static Diagnostic info(
            final @NonNull Location location,
            final @NonNull String message) {
        return new Diagnostic(location, Severity.INFORMATION, message, null, "xtc");
    }
}
