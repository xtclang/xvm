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

    /** Nonempty, ordered selection in the original document's UTF-16 coordinates. */
    fun selectionOffsets(
        text: String,
        selection: Range,
    ): Pair<Int, Int>? {
        val start = XdkRename.offset(text, SemanticModel.Position(selection.start.line, selection.start.column)) ?: return null
        val end = XdkRename.offset(text, SemanticModel.Position(selection.end.line, selection.end.column)) ?: return null
        return (start to end).takeIf { start < end }
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
