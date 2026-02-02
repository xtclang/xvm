package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock implementation of the XTC compiler adapter for testing.
 *
 * This simulates what a real adapter would do by parsing XTC-like syntax
 * and producing reasonable results without actually invoking the compiler.
 *
 * // TODO LSP: This is a MOCK - not connected to any real compiler!
 * // Replace with XtcCompilerAdapterImpl that uses the parallel compiler:
 * // - Phase 1-3: Use new Lexer/Parser for accurate parsing
 * // - Phase 4: Use NameResolver for real symbol resolution
 * // - Phase 5-7: Use full semantic analysis for type-aware features
 * //
 * // Current limitations of this mock:
 * // - compile(): Uses regex patterns, misses many constructs
 * // - findSymbolAt(): Only finds top-level symbols, no nested scope
 * // - getHoverInfo(): Shows declared type only, no inferred types
 * // - getCompletions(): Returns all keywords + document symbols, no context filtering
 * // - findDefinition(): Returns symbol's own location, can't follow references
 * // - findReferences(): Only returns the declaration, not actual usages
 */
class MockXtcCompilerAdapter : XtcCompilerAdapter {
    override val displayName: String = "Mock (regex-based)"

    private val compiledDocuments = ConcurrentHashMap<String, CompilationResult>()

    /**
     * Mock adapter is always healthy - no native code to verify.
     */
    override fun healthCheck(): Boolean = true

    companion object {
        // Simple patterns to recognize XTC constructs
        private val MODULE_PATTERN = Regex("""^\s*module\s+([\w.]+)\s*\{?""", RegexOption.MULTILINE)
        private val CLASS_PATTERN = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?class\s+(\w+)""", RegexOption.MULTILINE)
        private val INTERFACE_PATTERN = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?interface\s+(\w+)""", RegexOption.MULTILINE)
        private val SERVICE_PATTERN = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?service\s+(\w+)""", RegexOption.MULTILINE)
        private val METHOD_PATTERN =
            Regex(
                """^\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+)?(\w+(?:<[^>]+>)?(?:\s*\|\s*\w+)?)\s+(\w+)\s*\(""",
                RegexOption.MULTILINE,
            )
        private val PROPERTY_PATTERN =
            Regex("""^\s*(?:public\s+|private\s+|protected\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*[;=]""", RegexOption.MULTILINE)
        private val ERROR_PATTERN = Regex("""ERROR:\s*(.+)""", RegexOption.MULTILINE)

        private val KEYWORDS =
            listOf(
                "module",
                "class",
                "interface",
                "service",
                "mixin",
                "enum",
                "const",
                "public",
                "private",
                "protected",
                "static",
                "if",
                "else",
                "while",
                "for",
                "switch",
                "case",
                "return",
                "extends",
                "implements",
                "incorporates",
                "delegates",
                "into",
                "true",
                "false",
                "null",
                "this",
                "super",
            )

        private val BUILT_IN_TYPES =
            listOf(
                "Int",
                "Int8",
                "Int16",
                "Int32",
                "Int64",
                "UInt",
                "UInt8",
                "UInt16",
                "UInt32",
                "UInt64",
                "String",
                "Boolean",
                "Char",
                "Bit",
                "Byte",
                "Float",
                "Double",
                "Dec",
                "Dec64",
                "Dec128",
                "Array",
                "List",
                "Map",
                "Set",
                "Iterator",
            )
    }

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        val diagnostics = mutableListOf<Diagnostic>()
        val symbols = mutableListOf<SymbolInfo>()

        val lines = content.split("\n")

        // Check for deliberate ERROR markers (for testing)
        ERROR_PATTERN.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            diagnostics.add(Diagnostic.error(Location.ofLine(uri, line), match.groupValues[1]))
        }

        // Parse module
        MODULE_PATTERN.find(content)?.let { match ->
            val line = countLines(content, match.range.first)
            val moduleName = match.groupValues[1]
            symbols.add(
                SymbolInfo(
                    name = moduleName,
                    qualifiedName = moduleName,
                    kind = SymbolKind.MODULE,
                    location = Location(uri, line, 0, line, match.value.length),
                    documentation = "Module $moduleName",
                ),
            )
        }

        // Parse type declarations (classes, interfaces, services)
        parseTypeDeclarations(CLASS_PATTERN, content, uri, SymbolKind.CLASS, "class", "Class", symbols)
        parseTypeDeclarations(INTERFACE_PATTERN, content, uri, SymbolKind.INTERFACE, "interface", "Interface", symbols)
        parseTypeDeclarations(SERVICE_PATTERN, content, uri, SymbolKind.SERVICE, "service", "Service", symbols)

        // Parse methods
        METHOD_PATTERN.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            val returnType = match.groupValues[1]
            val methodName = match.groupValues[2]
            // Skip if this looks like a class/interface/service declaration
            if (returnType !in listOf("class", "interface", "service")) {
                symbols.add(
                    SymbolInfo(
                        name = methodName,
                        qualifiedName = methodName,
                        kind = SymbolKind.METHOD,
                        location = Location(uri, line, 0, line, match.value.length),
                        typeSignature = "$returnType $methodName(...)",
                    ),
                )
            }
        }

        // Parse properties
        PROPERTY_PATTERN.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            val propType = match.groupValues[1]
            val propName = match.groupValues[2]
            // Skip if this looks like something else
            if (propType !in listOf("class", "interface", "module", "return")) {
                symbols.add(
                    SymbolInfo(
                        name = propName,
                        qualifiedName = propName,
                        kind = SymbolKind.PROPERTY,
                        location = Location(uri, line, 0, line, match.value.length),
                        typeSignature = "$propType $propName",
                    ),
                )
            }
        }

        // Check for basic syntax errors
        if (content.contains("{") && !content.contains("}")) {
            diagnostics.add(
                Diagnostic.error(
                    Location.ofLine(uri, lines.size - 1),
                    "Unmatched opening brace",
                ),
            )
        }

        val result = CompilationResult.withDiagnostics(uri, diagnostics, symbols)
        compiledDocuments[uri] = result
        return result
    }

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? {
        val result = compiledDocuments[uri] ?: return null
        return result.symbols.find { containsPosition(it.location, line, column) }
    }

    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? =
        findSymbolAt(uri, line, column)?.let { symbol ->
            buildString {
                append("```xtc\n")
                if (symbol.typeSignature != null) {
                    append(symbol.typeSignature)
                } else {
                    append(symbol.kind.name.lowercase())
                    append(" ")
                    append(symbol.name)
                }
                append("\n```")
                if (symbol.documentation != null) {
                    append("\n\n")
                    append(symbol.documentation)
                }
            }
        }

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CompletionItem> {
        // TODO LSP: This is completely context-unaware. A real implementation should:
        // 1. Determine context (after '.', inside type position, statement start, etc.)
        // 2. Filter completions by type compatibility
        // 3. Rank by relevance (local vars first, then members, then imports)
        // 4. Include snippet completions for common patterns
        // 5. Support import-on-completion for unimported types

        val completions = mutableListOf<XtcCompilerAdapter.CompletionItem>()

        // Add keywords
        KEYWORDS.forEach { keyword ->
            completions.add(
                XtcCompilerAdapter.CompletionItem(
                    label = keyword,
                    kind = XtcCompilerAdapter.CompletionItem.CompletionKind.KEYWORD,
                    detail = "keyword",
                    insertText = keyword,
                ),
            )
        }

        // Add built-in types
        BUILT_IN_TYPES.forEach { type ->
            completions.add(
                XtcCompilerAdapter.CompletionItem(
                    label = type,
                    kind = XtcCompilerAdapter.CompletionItem.CompletionKind.CLASS,
                    detail = "built-in type",
                    insertText = type,
                ),
            )
        }

        // Add symbols from current document
        compiledDocuments[uri]?.symbols?.forEach { symbol ->
            completions.add(
                XtcCompilerAdapter.CompletionItem(
                    label = symbol.name,
                    kind = toCompletionKind(symbol.kind),
                    detail = symbol.typeSignature ?: symbol.kind.name,
                    insertText = symbol.name,
                ),
            )
        }

        return completions
    }

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        // TODO LSP: This mock can only find definitions in the same file.
        // A real implementation needs:
        // 1. Cross-file resolution (imports, inherited members)
        // 2. Source attachment for library types
        // 3. Jump to decompiled source for .xtc files without source

        // In the mock, just return the symbol's own location
        return findSymbolAt(uri, line, column)?.location
    }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        // TODO LSP: This mock returns NO actual references - only the declaration!
        // A real implementation needs:
        // 1. SemanticModel.findReferences() from Phase 4 (Symbol Resolution)
        // 2. Cross-file reference tracking (workspace-wide index)
        // 3. Include references from dependent modules

        // Mock implementation: just return the declaration
        val refs = mutableListOf<Location>()
        if (includeDeclaration) {
            findSymbolAt(uri, line, column)?.let { refs.add(it.location) }
        }
        return refs
    }

    private fun countLines(
        content: String,
        position: Int,
    ): Int = content.take(position).count { it == '\n' }

    private fun containsPosition(
        loc: Location,
        line: Int,
        column: Int,
    ): Boolean {
        if (line < loc.startLine || line > loc.endLine) return false
        if (line == loc.startLine && column < loc.startColumn) return false
        if (line == loc.endLine && column > loc.endColumn) return false
        return true
    }

    private fun toCompletionKind(kind: SymbolKind): XtcCompilerAdapter.CompletionItem.CompletionKind =
        when (kind) {
            SymbolKind.MODULE, SymbolKind.PACKAGE -> XtcCompilerAdapter.CompletionItem.CompletionKind.MODULE
            SymbolKind.CLASS, SymbolKind.ENUM, SymbolKind.CONST,
            SymbolKind.MIXIN, SymbolKind.SERVICE,
            -> XtcCompilerAdapter.CompletionItem.CompletionKind.CLASS
            SymbolKind.INTERFACE -> XtcCompilerAdapter.CompletionItem.CompletionKind.INTERFACE
            SymbolKind.METHOD, SymbolKind.CONSTRUCTOR -> XtcCompilerAdapter.CompletionItem.CompletionKind.METHOD
            SymbolKind.PROPERTY -> XtcCompilerAdapter.CompletionItem.CompletionKind.PROPERTY
            SymbolKind.PARAMETER, SymbolKind.TYPE_PARAMETER -> XtcCompilerAdapter.CompletionItem.CompletionKind.VARIABLE
        }

    private fun parseTypeDeclarations(
        pattern: Regex,
        content: String,
        uri: String,
        kind: SymbolKind,
        typeKeyword: String,
        kindLabel: String,
        symbols: MutableList<SymbolInfo>,
    ) {
        pattern.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            val name = match.groupValues[1]
            symbols.add(
                SymbolInfo(
                    name = name,
                    qualifiedName = name,
                    kind = kind,
                    location = Location(uri, line, 0, line, match.value.length),
                    documentation = "$kindLabel $name",
                    typeSignature = "$typeKeyword $name",
                ),
            )
        }
    }
}
