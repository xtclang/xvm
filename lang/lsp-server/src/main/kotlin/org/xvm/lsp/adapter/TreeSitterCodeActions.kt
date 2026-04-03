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
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.treesitter.XtcNode
import org.xvm.lsp.treesitter.XtcQueryEngine
import org.xvm.lsp.treesitter.XtcTree
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Code action provider for the tree-sitter adapter.
 *
 * Currently offers:
 * - Organize imports (sort alphabetically)
 * - Remove unused imports (names not referenced elsewhere in the file)
 * - Generate documentation comment (insert skeleton with @param entries)
 * - Auto-import (add import for unresolved type names found in workspace index)
 *
 * Extracted from [TreeSitterAdapter] to keep that class focused on LSP
 * feature orchestration.
 *
 * Stateless — a single instance is shared across all code action requests.
 */
class TreeSitterCodeActions {
    private val logger: Logger = LoggerFactory.getLogger(TreeSitterCodeActions::class.java)

    companion object {
        /** Declaration node types that can have doc comments generated. */
        private val docCommentableTypes =
            setOf(
                "class_declaration",
                "interface_declaration",
                "mixin_declaration",
                "service_declaration",
                "const_declaration",
                "enum_declaration",
                "module_declaration",
                "method_declaration",
                "function_declaration",
                "constructor_declaration",
            )

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
        tree: XtcTree,
        uri: String,
        range: Range,
        queryEngine: XtcQueryEngine,
        workspaceIndex: WorkspaceIndex?,
        indexReady: AtomicBoolean,
    ): List<CodeAction> =
        buildList {
            buildOrganizeImportsAction(tree, uri)?.let { add(it) }
            addAll(buildRemoveUnusedImportActions(tree, uri, queryEngine))
            addAll(buildGenerateDocCommentActions(tree, uri, range, queryEngine))
            addAll(buildAutoImportActions(tree, uri, queryEngine, workspaceIndex, indexReady))
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

    // ========================================================================
    // Generate documentation comment
    // ========================================================================

    private fun buildGenerateDocCommentActions(
        tree: XtcTree,
        uri: String,
        range: Range,
        queryEngine: XtcQueryEngine,
    ): List<CodeAction> {
        val lines = tree.source.split("\n")
        val declarations =
            queryEngine
                .findAllDeclarations(tree, uri)
                .filter { it.location.startLine in range.start.line..range.end.line }

        return declarations.mapNotNull { decl ->
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
        tree: XtcTree,
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
        tree: XtcTree,
        uri: String,
        queryEngine: XtcQueryEngine,
        workspaceIndex: WorkspaceIndex?,
        indexReady: AtomicBoolean,
    ): List<CodeAction> {
        if (workspaceIndex == null || !indexReady.get()) return emptyList()

        val localNames = queryEngine.findAllDeclarations(tree, uri).map { it.name }.toSet()
        val imports = queryEngine.findImports(tree).map { it.substringAfterLast(".") }.toSet()
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
        node: XtcNode,
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
    private fun findImportInsertLine(tree: XtcTree): Int {
        val importNodes = tree.root.children.filter { it.type == "import_statement" }
        return if (importNodes.isNotEmpty()) {
            importNodes.last().endLine + 1
        } else {
            0
        }
    }
}
