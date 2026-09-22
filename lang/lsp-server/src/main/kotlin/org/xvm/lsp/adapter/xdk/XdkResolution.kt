package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Argument
import org.xvm.asm.Constant
import org.xvm.asm.Register
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ComponentStatement
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range

/**
 * Navigation within a compiled document. Members match by constant identity; locals match by the
 * original register object, shared by its narrowed shadows. Register value equality is unsuitable:
 * unrelated methods can allocate equal registers, and sibling scopes can reuse the same spelling.
 *
 * Declarations in other files and generated/captured bindings without a retained source association
 * are not available here. Unresolved names produce no speculative navigation result.
 */
internal object XdkResolution {
    fun declarationOf(
        ast: AstNode?,
        line: Int,
        column: Int,
    ): Range? {
        val target = targetAt(ast, line, column) ?: return null
        return declarationOf(XdkAst.nodesIn(ast), target)
    }

    fun referencesTo(
        ast: AstNode?,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Range> {
        val target = targetAt(ast, line, column) ?: return emptyList()
        val nodes = XdkAst.nodesIn(ast)
        val declaration = declarationOf(nodes, target)
        val uses =
            nodes
                .mapNotNull { node ->
                    val resolved =
                        when (node) {
                            is NameExpression -> node.resolvedTarget
                            is NamedTypeExpression -> node.identityConstant
                            is InvocationExpression -> node.resolvedMethod
                            else -> null
                        }
                    if (sameTarget(resolved, target)) occurrenceRange(node) else null
                }.filter { it != declaration }
        return (if (includeDeclaration && declaration != null) uses + declaration else uses)
            .distinct()
            .sortedWith(compareBy({ it.start.line }, { it.start.column }, { it.end.line }, { it.end.column }))
    }

    /** Resolve the name or type under the cursor without borrowing an enclosing call's target. */
    private fun targetAt(
        ast: AstNode?,
        line: Int,
        column: Int,
    ): Argument? {
        val position = Position(line, column)
        val chain = XdkAst.chainAt(ast, line, column)
        for (node in chain.asReversed()) {
            when (node) {
                is NameExpression -> {
                    if (node.nameToken.contains(position)) {
                        // A callee name is resolved by its invocation, not by NameExpression.
                        // An argument can also have an invocation as its parent; compare the actual
                        // invoked expression so that unresolved arguments cannot borrow its target.
                        val invocation =
                            chain
                                .filterIsInstance<InvocationExpression>()
                                .lastOrNull { it.invokedExpression === node }
                        return invocation?.resolvedMethod ?: node.resolvedTarget
                    }
                }

                is VariableDeclarationStatement -> {
                    if (node.nameToken.contains(position)) return node.register
                }

                is NamedTypeExpression -> {
                    if (node.nameToken?.contains(position) == true) return node.identityConstant
                }

                is Parameter -> {
                    if (node.nameToken?.contains(position) == true) return node.resolvedTarget
                }

                else -> {
                    val token = declarationName(node)
                    if (token != null && token.contains(position)) return node.component?.identityConstant
                }
            }
        }
        return null
    }

    private fun declarationOf(
        nodes: List<AstNode>,
        target: Argument,
    ): Range? {
        val declaration =
            nodes.firstOrNull { node ->
                if (node is Parameter) return@firstOrNull sameTarget(node.resolvedTarget, target)
                when (target) {
                    is Register -> node is VariableDeclarationStatement && sameTarget(node.register, target)
                    is Constant -> node is ComponentStatement && declarationName(node) != null && node.component?.identityConstant == target
                    else -> false
                }
            } ?: return null
        return declarationName(declaration)?.let { XdkAst.spanOf(it.startPosition, it.endPosition) }
    }

    private fun sameTarget(
        candidate: Argument?,
        target: Argument,
    ): Boolean =
        when (target) {
            is Register -> candidate is Register && candidate.originalRegister === target.originalRegister
            is Constant -> candidate is Constant && candidate == target
            else -> false
        }

    private fun declarationName(node: AstNode): Token? =
        when (node) {
            is VariableDeclarationStatement -> node.nameToken
            is Parameter -> node.nameToken
            is PropertyDeclarationStatement -> node.nameToken
            is MethodDeclarationStatement -> node.nameToken
            is TypeCompositionStatement -> node.nameToken
            else -> null
        }

    private fun occurrenceRange(node: AstNode): Range? =
        when (node) {
            is NameExpression -> node.nameToken.let { XdkAst.spanOf(it.startPosition, it.endPosition) }
            is InvocationExpression -> (node.invokedExpression as? NameExpression)?.let { occurrenceRange(it) }
            is NamedTypeExpression -> node.nameToken?.let { XdkAst.spanOf(it.startPosition, it.endPosition) }
            else -> null
        }

    private fun Token.contains(position: Position): Boolean {
        val range = XdkAst.spanOf(startPosition, endPosition)
        if (position.line < range.start.line || position.line > range.end.line) return false
        if (position.line == range.start.line && position.column < range.start.column) return false
        return position.line != range.end.line || position.column < range.end.column
    }
}
