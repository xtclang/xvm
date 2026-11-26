package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.StatementBlock
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Source syntax shared by refactorings; eligibility and semantic proof remain with each action. */
internal object XdkRefactoringSyntax {
    /** Refactorings require a complete parse and retain the error list for subsequent lexing. */
    fun parse(
        text: String,
        errors: ErrorList,
        operation: String,
    ): StatementBlock? {
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource($operation)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        return root.takeUnless { errors.hasSeriousErrors() }
    }

    /** Fresh parses have no adopted parent links; enumerate ownership without mutating the AST. */
    fun tree(
        node: AstNode,
        parent: AstNode? = null,
    ): Sequence<Pair<AstNode, AstNode?>> = sequenceOf(node to parent) + node.childNodes().asSequence().flatMap { tree(it, node) }

    /** Nonempty, ordered selection of code in the original document's UTF-16 coordinates, without whitespace at its ends. */
    fun selectionOffsets(
        text: String,
        selection: Range,
    ): Pair<Int, Int>? {
        val from = XdkRename.offset(text, SemanticModel.Position(selection.start.line, selection.start.column)) ?: return null
        val to = XdkRename.offset(text, SemanticModel.Position(selection.end.line, selection.end.column)) ?: return null
        if (from >= to) return null
        // The whitespace at a selection's ends is not code: whole lines, from one line's start to the next one's, as
        // an editor selects them, select the statements on them.
        val start = (from until to).firstOrNull { !text[it].isWhitespace() } ?: return null
        val end = (to downTo start + 1).first { !text[it - 1].isWhitespace() }
        return start to end
    }

    /** Return indentation only when no sibling, label or comment precedes this position. */
    fun indentBefore(
        text: String,
        offset: Int,
    ): String? {
        val lineStart = maxOf(text.lastIndexOf('\n', offset - 1), text.lastIndexOf('\r', offset - 1)) + 1
        return text.substring(lineStart, offset).takeIf { indent -> indent.all { it == ' ' || it == '\t' } }
    }

    /**
     * The edit that removes [text] from [start] to [end], with the whole line when [lineStart], the start of a line
     * with nothing before [start], is given and a line break follows [end]. Null when what goes does not lex cleanly or
     * holds a comment, which a removal must never discard.
     */
    fun removal(
        text: String,
        start: Int,
        end: Int,
        lineStart: Int?,
        operation: String,
    ): XdkRename.Edit? {
        val errors = ErrorList()
        val tokens = ExecutionTrace.api("Lexer.lex($operation)") { Lexer(Source(text.substring(start, end)), errors).asSequence().toList() }
        if (errors.hasSeriousErrors() || tokens.any { it.id in setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT) }) return null
        val lineEnd = Regex("[ \t]*(?:\r\n|\r|\n)").find(text, end)?.takeIf { it.range.first == end }
        return if (lineStart != null && lineEnd != null) {
            XdkRename.Edit(lineStart, lineEnd.range.last + 1, "")
        } else {
            XdkRename.Edit(start, end, "")
        }
    }
}
