package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ErrorListener
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.MethodInfo
import org.xvm.asm.constants.PureIdentityConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.UnionTypeConstant
import org.xvm.lsp.util.ExecutionTrace

/**
 * A union has alternative receiver/dispatch chains, not one override chain. Copy the compiler's
 * selected legs before releasing its pool; never pick a leg by name or invent a runtime target.
 */
internal fun unionMethodIdentity(
    method: MethodConstant,
    errors: ErrorListener,
    identity: (Constant) -> ProofIdentity,
): ProofIdentity? {
    val owner = method.namespace as? PureIdentityConstant ?: return null

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
        // Parameterized/formal/annotated receiver identity needs its own detached type proof.
        // Keep that boundary explicit instead of collapsing e.g. Box<String> and Box<Int>.
        if (type.format != Constant.Format.TerminalType) return null
        val receiverClass = type.definingConstant as? ClassConstant ?: return null
        val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-union-leg)") { receiver.ensureTypeInfo(errors) }
        val dispatch = info.dispatch(selected, errors)
        if (!dispatch.supported || dispatch.methods.isEmpty()) return null
        return ProofIdentity.Composed(
            identity(receiverClass),
            dispatch.methods.map(identity),
            dispatch.delegates.map(identity),
        )
    }

    val info = ExecutionTrace.api("TypeConstant.ensureTypeInfo(rename-union)") { owner.type.ensureTypeInfo(errors) }
    val selected = info.getMethodById(method) ?: return null
    return route(owner.type, selected, 0)
}
