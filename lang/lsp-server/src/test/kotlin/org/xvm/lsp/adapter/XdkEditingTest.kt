package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename

class XdkEditingTest {
    @Test
    fun `formatting preserves CRLF literal contents and comments and is stable after application`() {
        val text = "module Editing {\r\nInt run() {\r\nString text=\"two  spaces\";   \r\n// 😀 https://example.com/docs.  \r\nreturn 1;   \r\n}\r\n}"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            val formatted = apply(text, adapter.formatDocument(URI, text, OPTIONS))
            assertThat(formatted).contains("\r\n    Int run()", "\r\n        return 1;\r\n", "\"two  spaces\"", "https://example.com/docs.  \r\n")
            assertThat(formatted).endsWith("}\r\n")
            assertThat(adapter.compile(URI, formatted).diagnostics).isEmpty()
            assertThat(adapter.formatDocument(URI, formatted, OPTIONS)).isEmpty()
            val ranged = adapter.formatRange(URI, text, Range(Position(4, 0), Position(5, 0)), OPTIONS)
            assertThat(ranged.map { it.range.start.line }).containsOnly(4)
            assertThat(adapter.onTypeFormatting(URI, 5, 1, "}", OPTIONS)).isEmpty()
            val broken = "module Editing { String text=\"unfinished"
            assertThat(adapter.formatDocument(URI, broken, OPTIONS)).isEmpty()
        }
    }

    @Test
    fun `links use Java lexical hosts and exact UTF16 ranges`() {
        val text = "module Editing {\r\n// 😀 https://example.com/docs.\r\nString url=\"https://example.org/path\";\r\n}"
        XdkAdapter().use { adapter ->
            val links = adapter.getDocumentLinks(URI, text)
            assertThat(links.map { it.target }).containsExactly("https://example.com/docs", "https://example.org/path")
            assertThat(links.first().range.start).isEqualTo(Position(1, 6))
            links.forEach { link ->
                val start = offset(text, link.range.start)
                val end = offset(text, link.range.end)
                assertThat(text.substring(start, end)).isEqualTo(link.target)
            }
        }
    }

    @Test
    fun `linked locals follow resolved identity and module lenses use the existing client command`() {
        val text = "module Editing { Int one() { Int value=1; return value; } Int two() { Int value=2; return value; } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            val linked = requireNotNull(adapter.getLinkedEditingRanges(URI, 0, text.indexOf("value")))
            assertThat(linked.ranges).hasSize(2)
            assertThat(linked.ranges.map { it.start.column }).containsExactly(text.indexOf("value"), text.indexOf("return value") + 7)
            assertThat(adapter.getLinkedEditingRanges(URI, 0, text.indexOf("Int"))).isNull()
            val lens = adapter.getCodeLenses(URI).single()
            assertThat(lens.command!!.command).isEqualTo("xtc.runModule")
            assertThat(lens.command.arguments).containsExactly(URI, "Editing")
            adapter.closeDocument(URI)
            assertThat(adapter.getCodeLenses(URI)).isEmpty()
            assertThat(adapter.getLinkedEditingRanges(URI, 0, text.indexOf("value"))).isNull()
        }
    }

    private fun apply(text: String, edits: List<TextEdit>): String =
        edits.sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit -> current.replaceRange(offset(text, edit.range.start), offset(text, edit.range.end), edit.newText) }

    private fun offset(text: String, at: Position): Int =
        requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))

    private companion object {
        const val URI = "untitled:Editing.x"
        val OPTIONS = FormattingOptions(4, true)
    }
}
