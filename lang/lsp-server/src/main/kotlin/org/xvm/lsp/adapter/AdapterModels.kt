package org.xvm.lsp.adapter

import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo

// ============================================================================
// Model classes for Adapter interface.
// These are clean, immutable data classes that isolate the LSP server
// from the internal compiler types.
// ============================================================================

/**
 * A position in a text document (0-based line and column).
 */
data class Position(
    val line: Int,
    val column: Int,
)

/**
 * A range in a text document.
 */
data class Range(
    val start: Position,
    val end: Position,
)

/**
 * A text edit to apply to a document.
 */
data class TextEdit(
    val range: Range,
    val newText: String,
)

/**
 * Completion item for code completion.
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionKind,
    val detail: String,
    val insertText: String,
) {
    enum class CompletionKind {
        CLASS,
        INTERFACE,
        METHOD,
        PROPERTY,
        VARIABLE,
        KEYWORD,
        MODULE,
    }
}

/**
 * Document highlight for symbol highlighting.
 */
data class DocumentHighlight(
    val range: Range,
    val kind: HighlightKind,
) {
    enum class HighlightKind {
        TEXT,
        READ,
        WRITE,
    }
}

/**
 * Selection range with optional parent for nested selections.
 */
data class SelectionRange(
    val range: Range,
    val parent: SelectionRange? = null,
)

/**
 * Folding range for code folding.
 */
data class FoldingRange(
    val startLine: Int,
    val endLine: Int,
    val kind: FoldingKind? = null,
) {
    enum class FoldingKind {
        COMMENT,
        IMPORTS,
        REGION,
    }
}

/**
 * Document link for clickable paths.
 */
data class DocumentLink(
    val range: Range,
    val target: String?,
    val tooltip: String? = null,
)

/**
 * Signature help for function calls.
 */
data class SignatureHelp(
    val signatures: List<SignatureInfo>,
    val activeSignature: Int = 0,
    val activeParameter: Int = 0,
)

/**
 * Information about a function signature.
 */
data class SignatureInfo(
    val label: String,
    val documentation: String? = null,
    val parameters: List<ParameterInfo> = emptyList(),
)

/**
 * Information about a function parameter.
 */
data class ParameterInfo(
    val label: String,
    val documentation: String? = null,
)

/**
 * Result of prepare rename operation.
 */
data class PrepareRenameResult(
    val range: Range,
    val placeholder: String,
)

/**
 * Workspace edit containing changes to multiple documents.
 */
data class WorkspaceEdit(
    val changes: Map<String, List<TextEdit>>,
)

/**
 * Code action (quick fix or refactoring).
 */
data class CodeAction(
    val title: String,
    val kind: CodeActionKind,
    val diagnostics: List<Diagnostic> = emptyList(),
    val edit: WorkspaceEdit? = null,
    val isPreferred: Boolean = false,
) {
    enum class CodeActionKind {
        QUICKFIX,
        REFACTOR,
        REFACTOR_EXTRACT,
        REFACTOR_INLINE,
        REFACTOR_REWRITE,
        SOURCE,
        SOURCE_ORGANIZE_IMPORTS,
    }
}

/**
 * Semantic tokens for enhanced syntax highlighting.
 */
data class SemanticTokens(
    val data: List<Int>,
)

/**
 * Inlay hint for inline annotations.
 */
data class InlayHint(
    val position: Position,
    val label: String,
    val kind: InlayHintKind,
    val paddingLeft: Boolean = false,
    val paddingRight: Boolean = false,
) {
    enum class InlayHintKind {
        TYPE,
        PARAMETER,
    }
}

/**
 * Formatting options from client.
 *
 * Defaults are XTC conventions (trim trailing whitespace, insert final newline).
 * The editor can override these via LSP `FormattingOptions` properties.
 */
data class FormattingOptions(
    val tabSize: Int,
    val insertSpaces: Boolean,
    val trimTrailingWhitespace: Boolean = true,
    val insertFinalNewline: Boolean = true,
)

// ========================================================================
// Data classes for planned features (type hierarchy, call hierarchy, etc.)
// ========================================================================

/**
 * An item in a type hierarchy (a type and its position in the source).
 *
 * Used by [Adapter.prepareTypeHierarchy], [Adapter.getSupertypes], [Adapter.getSubtypes].
 */
data class TypeHierarchyItem(
    val name: String,
    val kind: SymbolInfo.SymbolKind,
    val uri: String,
    val range: Range,
    val selectionRange: Range,
    val detail: String? = null,
)

/**
 * An item in a call hierarchy (a function/method and its position).
 *
 * Used by [Adapter.prepareCallHierarchy], [Adapter.getIncomingCalls], [Adapter.getOutgoingCalls].
 */
data class CallHierarchyItem(
    val name: String,
    val kind: SymbolInfo.SymbolKind,
    val uri: String,
    val range: Range,
    val selectionRange: Range,
    val detail: String? = null,
)

/**
 * An incoming call to a call hierarchy item (who calls it).
 *
 * @param from       the calling function/method
 * @param fromRanges the specific call-site ranges within the caller
 */
data class CallHierarchyIncomingCall(
    val from: CallHierarchyItem,
    val fromRanges: List<Range>,
)

/**
 * An outgoing call from a call hierarchy item (what it calls).
 *
 * @param to         the called function/method
 * @param fromRanges the specific call-site ranges within the caller
 */
data class CallHierarchyOutgoingCall(
    val to: CallHierarchyItem,
    val fromRanges: List<Range>,
)

/**
 * A code lens (actionable inline annotation above a declaration).
 *
 * @param range   the range this code lens applies to
 * @param command the command to execute when clicked (null until resolved)
 */
data class CodeLens(
    val range: Range,
    val command: CodeLensCommand? = null,
)

/**
 * A command associated with a code lens.
 *
 * @param title     display text (e.g., "3 references", "Run Test")
 * @param command   the command identifier to execute
 * @param arguments optional arguments to the command
 */
data class CodeLensCommand(
    val title: String,
    val command: String,
    val arguments: List<Any> = emptyList(),
)

/**
 * Linked editing ranges -- ranges that should be edited simultaneously.
 *
 * @param ranges      the ranges that are linked
 * @param wordPattern optional regex pattern that the new text must match
 */
data class LinkedEditingRanges(
    val ranges: List<Range>,
    val wordPattern: String? = null,
)
