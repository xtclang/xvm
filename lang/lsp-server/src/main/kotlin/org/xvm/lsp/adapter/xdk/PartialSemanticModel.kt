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
    enum class Kind { MEMBER_ACCESS, CALL }

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

    /** Candidates are accessible receiver members, not selected or argument-filtered overloads. */
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
    ) {
        /** Source argument index only; no argument-to-parameter mapping exists for an incomplete call. */
        fun argumentIndexAt(position: Position): Int? =
            if (kind == Kind.CALL && position >= operator.end && position <= range.end) {
                separators.count { it < position }
            } else {
                null
            }
    }

    val sites: List<Site> = immutableList(sites)
}
