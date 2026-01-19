package org.xvm.lsp.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("CompilationResult")
class CompilationResultTest {

    @Test
    @DisplayName("success() should create successful result")
    fun successShouldCreateSuccessfulResult() {
        val uri = "file:///test.x"
        val symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS, Location.of(uri, 0, 0))

        val result = CompilationResult.success(uri, listOf(symbol))

        assertThat(result.success).isTrue()
        assertThat(result.diagnostics).isEmpty()
        assertThat(result.symbols).hasSize(1)
    }

    @Test
    @DisplayName("failure() should create failed result")
    fun failureShouldCreateFailedResult() {
        val uri = "file:///test.x"
        val error = Diagnostic.error(Location.of(uri, 0, 0), "Syntax error")

        val result = CompilationResult.failure(uri, listOf(error))

        assertThat(result.success).isFalse()
        assertThat(result.diagnostics).hasSize(1)
        assertThat(result.symbols).isEmpty()
    }

    @Test
    @DisplayName("withDiagnostics() with errors should mark as failure")
    fun withDiagnosticsWithErrorsShouldMarkAsFailure() {
        val uri = "file:///test.x"
        val error = Diagnostic.error(Location.of(uri, 0, 0), "Error")
        val symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS, Location.of(uri, 0, 0))

        val result = CompilationResult.withDiagnostics(uri, listOf(error), listOf(symbol))

        assertThat(result.success).isFalse()
        assertThat(result.diagnostics).hasSize(1)
        assertThat(result.symbols).hasSize(1)
    }

    @Test
    @DisplayName("withDiagnostics() with only warnings should mark as success")
    fun withDiagnosticsWithOnlyWarningsShouldMarkAsSuccess() {
        val uri = "file:///test.x"
        val warning = Diagnostic.warning(Location.of(uri, 0, 0), "Warning")
        val symbol = SymbolInfo.of("Test", SymbolInfo.SymbolKind.CLASS, Location.of(uri, 0, 0))

        val result = CompilationResult.withDiagnostics(uri, listOf(warning), listOf(symbol))

        assertThat(result.success).isTrue()
        assertThat(result.diagnostics).hasSize(1)
    }

    @Test
    @DisplayName("lists should be immutable copies")
    fun listsShouldBeImmutableCopies() {
        val uri = "file:///test.x"
        val diagnostics = mutableListOf<Diagnostic>()
        diagnostics.add(Diagnostic.warning(Location.of(uri, 0, 0), "Warning"))

        val result = CompilationResult.withDiagnostics(uri, diagnostics, emptyList())

        diagnostics.clear()

        assertThat(result.diagnostics).hasSize(1)
    }
}
