package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.InlayHint
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.xdk.SemanticModel.Role
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolKind
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeCategory
import org.xvm.lsp.adapter.xdk.SemanticModel.Usage
import org.xvm.lsp.treesitter.SemanticTokenLegend

/** Presentation of copied compiler facts; the shared protocol legend does not load a parser. */
internal object XdkPresentation {
    /** Hover describes the resolved occurrence, never the declaration enclosing its body. */
    fun hover(
        model: SemanticModel,
        line: Int,
        column: Int,
    ): String? {
        val symbol = model.symbolAt(line, column)
        val type = model.typeAt(line, column)?.displayName
        val position = SemanticModel.Position(line, column)
        val signature =
            model.calls.firstOrNull { it.method == symbol?.id && position in it.callee }?.signature
                ?: symbol?.signature
        return hover(model, symbol, type, signature)
    }

    private fun hover(
        model: SemanticModel,
        symbol: SemanticModel.Symbol?,
        type: String?,
        signature: SemanticModel.Signature?,
    ): String? {
        val label =
            when {
                symbol != null && signature != null -> {
                    XdkCursorQueries.signature(model, symbol.name, signature).label
                }

                symbol != null -> {
                    listOfNotNull(type ?: symbol.kind.name.lowercase(), symbol.name)
                        .joinToString(" ")
                }

                else -> {
                    type
                }
            } ?: return null
        return "```xtc\n$label\n```" + symbol?.documentation?.let { "\n\n$it" }.orEmpty()
    }

    fun tokens(
        model: SemanticModel,
        lexical: List<List<Int>> = emptyList(),
    ): SemanticTokens {
        val tokens =
            model.occurrences
                .mapNotNull { occurrence ->
                    val symbol = occurrence.symbol?.let(model::symbol) ?: return@mapNotNull null
                    val range = occurrence.range
                    if (range.start.line != range.end.line || range.start == range.end) {
                        return@mapNotNull null
                    }
                    val type =
                        when (symbol.kind) {
                            SymbolKind.MODULE,
                            SymbolKind.PACKAGE,
                            -> {
                                "namespace"
                            }

                            SymbolKind.TYPE -> {
                                when (symbol.typeCategory) {
                                    TypeCategory.CLASS,
                                    TypeCategory.SERVICE,
                                    -> "class"

                                    TypeCategory.INTERFACE,
                                    TypeCategory.MIXIN,
                                    -> "interface"

                                    TypeCategory.CONST -> "struct"

                                    TypeCategory.ENUM -> "enum"

                                    TypeCategory.ENUM_VALUE -> "enumMember"

                                    null -> "type"
                                }
                            }

                            SymbolKind.TYPE_PARAMETER -> {
                                "typeParameter"
                            }

                            SymbolKind.METHOD -> {
                                if (symbol.isFunction) "function" else "method"
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
                            if (occurrence.usage == Usage.WRITE || occurrence.usage == Usage.READ_WRITE) {
                                add("modification")
                            }
                        }
                    listOf(
                        range.start.line,
                        range.start.column,
                        range.end.column - range.start.column,
                        SemanticTokenLegend.typeIndex.getValue(type),
                        SemanticTokenLegend.modifierBitmask(*modifiers.toTypedArray()),
                    )
                }.let { semantic ->
                    val coverage = TokenCoverage(semantic)
                    semantic + lexical.filterNot(coverage::overlaps)
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
        val requested =
            SemanticModel.Range(
                SemanticModel.Position(range.start.line, range.start.column),
                SemanticModel.Position(range.end.line, range.end.column),
            )
        val types =
            model.occurrences
                .asSequence()
                .filter {
                    model.status == SemanticModel.Status.COMPLETE &&
                        it.role == Role.DECLARATION && it.range.end in requested
                }.mapNotNull { occurrence ->
                    val symbol =
                        occurrence.symbol?.let(model::symbol)?.takeIf { it.inferred }
                            ?: return@mapNotNull null
                    val type = symbol.type?.let(model::type) ?: return@mapNotNull null
                    InlayHint(
                        occurrence.range.end.toPosition(),
                        ": ${type.displayName}",
                        InlayHint.InlayHintKind.TYPE,
                        tooltip =
                            hover(
                                model,
                                symbol,
                                occurrence.type?.let(model::type)?.displayName,
                                symbol.signature,
                            ),
                    )
                }.toList()
        val parameters =
            model.calls.flatMap { call ->
                call.arguments
                    .asSequence()
                    .filter { !it.named && it.range.start in requested }
                    .mapNotNull { argument ->
                        val name =
                            call.signature.parameters
                                .getOrNull(argument.parameterIndex)
                                ?.name
                                ?: return@mapNotNull null
                        InlayHint(
                            argument.range.start.toPosition(),
                            "$name:",
                            InlayHint.InlayHintKind.PARAMETER,
                            paddingRight = true,
                            tooltip =
                                model.symbol(call.method)?.let { symbol ->
                                    "```xtc\n${XdkCursorQueries.signature(model, symbol.name, call.signature).label}\n```" +
                                        symbol.documentation?.let { "\n\n$it" }.orEmpty()
                                },
                        )
                    }.toList()
            }
        val returns =
            model.lambdas.filter { it.arrow.start in requested }.map { lambda ->
                val types = lambda.signature.returns.mapNotNull { model.type(it)?.displayName }
                val label =
                    when (types.size) {
                        0 -> "void"
                        1 -> types.single()
                        else -> types.joinToString(", ", "(", ")")
                    }
                InlayHint(
                    lambda.arrow.start.toPosition(),
                    ": $label",
                    InlayHint.InlayHintKind.TYPE,
                    paddingRight = true,
                )
            }
        return (types + parameters + returns)
            .distinct()
            .sortedWith(compareBy({ it.position.line }, { it.position.column }))
    }

    /** Prefix maxima handle nested/duplicate ranges, including a file written on one line. */
    private class TokenCoverage(
        tokens: List<List<Int>>,
    ) {
        private val ordered = tokens.sortedWith(compareBy({ it[0] }, { it[1] }))
        private val ends =
            ordered
                .runningFold(SemanticModel.Position(0, 0)) { previous, token ->
                    maxOf(previous, SemanticModel.Position(token[0], token[1] + token[2]))
                }.drop(1)

        fun overlaps(token: List<Int>): Boolean {
            val end = SemanticModel.Position(token[0], token[1] + token[2])
            var low = 0
            var high = ordered.size
            // First semantic start at or after the lexical end. Only the preceding prefix can overlap.
            while (low < high) {
                val middle = (low + high) ushr 1
                val candidate = ordered[middle]
                if (SemanticModel.Position(candidate[0], candidate[1]) < end) low = middle + 1 else high = middle
            }
            return low > 0 && ends[low - 1] > SemanticModel.Position(token[0], token[1])
        }
    }

    private fun SemanticModel.Position.toPosition(): Position = Position(line, column)
}
