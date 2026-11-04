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
import org.xvm.lsp.util.ExecutionTrace

/**
 * Java parser ranges propose edits; XdkProjectQueries proves their semantic safety before
 * publication.
 */
internal object XdkImports {
    /** Proposed import edit awaiting the compiler checks that establish its safety. */
    data class Candidate(
        val title: String,
        val kind: CodeActionKind,
        val edits: List<XdkRename.Edit>,
    )

    /** An import statement the cleanup can remove or sort: the name it imports, and its range through its semicolon. */
    data class Import(
        val name: String,
        val statement: XdkRename.Edit,
    )

    /** The file's named imports, in source order; none when it does not parse or lex cleanly. */
    fun imports(text: String): List<Import> {
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(imports)") {
                    Parser(Source(text), errors).parseSource()
                }
            } catch (_: CompilerException) {
                return emptyList()
            }
        if (errors.hasSeriousErrors()) return emptyList()
        val tokens =
            ExecutionTrace.api("Lexer.lex(imports)") {
                Lexer(Source(text), errors).asSequence().toList()
            }
        if (errors.hasSeriousErrors()) return emptyList()
        return nodes(root)
            .filterIsInstance<ImportStatement>()
            .filter { !it.isWildcard && it.childNodes().none() }
            .mapNotNull { node ->
                val start = offset(text, node.startPosition) ?: return@mapNotNull null
                val last =
                    tokens.firstOrNull { it.startPosition >= node.endPosition }
                        ?: return@mapNotNull null
                if (last.id != Token.Id.SEMICOLON) return@mapNotNull null
                Import(node.aliasName, XdkRename.Edit(start, offset(text, last.endPosition) ?: return@mapNotNull null, ""))
            }.sortedBy { it.statement.start }
    }

    /** One removal for each of the first imports, each to be proved unused on its own. */
    fun removals(imports: List<Import>): List<Pair<Import, Candidate>> =
        // Bound compiler proof work per request; explicit follow-up requests can remove further
        // imports.
        imports.take(32).map { import ->
            import to Candidate("Remove unused import '${import.name}'", CodeActionKind.SOURCE, listOf(import.statement))
        }

    /**
     * "Organize imports": removes the [unused] imports and sorts the others of each group, the imports with only
     * whitespace between them, in place; a comment or other text between imports starts another group. Null when
     * nothing would change.
     */
    fun organize(
        text: String,
        imports: List<Import>,
        unused: Set<Import>,
    ): Candidate? {
        val edits =
            groups(text, imports).flatMap { group ->
                if (group.all { it in unused }) {
                    listOf(deletion(text, group.first().statement.start, group.last().statement.end))
                } else {
                    listOfNotNull(sorted(text, group, unused))
                }
            }
        return if (edits.isEmpty()) null else Candidate("Organize imports", CodeActionKind.SOURCE_ORGANIZE_IMPORTS, edits)
    }

    /** The runs of [imports] with only whitespace between them, in source order. */
    private fun groups(
        text: String,
        imports: List<Import>,
    ): List<List<Import>> {
        val starts =
            imports.zipWithNext().mapIndexedNotNull { index, (a, b) ->
                (index + 1).takeUnless { text.substring(a.statement.end, b.statement.start).isBlank() }
            }
        return (listOf(0) + starts + imports.size)
            .zipWithNext { from, to -> imports.subList(from, to) }
            .filter { it.isNotEmpty() }
    }

    /**
     * The group's kept imports in sorted order, in place of the whole group: each takes the whitespace written
     * before the import whose place it fills, and an unused import's place goes with the whitespace before it.
     */
    private fun sorted(
        text: String,
        group: List<Import>,
        unused: Set<Import>,
    ): XdkRename.Edit? {
        val start = group.first().statement.start
        val end = group.last().statement.end
        val kept = group.filter { it !in unused }
        val written = kept.map { text.substring(it.statement.start, it.statement.end) }
        val replacement =
            written.sorted().withIndex().joinToString("") { (index, import) ->
                if (index == 0) {
                    import
                } else {
                    val before = group[group.indexOf(kept[index]) - 1]
                    text.substring(before.statement.end, kept[index].statement.start) + import
                }
            }
        return XdkRename.Edit(start, end, replacement).takeUnless { replacement == text.substring(start, end) }
    }

    /** Deletes the text from [start] to [end], and its lines too when nothing else is written on them. */
    private fun deletion(
        text: String,
        start: Int,
        end: Int,
    ): XdkRename.Edit {
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
        return if (text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()) {
            XdkRename.Edit(lineStart, lineEnd, "")
        } else {
            XdkRename.Edit(start, end, "")
        }
    }

    private fun offset(
        text: String,
        position: Long,
    ): Int? =
        XdkRename.offset(
            text,
            SemanticModel.Position(
                Source.calculateLine(position),
                Source.calculateOffset(position),
            ),
        )

    private fun nodes(root: AstNode): List<AstNode> = listOf(root) + root.childNodes().flatMap(::nodes)
}
