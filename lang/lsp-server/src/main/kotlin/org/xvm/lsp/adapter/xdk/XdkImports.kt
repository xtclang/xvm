package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ImportStatement
import org.xvm.lsp.adapter.CodeAction.CodeActionKind

/** Java parser ranges propose edits; XdkProjectQueries proves their semantic safety before publication. */
internal object XdkImports {
    data class Candidate(val title: String, val kind: CodeActionKind, val edits: List<XdkRename.Edit>)

    fun candidates(text: String): List<Candidate> {
        val errors = ErrorList()
        val root = try { Parser(Source(text), errors).parseSource() } catch (_: CompilerException) { return emptyList() }
        if (errors.hasSeriousErrors()) return emptyList()
        val tokens = Lexer(Source(text), errors).asSequence().toList()
        if (errors.hasSeriousErrors()) return emptyList()
        val imports = nodes(root).filterIsInstance<ImportStatement>()
            .filter { !it.isWildcard && it.childNodes().none() }.mapNotNull { node ->
                val start = offset(text, node.startPosition) ?: return@mapNotNull null
                val last = tokens.firstOrNull { it.startPosition >= node.endPosition } ?: return@mapNotNull null
                if (last.id != Token.Id.SEMICOLON) return@mapNotNull null
                node to XdkRename.Edit(start, offset(text, last.endPosition) ?: return@mapNotNull null, "")
            }
        // Bound compiler proof work per request; explicit follow-up requests can remove further imports.
        val removals = imports.take(32).map { (node, edit) ->
            Candidate("Remove unused import '${node.aliasName}'", CodeActionKind.SOURCE, listOf(edit))
        }
        val groups = imports.groupBy { it.first.parent }.values.map { it.sortedBy { (_, edit) -> edit.start } }
        val reorder = groups.mapNotNull { group ->
            if (group.size < 2 || group.zipWithNext().any { (a, b) -> text.substring(a.second.end, b.second.start).any { !it.isWhitespace() } }) {
                return@mapNotNull null
            }
            val written = group.map { (_, edit) -> text.substring(edit.start, edit.end) }
            val sorted = written.sorted()
            if (written == sorted) return@mapNotNull null
            Candidate("Organize imports", CodeActionKind.SOURCE_ORGANIZE_IMPORTS,
                group.mapIndexed { index, (_, edit) -> edit.copy(text = sorted[index]) })
        }
        return reorder + removals
    }

    private fun offset(text: String, position: Long): Int? =
        XdkRename.offset(text, SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)))

    private fun nodes(root: AstNode): List<AstNode> = listOf(root) + root.childNodes().flatMap(::nodes)
}
