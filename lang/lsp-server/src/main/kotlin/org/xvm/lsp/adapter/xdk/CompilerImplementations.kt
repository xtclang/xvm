package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodBody
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodInfo
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.PropertyInfo
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement

/**
 * Worker-only inspection of successful source types. TypeInfo supplies actual member chains,
 * including generic substitution and inherited bodies; no matching by method name or arity.
 * Delegation follows compiler-selected signatures on statically known concrete receiver types.
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
            val implementation = info.methodImplementation(method, inspection) ?: continue
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
 * Copy property composition without constructing a separate Ref/Var implementation type or generating
 * forwarding methods. The host's nested method chains already include Ref/Var annotation dispatch.
 * Some mixin accessors live only in PropertyBody, with their compiler order and field identity.
 */
private fun TypeInfo.propertyImplementationTargets(errors: ErrorListener): Map<IdentityConstant, Set<IdentityConstant>> {
    val targets = linkedMapOf<IdentityConstant, MutableSet<IdentityConstant>>()
    properties.values.filter { !it.isConstant && !it.isFormalType }.forEach { property ->
        if (errors.isAbortDesired) return emptyMap()
        for (getter in if (property.isVar) listOf(true, false) else listOf(true)) {
            val accessor = if (getter) property.getterId else property.setterId
            val method = getMethodById(accessor)
            val written = property.writtenAccessors(getter)
            val implementation = accessorImplementation(property, getter, errors) ?: continue
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

/** A delegate's declared concrete type bounds lookup; an interface value has no selected body. */
private fun TypeInfo.delegateType(
    property: PropertyConstant,
    errors: ErrorListener,
): TypeInfo? {
    if (errors.isAbortDesired) return null
    val target = findProperty(property)?.type ?: return null
    if (!target.isSingleUnderlyingClass(false)) return null
    val info = target.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
    return info.takeIf { it.isClass && !it.isAbstract && !errors.hasSeriousErrors() && !errors.isAbortDesired }
}

private fun TypeInfo.methodImplementation(
    method: MethodInfo,
    errors: ErrorListener,
    visited: Set<Pair<TypeConstant, IdentityConstant>> = emptySet(),
): IdentityConstant? {
    val key = type to method.identity
    if (errors.isAbortDesired || key in visited || visited.size >= 64) return null
    val body = method.chain.firstOrNull { !it.isAbstract } ?: return null
    return when (body.implementation) {
        Implementation.Explicit, Implementation.Default -> {
            body.methodStructure?.identityConstant
        }

        Implementation.Delegating -> {
            val delegate = delegateType(body.propertyConstant ?: return null, errors) ?: return null
            val selected = delegate.getMethodBySignature(body.signature) ?: return null
            delegate.methodImplementation(selected, errors, visited + key)
        }

        else -> {
            null
        }
    }
}

private fun TypeInfo.accessorImplementation(
    property: PropertyInfo,
    getter: Boolean,
    errors: ErrorListener,
    visited: Set<Pair<TypeConstant, IdentityConstant>> = emptySet(),
): IdentityConstant? {
    val key = type to property.identity
    if (errors.isAbortDesired || key in visited || visited.size >= 64) return null
    if (property.isDelegating) {
        val delegate = delegateType(property.delegate, errors) ?: return null
        val selected = delegate.findProperty(property.identity) ?: return null
        return delegate.accessorImplementation(selected, getter, errors, visited + key)
    }
    val accessor = if (getter) property.getterId else property.setterId
    val method = getMethodById(accessor)
    // Optimizing redirects can generate methods. Keep that operation out of source inspection.
    if (method?.chain?.any { it.implementation in setOf(Implementation.Delegating, Implementation.Capped) } == true) return null
    if (method != null) {
        val chain = if (getter) property.ensureOptimizedGetChain(this, null) else property.ensureOptimizedSetChain(this, null)
        val body = chain?.firstOrNull() ?: return null
        return when (body.implementation) {
            Implementation.Explicit, Implementation.Default -> body.methodStructure?.identityConstant

            // Annotation/native storage is not a written accessor (for example Lazy.set).
            Implementation.Field -> property.takeUnless { it.isRefAnnotated }?.sourceField()

            else -> null
        }
    }
    // An annotated accessor needs its composed method chain; a field/body guess loses dispatch.
    if (property.isRefAnnotated) return null
    val written = property.writtenAccessors(getter)
    // Explicit accessors precede interface defaults; storage overrides a default accessor.
    val selected = written.firstOrNull { it.first == Implementation.Explicit } ?: written.firstOrNull()
    return selected?.takeUnless { it.first == Implementation.Default && property.hasField() }?.second ?: property.sourceField()
}

private fun PropertyInfo.writtenAccessors(getter: Boolean): List<Pair<Implementation, IdentityConstant>> =
    propertyBodies
        .filter { it.implementation in setOf(Implementation.Explicit, Implementation.Default) }
        .mapNotNull { body ->
            val member =
                when {
                    getter && body.hasGetter() -> body.structure?.getter
                    !getter && body.hasSetter() -> body.structure?.setter
                    else -> null
                }
            member?.takeUnless { it.isAbstract || it.isNative }?.let { body.implementation to it.identityConstant }
        }
