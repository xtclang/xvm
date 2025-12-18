package org.xvm.lsp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompilationResult")
class CompilationResultTest {

    @Test
    @DisplayName("success() should create successful result")
    void successShouldCreateSuccessfulResult() {
        final String uri = "file:///test.x";
        final SymbolInfo symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS,
                Location.of(uri, 0, 0));

        final CompilationResult result = CompilationResult.success(uri, List.of(symbol));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.symbols()).hasSize(1);
    }

    @Test
    @DisplayName("failure() should create failed result")
    void failureShouldCreateFailedResult() {
        final String uri = "file:///test.x";
        final Diagnostic error = Diagnostic.error(Location.of(uri, 0, 0), "Syntax error");

        final CompilationResult result = CompilationResult.failure(uri, List.of(error));

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.symbols()).isEmpty();
    }

    @Test
    @DisplayName("withDiagnostics() with errors should mark as failure")
    void withDiagnosticsWithErrorsShouldMarkAsFailure() {
        final String uri = "file:///test.x";
        final Diagnostic error = Diagnostic.error(Location.of(uri, 0, 0), "Error");
        final SymbolInfo symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS,
                Location.of(uri, 0, 0));

        final CompilationResult result = CompilationResult.withDiagnostics(
                uri, List.of(error), List.of(symbol));

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.symbols()).hasSize(1);
    }

    @Test
    @DisplayName("withDiagnostics() with only warnings should mark as success")
    void withDiagnosticsWithOnlyWarningsShouldMarkAsSuccess() {
        final String uri = "file:///test.x";
        final Diagnostic warning = Diagnostic.warning(Location.of(uri, 0, 0), "Warning");
        final SymbolInfo symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS,
                Location.of(uri, 0, 0));

        final CompilationResult result = CompilationResult.withDiagnostics(
                uri, List.of(warning), List.of(symbol));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).hasSize(1);
    }

    @Test
    @DisplayName("lists should be immutable copies")
    void listsShouldBeImmutableCopies() {
        final String uri = "file:///test.x";
        final List<Diagnostic> diagnostics = new java.util.ArrayList<>();
        diagnostics.add(Diagnostic.warning(Location.of(uri, 0, 0), "Warning"));

        final CompilationResult result = CompilationResult.withDiagnostics(
                uri, diagnostics, List.of());

        diagnostics.clear();

        assertThat(result.diagnostics()).hasSize(1);
    }
}
