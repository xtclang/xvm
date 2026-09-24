package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.PropertyInfo
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement

/**
 * Worker-only inspection of successful source types. TypeInfo supplies actual member chains,
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
        info.propertyImplementationTargets(inspection).forEach { (declaration, implementations) ->
            targets.getOrPut(declaration) { linkedSetOf() }.addAll(implementations)
        }
    }
    return if (inspection.hasSeriousErrors() || inspection.isAbortDesired) emptyMap() else targets
}

/**
 * Copy ordinary property composition without constructing a Ref/Var implementation type or generating
 * forwarding methods. Some mixin accessors live only in PropertyBody, not the host's method table.
 * Those bodies already carry their compiler order, accessor structures and field identity.
 */
private fun TypeInfo.propertyImplementationTargets(errors: ErrorListener): Map<IdentityConstant, Set<IdentityConstant>> {
    val targets = linkedMapOf<IdentityConstant, MutableSet<IdentityConstant>>()
    properties.values.filter { !it.isConstant && !it.isFormalType && !it.isDelegating && !it.isRefAnnotated }.forEach { property ->
        if (errors.isAbortDesired) return emptyMap()
        for (getter in if (property.isVar) listOf(true, false) else listOf(true)) {
            val accessor = if (getter) property.getterId else property.setterId
            val method = getMethodById(accessor)
            // Never optimize a redirect: the compiler may generate a forwarding method for it.
            if (method?.chain?.any { it.implementation in setOf(Implementation.Delegating, Implementation.Capped) } == true) continue
            val written =
                property.propertyBodies
                    .filter { it.implementation in setOf(Implementation.Explicit, Implementation.Default) }
                    .mapNotNull { body ->
                        val structure = body.structure ?: return@mapNotNull null
                        val member =
                            when {
                                getter && body.hasGetter() -> structure.getter
                                !getter && body.hasSetter() -> structure.setter
                                else -> null
                            }
                        member?.takeUnless { it.isAbstract || it.isNative }?.let { body.implementation to it.identityConstant }
                    }
            val implementation =
                if (method == null) {
                    // As in PropertyInfo's field augmentation, storage overrides interface defaults,
                    // while an explicit getter/setter remains ahead of the field in the call chain.
                    written.firstOrNull()?.takeUnless { it.first == Implementation.Default && property.hasField() }?.second
                        ?: property.sourceField()
                } else {
                    val chain = if (getter) property.ensureOptimizedGetChain(this, null) else property.ensureOptimizedSetChain(this, null)
                    val body = chain?.firstOrNull() ?: continue
                    when (body.implementation) {
                        Implementation.Explicit, Implementation.Default -> body.methodStructure?.identityConstant
                        Implementation.Field -> property.sourceField()
                        else -> null
                    }
                } ?: continue
            val declarations =
                property.propertyBodies.map { it.identity } + written.map { it.second } +
                    method
                        ?.chain
                        .orEmpty()
                        .mapNotNull(MethodBody::getMethodStructure)
                        .map { it.identityConstant }
            declarations.forEach { declaration -> targets.getOrPut(declaration) { linkedSetOf() }.add(implementation) }
        }
    }
    return targets
}

/** Implicit adoption or injected/native storage has no written field implementation to navigate to. */
private fun PropertyInfo.sourceField(): IdentityConstant? =
    fieldIdentity?.takeIf { field ->
        propertyBodies.any { it.identity == field && it.implementation == Implementation.Explicit && !it.isAbstract && !it.isSynthetic }
    }
