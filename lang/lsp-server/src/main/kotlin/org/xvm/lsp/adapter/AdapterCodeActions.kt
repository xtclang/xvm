package org.xvm.lsp.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range
import org.xvm.lsp.adapter.XtcCompilerAdapter.TextEdit
import org.xvm.lsp.adapter.XtcCompilerAdapter.WorkspaceEdit
import org.xvm.lsp.index.WorkspaceIndex
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/**
 * Backend-agnostic code action provider that works with any [AdapterTree]/[AdapterNode] implementation.
 *
 * Currently offers:
 * - Organize imports (sort alphabetically)
 * - Remove unused imports (names not referenced elsewhere in the file)
 * - Generate documentation comment (insert skeleton with @param entries)
 * - Auto-import (add import for unresolved type names found in workspace index)
 *
 * Query-engine-specific data (imports, declarations, identifier lookups) is passed
 * via [CodeActionQueryData] so that both tree-sitter and compiler adapters can
 * provide the same information from their own backends.
 *
 * Stateless — a single instance is shared across all code action requests.
 */
class AdapterCodeActions {
    private val logger: Logger = LoggerFactory.getLogger(AdapterCodeActions::class.java)

    companion object {
        /** Type declaration kinds eligible for auto-import. */
        private val typeKinds =
            setOf(
                SymbolKind.CLASS,
                SymbolKind.INTERFACE,
                SymbolKind.MIXIN,
                SymbolKind.SERVICE,
                SymbolKind.CONST,
                SymbolKind.ENUM,
                SymbolKind.MODULE,
            )

        /** Node types that indicate a type position in the AST. */
        private val typePositionParentTypes =
            setOf(
                "type_expression",
                "type_name",
                "generic_type",
            )
    }

    /**
     * Build all available code actions for the given document.
     */
    fun getCodeActions(
        tree: AdapterTree,
        uri: String,
        range: Range,
        queryData: CodeActionQueryData,
        workspaceIndex: WorkspaceIndex?,
        indexReady: Boolean,
    ): List<CodeAction> =
        buildList {
            buildOrganizeImportsAction(tree, uri)?.let { add(it) }
            addAll(buildRemoveUnusedImportActions(uri, queryData))
            addAll(buildGenerateDocCommentActions(tree, uri, range, queryData.declarations))
            addAll(buildAutoImportActions(tree, uri, queryData, workspaceIndex, indexReady))
        }.also {
            logger.info("getCodeActions -> {} actions", it.size)
        }

    private fun buildOrganizeImportsAction(
        tree: AdapterTree,
        uri: String,
    ): CodeAction? {
        val importNodes = tree.root.children.filter { it.type == "import_statement" }
        if (importNodes.size < 2) return null

        val sortedTexts = importNodes.map { it.text }.sorted()
        val currentTexts = importNodes.map { it.text }
        if (sortedTexts == currentTexts) return null

        val firstImport = importNodes.first()
        val lastImport = importNodes.last()
        val replaceRange =
            Range(
                Position(firstImport.startLine, firstImport.startColumn),
                Position(lastImport.endLine, lastImport.endColumn),
            )
        val edit = WorkspaceEdit(mapOf(uri to listOf(TextEdit(replaceRange, sortedTexts.joinToString("\n")))))
        return CodeAction("Organize Imports", CodeActionKind.SOURCE_ORGANIZE_IMPORTS, edit = edit)
    }

    private fun buildRemoveUnusedImportActions(
        uri: String,
        queryData: CodeActionQueryData,
    ): List<CodeAction> {
        if (queryData.imports.isEmpty()) return emptyList()

        return queryData.importLocations.mapNotNull { (importPath, loc) ->
            val simpleName = importPath.substringAfterLast(".")
            val allOccurrences = queryData.findIdentifiers(simpleName)
            // If the name appears only in the import itself (or not at all), it's unused
            val usagesOutsideImport = allOccurrences.count { it.startLine != loc.startLine }
            if (usagesOutsideImport > 0) return@mapNotNull null

            val deleteRange = Range(Position(loc.startLine, 0), Position(loc.startLine + 1, 0))
            val edit = WorkspaceEdit(mapOf(uri to listOf(TextEdit(deleteRange, ""))))
            CodeAction("Remove unused import '$simpleName'", CodeActionKind.SOURCE, edit = edit)
        }
    }

    // ========================================================================
    // Generate documentation comment
    // ========================================================================

    private fun buildGenerateDocCommentActions(
        tree: AdapterTree,
        uri: String,
        range: Range,
        declarations: List<SymbolInfo>,
    ): List<CodeAction> {
        val lines = tree.source.split("\n")
        val filtered = declarations.filter { it.location.startLine in range.start.line..range.end.line }

        return filtered.mapNotNull { decl ->
            val declLine = decl.location.startLine
            // Check if there is already a doc comment above
            val prevLine = if (declLine > 0) lines[declLine - 1].trimEnd() else ""
            if (prevLine.endsWith("*/")) return@mapNotNull null

            val skeleton = buildDocSkeleton(tree, decl.kind, declLine, lines)
            val insertEdit =
                TextEdit(
                    range = Range(Position(declLine, 0), Position(declLine, 0)),
                    newText = skeleton,
                )
            CodeAction(
                title = "Generate documentation comment",
                kind = CodeActionKind.SOURCE,
                edit = WorkspaceEdit(mapOf(uri to listOf(insertEdit))),
            )
        }
    }

    private fun buildDocSkeleton(
        tree: AdapterTree,
        kind: SymbolKind,
        declLine: Int,
        lines: List<String>,
    ): String {
        val indent = lines[declLine].takeWhile { it == ' ' }
        val sb = StringBuilder()
        sb.appendLine("$indent/**")
        sb.appendLine("$indent * TODO: add description.")

        // Extract parameters if this is a method or constructor
        if (kind == SymbolKind.METHOD || kind == SymbolKind.CONSTRUCTOR) {
            val firstNonWs = lines[declLine].indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val node = tree.nodeAt(declLine, firstNonWs)
            val methodNode =
                node?.let {
                    generateSequence(it) { n -> n.parent }
                        .firstOrNull { n -> n.type == "method_declaration" || n.type == "constructor_declaration" }
                }
            val paramsNode = methodNode?.childByFieldName("parameters")
            paramsNode
                ?.children
                ?.filter { it.type == "parameter" }
                ?.forEach { param ->
                    val paramName = param.childByFieldName("name")?.text ?: return@forEach
                    sb.appendLine("$indent * @param $paramName TODO")
                }
        }

        sb.appendLine("$indent */")
        return sb.toString()
    }

    // ========================================================================
    // Auto-import code action
    // ========================================================================

    private fun buildAutoImportActions(
        tree: AdapterTree,
        uri: String,
        queryData: CodeActionQueryData,
        workspaceIndex: WorkspaceIndex?,
        indexReady: Boolean,
    ): List<CodeAction> {
        if (workspaceIndex == null || !indexReady) return emptyList()

        val localNames = queryData.declarations.map { it.name }.toSet()
        val imports = queryData.imports.map { it.substringAfterLast(".") }.toSet()
        val knownNames = localNames + imports

        val candidates = mutableListOf<String>()
        collectUnresolvedTypeNames(tree.root, knownNames, candidates)

        // Deduplicate candidate names
        val uniqueCandidates = candidates.distinct()

        return uniqueCandidates.flatMap { name ->
            val indexed = workspaceIndex.findByName(name).filter { it.kind in typeKinds }
            indexed.map { symbol ->
                val importText = "import ${symbol.qualifiedName};"
                val insertLine = findImportInsertLine(tree)
                val insertEdit =
                    TextEdit(
                        range = Range(Position(insertLine, 0), Position(insertLine, 0)),
                        newText = "$importText\n",
                    )
                CodeAction(
                    title = "Add import for '${symbol.name}'",
                    kind = CodeActionKind.SOURCE,
                    edit = WorkspaceEdit(mapOf(uri to listOf(insertEdit))),
                )
            }
        }
    }

    private fun collectUnresolvedTypeNames(
        node: AdapterNode,
        knownNames: Set<String>,
        result: MutableList<String>,
    ) {
        // Look for identifiers in type positions that aren't locally known
        if (node.type == "identifier" || node.type == "type_name") {
            val parent = node.parent
            if (parent != null && parent.type in typePositionParentTypes) {
                val name = node.text
                if (name.isNotEmpty() && name[0].isUpperCase() && name !in knownNames) {
                    result.add(name)
                }
            }
        }
        node.children.forEach { collectUnresolvedTypeNames(it, knownNames, result) }
    }

    /**
     * Find the line where a new import statement should be inserted.
     * Inserts after the last existing import, or at line 0 if there are none.
     */
    private fun findImportInsertLine(tree: AdapterTree): Int {
        val importNodes = tree.root.children.filter { it.type == "import_statement" }
        return if (importNodes.isNotEmpty()) {
            importNodes.last().endLine + 1
        } else {
            0
        }
    }
}

/**
 * Pre-computed query data passed to [AdapterCodeActions] to decouple it from
 * any specific query engine implementation.
 *
 * The adapter (tree-sitter, compiler, etc.) populates this from its own backend
 * before calling code action methods.
 *
 * @property imports            All import paths in the document (e.g., "ecstasy.collections.List")
 * @property importLocations    Import path → source location pairs for each import statement
 * @property declarations       All declarations found in the document
 * @property findIdentifiers    Callback to find all occurrences of a given identifier name
 */
data class CodeActionQueryData(
    val imports: List<String>,
    val importLocations: List<Pair<String, Location>>,
    val declarations: List<SymbolInfo>,
    val findIdentifiers: (name: String) -> List<Location>,
)
