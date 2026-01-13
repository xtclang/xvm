package org.xvm.lsp.model;

import org.jspecify.annotations.NonNull;

/**
 * Immutable source location.
 */
public record Location(
        @NonNull String uri,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
) {
    public Location {
        if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
            throw new IllegalArgumentException("Positions must be non-negative");
        }
    }

    public static Location of(
            final @NonNull String uri,
            final int line,
            final int column) {
        return new Location(uri, line, column, line, column);
    }

    public static Location ofLine(final @NonNull String uri, final int line) {
        return new Location(uri, line, 0, line, Integer.MAX_VALUE);
    }
}
