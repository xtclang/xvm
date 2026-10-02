package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Inline only an adjacent, single-use returned local with the same written expected type. */
internal object XdkLocalInline {
    data class Candidate(
        val edits: List<XdkRename.Edit>,
        val relocation: XdkRename.Relocation,
        val removed: Set<SemanticModel.SourceLocation>,
    )

    private data class ReturnedLocal(
        val assignment: AssignmentStatement,
        val returned: ReturnStatement,
        val method: MethodDeclarationStatement?,
    )

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val selected =
            model
                .symbolAt(selection.start.line, selection.start.column)
                ?.takeIf { it.kind == SemanticModel.SymbolKind.VARIABLE && it.declarationSource == source }
                ?: return null
        val declaration = selected.declaration ?: return null
        val uses = model.occurrences.filter { it.symbol == selected.id }
        if (uses.size != 2 || uses.count { it.role == SemanticModel.Role.DECLARATION } != 1) return null
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(inline-local)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null

        fun position(position: Long) = SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position))

        fun offset(position: Long) = XdkRename.offset(text, position(position))

        fun pairs(
            node: AstNode,
            method: MethodDeclarationStatement? = null,
        ): List<ReturnedLocal> {
            val owner =
                when (node) {
                    is MethodDeclarationStatement -> node
                    is LambdaExpression -> null
                    else -> method
                }
            val children = node.childNodes()
            val adjacent =
                if (node is StatementBlock) {
                    children.zipWithNext().mapNotNull { (first, second) ->
                        if (first is AssignmentStatement && second is ReturnStatement) ReturnedLocal(first, second, owner) else null
                    }
                } else {
                    emptyList()
                }
            return adjacent + children.flatMap { pairs(it, owner) }
        }
        val pair =
            pairs(root).singleOrNull {
                val local = it.assignment.lValue as? VariableDeclarationStatement
                local != null && position(local.nameToken.startPosition) == declaration.start &&
                    position(local.nameToken.endPosition) == declaration.end
            } ?: return null
        val assignment = pair.assignment
        if (assignment.op.id != Token.Id.ASN) return null
        val local = assignment.lValue as VariableDeclarationStatement
        val read = pair.returned.expressions?.singleOrNull() as? NameExpression ?: return null
        if (!read.isSimpleName || read.name != selected.name) return null
        val readRange = SemanticModel.Range(position(read.startPosition), position(read.endPosition))
        if (uses.singleOrNull { it.role == SemanticModel.Role.REFERENCE }?.range != readRange) return null
        val type = local.childNodes().filterIsInstance<TypeExpression>().singleOrNull() ?: return null
        val writtenType = text.substring(offset(type.startPosition) ?: return null, offset(type.endPosition) ?: return null)
        if (writtenType != XdkLocalExtraction.writtenReturnType(text, pair.method)) return null
        val expression = assignment.rValue
        val start = offset(assignment.startPosition) ?: return null
        val valueStart = offset(expression.startPosition) ?: return null
        val valueEnd = offset(expression.endPosition) ?: return null
        val returnStart = offset(pair.returned.startPosition) ?: return null
        val readStart = offset(read.startPosition) ?: return null
        val readEnd = offset(read.endPosition) ?: return null
        val lineStart = maxOf(text.lastIndexOf('\n', start - 1), text.lastIndexOf('\r', start - 1)) + 1
        val indent = text.substring(lineStart, start)
        if (indent.any { it != ' ' && it != '\t' }) return null
        val trailing = text.substring(valueEnd, returnStart)
        // Do not erase comments or labels; the declaration must be on its own preceding line.
        if (!Regex("\\s*;[ \\t]*(?:\\r\\n|\\r|\\n)[ \\t\\r\\n]*").matches(trailing)) return null
        // Ref/Var annotations and declaration modifiers are outside this transformation.
        if (!text.substring(start, offset(type.startPosition) ?: return null).isBlank() ||
            !text.substring(offset(type.endPosition) ?: return null, offset(local.nameToken.startPosition) ?: return null).isBlank() ||
            !Regex("\\s*=\\s*").matches(text.substring(offset(local.nameToken.endPosition) ?: return null, valueStart))
        ) {
            return null
        }
        val destination = XdkRename.Edit(readStart, readEnd, text.substring(valueStart, valueEnd))
        val removed =
            model.occurrences
                .filter {
                    it.range == readRange ||
                        (
                            (XdkRename.offset(text, it.range.start) ?: -1) >= start &&
                                (XdkRename.offset(text, it.range.end) ?: Int.MAX_VALUE) <= valueStart
                        )
                }.map { SemanticModel.SourceLocation(source, it.range) }
                .toSet()
        return Candidate(
            listOf(XdkRename.Edit(start, returnStart, ""), destination),
            XdkRename.Relocation(valueStart, valueEnd, destination, 0),
            removed,
        )
    }
}
