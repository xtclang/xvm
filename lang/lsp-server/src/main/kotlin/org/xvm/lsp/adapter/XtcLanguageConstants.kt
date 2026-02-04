package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem.CompletionKind
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/**
 * Common XTC language constants and utilities shared across adapters.
 *
 * These data structures define XTC language elements used for:
 * - Code completion (keywords, built-in types)
 * - Symbol kind mapping (for LSP responses)
 * - Hover text formatting
 */
object XtcLanguageConstants {
    /**
     * XTC language keywords for code completion.
     *
     * Sourced from the XTC language specification.
     * See: javatools/src/main/java/org/xvm/compiler/Token.java
     */
    val KEYWORDS: List<String> =
        listOf(
            // Module structure
            "module",
            "package",
            "import",
            "as",
            // Type declarations
            "class",
            "interface",
            "mixin",
            "service",
            "const",
            "enum",
            // Access modifiers
            "public",
            "private",
            "protected",
            "static",
            "abstract",
            // Inheritance
            "extends",
            "implements",
            "incorporates",
            "delegates",
            "into",
            // Control flow
            "if",
            "else",
            "switch",
            "case",
            "default",
            "for",
            "while",
            "do",
            "break",
            "continue",
            "return",
            // Exception handling
            "try",
            "catch",
            "finally",
            "throw",
            // Other
            "using",
            "assert",
            "new",
            "this",
            "super",
            "outer",
            "is",
            "val",
            "var",
            "construct",
            "function",
            "typedef",
            // Literals
            "true",
            "false",
            "null",
        )

    /**
     * XTC built-in types for code completion.
     *
     * Sourced from the Ecstasy standard library (lib_ecstasy).
     */
    val BUILT_IN_TYPES: List<String> =
        listOf(
            // Integer types
            "Int",
            "Int8",
            "Int16",
            "Int32",
            "Int64",
            "Int128",
            "IntN",
            "UInt",
            "UInt8",
            "UInt16",
            "UInt32",
            "UInt64",
            "UInt128",
            "UIntN",
            // Decimal types
            "Dec",
            "Dec32",
            "Dec64",
            "Dec128",
            "DecN",
            // Floating point types
            "Float",
            "Float16",
            "Float32",
            "Float64",
            "Float128",
            "FloatN",
            // Basic types
            "String",
            "Char",
            "Boolean",
            "Bit",
            "Byte",
            "Object",
            "Enum",
            "Exception",
            "Const",
            "Service",
            "Module",
            "Package",
            // Collections
            "Array",
            "List",
            "Set",
            "Map",
            "Range",
            "Interval",
            "Tuple",
            // Reflection
            "Function",
            "Method",
            "Property",
            "Type",
            "Class",
            // Interfaces
            "Nullable",
            "Orderable",
            "Hashable",
            "Stringable",
            "Iterator",
            "Iterable",
            "Collection",
            "Sequence",
            // Special
            "void",
            "Null",
            "True",
            "False",
        )

    /**
     * Maps XTC symbol kinds to LSP completion item kinds.
     *
     * Used to convert internal symbol representations to LSP protocol values.
     */
    val SYMBOL_TO_COMPLETION_KIND: Map<SymbolKind, CompletionKind> =
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
     * Convert a symbol kind to a completion kind using the mapping.
     * Falls back to VARIABLE for unmapped kinds.
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
        BUILT_IN_TYPES.map { type ->
            CompletionItem(
                label = type,
                kind = CompletionKind.CLASS,
                detail = "built-in type",
                insertText = type,
            )
        }

    /**
     * Format a symbol as Markdown hover text.
     *
     * Produces a code block with the symbol's type signature (or kind + name),
     * followed by documentation if available.
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
