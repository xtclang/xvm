package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.XtcLanguageConstants.builtInTypeCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.keywordCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.toCompletionKind
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import java.util.concurrent.ConcurrentHashMap

/** Pattern to recognize module declarations. */
private val modulePattern = Regex("""^\s*module\s+([\w.]+)\s*\{?""", RegexOption.MULTILINE)

/** Pattern to recognize class declarations. */
private val classPattern = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?class\s+(\w+)""", RegexOption.MULTILINE)

/** Pattern to recognize interface declarations. */
private val interfacePattern = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?interface\s+(\w+)""", RegexOption.MULTILINE)

/** Pattern to recognize service declarations. */
private val servicePattern = Regex("""^\s*(?:public\s+|private\s+|protected\s+)?service\s+(\w+)""", RegexOption.MULTILINE)

/** Pattern to recognize method declarations. */
private val methodPattern =
    Regex(
        """^\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+)?(\w+(?:<[^>]+>)?(?:\s*\|\s*\w+)?)\s+(\w+)\s*\(""",
        RegexOption.MULTILINE,
    )

/** Pattern to recognize property declarations. */
private val propertyPattern =
    Regex(
        """^\s*(?:public\s+|private\s+|protected\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*[;=]""",
        RegexOption.MULTILINE,
    )

/** Pattern to recognize ERROR markers (for testing). */
private val errorPattern = Regex("""ERROR:\s*(.+)""", RegexOption.MULTILINE)

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
class MockXtcCompilerAdapter : AbstractXtcCompilerAdapter() {
    override val displayName: String = "Mock"

    private val compiledDocuments = ConcurrentHashMap<String, CompilationResult>()

    /**
     * Mock adapter is always healthy - no native code to verify.
     */
    override fun healthCheck(): Boolean {
        logger.info("$logPrefix healthCheck() -> true")
        return true
    }

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix compile(uri={}, content={} bytes)", fileName, content.length)

        val diagnostics = mutableListOf<Diagnostic>()
        val symbols = mutableListOf<SymbolInfo>()

        val lines = content.split("\n")

        // Check for deliberate ERROR markers (for testing)
        errorPattern.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            diagnostics.add(Diagnostic.error(Location.ofLine(uri, line), match.groupValues[1]))
        }

        // Parse module
        modulePattern.find(content)?.let { match ->
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
        parseTypeDeclarations(classPattern, content, uri, SymbolKind.CLASS, "class", "Class", symbols)
        parseTypeDeclarations(interfacePattern, content, uri, SymbolKind.INTERFACE, "interface", "Interface", symbols)
        parseTypeDeclarations(servicePattern, content, uri, SymbolKind.SERVICE, "service", "Service", symbols)

        // Parse methods
        methodPattern.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            val (returnType, methodName) = match.destructured
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
        propertyPattern.findAll(content).forEach { match ->
            val line = countLines(content, match.range.first)
            val (propType, propName) = match.destructured
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
        logger.info("$logPrefix compile -> {} symbols, {} diagnostics", symbols.size, diagnostics.size)
        return result
    }

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix findSymbolAt(uri={}, line={}, column={})", fileName, line, column)
        val result =
            compiledDocuments[uri] ?: run {
                logger.info("$logPrefix findSymbolAt -> null (no compiled document)")
                return null
            }
        val symbol = result.symbols.find { it.location.contains(line, column) }
        logger.info("$logPrefix findSymbolAt -> {}", symbol?.name ?: "null")
        return symbol
    }

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CompletionItem> {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix getCompletions(uri={}, line={}, column={})", fileName, line, column)
        // TODO LSP: This is completely context-unaware. A real implementation should:
        // 1. Determine context (after '.', inside type position, statement start, etc.)
        // 2. Filter completions by type compatibility
        // 3. Rank by relevance (local vars first, then members, then imports)
        // 4. Include snippet completions for common patterns
        // 5. Support import-on-completion for unimported types

        val completions = mutableListOf<XtcCompilerAdapter.CompletionItem>()

        // Add keywords and built-in types
        completions.addAll(keywordCompletions())
        completions.addAll(builtInTypeCompletions())

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

        logger.info("$logPrefix getCompletions -> {} items", completions.size)
        return completions
    }

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix findDefinition(uri={}, line={}, column={})", fileName, line, column)
        // TODO LSP: This mock can only find definitions in the same file.
        // A real implementation needs:
        // 1. Cross-file resolution (imports, inherited members)
        // 2. Source attachment for library types
        // 3. Jump to decompiled source for .xtc files without source

        // In the mock, just return the symbol's own location
        val location = findSymbolAt(uri, line, column)?.location
        logger.info("$logPrefix findDefinition -> {}", location?.let { "${it.startLine}:${it.startColumn}" } ?: "null")
        return location
    }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix findReferences(uri={}, line={}, column={}, includeDecl={})", fileName, line, column, includeDeclaration)
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
        logger.info("$logPrefix findReferences -> {} locations", refs.size)
        return refs
    }

    private fun countLines(
        content: String,
        position: Int,
    ): Int = content.take(position).count { it == '\n' }

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
