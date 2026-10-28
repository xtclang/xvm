package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.MethodInfo
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeInfo
import org.xvm.asm.constants.UnionTypeConstant
import org.xvm.lsp.util.ExecutionTrace

/** Written contracts and receiver properties behind a compiler-composed method. Worker-only. */
internal data class CompilerDispatch(
    val methods: List<MethodConstant>,
    val delegates: List<PropertyConstant> = emptyList(),
    val supported: Boolean = true,
    val cycles: List<Cycle> = emptyList(),
    val alternatives: List<Alternatives> = emptyList(),
) {
    /** Branches stay separate even when their contracts share the same written declaration. */
    data class Alternatives(
        val branches: List<Branch>,
    )

    data class Branch(
        val receiver: TypeConstant,
        val dispatch: CompilerDispatch,
    )

    /** A finite back edge to written contracts; it does not identify an executable body. */
    data class Cycle(
        val receiver: TypeConstant,
        val contracts: List<MethodConstant>,
    )
}

/** Follow existing dispatch metadata without generating optimized or forwarding method bodies. */
internal fun TypeInfo.dispatch(
    method: MethodInfo,
    errors: ErrorListener,
    visited: List<Pair<TypeConstant, MethodInfo>> = emptyList(),
): CompilerDispatch {
    val key = type to method
    if (
        errors.isAbortDesired ||
        visited.size >= 64
    ) {
        return CompilerDispatch(listOf(method.identity), supported = false)
    }
    if (visited.any { it.first == type && it.second === method }) {
        // A delegating cycle can still have a finite, written interface contract. Retain where
        // the route closes instead of inventing a terminal implementation or dropping the edge.
        val contracts = method.closingContracts()
        return if (!contracts.isNullOrEmpty() &&
            visited.any { (_, route) -> route.chain.any { it.implementation == Implementation.Delegating } }
        ) {
            CompilerDispatch(contracts, cycles = listOf(CompilerDispatch.Cycle(type, contracts)))
        } else {
            CompilerDispatch(listOf(method.identity), supported = false)
        }
    }
    val seen = visited + key
    val bodies =
        method.chain.map { body ->
            when (body.implementation) {
                Implementation.Explicit,
                Implementation.Default,
                Implementation.Declared,
                Implementation.Abstract,
                Implementation.SansCode,
                -> {
                    val declaration = body.methodStructure
                    CompilerDispatch(
                        listOf(declaration?.identityConstant ?: body.identity),
                        supported = declaration?.isSynthetic == false,
                    )
                }

                Implementation.FromInto -> {
                    body.intoMethodInfo?.let { dispatch(it, errors, seen) }
                        ?: CompilerDispatch(listOf(body.identity), supported = false)
                }

                Implementation.Capped -> {
                    getNarrowingMethod(method)?.let { dispatch(it, errors, seen) }
                        ?: CompilerDispatch(listOf(body.identity), supported = false)
                }

                Implementation.Delegating -> {
                    val receiver = body.propertyConstant
                    val receiverType = receiver?.let { findProperty(it)?.type }
                    val delegate =
                        receiverType?.let {
                            ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-delegate)") {
                                val view = if (it.isSingleUnderlyingClass(false)) it.ensureAccess(Access.PRIVATE) else it
                                view.ensureTypeInfo(errors)
                            }
                        }
                    val selected = delegate?.getMethodBySignature(body.signature)
                    if (selected == null) {
                        CompilerDispatch(listOf(body.identity), supported = false)
                    } else {
                        val target = delegate.dispatch(selected, errors, seen)
                        target.copy(delegates = listOf(receiver) + target.delegates)
                    }
                }

                Implementation.Union -> {
                    val union = type.resolveTypedefs().removeAccess() as? UnionTypeConstant
                    if (union == null) {
                        CompilerDispatch(listOf(body.identity), supported = false)
                    } else {
                        val branches =
                            listOf(union.underlyingType to body.unionLeft, union.underlyingType2 to body.unionRight)
                                .map { (receiver, selected) ->
                                    val info =
                                        ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-union-leg)") {
                                            receiver.ensureTypeInfo(errors)
                                        }
                                    CompilerDispatch.Branch(receiver, info.dispatch(selected, errors, seen))
                                }
                        CompilerDispatch(
                            branches.flatMap { it.dispatch.methods }.distinct(),
                            supported = branches.all { it.dispatch.supported },
                            alternatives = listOf(CompilerDispatch.Alternatives(branches)),
                        )
                    }
                }

                Implementation.Implicit,
                Implementation.Field,
                Implementation.Native,
                -> {
                    // These bodies do not independently identify a written callable contract:
                    // assumed dispatch, generated accessors or runtime
                    // implementations.
                    CompilerDispatch(listOf(body.identity), supported = false)
                }

                else -> {
                    CompilerDispatch(listOf(body.identity), supported = false)
                }
            }
        }
    return CompilerDispatch(
        bodies.flatMap { it.methods }.distinct(),
        bodies.flatMap { it.delegates }.distinct(),
        bodies.all { it.supported } && !errors.hasSeriousErrors() && !errors.isAbortDesired,
        bodies.flatMap { it.cycles }.distinct(),
        bodies.flatMap { it.alternatives }.distinct(),
    )
}

/** Only inspect the finite MethodBody tree; following delegate receivers again would loop. */
private fun MethodInfo.closingContracts(depth: Int = 0): List<MethodConstant>? {
    if (depth >= 64) return null
    return chain
        .flatMap { body ->
            when (body.implementation) {
                Implementation.Delegating -> {
                    emptyList()
                }

                Implementation.Union -> {
                    val left = body.unionLeft.closingContracts(depth + 1) ?: return null
                    val right = body.unionRight.closingContracts(depth + 1) ?: return null
                    left + right
                }

                Implementation.Declared, Implementation.Abstract, Implementation.Default,
                Implementation.Explicit, Implementation.SansCode,
                -> {
                    listOf(body.methodStructure?.takeUnless { it.isSynthetic }?.identityConstant ?: return null)
                }

                else -> {
                    return null
                }
            }
        }.distinct()
}
