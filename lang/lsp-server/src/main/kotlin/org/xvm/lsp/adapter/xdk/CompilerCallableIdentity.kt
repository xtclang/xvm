package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.MethodConstant
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
    if (errors.isAbortDesired || receiver.isFormalType || receiver.containsUnresolved()) return null
    val union = receiver.resolveTypedefs().removeAccess() is UnionTypeConstant
    val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-callable)") { receiver.ensureTypeInfo(errors) }
    // The declaration can still have a formal return (First.T). Resolve its nested identity
    // against this receiver before comparing concrete signatures.
    val selected =
        info.getMethodById(method) ?: info.getMethodBySignature(method.signature)
            ?: return if (union) ProofIdentity.Unproven() else null
    val dispatch = info.dispatch(selected, errors)
    if (union) {
        if (!dispatch.supported) return ProofIdentity.Unproven()
        return dispatch.alternatives.singleOrNull()?.proof(identity) ?: ProofIdentity.Unproven()
    }
    if (dispatch.cycles.isEmpty() && dispatch.alternatives.isEmpty()) return null
    val owner = receiverIdentity(receiver, identity) ?: return ProofIdentity.Unproven()
    return dispatch.proof(owner, identity)
}

/** Written contracts form the rename family; branch structure independently proves equivalence. */
internal fun CompilerDispatch.proof(
    owner: ProofIdentity,
    identity: (Constant) -> ProofIdentity,
): ProofIdentity {
    if (!supported || methods.isEmpty()) return ProofIdentity.Unproven()
    return ProofIdentity.Composed(
        owner,
        methods.map(identity),
        delegates.map(identity),
        cycles.map {
            ProofIdentity.Composed(
                receiverIdentity(it.receiver, identity) ?: ProofIdentity.Unproven(),
                it.contracts.map(identity),
                emptyList(),
            )
        },
        alternatives.map { it.proof(identity) },
    )
}

internal fun CompilerDispatch.Alternatives.proof(identity: (Constant) -> ProofIdentity): ProofIdentity =
    ProofIdentity.Alternatives(
        branches.mapTo(linkedSetOf()) { branch ->
            val owner = receiverIdentity(branch.receiver, identity) ?: return ProofIdentity.Unproven()
            branch.dispatch.proof(owner, identity)
        },
    )
