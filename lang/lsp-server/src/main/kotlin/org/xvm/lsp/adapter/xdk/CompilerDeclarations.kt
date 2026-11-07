package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.util.ExecutionTrace

/**
 * Written inherited contracts for an overriding member, copied before leaving the compiler worker.
 */
internal fun compilerDeclarationTargets(
    nodes: List<AstNode>,
    errors: ErrorListener,
): Map<IdentityConstant, Set<IdentityConstant>> =
    buildMap {
        nodes.filterIsInstance<TypeCompositionStatement>().forEach { node ->
            if (errors.isAbortDesired) return emptyMap()
            val structure = node.component as? ClassStructure ?: return@forEach
            val info =
                ExecutionTrace.api("TypeConstant.ensureTypeInfo(declaration)") {
                    structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
                }
            if (errors.hasSeriousErrors() || errors.isAbortDesired) return emptyMap()
            val chains =
                info.methods.values
                    .filter { !it.isFunction && !it.isCtorOrValidator }
                    .map { method ->
                        method.chain.mapNotNull { it.methodStructure?.identityConstant }
                    } +
                    info.properties.values.map { property ->
                        property.propertyBodies.map { it.identity }
                    }
            chains.forEach { chain ->
                val inherited = chain.filter { it.namespace != structure.identityConstant }.toSet()
                if (inherited.isNotEmpty()) {
                    // Only the overriding host owns this relation. An inherited method used by a
                    // later class must not acquire that later class's unrelated contracts.
                    chain
                        .filter { it.namespace == structure.identityConstant }
                        .forEach { member ->
                            put(member, get(member).orEmpty() + inherited)
                        }
                }
            }
        }
    }
