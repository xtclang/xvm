package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodConstant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement

/** Worker-only dispatch facts. These compiler identities must never enter a published snapshot. */
internal class CompilerMethodRelations(
    val declarations: Set<MethodConstant>,
    val chains: List<Chain>,
) {
    data class Chain(
        val owner: IdentityConstant,
        val methods: List<MethodConstant>,
        val supported: Boolean,
    )
}

/** Inspect compiler-composed chains, including abstract contracts and generic substitutions. */
internal fun compilerMethodRelations(
    nodes: List<AstNode>,
    errors: ErrorListener,
): CompilerMethodRelations {
    val declarations =
        nodes
            .filterIsInstance<MethodDeclarationStatement>()
            .mapNotNull { it.component as? MethodStructure }
            .filter { !it.isFunction && !it.isConstructor && it.identityConstant.isTopLevel }
            .mapTo(linkedSetOf()) { it.identityConstant }
    val chains =
        nodes.filterIsInstance<TypeCompositionStatement>().flatMap { node ->
            if (errors.isAbortDesired) return@flatMap emptyList()
            val structure = node.component as? ClassStructure ?: return@flatMap emptyList()
            val info = structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
            info.methods.values
                .filter { it.identity.isTopLevel && !it.isFunction && !it.isCtorOrValidator }
                .map { method ->
                    CompilerMethodRelations.Chain(
                        structure.identityConstant,
                        method.chain.map { it.methodStructure?.identityConstant ?: it.identity },
                        structure.format != Format.MIXIN &&
                            method.chain.all {
                                it.implementation in
                                    setOf(Implementation.Explicit, Implementation.Default, Implementation.Declared, Implementation.Abstract)
                            },
                    )
                }
        }
    return CompilerMethodRelations(declarations, chains)
}
