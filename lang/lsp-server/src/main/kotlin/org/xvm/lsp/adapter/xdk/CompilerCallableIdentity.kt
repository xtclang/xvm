package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.MethodInfo
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.UnionTypeConstant
import org.xvm.lsp.util.ExecutionTrace

/**
 * Preserve alternative receivers and recursive dispatch at the call site. A method constant can
 * identify one written contract shared by different receivers; it cannot prove the site's route.
 * Copy the compiler's selected legs before releasing its pool, without inventing runtime targets.
 */
internal fun callableIdentity(
    receiver: TypeConstant,
    method: MethodConstant,
    errors: ErrorListener,
    identity: (Constant) -> ProofIdentity,
): ProofIdentity? {
    fun route(
        receiver: TypeConstant,
        selected: MethodInfo,
        depth: Int,
    ): ProofIdentity? {
        if (depth >= 64 || errors.isAbortDesired || errors.hasSeriousErrors()) return null
        val type = receiver.resolveTypedefs().removeAccess()
        val union = selected.chain.singleOrNull()?.takeIf { it.implementation == Implementation.Union }
        if (union != null) {
            if (type !is UnionTypeConstant) return null
            val left = route(type.underlyingType, union.unionLeft, depth + 1) ?: return null
            val right = route(type.underlyingType2, union.unionRight, depth + 1) ?: return null
            return ProofIdentity.Alternatives(setOf(left, right))
        }
        val receiverProof = receiverIdentity(receiver, identity) ?: return null
        val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-callable-leg)") { receiver.ensureTypeInfo(errors) }
        val dispatch = info.dispatch(selected, errors)
        if (!dispatch.supported || dispatch.methods.isEmpty()) return null
        return ProofIdentity.Composed(
            receiverProof,
            dispatch.methods.map(identity),
            dispatch.delegates.map(identity),
            dispatch.cycles.map { cycle ->
                ProofIdentity.Composed(identity(cycle.owner), cycle.contracts.map(identity), emptyList())
            },
        )
    }

    if (errors.isAbortDesired || receiver.isFormalType || receiver.containsUnresolved()) return null
    val union = receiver.resolveTypedefs().removeAccess() is UnionTypeConstant
    val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-callable)") { receiver.ensureTypeInfo(errors) }
    val selected = info.getMethodBySignature(method.signature) ?: return if (union) ProofIdentity.Unproven() else null
    if (union) return route(receiver, selected, 0) ?: ProofIdentity.Unproven()
    val dispatch = info.dispatch(selected, errors)
    if (dispatch.cycles.isEmpty()) return null
    return route(receiver, selected, 0) ?: ProofIdentity.Unproven()
}
