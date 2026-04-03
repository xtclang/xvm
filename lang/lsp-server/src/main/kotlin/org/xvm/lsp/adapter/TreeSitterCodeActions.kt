package org.xvm.lsp.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range
import org.xvm.lsp.adapter.XtcCompilerAdapter.TextEdit
import org.xvm.lsp.adapter.XtcCompilerAdapter.WorkspaceEdit
import org.xvm.lsp.treesitter.XtcQueryEngine
import org.xvm.lsp.treesitter.XtcTree

/**
 * Code action provider for the tree-sitter adapter.
 *
 * Currently offers:
 * - Organize imports (sort alphabetically)
 * - Remove unused imports (names not referenced elsewhere in the file)
 *
 * Extracted from [TreeSitterAdapter] to keep that class focused on LSP
 * feature orchestration.
 *
 * Stateless — a single instance is shared across all code action requests.
 */
class TreeSitterCodeActions {
    private val logger: Logger = LoggerFactory.getLogger(TreeSitterCodeActions::class.java)

    /**
     * Build all available code actions for the given document.
     */
    fun getCodeActions(
        tree: XtcTree,
        uri: String,
        queryEngine: XtcQueryEngine,
    ): List<CodeAction> =
        buildList {
            buildOrganizeImportsAction(tree, uri)?.let { add(it) }
            addAll(buildRemoveUnusedImportActions(tree, uri, queryEngine))
        }.also {
            logger.info("getCodeActions -> {} actions", it.size)
        }

    private fun buildOrganizeImportsAction(
        tree: XtcTree,
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
        tree: XtcTree,
        uri: String,
        queryEngine: XtcQueryEngine,
    ): List<CodeAction> {
        val imports = queryEngine.findImports(tree)
        if (imports.isEmpty()) return emptyList()

        val importLocations = queryEngine.findImportLocations(tree, uri)

        return importLocations.mapNotNull { (importPath, loc) ->
            val simpleName = importPath.substringAfterLast(".")
            val allOccurrences = queryEngine.findAllIdentifiers(tree, simpleName, uri)
            // If the name appears only in the import itself (or not at all), it's unused
            val usagesOutsideImport = allOccurrences.count { it.startLine != loc.startLine }
            if (usagesOutsideImport > 0) return@mapNotNull null

            val deleteRange = Range(Position(loc.startLine, 0), Position(loc.startLine + 1, 0))
            val edit = WorkspaceEdit(mapOf(uri to listOf(TextEdit(deleteRange, ""))))
            CodeAction("Remove unused import '$simpleName'", CodeActionKind.SOURCE, edit = edit)
        }
    }
}
