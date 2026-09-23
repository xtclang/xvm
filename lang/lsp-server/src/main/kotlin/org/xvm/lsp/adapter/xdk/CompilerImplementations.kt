package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement

/**
 * Worker-only inspection of successful source types. TypeInfo supplies actual method chains,
 * including generic substitution and inherited bodies; no matching by method name or arity.
 * Synthetic redirects/delegation have no source body here and deliberately produce no target.
 */
internal fun compilerImplementationTargets(
    nodes: List<AstNode>,
    errors: ErrorListener,
): Map<IdentityConstant, Set<IdentityConstant>> {
    val targets = linkedMapOf<IdentityConstant, MutableSet<IdentityConstant>>()
    val inspection = ErrorListener.cancellable(ErrorListener.collecting(errors::log), errors::isAbortDesired)
    for (node in nodes.filterIsInstance<TypeCompositionStatement>()) {
        if (inspection.isAbortDesired) return emptyMap()
        val structure = node.component as? ClassStructure ?: continue
        // A mixin's into type is a constraint, not an adopting host. Inspect its composed hosts.
        if (structure.format == Format.MIXIN) continue
        val info = structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(inspection)
        if (inspection.hasSeriousErrors() || inspection.isAbortDesired) return emptyMap()
        if (info.isClass && !info.isAbstract && !info.isSynthetic) {
            (info.classChain.keys + info.defaultChain.keys).forEach { ancestor ->
                targets.getOrPut(ancestor) { linkedSetOf() }.add(structure.identityConstant)
            }
        }
        // Anonymous classes are synthetic containers, but their written method bodies are targets.
        val methods = info.methods.values.filter { it.identity.isTopLevel && !it.isFunction && !it.isCtorOrValidator && !it.isAbstract }
        for (method in methods) {
            val body = method.chain.firstOrNull { !it.isAbstract } ?: continue
            if (body.implementation !in setOf(Implementation.Explicit, Implementation.Default)) continue
            val implementation = body.methodStructure?.identityConstant ?: continue
            for (inherited in method.chain) {
                val declaration = inherited.methodStructure?.identityConstant ?: continue
                targets.getOrPut(declaration) { linkedSetOf() }.add(implementation)
            }
        }
    }
    return if (inspection.isAbortDesired) emptyMap() else targets
}
