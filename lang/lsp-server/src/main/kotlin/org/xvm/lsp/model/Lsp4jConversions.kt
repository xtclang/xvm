package org.xvm.lsp.model

import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.xvm.lsp.adapter.XtcCompilerAdapter

// Extension functions for converting between LSP model types and LSP4J types.
// Keeps the model classes pure while centralizing conversion logic.

/** Convert this Location to an LSP4J Range. */
fun Location.toRange(): Range =
    Range(
        Position(startLine, startColumn),
        Position(endLine, endColumn),
    )

/** Convert this Location to an LSP4J Location. */
fun Location.toLsp(): org.eclipse.lsp4j.Location = org.eclipse.lsp4j.Location(uri, toRange())

/** Create a Location from an LSP4J Range and URI. */
fun Location.Companion.fromLsp(
    uri: String,
    range: Range,
): Location =
    Location(
        uri,
        range.start.line,
        range.start.character,
        range.end.line,
        range.end.character,
    )

/** Convert this Severity to an LSP4J DiagnosticSeverity. */
fun Diagnostic.Severity.toLsp(): DiagnosticSeverity =
    when (this) {
        Diagnostic.Severity.ERROR -> DiagnosticSeverity.Error
        Diagnostic.Severity.WARNING -> DiagnosticSeverity.Warning
        Diagnostic.Severity.INFORMATION -> DiagnosticSeverity.Information
        Diagnostic.Severity.HINT -> DiagnosticSeverity.Hint
    }

/** Create a Severity from an LSP4J DiagnosticSeverity. */
fun Diagnostic.Severity.Companion.fromLsp(severity: DiagnosticSeverity?): Diagnostic.Severity =
    when (severity) {
        DiagnosticSeverity.Error -> Diagnostic.Severity.ERROR
        DiagnosticSeverity.Warning -> Diagnostic.Severity.WARNING
        DiagnosticSeverity.Information -> Diagnostic.Severity.INFORMATION
        DiagnosticSeverity.Hint -> Diagnostic.Severity.HINT
        null -> Diagnostic.Severity.INFORMATION
    }

/** Create a Diagnostic from an LSP4J Diagnostic. */
fun Diagnostic.Companion.fromLsp(
    uri: String,
    lspDiagnostic: org.eclipse.lsp4j.Diagnostic,
): Diagnostic =
    Diagnostic(
        location = Location.fromLsp(uri, lspDiagnostic.range),
        severity = Diagnostic.Severity.fromLsp(lspDiagnostic.severity),
        message = lspDiagnostic.message,
        code = lspDiagnostic.code?.left,
        source = lspDiagnostic.source,
    )

/** Convert this Diagnostic to an LSP4J Diagnostic. */
fun Diagnostic.toLsp(): org.eclipse.lsp4j.Diagnostic {
    val result = org.eclipse.lsp4j.Diagnostic()
    result.range = location.toRange()
    result.severity = severity.toLsp()
    result.message = message
    result.source = source
    if (code != null) {
        result.code = Either.forLeft(code)
    }
    return result
}

/** Convert this SymbolKind to an LSP4J SymbolKind. */
fun SymbolInfo.SymbolKind.toLsp(): SymbolKind =
    when (this) {
        SymbolInfo.SymbolKind.MODULE -> SymbolKind.Module
        SymbolInfo.SymbolKind.PACKAGE -> SymbolKind.Package
        SymbolInfo.SymbolKind.CLASS,
        SymbolInfo.SymbolKind.MIXIN,
        SymbolInfo.SymbolKind.SERVICE, -> SymbolKind.Class
        SymbolInfo.SymbolKind.INTERFACE -> SymbolKind.Interface
        SymbolInfo.SymbolKind.ENUM -> SymbolKind.Enum
        SymbolInfo.SymbolKind.CONST -> SymbolKind.Constant
        SymbolInfo.SymbolKind.METHOD -> SymbolKind.Method
        SymbolInfo.SymbolKind.PROPERTY -> SymbolKind.Property
        SymbolInfo.SymbolKind.PARAMETER -> SymbolKind.Variable
        SymbolInfo.SymbolKind.TYPE_PARAMETER -> SymbolKind.TypeParameter
        SymbolInfo.SymbolKind.CONSTRUCTOR -> SymbolKind.Constructor
    }

/** Convert adapter HighlightKind to LSP4J DocumentHighlightKind. */
fun XtcCompilerAdapter.DocumentHighlight.HighlightKind.toLsp(): DocumentHighlightKind =
    when (this) {
        XtcCompilerAdapter.DocumentHighlight.HighlightKind.TEXT -> DocumentHighlightKind.Text
        XtcCompilerAdapter.DocumentHighlight.HighlightKind.READ -> DocumentHighlightKind.Read
        XtcCompilerAdapter.DocumentHighlight.HighlightKind.WRITE -> DocumentHighlightKind.Write
    }

/** Convert adapter FoldingKind to LSP4J FoldingRangeKind. */
fun XtcCompilerAdapter.FoldingRange.FoldingKind.toLsp(): String =
    when (this) {
        XtcCompilerAdapter.FoldingRange.FoldingKind.COMMENT -> FoldingRangeKind.Comment
        XtcCompilerAdapter.FoldingRange.FoldingKind.IMPORTS -> FoldingRangeKind.Imports
        XtcCompilerAdapter.FoldingRange.FoldingKind.REGION -> FoldingRangeKind.Region
    }

/** Convert adapter InlayHintKind to LSP4J InlayHintKind. */
fun XtcCompilerAdapter.InlayHint.InlayHintKind.toLsp(): InlayHintKind =
    when (this) {
        XtcCompilerAdapter.InlayHint.InlayHintKind.TYPE -> InlayHintKind.Type
        XtcCompilerAdapter.InlayHint.InlayHintKind.PARAMETER -> InlayHintKind.Parameter
    }

/** Convert adapter Range to LSP4J Range. */
fun XtcCompilerAdapter.Range.toLsp(): Range =
    Range(
        Position(start.line, start.column),
        Position(end.line, end.column),
    )

/** Convert adapter CodeActionKind to LSP4J CodeActionKind string. */
fun XtcCompilerAdapter.CodeAction.CodeActionKind.toLsp(): String =
    when (this) {
        XtcCompilerAdapter.CodeAction.CodeActionKind.QUICKFIX -> CodeActionKind.QuickFix
        XtcCompilerAdapter.CodeAction.CodeActionKind.REFACTOR -> CodeActionKind.Refactor
        XtcCompilerAdapter.CodeAction.CodeActionKind.REFACTOR_EXTRACT -> CodeActionKind.RefactorExtract
        XtcCompilerAdapter.CodeAction.CodeActionKind.REFACTOR_INLINE -> CodeActionKind.RefactorInline
        XtcCompilerAdapter.CodeAction.CodeActionKind.REFACTOR_REWRITE -> CodeActionKind.RefactorRewrite
        XtcCompilerAdapter.CodeAction.CodeActionKind.SOURCE -> CodeActionKind.Source
        XtcCompilerAdapter.CodeAction.CodeActionKind.SOURCE_ORGANIZE_IMPORTS -> CodeActionKind.SourceOrganizeImports
    }
