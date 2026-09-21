package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement

/**
 * Answering "what is this, and where does it come from" - the questions that need what a name
 * *means* rather than how it is spelled.
 *
 * Two things make it possible, and they are different things.
 * [NamedTypeExpression.getIdentityConstant] was already public: a type name knows the class it
 * resolved to. [NameExpression.getResolvedTarget] was not, and is the accessor this work added:
 * the compiler decides what a name refers to while it validates and keeps the answer on the node,
 * where nobody outside the compiler could read it. Two properties both called `x` on different
 * classes resolve to different constants, which is the whole difference between this and a text
 * search.
 *
 * Three limits, all real:
 *
 * - **One document.** A target declared elsewhere - anything from the core library, another file
 *   in the project - resolves perfectly well and has nothing here to point at. The answer is
 *   nothing rather than a guess. Answering those needs an index from identity to the place it was
 *   written, built across compiled documents.
 * - **Only what is declared by a statement.** A class, a method and a property are declared by a
 *   node that carries a component, and a component carries the identity to match against. A
 *   constructor parameter that becomes a property is not written as one of those, so there is
 *   nothing to point at even though the name resolves.
 * - **Locals are matched by name within their method.** A local resolves to a register, and a use
 *   is a *shadow* of the register the declaration produced once a type has been narrowed -
 *   deliberately not equal to it. Name-within-method is the honest approximation, and it is wrong
 *   only where two sibling scopes declare the same name.
 */
internal object XdkResolution {
    /**
     * Where the thing at this position was declared, if it was declared in this document.
     */
    fun declarationOf(
        ast: AstNode?,
        line: Int,
        column: Int,
    ): AstNode? {
        val chain = XdkAst.chainAt(ast, line, column)
        val target = identityAt(chain)
        return if (target == null) {
            localsNamed(chain, chain.filterIsInstance<NameExpression>().lastOrNull()).firstOrNull()
        } else {
            declaringNode(ast, target)
        }
    }

    /**
     * Every place in this document that refers to the same thing as the name at this position.
     */
    fun referencesTo(
        ast: AstNode?,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<AstNode> {
        val chain = XdkAst.chainAt(ast, line, column)
        val target = identityAt(chain)
        val uses =
            if (target == null) {
                localsNamed(chain, chain.filterIsInstance<NameExpression>().lastOrNull())
            } else {
                occurrencesOf(ast, target)
            }
        if (!includeDeclaration) {
            return uses
        }
        val declaration = declarationOf(ast, line, column)
        return if (declaration == null || declaration in uses) uses else listOf(declaration) + uses
    }

    /**
     * What the innermost thing at this position resolved to, when that is something with an
     * identity. A local variable has none - it is a register inside one method - so this answers
     * null for one, and the caller falls back to the scope.
     */
    private fun identityAt(chain: List<AstNode>): Constant? =
        chain.asReversed().firstNotNullOfOrNull { node ->
            when (node) {
                is NameExpression -> node.resolvedTarget as? Constant

                is NamedTypeExpression -> node.identityConstant

                // the name in a call resolves to nothing by itself: which method it is depends
                // on what it is called on and with what, and that is decided on the invocation
                is InvocationExpression -> node.resolvedMethod

                else -> null
            }
        }

    /** Everywhere in the document that resolved to this same identity. */
    private fun occurrencesOf(
        ast: AstNode?,
        target: Constant,
    ): List<AstNode> =
        distinctBySpan(
            XdkAst.nodesIn(ast).filter { node ->
                when (node) {
                    is NameExpression -> node.resolvedTarget == target
                    is NamedTypeExpression -> node.identityConstant == target
                    is InvocationExpression -> node.resolvedMethod == target
                    else -> false
                }
            },
        )

    /**
     * The declaration statement in this document that declares what an identity identifies.
     *
     * A class or a method is not written as a name where it is declared, so no expression there
     * points at it. Its declaration statement carries the component, and the component carries
     * the identity.
     */
    private fun declaringNode(
        ast: AstNode?,
        target: Constant,
    ): AstNode? =
        XdkAst.nodesIn(ast).firstOrNull { node ->
            node.declares() && runCatching { node.component?.identityConstant }.getOrNull() == target
        }

    /**
     * Uses of a local: every name spelled the same inside the method that declares it.
     */
    private fun localsNamed(
        chain: List<AstNode>,
        name: NameExpression?,
    ): List<AstNode> {
        if (name == null) {
            return emptyList()
        }
        val scope = chain.lastOrNull { it.declares() } ?: return emptyList()
        return distinctBySpan(XdkAst.nodesIn(scope).filterIsInstance<NameExpression>().filter { it.name == name.name })
    }

    /**
     * The same node can be reached twice - a constructor parameter's type is part of the
     * declaration and part of the property it becomes - and an editor should be shown one
     * occurrence per place in the text.
     */
    private fun distinctBySpan(nodes: List<AstNode>): List<AstNode> = nodes.distinctBy { it.startPosition to it.endPosition }

    private fun AstNode.declares(): Boolean =
        this is TypeCompositionStatement || this is MethodDeclarationStatement || this is PropertyDeclarationStatement
}
