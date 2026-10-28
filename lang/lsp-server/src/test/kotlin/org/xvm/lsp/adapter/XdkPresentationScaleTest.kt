package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkPresentation
import org.xvm.lsp.treesitter.SemanticTokenLegend
import java.util.UUID

class XdkPresentationScaleTest {
    @Test
    fun `semantic coverage preserves adjacent lexical tokens and excludes nested overlaps`() {
        val ranges = listOf(30 to 40, 10 to 20, 10 to 12, 14 to 16)
        val model = model(ranges.map { (start, end) -> span(0, start, end) })
        val number = SemanticTokenLegend.typeIndex.getValue("number")
        val lexical =
            listOf(0 to 10, 9 to 10, 9 to 11, 12 to 13, 20 to 30, 39 to 41, 40 to 41)
                .map { (start, end) -> listOf(0, start, end - start, number, 0) } +
                listOf(listOf(1, 12, 1, number, 0))
        val decoded =
            XdkPresentation
                .tokens(model, lexical)
                .data
                .chunked(5)
                .runningFold(listOf(0, 0, 0, 0, 0)) { previous, token ->
                    listOf(previous[0] + token[0], token[1] + if (token[0] == 0) previous[1] else 0) + token.drop(2)
                }.drop(1)
        assertThat(decoded.filter { it[3] == number }).containsExactly(
            listOf(0, 0, 10, number, 0),
            listOf(0, 9, 1, number, 0),
            listOf(0, 20, 10, number, 0),
            listOf(0, 40, 1, number, 0),
            listOf(1, 12, 1, number, 0),
        )
        assertThat(XdkPresentation.tokens(model(emptyList()), lexical).data).hasSize(lexical.size * 5)
    }

    @Test
    fun `large inferred model preserves tooltips and half open hint boundaries`() {
        val model = model((0 until 5_000).map { span(it, 4, 9) })
        val all = XdkPresentation.hints(model, Range(Position(0, 0), Position(5_000, 0)))
        assertThat(all).hasSize(5_000)
        assertThat(all.first().tooltip).isEqualTo("```xtc\nInt local0\n```\n\nDocumentation 0")
        assertThat(all.last().tooltip).isEqualTo("```xtc\nInt local4999\n```\n\nDocumentation 4999")
        listOf(0, 2_500, 4_999).forEach { line ->
            assertThat(XdkPresentation.hints(model, Range(Position(line, 9), Position(line, 10))))
                .containsExactly(all[line])
            assertThat(XdkPresentation.hints(model, Range(Position(line, 0), Position(line, 9)))).isEmpty()
            assertThat(all[line].tooltip).isEqualTo(XdkPresentation.hover(model, line, 4))
        }
    }

    private fun span(
        line: Int,
        start: Int,
        end: Int,
    ) = SemanticModel.Range(SemanticModel.Position(line, start), SemanticModel.Position(line, end))

    private fun model(ranges: List<SemanticModel.Range>): SemanticModel {
        val snapshot = UUID.randomUUID()
        val typeId = SemanticModel.TypeId(snapshot, 0)
        val type = SemanticModel.Type(typeId, "Int", SemanticModel.TypeForm.NAMED, emptyList(), emptyList(), false)
        val symbols =
            ranges.mapIndexed { index, range ->
                SemanticModel.Symbol(
                    SemanticModel.SymbolId(snapshot, index),
                    "local$index",
                    SemanticModel.SymbolKind.VARIABLE,
                    range,
                    typeId,
                    null,
                    "Scale.x",
                    inferred = true,
                    documentation = "Documentation $index",
                )
            }
        return SemanticModel(
            snapshot,
            SemanticModel.Status.COMPLETE,
            "Scale.x",
            SemanticModel.Facts(symbols.associateBy { it.id }, mapOf(typeId to type)),
            symbols.map { SemanticModel.Occurrence(it.declaration!!, it.name, SemanticModel.Role.DECLARATION, it.id, typeId) },
            emptyList(),
        )
    }
}
