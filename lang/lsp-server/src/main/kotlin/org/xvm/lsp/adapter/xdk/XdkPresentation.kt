package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.InlayHint
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.xdk.SemanticModel.Role
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolKind
import org.xvm.lsp.adapter.xdk.SemanticModel.Usage
import org.xvm.lsp.treesitter.SemanticTokenLegend

/** Presentation of copied compiler facts; the shared protocol legend does not load a parser. */
internal object XdkPresentation {
    fun tokens(model: SemanticModel, lexical: List<List<Int>> = emptyList()): SemanticTokens {
        val tokens =
            model.occurrences
                .mapNotNull { occurrence ->
                    val symbol = occurrence.symbol?.let(model::symbol) ?: return@mapNotNull null
                    val range = occurrence.range
                    if (range.start.line != range.end.line || range.start == range.end) return@mapNotNull null
                    val type =
                        when (symbol.kind) {
                            SymbolKind.MODULE, SymbolKind.PACKAGE -> {
                                "namespace"
                            }

                            SymbolKind.TYPE -> {
                                when (model.typeDeclarations[symbol.id]?.category) {
                                    "class", "service" -> "class"
                                    "interface", "mixin" -> "interface"
                                    "const" -> "struct"
                                    "enum" -> "enum"
                                    else -> "type"
                                }
                            }

                            SymbolKind.TYPE_PARAMETER -> {
                                "typeParameter"
                            }

                            SymbolKind.METHOD -> {
                                "method"
                            }

                            SymbolKind.PROPERTY -> {
                                "property"
                            }

                            SymbolKind.VARIABLE -> {
                                "variable"
                            }

                            SymbolKind.PARAMETER -> {
                                "parameter"
                            }
                        }
                    val modifiers =
                        buildList {
                            if (occurrence.role == Role.DECLARATION) add("declaration")
                            addAll(symbol.modifiers.map { it.name.lowercase() })
                            if (occurrence.usage == Usage.WRITE || occurrence.usage == Usage.READ_WRITE) add("modification")
                        }
                    listOf(
                        range.start.line,
                        range.start.column,
                        range.end.column - range.start.column,
                        SemanticTokenLegend.typeIndex.getValue(type),
                        SemanticTokenLegend.modifierBitmask(*modifiers.toTypedArray()),
                    )
                }.let { semantic ->
                    semantic + lexical.filter { token -> semantic.none { name ->
                        name[0] == token[0] && name[1] < token[1] + token[2] && token[1] < name[1] + name[2]
                    } }
                }.sortedWith(compareBy({ it[0] }, { it[1] }))
        return SemanticTokens(
            tokens.flatMapIndexed { index, token ->
                val previous = tokens.getOrNull(index - 1)
                val lineDelta = token[0] - (previous?.get(0) ?: 0)
                val columnDelta = token[1] - if (lineDelta == 0) previous?.get(1) ?: 0 else 0
                listOf(lineDelta, columnDelta, token[2], token[3], token[4])
            },
        )
    }

    fun hints(
        model: SemanticModel,
        range: Range,
    ): List<InlayHint> {
        val types =
            model.occurrences
                .filter {
                    model.status == SemanticModel.Status.COMPLETE && it.role == Role.DECLARATION
                }.mapNotNull { occurrence ->
                    val symbol = occurrence.symbol?.let(model::symbol)?.takeIf { it.inferred } ?: return@mapNotNull null
                    val type = symbol.type?.let(model::type) ?: return@mapNotNull null
                    InlayHint(occurrence.range.end.toPosition(), ": ${type.displayName}", InlayHint.InlayHintKind.TYPE)
                }
        val parameters =
            model.calls.flatMap { call ->
                call.arguments.filterNot { it.named }.mapNotNull { argument ->
                    val name =
                        call.signature.parameters
                            .getOrNull(argument.parameterIndex)
                            ?.name ?: return@mapNotNull null
                    InlayHint(argument.range.start.toPosition(), "$name:", InlayHint.InlayHintKind.PARAMETER, paddingRight = true)
                }
            }
        val start = SemanticModel.Position(range.start.line, range.start.column)
        val end = SemanticModel.Position(range.end.line, range.end.column)
        return (types + parameters)
            .filter { SemanticModel.Position(it.position.line, it.position.column).let { at -> at >= start && at < end } }
            .distinct()
            .sortedWith(compareBy({ it.position.line }, { it.position.column }))
    }

    private fun SemanticModel.Position.toPosition(): Position = Position(line, column)
}
