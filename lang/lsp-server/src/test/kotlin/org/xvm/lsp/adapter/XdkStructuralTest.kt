package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkStructuralTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r"])
    fun `damaged shared fixtures retain current structure and strictly nested selections`(newline: String) {
        val catalog = Path.of(System.getProperty("xtc.composite.root"), "lang/test-fixtures/compiler-playbook/scenarios.json")
        val data =
            JsonParser
                .parseString(
                    catalog.toFile().readText(),
                ).asJsonObject["cases"]
                .asJsonObject["X248"]
                .asJsonObject["values"]
                .asJsonObject
        XdkAdapter().use { adapter ->
            data["variants"].asJsonArray.forEach { row ->
                val variant = row.asJsonObject
                val text = variant["source"].asString.replace("\n", newline)
                val result = adapter.compile(URI, text)
                assertThat(result.diagnostics).describedAs(text).isNotEmpty()
                assertThat(result.diagnostics.map { it.code }).doesNotContain("EMB-5")
                assertThat(adapter.findWorkspaceSymbols("").map { it.name }).containsAll(variant["symbols"].asJsonArray.map { it.asString })
                val folds = adapter.getFoldingRanges(URI)
                assertThat(folds).anySatisfy {
                    assertThat(it.startLine).isEqualTo(variant["foldLine"].asInt)
                    assertThat(it.endLine).isEqualTo(4)
                    assertThat(it.endCharacter).isEqualTo(text.lines()[4].indexOf('}'))
                }
                val at = XdkRename.position(text, text.indexOf(variant["anchor"].asString))
                val chain = generateSequence(adapter.getSelectionRanges(URI, listOf(at)).single()) { it.parent }.map { it.range }.toList()
                assertThat(chain).hasSizeGreaterThan(1).doesNotHaveDuplicates()
                chain.forEach {
                    assertThat(compare(it.start, at)).isLessThanOrEqualTo(0)
                    assertThat(compare(it.end, at)).isGreaterThanOrEqualTo(0)
                }
                chain.zipWithNext().forEach { (child, parent) ->
                    assertThat(compare(parent.start, child.start)).isLessThanOrEqualTo(0)
                    assertThat(compare(parent.end, child.end)).isGreaterThanOrEqualTo(0)
                    assertThat(parent).isNotEqualTo(child)
                }
            }
            assertThat(adapter.compile(URI, data["repaired"].asString).diagnostics).isEmpty()
            assertThat(adapter.findWorkspaceSymbols("").map { it.name }).containsExactly("Structure", "repaired")
            assertThat(adapter.getFoldingRanges(URI)).isEmpty()
            adapter.closeDocument(URI)
            assertThat(adapter.findWorkspaceSymbols("")).isEmpty()
            assertThat(adapter.getFoldingRanges(URI)).isEmpty()
        }
    }

    private fun compare(
        first: Position,
        second: Position,
    ) = compareValuesBy(first, second, Position::line, Position::column)

    private companion object {
        const val URI = "untitled:Structure.x"
    }
}
