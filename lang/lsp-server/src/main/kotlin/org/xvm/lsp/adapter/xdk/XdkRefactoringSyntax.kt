package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
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
}
