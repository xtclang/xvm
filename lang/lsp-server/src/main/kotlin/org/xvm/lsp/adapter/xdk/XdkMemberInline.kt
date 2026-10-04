package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotationExpression
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Copy one private member expression at its use; declaration deletion is a separate proof. */
internal object XdkMemberInline {
    data class Candidate(
        val title: String,
        val source: String,
        val expression: SemanticModel.Range,
        val use: SemanticModel.Range,
        val edit: XdkRename.Edit,
        val ignored: Set<SemanticModel.SourceLocation>,
    )

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val symbol = model.symbolAt(selection.start.line, selection.start.column) ?: return null
        if (symbol.declarationSource != source) return null
        val declaration = symbol.declaration ?: return null
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(inline-member)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null

        fun tree(
            node: AstNode,
            parent: AstNode? = null,
        ): Sequence<Pair<AstNode, AstNode?>> = sequenceOf(node to parent) + node.childNodes().asSequence().flatMap { tree(it, node) }
        val parents = tree(root).toMap()

        fun at(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun range(node: AstNode) = SemanticModel.Range(at(node.startPosition), at(node.endPosition))

        fun owner(node: AstNode) =
            generateSequence(parents[node]) { parents[it] }.filterIsInstance<TypeCompositionStatement>().firstOrNull()
        val selected = SemanticModel.Position(selection.start.line, selection.start.column)
        val read =
            parents.keys.filterIsInstance<NameExpression>().singleOrNull {
                it.isSimpleName && !it.hasTrailingTypeParams() && !it.isSuppressDeref && selected in range(it)
            } ?: return null
        val (member, use, expression) =
            when (symbol.kind) {
                SemanticModel.SymbolKind.METHOD -> {
                    val signature = symbol.signature ?: return null
                    if (signature.parameters.isNotEmpty() || signature.conditional || signature.returns.size != 1) return null
                    val method =
                        parents.keys.filterIsInstance<MethodDeclarationStatement>().singleOrNull {
                            it.nameToken?.startPosition?.let(::at) == declaration.start
                        } ?: return null
                    if (method.defaultAccess != Access.PRIVATE || method.isConstructor || method.isConstructorFinally) return null
                    val block = method.childNodes().filterIsInstance<StatementBlock>().singleOrNull() ?: return null
                    val returned = block.childNodes().singleOrNull() as? ReturnStatement ?: return null
                    val value = returned.expressions?.singleOrNull() ?: return null
                    val call = parents[read] as? InvocationExpression ?: return null
                    if (call.isAsync || call.childNodes().singleOrNull() !== read) return null
                    Triple(method, call, value)
                }

                SemanticModel.SymbolKind.PROPERTY -> {
                    val property =
                        parents.keys.filterIsInstance<PropertyDeclarationStatement>().singleOrNull {
                            at(it.nameToken.startPosition) == declaration.start
                        } ?: return null
                    if (property.defaultAccess != Access.PRIVATE || !property.isStatic) return null
                    val value =
                        property
                            .childNodes()
                            .filterIsInstance<Expression>()
                            .filterNot { it is TypeExpression }
                            .singleOrNull() ?: return null
                    if (property.childNodes().any { it is StatementBlock }) return null
                    Triple(property, read, value)
                }

                else -> {
                    return null
                }
            }
        if (owner(member) !== owner(use)) return null
        // A local callable or closure can have a different receiver or capture environment.
        if (generateSequence(parents[use]) { parents[it] }
                .takeWhile { it !== owner(use) }
                .any { it is LambdaExpression }
        ) {
            return null
        }
        if (tree(member).any { (node, _) -> node is AnnotationExpression }) return null
        if (tree(expression).any { (node, _) -> node is LambdaExpression || node is TypeCompositionStatement }) return null
        val expressionRange = range(expression)
        val useRange = range(use)
        if (useRange.start >= expressionRange.start && useRange.end <= expressionRange.end) return null
        val from = XdkRename.offset(text, expressionRange.start) ?: return null
        val to = XdkRename.offset(text, expressionRange.end) ?: return null
        val value = text.substring(from, to)
        val tokens = ExecutionTrace.api("Lexer.lex(inline-member)") { Lexer(Source(value), errors).asSequence().toList() }
        if (errors.hasSeriousErrors() || tokens.any { it.id == Token.Id.BIT_AND || it.valueText == "super" }) return null
        // Copying a recursive invocation or escaped self-reference is not method expansion.
        if (model.occurrences.any {
                it.symbol == symbol.id && it.range.start >= expressionRange.start && it.range.end <= expressionRange.end
            }
        ) {
            return null
        }
        val start = XdkRename.offset(text, useRange.start) ?: return null
        val end = XdkRename.offset(text, useRange.end) ?: return null
        val ignored =
            model.occurrences
                .filter { it.range.start >= useRange.start && it.range.end <= useRange.end }
                .mapTo(linkedSetOf()) { SemanticModel.SourceLocation(source, it.range) }
        return Candidate(
            if (symbol.kind == SemanticModel.SymbolKind.METHOD) "Inline private method call" else "Inline constant property",
            source,
            expressionRange,
            useRange,
            XdkRename.Edit(start, end, "($value)"),
            ignored,
        )
    }
}
