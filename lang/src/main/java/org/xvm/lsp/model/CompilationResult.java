package org.xvm.lsp.model;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Immutable result of compiling an XTC source file.
 * This is what the adapter layer produces from the compiler.
 */
public record CompilationResult(
        @NonNull String uri,
        @NonNull List<Diagnostic> diagnostics,
        @NonNull List<SymbolInfo> symbols,
        boolean success
) {
    public CompilationResult {
        diagnostics = List.copyOf(diagnostics);
        symbols = List.copyOf(symbols);
    }

    public static CompilationResult success(
            final @NonNull String uri,
            final @NonNull List<SymbolInfo> symbols) {
        return new CompilationResult(uri, List.of(), symbols, true);
    }

    public static CompilationResult withDiagnostics(
            final @NonNull String uri,
            final @NonNull List<Diagnostic> diagnostics,
            final @NonNull List<SymbolInfo> symbols) {
        final boolean hasErrors = diagnostics.stream()
                .anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        return new CompilationResult(uri, diagnostics, symbols, !hasErrors);
    }

    public static CompilationResult failure(
            final @NonNull String uri,
            final @NonNull List<Diagnostic> diagnostics) {
        return new CompilationResult(uri, diagnostics, List.of(), false);
    }
}
