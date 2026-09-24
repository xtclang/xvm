package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.xdk.SemanticModel.Position
import org.xvm.lsp.adapter.xdk.SemanticModel.Range
import org.xvm.lsp.adapter.xdk.SemanticModel.Signature
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolId
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolKind
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeId
import java.util.List.copyOf as immutableList

/** Copied facts from one explicit partial-analysis attempt; retains no compiler or protocol objects. */
class PartialSemanticModel internal constructor(
    val semantics: SemanticModel,
    sites: List<Site>,
) {
    enum class Kind { NAME, MEMBER_ACCESS, CALL }

    @ConsistentCopyVisibility
    data class Member internal constructor(
        val symbol: SymbolId,
        val name: String,
        val kind: SymbolKind,
        val type: TypeId?,
        val signature: Signature?,
    )

    data class Argument(
        val range: Range,
        val label: String?,
        val type: TypeId?,
    )

    /** Decoded identifier text and its original UTF-16 replacement range; empty after a bare dot. */
    data class MemberPrefix(
        val text: String,
        val range: Range,
    )

    @ConsistentCopyVisibility
    data class CallCandidate internal constructor(
        val member: Member,
        val arguments: List<SemanticModel.CallArgument>,
        val converting: Boolean,
        val constructor: Boolean = false,
    )

    @ConsistentCopyVisibility
    data class FunctionCandidate internal constructor(
        val signature: Signature,
        val arguments: List<SemanticModel.CallArgument>,
    )

    /** Members describe accessible names; callCandidates separately records compiler argument fitting. */
    @ConsistentCopyVisibility
    data class Site internal constructor(
        val kind: Kind,
        val range: Range,
        val operator: Range,
        val receiver: Range?,
        val receiverType: TypeId?,
        val calleeName: String?,
        val scope: SymbolId?,
        val arguments: List<Argument>,
        val separators: List<Position>,
        val members: List<Member>,
        val memberPrefix: MemberPrefix? = null,
        val callCandidates: List<CallCandidate>? = null,
        val pendingArgumentName: String? = null,
        val functions: List<FunctionCandidate> = emptyList(),
    ) {
        /** Source argument index only; no argument-to-parameter mapping exists for an incomplete call. */
        fun argumentIndexAt(position: Position): Int? =
            if (kind == Kind.CALL && position >= operator.end && position <= range.end) {
                separators.count { it < position }
            } else {
                null
            }

        /** Compiler-proven mapping for a written argument, or a named/positional insertion slot. */
        fun parameterAt(
            candidate: CallCandidate,
            position: Position,
        ): Int? = candidate.member.signature?.let { parameterAt(it, candidate.arguments, position) }

        fun parameterAt(
            candidate: FunctionCandidate,
            position: Position,
        ): Int? = parameterAt(candidate.signature, candidate.arguments, position)

        private fun parameterAt(
            signature: Signature,
            mapping: List<SemanticModel.CallArgument>,
            position: Position,
        ): Int? {
            val slot = argumentIndexAt(position) ?: return null
            val parameters = signature.parameters
            if (slot < arguments.size) {
                return mapping.singleOrNull { it.range == arguments[slot].range }?.parameterIndex
            }
            if (pendingArgumentName != null) {
                return parameters.indexOfFirst { it.name == pendingArgumentName }.takeIf { it >= 0 }
            }
            return slot.takeIf { arguments.none { it.label != null } && it in parameters.indices }
        }

        /** Expected type for this candidate, not an assertion that the overload is selected. */
        fun expectedTypeAt(
            candidate: CallCandidate,
            position: Position,
        ): TypeId? =
            parameterAt(candidate, position)?.let {
                candidate.member.signature
                    ?.parameters
                    ?.getOrNull(it)
                    ?.type
            }

        fun expectedTypeAt(
            candidate: FunctionCandidate,
            position: Position,
        ): TypeId? =
            parameterAt(candidate, position)?.let {
                candidate.signature.parameters
                    .getOrNull(it)
                    ?.type
            }
    }

    val sites: List<Site> = immutableList(sites)
}
