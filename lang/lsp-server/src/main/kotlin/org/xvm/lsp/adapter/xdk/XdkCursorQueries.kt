package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.CompletionItem.CompletionKind
import org.xvm.lsp.adapter.ParameterInfo
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.SignatureInfo
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.xdk.SemanticModel.Position
import org.xvm.lsp.adapter.xdk.SemanticModel.Signature
import org.xvm.lsp.adapter.Position as AdapterPosition

/** Editor queries over copied facts only: no AST, constant pool, resolution or source rewriting. */
internal object XdkCursorQueries {
    fun completions(model: PartialSemanticModel): List<CompletionItem> {
        val site = model.sites.singleOrNull() ?: return emptyList()
        val prefix =
            site.memberPrefix
                ?: when (site.kind) {
                    PartialSemanticModel.Kind.CALL -> {
                        PartialSemanticModel.MemberPrefix(
                            "",
                            SemanticModel.Range(site.range.end, site.range.end),
                        )
                    }

                    else -> {
                        return emptyList()
                    }
                }
        val range =
            Range(
                AdapterPosition(prefix.range.start.line, prefix.range.start.column),
                AdapterPosition(prefix.range.end.line, prefix.range.end.column),
            )
        val members = if (site.kind == PartialSemanticModel.Kind.CALL) site.argumentValues else site.members
        return members
            .filter { it.name.startsWith(prefix.text) }
            .map { member ->
                CompletionItem(
                    member.name,
                    when (member.kind) {
                        SemanticModel.SymbolKind.METHOD -> CompletionKind.METHOD
                        SemanticModel.SymbolKind.VARIABLE, SemanticModel.SymbolKind.PARAMETER -> CompletionKind.VARIABLE
                        SemanticModel.SymbolKind.TYPE, SemanticModel.SymbolKind.TYPE_PARAMETER -> CompletionKind.CLASS
                        else -> CompletionKind.PROPERTY
                    },
                    member.signature?.let { signature(model.semantics, member.name, it).label }
                        ?: "${member.type?.let { model.semantics.type(it)?.displayName } ?: "?"} ${member.name}",
                    member.name,
                    TextEdit(range, member.name),
                )
            }.distinctBy { it.label to it.detail }
    }

    fun signatureHelp(
        model: PartialSemanticModel,
        position: Position,
    ): SignatureHelp? {
        val site = model.sites.singleOrNull() ?: return null
        val slot = site.argumentIndexAt(position) ?: return null
        if (site.functions.isNotEmpty()) {
            val signatures =
                site.functions.map { candidate ->
                    signature(
                        model.semantics,
                        site.calleeName ?: "function",
                        candidate.signature,
                        site.parameterAt(candidate, position),
                        "Function signature; written arguments fit, runtime target unknown.",
                    )
                }
            return SignatureHelp(signatures, activeParameter = signatures.first().activeParameter ?: 0)
        }
        site.callCandidates?.let { candidates ->
            val signatures =
                candidates
                    .sortedBy { it.converting }
                    .mapNotNull { candidate ->
                        candidate.member.signature?.let {
                            signature(
                                model.semantics,
                                candidate.member.name,
                                it,
                                site.parameterAt(candidate, position),
                                "Candidate signature; written arguments fit, overload not selected.",
                                candidate.constructor,
                            )
                        }
                    }.distinctBy { it.label }
            return signatures.takeIf { it.isNotEmpty() }?.let { SignatureHelp(it, activeParameter = it.first().activeParameter ?: 0) }
        }
        val signatures =
            site.members
                .mapNotNull { member ->
                    member.signature?.let { candidate ->
                        // Positional source slots map directly. Named partial arguments need compiler
                        // mapping; do not infer one from commas or choose an applicable overload here.
                        val active = slot.takeIf { site.arguments.none { it.label != null } && it in candidate.parameters.indices }
                        signature(model.semantics, member.name, candidate, active, "Candidate signature; overload not selected.")
                    }
                }.distinctBy { it.label }
        return signatures.takeIf { it.isNotEmpty() }?.let { SignatureHelp(it, activeParameter = it.first().activeParameter ?: 0) }
    }

    fun signatureHelp(
        model: SemanticModel,
        position: Position,
    ): SignatureHelp? {
        val methods =
            model.calls.mapNotNull { call ->
                model.symbol(call.method)?.let { SignatureSite(call.range, call.callee, it.name, call.signature, call.arguments) }
            }
        val functions =
            model.functionCalls.map { call ->
                val name = model.symbolAt(call.callee.end.line, call.callee.end.column - 1)?.name ?: "function"
                SignatureSite(call.range, call.callee, name, call.signature, call.arguments)
            }
        val call =
            (methods + functions)
                .filter { position > it.callee.end && position < it.range.end }
                .minWithOrNull(compareByDescending<SignatureSite> { it.range.start }.thenBy { it.range.end }) ?: return null
        val active = call.arguments.firstOrNull { position >= it.range.start && position <= it.range.end }?.parameterIndex
        return SignatureHelp(listOf(signature(model, call.name, call.signature, active)), activeParameter = active ?: 0)
    }

    private data class SignatureSite(
        val range: SemanticModel.Range,
        val callee: SemanticModel.Range,
        val name: String,
        val signature: Signature,
        val arguments: List<SemanticModel.CallArgument>,
    )

    private fun signature(
        model: SemanticModel,
        name: String,
        signature: Signature,
        active: Int? = null,
        documentation: String? = null,
        constructor: Boolean = false,
    ): SignatureInfo {
        val parameters =
            signature.parameters.map { parameter ->
                ParameterInfo(
                    "${model.type(parameter.type)?.displayName ?: "?"}${parameter.name?.let { " $it" }.orEmpty()}" +
                        if (parameter.defaulted) " = …" else "",
                )
            }
        // The compiler signature includes the conditional success flag; source syntax does not.
        val returnTypes = if (signature.conditional) signature.returns.drop(1) else signature.returns
        val returns = returnTypes.joinToString(", ") { model.type(it)?.displayName ?: "?" }
        val result = if (returnTypes.size > 1) "($returns)" else returns.ifEmpty { "void" }
        val returnLabel =
            when {
                constructor -> ""
                signature.conditional -> "conditional $result "
                else -> "$result "
            }
        return SignatureInfo(
            "$returnLabel$name(${parameters.joinToString { it.label }})",
            documentation,
            // LSP defaults an absent/out-of-range active index to parameter zero. Keeping the
            // signature label but omitting parameter metadata avoids a fabricated highlight.
            parameters.takeIf { active != null }.orEmpty(),
            active,
        )
    }
}
