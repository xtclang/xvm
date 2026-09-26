package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.PropertyStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.PropertyConstant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement

/** Attempt-owned property dispatch facts, including written getter/setter owners. */
internal class CompilerPropertyRelations(
    val declarations: Set<PropertyConstant> = emptySet(),
    val chains: List<Chain> = emptyList(),
) {
    data class Chain(
        val owner: IdentityConstant,
        val properties: List<PropertyConstant>,
        val supported: Boolean,
    )
}

internal fun compilerPropertyRelations(
    nodes: List<AstNode>,
    errors: ErrorListener,
): CompilerPropertyRelations {
    val declarations =
        nodes
            .filterIsInstance<PropertyDeclarationStatement>()
            .mapNotNull { it.component as? PropertyStructure }
            .filterNot { it.isStatic || it.isSynthetic }
            .mapTo(linkedSetOf()) { it.identityConstant }
    val chains =
        nodes.filterIsInstance<TypeCompositionStatement>().flatMap { node ->
            if (errors.isAbortDesired) return@flatMap emptyList()
            val structure = node.component as? ClassStructure ?: return@flatMap emptyList()
            val info = structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
            info.properties.values
                .filter { property -> property.propertyBodies.any { it.identity in declarations } }
                .map { property ->
                    CompilerPropertyRelations.Chain(
                        structure.identityConstant,
                        property.propertyBodies.map { it.identity },
                        structure.format != Format.MIXIN &&
                            property.propertyBodies.all {
                                it.refAnnotations.isEmpty() &&
                                    it.implementation in setOf(Implementation.Explicit, Implementation.Declared, Implementation.Default)
                            },
                    )
                }
        }
    return CompilerPropertyRelations(declarations, chains)
}
