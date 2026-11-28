package org.xvm.lsp.adapter

import org.xtclang.tooling.xtcLanguage
import org.xvm.lsp.adapter.CompletionItem.CompletionKind
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/**
 * Common Ecstasy language constants and utilities shared across adapters.
 *
 * These data structures define Ecstasy language elements used for:
 * - Code completion (keywords, built-in types)
 * - Symbol kind mapping (for LSP responses)
 * - Hover text formatting
 */
object LanguageConstants {
    private val declarationKeywordsInTypePositions =
        listOf(
            "class",
            "interface",
            "mixin",
            "service",
            "const",
            "enum",
            "module",
            "package",
        )

    /**
     * Ecstasy's keywords for code completion, reserved and context-sensitive, from the language model that the
     * grammars are generated from ([xtcLanguage]).
     */
    private val KEYWORDS: List<String> = (xtcLanguage.keywords + xtcLanguage.contextKeywords).distinct()

    /** Ecstasy's built-in types for code completion, from the language model ([xtcLanguage]). */
    private val builtInTypes: List<String> = xtcLanguage.builtinTypes.distinct()

    /**
     * Maps XTC symbol kinds to LSP completion item kinds.
     *
     * Used to convert internal symbol representations to LSP protocol values.
     */
    private val SYMBOL_TO_COMPLETION_KIND: Map<SymbolKind, CompletionKind> =
        mapOf(
            SymbolKind.MODULE to CompletionKind.MODULE,
            SymbolKind.PACKAGE to CompletionKind.MODULE,
            SymbolKind.CLASS to CompletionKind.CLASS,
            SymbolKind.ENUM to CompletionKind.CLASS,
            SymbolKind.CONST to CompletionKind.CLASS,
            SymbolKind.MIXIN to CompletionKind.CLASS,
            SymbolKind.SERVICE to CompletionKind.CLASS,
            SymbolKind.INTERFACE to CompletionKind.INTERFACE,
            SymbolKind.METHOD to CompletionKind.METHOD,
            SymbolKind.CONSTRUCTOR to CompletionKind.METHOD,
            SymbolKind.PROPERTY to CompletionKind.PROPERTY,
            SymbolKind.PARAMETER to CompletionKind.VARIABLE,
            SymbolKind.TYPE_PARAMETER to CompletionKind.VARIABLE,
        )

    /**
     * Convert a symbol kind to a completion kind using the mapping. Falls back to VARIABLE for
     * unmapped kinds.
     */
    fun toCompletionKind(kind: SymbolKind): CompletionKind = SYMBOL_TO_COMPLETION_KIND[kind] ?: CompletionKind.VARIABLE

    /**
     * Generate completion items for all XTC keywords.
     *
     * @return list of completion items for keywords
     */
    fun keywordCompletions(): List<CompletionItem> =
        KEYWORDS.map { keyword ->
            CompletionItem(
                label = keyword,
                kind = CompletionKind.KEYWORD,
                detail = "keyword",
                insertText = keyword,
            )
        }

    /**
     * Generate completion items for all XTC built-in types.
     *
     * @return list of completion items for built-in types
     */
    fun builtInTypeCompletions(): List<CompletionItem> =
        builtInTypes.map { type ->
            CompletionItem(
                label = type,
                kind = CompletionKind.CLASS,
                detail = "built-in type",
                insertText = type,
            )
        }

    fun declarationContextBuiltInTypeCompletions(): List<CompletionItem> =
        builtInTypes
            .filterNot {
                it in
                    setOf(
                        "Class",
                        "Module",
                        "Package",
                        "Service",
                        "Const",
                        "Type",
                        "Property",
                        "Method",
                    )
            }.map { type ->
                CompletionItem(
                    label = type,
                    kind = CompletionKind.CLASS,
                    detail = "built-in type",
                    insertText = type,
                )
            }

    /**
     * Keywords that are valid and useful at declaration starts, even when the parser has
     * temporarily classified the cursor as being in a type-like position.
     */
    fun declarationKeywordCompletions(): List<CompletionItem> =
        declarationKeywordsInTypePositions.map { keyword ->
            CompletionItem(
                label = keyword,
                kind = CompletionKind.KEYWORD,
                detail = "declaration keyword",
                insertText = keyword,
            )
        }

    /**
     * Format a symbol as Markdown hover text.
     *
     * Produces a code block with the symbol's type signature (or kind + name), followed by
     * documentation if available.
     *
     * @return Markdown-formatted hover text
     */
    fun SymbolInfo.toHoverMarkdown(): String =
        buildString {
            append("```xtc\n")
            append(typeSignature ?: "${kind.name.lowercase()} $name")
            append("\n```")
            documentation?.let { append("\n\n$it") }
        }
}
