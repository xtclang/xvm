package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorListener
import org.xvm.asm.PropertyStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.PropertyConstant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.util.ExecutionTrace

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
    val primaryProperties =
        nodes
            .filterIsInstance<Parameter>()
            .filter { it.parent is TypeCompositionStatement }
            .mapNotNull { it.resolvedTarget as? PropertyConstant }
    val declarations =
        nodes
            .filterIsInstance<PropertyDeclarationStatement>()
            .mapNotNull { it.component as? PropertyStructure }
            .filterNot { it.isStatic || it.isSynthetic }
            .mapTo(linkedSetOf()) { it.identityConstant } + primaryProperties
    val chains =
        compilerSourceTypes(nodes)
            .flatMap { type ->
                if (errors.isAbortDesired) return@flatMap emptyList()
                val info =
                    ExecutionTrace.api("TypeConstant.ensureTypeInfo(property-relations)") {
                        type.ensureTypeInfo(errors)
                    }
                info.properties.values
                    .filter { property -> property.propertyBodies.any { it.identity in declarations } }
                    .map { property ->
                        CompilerPropertyRelations.Chain(
                            type.getSingleUnderlyingClass(true),
                            property.propertyBodies.map { it.identity },
                            property.propertyBodies.all {
                                it.implementation in
                                    setOf(
                                        Implementation.Explicit,
                                        Implementation.Declared,
                                        Implementation.Default,
                                        Implementation.FromInto,
                                        Implementation.Delegating,
                                    )
                            },
                        )
                    }
            }.distinct()
    return CompilerPropertyRelations(declarations, chains)
}
