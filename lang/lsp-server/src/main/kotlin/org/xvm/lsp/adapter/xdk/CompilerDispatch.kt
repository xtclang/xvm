package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.MethodInfo
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeInfo
import org.xvm.lsp.util.ExecutionTrace

/** Written contracts and receiver properties behind a compiler-composed method. Worker-only. */
internal data class CompilerDispatch(
    val methods: List<MethodConstant>,
    val delegates: List<PropertyConstant> = emptyList(),
    val supported: Boolean = true,
)

/** Follow existing dispatch metadata without generating optimized or forwarding method bodies. */
internal fun TypeInfo.dispatch(
    method: MethodInfo,
    errors: ErrorListener,
    visited: List<Pair<TypeConstant, MethodInfo>> = emptyList(),
): CompilerDispatch {
    val key = type to method
    if (errors.isAbortDesired || visited.any { it.first == type && it.second === method } || visited.size >= 64) {
        return CompilerDispatch(listOf(method.identity), supported = false)
    }
    val seen = visited + key
    val bodies =
        method.chain.map { body ->
            when (body.implementation) {
                Implementation.Explicit, Implementation.Default, Implementation.Declared, Implementation.Abstract -> {
                    val declaration = body.methodStructure
                    CompilerDispatch(listOf(declaration?.identityConstant ?: body.identity), supported = declaration?.isSynthetic == false)
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
                                it.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
                            }
                        }
                    val selected = delegate?.getMethodBySignature(body.signature)
                    if (selected == null || receiver == null) {
                        CompilerDispatch(listOf(body.identity), supported = false)
                    } else {
                        val target = delegate.dispatch(selected, errors, seen)
                        target.copy(delegates = listOf(receiver) + target.delegates)
                    }
                }

                else -> {
                    CompilerDispatch(listOf(body.identity), supported = false)
                }
            }
        }
    return CompilerDispatch(
        bodies.flatMap { it.methods }.distinct(),
        bodies.flatMap { it.delegates }.distinct(),
        bodies.all { it.supported } && !errors.hasSeriousErrors && !errors.isAbortDesired,
    )
}
