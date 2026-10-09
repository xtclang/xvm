package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Inline single-use locals with either adjacent evaluation or compiler-proven constant values. */
internal object XdkLocalInline {
    data class Candidate(
        val title: String,
        val edits: List<XdkRename.Edit>,
        val relocation: XdkRename.Relocation,
        val removed: Set<SemanticModel.SourceLocation>,
        val expression: SemanticModel.SourceLocation? = null,
    )

    private data class AdjacentUse(
        val assignment: AssignmentStatement,
        val statement: AstNode,
        val expression: Expression,
        val type: String,
    )

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): Candidate? = adjacent(text, selection, model) ?: constant(text, selection, model, facts)

    private fun adjacent(
        text: String,
        selection: Range,
        model: SemanticModel,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val selected = XdkLocalDeclarations.selectedSymbol(model, selection) ?: return null
        val declaration = selected.declaration ?: return null
        val uses = model.occurrences.filter { it.symbol == selected.id }
        if (uses.size != 2 || uses.count { it.role == SemanticModel.Role.DECLARATION } != 1) return null
        val errors = ErrorList()
        val root = XdkRefactoringSyntax.parse(text, errors, "inline-local") ?: return null

        fun position(position: Long) = SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position))

        fun offset(position: Long) = XdkRename.offset(text, position(position))

        fun pairs(
            node: AstNode,
            method: MethodDeclarationStatement? = null,
        ): List<AdjacentUse> {
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
                        if (first !is AssignmentStatement) return@mapNotNull null
                        when (second) {
                            is ReturnStatement -> {
                                val expression = second.expressions?.singleOrNull() ?: return@mapNotNull null
                                val type = XdkLocalExtraction.writtenReturnType(text, owner) ?: return@mapNotNull null
                                AdjacentUse(first, second, expression, type)
                            }

                            is AssignmentStatement -> {
                                XdkLocalDeclarations.initializer(text, second)?.let {
                                    AdjacentUse(first, second, second.rValue, it.type)
                                }
                            }

                            else -> {
                                null
                            }
                        }
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
        val initializer = XdkLocalDeclarations.initializer(text, assignment) ?: return null
        val read = pair.expression as? NameExpression ?: return null
        if (!read.isSimpleName || read.name != selected.name) return null
        val readRange = SemanticModel.Range(position(read.startPosition), position(read.endPosition))
        if (uses.singleOrNull { it.role == SemanticModel.Role.REFERENCE }?.range != readRange) return null
        if (initializer.type != pair.type) return null
        val expression = assignment.rValue
        val start = offset(assignment.startPosition) ?: return null
        val valueStart = offset(expression.startPosition) ?: return null
        val valueEnd = offset(expression.endPosition) ?: return null
        val nextStart = offset(pair.statement.startPosition) ?: return null
        val readStart = offset(read.startPosition) ?: return null
        val readEnd = offset(read.endPosition) ?: return null
        if (XdkRefactoringSyntax.indentBefore(text, start) == null) return null
        val trailing = text.substring(valueEnd, nextStart)
        // Do not erase comments or labels; the declaration must be on its own preceding line.
        if (!Regex("\\s*;[ \\t]*(?:\\r\\n|\\r|\\n)[ \\t\\r\\n]*").matches(trailing)) return null
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
            if (pair.statement is ReturnStatement) "Inline returned local variable" else "Inline local variable into initializer",
            listOf(XdkRename.Edit(start, nextStart, ""), destination),
            XdkRename.Relocation(valueStart, valueEnd, destination, 0),
            removed,
        )
    }

    /** A constant needs no evaluation at its declaration; its sole read keeps its exact type. */
    private fun constant(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: CompilerRenameFacts,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val symbol = XdkLocalDeclarations.selectedSymbol(model, selection) ?: return null
        val declared = SemanticModel.SourceLocation(source, symbol.declaration ?: return null)
        if (declared !in facts.removableLocals) return null
        val uses = model.occurrences.filter { it.symbol == symbol.id }
        if (uses.size != 2 || uses.count { it.role == SemanticModel.Role.DECLARATION } != 1) return null
        val use = uses.singleOrNull { it.role == SemanticModel.Role.REFERENCE && it.usage == SemanticModel.Usage.READ } ?: return null
        val errors = ErrorList()
        val root = XdkRefactoringSyntax.parse(text, errors, "inline-constant-local") ?: return null

        fun at(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun offset(value: Long) = XdkRename.offset(text, at(value))
        val all = XdkRefactoringSyntax.tree(root).map { it.first }.toList()
        val local =
            all
                .filterIsInstance<AssignmentStatement>()
                .mapNotNull { XdkLocalDeclarations.initializer(text, it) }
                .singleOrNull { at(it.local.nameToken.startPosition) == declared.range.start } ?: return null
        val read =
            all.filterIsInstance<NameExpression>().singleOrNull {
                it.isSimpleName && at(it.startPosition) == use.range.start && at(it.endPosition) == use.range.end
            } ?: return null
        val expression = local.statement.rValue
        val valueStart = offset(expression.startPosition) ?: return null
        val valueEnd = offset(expression.endPosition) ?: return null
        val start = offset(local.statement.startPosition) ?: return null
        val indent = XdkRefactoringSyntax.indentBefore(text, start) ?: return null
        val lineStart = start - indent.length
        // Keep comments and declaration trivia intact by refusing anything beyond whitespace.
        val tail = Regex("[ \t]*;[ \t]*(?:\r\n|\r|\n)").find(text, valueEnd)?.takeIf { it.range.first == valueEnd } ?: return null
        val header =
            ExecutionTrace.api("Lexer.lex(inline-local-declaration)") {
                Lexer(Source(text.substring(start, valueStart)), errors).asSequence().toList()
            }
        if (errors.hasSeriousErrors() || header.any { it.id in setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT) }) return null
        val destination =
            XdkRename.Edit(
                offset(read.startPosition) ?: return null,
                offset(read.endPosition) ?: return null,
                "(${text.substring(valueStart, valueEnd)})",
            )
        val removed =
            model.occurrences
                .filter {
                    it.range == use.range || (XdkRename.offset(text, it.range.start) ?: -1) in start until valueStart
                }.mapTo(linkedSetOf()) { SemanticModel.SourceLocation(source, it.range) }
        return Candidate(
            "Inline constant local variable",
            listOf(XdkRename.Edit(lineStart, tail.range.last + 1, ""), destination),
            XdkRename.Relocation(valueStart, valueEnd, destination, 1),
            removed,
            SemanticModel.SourceLocation(source, SemanticModel.Range(at(expression.startPosition), at(expression.endPosition))),
        )
    }
}
