package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.DocumentHighlight
import org.xvm.lsp.adapter.XtcCompilerAdapter.DocumentHighlight.HighlightKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.FoldingRange
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position
import org.xvm.lsp.adapter.XtcCompilerAdapter.PrepareRenameResult
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range
import org.xvm.lsp.adapter.XtcCompilerAdapter.TextEdit
import org.xvm.lsp.adapter.XtcCompilerAdapter.WorkspaceEdit
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

/** Pattern to recognize import statements. */
private val importPattern = Regex("""^\s*import\s+([\w.]+)\s*;""", RegexOption.MULTILINE)

/** Pattern to recognize identifier words. */
private val identifierPattern = Regex("""\b(\w+)\b""")

/**
 * Mock implementation of the XTC compiler adapter for testing.
 *
 * This simulates what a real adapter would do by parsing XTC-like syntax
 * and producing reasonable results without actually invoking the compiler.
 *
 * This adapter implements all syntax-level LSP features using regex patterns:
 * - Document symbols and outline
 * - Basic go-to-definition (same file, by name)
 * - Find references (declaration only)
 * - Document highlight (text matching)
 * - Folding ranges (brace matching)
 * - Same-file rename (text replacement)
 * - Code actions (organize imports)
 * - Document formatting (trailing whitespace, final newline)
 * - Document links (import statements)
 *
 * Capabilities using interface defaults (not overridden):
 * - Selection ranges: returns empty -- requires AST for structural expand/shrink
 * - Signature help: returns null -- requires AST to extract method parameters
 * - Inlay hints: returns empty -- requires type inference
 * - Semantic tokens: returns null -- requires type inference
 *
 * Limitations (requires compiler adapter for these):
 * - Type inference and semantic types
 * - Cross-file go-to-definition and rename
 * - Semantic error reporting (only ERROR markers work)
 * - Smart completion based on types
 *
 * // TODO LSP: This is a MOCK - not connected to any real compiler!
 * // Replace with XtcCompilerAdapterImpl that uses the parallel compiler.
 */
class MockXtcCompilerAdapter : AbstractXtcCompilerAdapter() {
    override val displayName: String = "Mock"

    // ConcurrentHashMap is required because compile() is called from the LSP message thread
    // (via didOpen/didChange), while read methods like findSymbolAt() and getCompletions()
    // are called from CompletableFuture.supplyAsync on the ForkJoinPool.
    private val compiledDocuments = ConcurrentHashMap<String, CompilationResult>()
    private val documentContents = ConcurrentHashMap<String, String>()

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

        documentContents[uri] = content
        val lines = content.split("\n")

        val diagnostics =
            buildList {
                // Check for deliberate ERROR markers (for testing)
                errorPattern.findAll(content).forEach { match ->
                    val line = countLines(content, match.range.first)
                    add(Diagnostic.error(Location.ofLine(uri, line), match.groupValues[1]))
                }

                // Check for basic syntax errors
                if (content.contains("{") && !content.contains("}")) {
                    add(Diagnostic.error(Location.ofLine(uri, lines.size - 1), "Unmatched opening brace"))
                }
            }

        val symbols =
            buildList {
                // Parse module
                modulePattern.find(content)?.let { match ->
                    val line = countLines(content, match.range.first)
                    val moduleName = match.groupValues[1]
                    add(
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
                addAll(parseTypeDeclarations(classPattern, content, uri, SymbolKind.CLASS, "class", "Class"))
                addAll(parseTypeDeclarations(interfacePattern, content, uri, SymbolKind.INTERFACE, "interface", "Interface"))
                addAll(parseTypeDeclarations(servicePattern, content, uri, SymbolKind.SERVICE, "service", "Service"))

                // Parse methods
                methodPattern.findAll(content).forEach { match ->
                    val line = countLines(content, match.range.first)
                    val (returnType, methodName) = match.destructured
                    // Skip if this looks like a class/interface/service declaration
                    if (returnType !in listOf("class", "interface", "service")) {
                        add(
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
                        add(
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
            }

        val result = CompilationResult.withDiagnostics(uri, diagnostics, symbols)
        compiledDocuments[uri] = result
        logger.info("$logPrefix compile -> {} symbols, {} diagnostics", symbols.size, diagnostics.size)
        return result
    }

    override fun getCachedResult(uri: String): CompilationResult? = compiledDocuments[uri]

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

        return buildList {
            addAll(keywordCompletions())
            addAll(builtInTypeCompletions())

            // Add symbols from current document
            compiledDocuments[uri]?.symbols?.forEach { symbol ->
                add(
                    XtcCompilerAdapter.CompletionItem(
                        label = symbol.name,
                        kind = toCompletionKind(symbol.kind),
                        detail = symbol.typeSignature ?: symbol.kind.name,
                        insertText = symbol.name,
                    ),
                )
            }
        }.also {
            logger.info("$logPrefix getCompletions -> {} items", it.size)
        }
    }

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        val fileName = uri.substringAfterLast('/')
        logger.info("$logPrefix findDefinition(uri={}, line={}, column={})", fileName, line, column)

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

        // Mock implementation: just return the declaration
        return listOfNotNull(
            if (includeDeclaration) findSymbolAt(uri, line, column)?.location else null,
        ).also {
            logger.info("$logPrefix findReferences -> {} locations", it.size)
        }
    }

    // ========================================================================
    // Syntax-level features (regex-based implementations)
    // ========================================================================

    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        val content = documentContents[uri] ?: return emptyList()
        val word = getWordAt(content, line, column) ?: return emptyList()

        logger.info("$logPrefix highlight '{}' at {}:{}", word, line, column)
        return content
            .split("\n")
            .flatMapIndexed { lineIdx, lineText ->
                findWholeWordOccurrences(lineText, word)
                    .map { idx ->
                        DocumentHighlight(
                            range =
                                Range(
                                    start = Position(lineIdx, idx),
                                    end = Position(lineIdx, idx + word.length),
                                ),
                            kind = HighlightKind.TEXT,
                        )
                    }.toList()
            }.also {
                logger.info("$logPrefix highlight '{}' -> {} occurrences", word, it.size)
            }
    }

    override fun getFoldingRanges(uri: String): List<FoldingRange> {
        val content = documentContents[uri] ?: return emptyList()
        val lines = content.split("\n")
        return buildList {
            // Find brace-delimited blocks
            val braceStack = mutableListOf<Int>()
            lines.forEachIndexed { lineIdx, lineText ->
                for (ch in lineText) {
                    if (ch == '{') braceStack.add(lineIdx)
                    if (ch == '}' && braceStack.isNotEmpty()) {
                        val startLine = braceStack.removeLast()
                        if (lineIdx > startLine) {
                            add(FoldingRange(startLine, lineIdx))
                        }
                    }
                }
            }

            // Find import blocks
            val importLines = lines.indices.filter { lines[it].trimStart().startsWith("import ") }
            if (importLines.size >= 2) {
                add(FoldingRange(importLines.first(), importLines.last(), FoldingRange.FoldingKind.IMPORTS))
            }
        }.also {
            logger.info("$logPrefix folding ranges -> {} found", it.size)
        }
    }

    override fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): PrepareRenameResult? {
        val content = documentContents[uri] ?: return null
        val word = getWordAt(content, line, column) ?: return null
        val lines = content.split("\n")
        if (line >= lines.size) return null

        val lineText = lines[line]
        val idx = findWordAt(lineText, column, word) ?: return null

        logger.info("$logPrefix prepareRename '{}' at {}:{}", word, line, column)
        return PrepareRenameResult(
            range =
                Range(
                    start = Position(line, idx),
                    end = Position(line, idx + word.length),
                ),
            placeholder = word,
        )
    }

    override fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): WorkspaceEdit? {
        val content = documentContents[uri] ?: return null
        val word = getWordAt(content, line, column) ?: return null

        val edits =
            content
                .split("\n")
                .flatMapIndexed { lineIdx, lineText ->
                    findWholeWordOccurrences(lineText, word)
                        .map { idx ->
                            TextEdit(
                                range =
                                    Range(
                                        start = Position(lineIdx, idx),
                                        end = Position(lineIdx, idx + word.length),
                                    ),
                                newText = newName,
                            )
                        }.toList()
                }

        if (edits.isEmpty()) return null
        logger.info("$logPrefix rename '{}' -> '{}' ({} occurrences)", word, newName, edits.size)
        return WorkspaceEdit(changes = mapOf(uri to edits))
    }

    override fun getCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
    ): List<CodeAction> =
        listOfNotNull(buildOrganizeImportsAction(uri)).also {
            logger.info("$logPrefix codeActions -> {} actions", it.size)
        }

    private fun buildOrganizeImportsAction(uri: String): CodeAction? {
        val content = documentContents[uri] ?: return null
        val lines = content.split("\n")
        val importLines =
            lines.indices
                .filter { lines[it].trimStart().startsWith("import ") }
                .map { it to lines[it] }
        if (importLines.size < 2) return null

        val sorted = importLines.map { it.second }.sorted()
        val current = importLines.map { it.second }
        if (sorted == current) return null

        val firstLine = importLines.first().first
        val lastLine = importLines.last().first
        val edit =
            TextEdit(
                range =
                    Range(
                        start = Position(firstLine, 0),
                        end = Position(lastLine, lines[lastLine].length),
                    ),
                newText = sorted.joinToString("\n"),
            )
        return CodeAction(
            title = "Organize Imports",
            kind = CodeActionKind.SOURCE_ORGANIZE_IMPORTS,
            edit = WorkspaceEdit(changes = mapOf(uri to listOf(edit))),
        )
    }

    override fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<XtcCompilerAdapter.DocumentLink> =
        buildList {
            importPattern.findAll(content).forEach { match ->
                val importPath = match.groupValues[1]
                val line = countLines(content, match.range.first)
                val importStart = match.value.indexOf(importPath)
                val col = match.value.indexOf(importPath, importStart)
                add(
                    XtcCompilerAdapter.DocumentLink(
                        range =
                            Range(
                                start = Position(line, col),
                                end = Position(line, col + importPath.length),
                            ),
                        target = null,
                        tooltip = "import $importPath",
                    ),
                )
            }
        }.also {
            logger.info("$logPrefix documentLinks -> {} found", it.size)
        }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun getWordAt(
        content: String,
        line: Int,
        column: Int,
    ): String? {
        val lines = content.split("\n")
        if (line >= lines.size) return null
        val lineText = lines[line]
        if (column >= lineText.length) return null

        return identifierPattern
            .findAll(lineText)
            .firstOrNull { column >= it.range.first && column <= it.range.last }
            ?.value
    }

    private fun findWordAt(
        lineText: String,
        column: Int,
        word: String,
    ): Int? =
        identifierPattern
            .findAll(lineText)
            .firstOrNull { it.value == word && column >= it.range.first && column <= it.range.last }
            ?.range
            ?.first

    private fun findWholeWordOccurrences(
        lineText: String,
        word: String,
    ): Sequence<Int> =
        generateSequence(lineText.indexOf(word)) { prev ->
            lineText.indexOf(word, prev + word.length).takeIf { it >= 0 }
        }.filter { idx ->
            val before = if (idx > 0) lineText[idx - 1] else ' '
            val after = if (idx + word.length < lineText.length) lineText[idx + word.length] else ' '
            !before.isLetterOrDigit() && before != '_' && !after.isLetterOrDigit() && after != '_'
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
    ): List<SymbolInfo> =
        pattern
            .findAll(content)
            .map { match ->
                val line = countLines(content, match.range.first)
                val name = match.groupValues[1]
                SymbolInfo(
                    name = name,
                    qualifiedName = name,
                    kind = kind,
                    location = Location(uri, line, 0, line, match.value.length),
                    documentation = "$kindLabel $name",
                    typeSignature = "$typeKeyword $name",
                )
            }.toList()
}
