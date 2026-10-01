package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/**
 * A deliberately small extract-local boundary: one literal, returned directly from a block.
 * Parsing proves evaluation order; the graph proof must still validate types and every old binding.
 */
internal object XdkLiteralExtraction {
    fun edits(
        text: String,
        selection: Range,
    ): List<XdkRename.Edit>? {
        val start = XdkRename.offset(text, SemanticModel.Position(selection.start.line, selection.start.column)) ?: return null
        val end = XdkRename.offset(text, SemanticModel.Position(selection.end.line, selection.end.column)) ?: return null
        if (start >= end) return null
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(extract-local)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null

        fun offset(position: Long): Int? =
            XdkRename.offset(text, SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)))

        fun returns(node: AstNode): List<ReturnStatement> =
            node.childNodes().flatMap { child ->
                if (node is StatementBlock && child is ReturnStatement) listOf(child) else returns(child)
            }
        val statement =
            returns(root).singleOrNull { statement ->
                val literal = statement.expressions?.singleOrNull() as? LiteralExpression
                literal != null && literal.literal.id in setOf(Token.Id.LIT_INT, Token.Id.LIT_STRING, Token.Id.LIT_CHAR) &&
                    offset(literal.startPosition) == start && offset(literal.endPosition) == end
            } ?: return null
        val insertion = offset(statement.startPosition) ?: return null
        val indent = text.substring(text.lastIndexOf('\n', insertion - 1) + 1, insertion)
        // Do not rewrite same-line siblings, labels, comments or expression-bodied declarations.
        if (indent.any { it != ' ' && it != '\t' }) return null
        val names =
            ExecutionTrace.api("Lexer.lex(extract-local)") {
                Lexer(Source(text), errors)
                    .asSequence()
                    .filter { it.id == Token.Id.IDENTIFIER }
                    .map { it.valueText }
                    .toSet()
            }
        if (errors.hasSeriousErrors()) return null
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) "extractedValue" else "extractedValue$it" }.first { it !in names }
        val newline = if ("\r\n" in text) "\r\n" else "\n"
        return listOf(
            XdkRename.Edit(insertion, insertion, "val $name = ${text.substring(start, end)};$newline$indent"),
            XdkRename.Edit(start, end, name),
        )
    }
}
