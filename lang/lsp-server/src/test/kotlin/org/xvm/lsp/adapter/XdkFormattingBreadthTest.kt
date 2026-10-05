package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLexical
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkFormattingBreadthTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r"])
    fun `shared formatter scenarios preserve compilation line endings and idempotence`(newline: String) {
        val catalog = Path.of(System.getProperty("xtc.composite.root"), "lang/test-fixtures/compiler-playbook/scenarios.json")
        val cases = JsonParser.parseString(catalog.toFile().readText()).asJsonObject["cases"].asJsonObject
        listOf("X249", "X250").forEach { id ->
            val data = cases[id].asJsonObject["values"].asJsonObject
            val text = data["source"].asString.replace("\n", newline)
            val expected = data["expected"].asString.replace("\n", newline)
            XdkAdapter().use { adapter ->
                val uri = "untitled:" + data["file"].asString
                assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
                val changed = apply(text, adapter.formatDocument(uri, text, OPTIONS))
                assertThat(changed).describedAs(id).isEqualTo(expected)
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
                assertThat(adapter.formatDocument(uri, changed, OPTIONS)).isEmpty()
            }
        }
    }

    @Test
    fun `range and on type formatting preserve unrelated lines and do not wrap while typing`() {
        val text =
            """
            module Layout {
            Int sum(Int first, Int second, Int third) = first + second + third;
            Int unchanged = 1;
            }
            """.trimIndent()
        val config = FormattingConfig(maxLineWidth = 40)
        val range = Range(Position(1, 0), Position(2, 0))
        val changed = apply(text, XdkLexical.format(text, config, OPTIONS, range))
        assertThat(changed).contains("\nInt unchanged = 1;\n}").doesNotEndWith("\n")
        assertThat(changed.lines().size).isGreaterThan(text.lines().size)
        val typing = apply(text, XdkLexical.format(text, config, OPTIONS, range, wrapLines = false))
        assertThat(typing.lines()).hasSameSizeAs(text.lines())
        assertThat(typing).contains("\n    Int sum(")
        assertThat(XdkLexical.format(changed, config, OPTIONS.copy(insertFinalNewline = false)))
            .allSatisfy { assertThat(it.range.start.line).isEqualTo(changed.lines().lastIndex - 1) }
    }

    @Test
    fun `multiline literals templates and unterminated lexemes never have their contents reformatted`() {
        val samples =
            listOf(
                """
                module Literals {
                String value = \|keep  this indent
                               |and "quoted" content
                    ;
                }
                """.trimIndent(),
                "module Literals { String value = $\"keep  {1 + 2}\"; }",
            )
        samples.forEach { source ->
            XdkAdapter().use { adapter -> assertThat(adapter.compile("untitled:Literals.x", source).diagnostics).isEmpty() }
            val formatted = apply(source, XdkLexical.format(source, FormattingConfig(maxLineWidth = 20), OPTIONS))
            assertThat(formatted).contains(source.substringAfter("= ").substringBeforeLast(";"))
        }
        assertThat(XdkLexical.format("module Bad { String text = \"unfinished", FormattingConfig.DEFAULT, OPTIONS)).isEmpty()
    }

    @Test
    fun `wrapping honors configured indentation continuation width and tabs`() {
        val text =
            """
            module Layout {
            Int sum(Int first, Int second, Int third) = first + second + third;
            }
            """.trimIndent()
        val options = FormattingOptions(2, true)
        listOf(true, false).forEach { spaces ->
            val config = FormattingConfig(2, 6, spaces, 40)
            val formatted = apply(text, XdkLexical.format(text, config, options))
            val lines = formatted.lines().filter(String::isNotEmpty)
            assertThat(lines[1]).startsWith(if (spaces) "  Int" else "\tInt")
            assertThat(lines.drop(2).dropLast(1))
                .isNotEmpty()
                .allSatisfy { line ->
                    assertThat(line.takeWhile(Char::isWhitespace)).isEqualTo(if (spaces) "        " else "\t\t\t\t")
                }
            assertThat(XdkLexical.format(formatted, config, options)).isEmpty()
            XdkAdapter().use { adapter -> assertThat(adapter.compile("untitled:Layout.x", formatted).diagnostics).isEmpty() }
        }
    }

    @Test
    fun `wrapping compact declarations uses nesting at each break and remains stable`() {
        val text =
            """
            module Compact { Int sum(Int first, Int second, Int third) = first + second + third; }
            """.trimIndent()
        val config = FormattingConfig(maxLineWidth = 40)
        val formatted = apply(text, XdkLexical.format(text, config, OPTIONS))
        assertThat(formatted.lines()).hasSizeGreaterThan(1)
        assertThat(XdkLexical.format(formatted, config, OPTIONS)).isEmpty()
        XdkAdapter().use { adapter -> assertThat(adapter.compile("untitled:Compact.x", formatted).diagnostics).isEmpty() }
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String {
        fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
        return edits.sortedByDescending { offset(it.range.start) }.fold(text) { changed, edit ->
            changed.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
        }
    }

    private companion object {
        val OPTIONS = FormattingOptions(4, true)
    }
}
