package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Delete an unused plain local only after the compiler proves its initializer needs no evaluation. */
internal object XdkLocalRemoval {
    data class Candidate(
        val edit: XdkRename.Edit,
        val removed: Set<SemanticModel.SourceLocation>,
    )

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
        removable: Set<SemanticModel.SourceLocation>,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val symbol = XdkLocalDeclarations.selectedSymbol(model, selection) ?: return null
        val declaration = symbol.declaration ?: return null
        if (SemanticModel.SourceLocation(source, declaration) !in removable) return null
        val uses = model.occurrences.filter { it.symbol == symbol.id }
        if (uses.singleOrNull()?.role != SemanticModel.Role.DECLARATION) return null
        val errors = ErrorList()
        val root = XdkRefactoringSyntax.parse(text, errors, "remove-local") ?: return null

        fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun offset(value: Long) = XdkRename.offset(text, position(value))
        val candidate =
            XdkRefactoringSyntax
                .tree(root)
                .map { it.first }
                .filterIsInstance<StatementBlock>()
                .flatMap { it.childNodes().asSequence() }
                .filterIsInstance<AssignmentStatement>()
                .mapNotNull { XdkLocalDeclarations.initializer(text, it) }
                .singleOrNull {
                    position(it.local.nameToken.startPosition) == declaration.start &&
                        position(it.local.nameToken.endPosition) == declaration.end
                }
                ?: return null
        val start = offset(candidate.statement.startPosition) ?: return null
        val end = offset(candidate.statement.endPosition) ?: return null
        val indent = XdkRefactoringSyntax.indentBefore(text, start) ?: return null
        val lineStart = start - indent.length
        val tail = Regex("[ \t]*;").find(text, end)?.takeIf { it.range.first == end } ?: return null
        val syntaxEnd = tail.range.last + 1
        val tokens =
            ExecutionTrace.api("Lexer.lex(remove-local)") {
                Lexer(Source(text.substring(start, syntaxEnd)), errors).asSequence().toList()
            }
        // Never discard a comment hidden among the declaration or expression tokens.
        if (errors.hasSeriousErrors() || tokens.any { it.id in setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT) }) return null
        val lineEnd = Regex("[ \t]*(?:\r\n|\r|\n)").find(text, syntaxEnd)?.takeIf { it.range.first == syntaxEnd }
        val edit = if (lineEnd == null) XdkRename.Edit(start, syntaxEnd, "") else XdkRename.Edit(lineStart, lineEnd.range.last + 1, "")
        val removed =
            model.occurrences
                .filter {
                    (XdkRename.offset(text, it.range.start) ?: -1) >= start &&
                        (XdkRename.offset(text, it.range.end) ?: Int.MAX_VALUE) <= syntaxEnd
                }.map { SemanticModel.SourceLocation(source, it.range) }
                .toSet()
        return Candidate(edit, removed)
    }
}
