package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.FormatterTags.Comment
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter

class FormatterTagsTest {
    private val markers = FormattingConfig(formatterTagsEnabled = true)

    @Test
    fun `a region runs from the off comment through the next on comment, or to the end`() {
        val comments =
            listOf(
                Comment(1..1, "// @formatter:off"),
                Comment(2..2, "// @formatter:off again"),
                Comment(4..5, "/* @formatter:on */"),
                Comment(6..6, "// @formatter:on, already on"),
                Comment(8..8, "// @formatter:off"),
            )
        assertThat(FormatterTags.regions(markers, comments)).containsExactly(1..5, 8..Int.MAX_VALUE)
    }

    @Test
    fun `tags are text, or regular expressions that a comment has a match of, and an empty tag matches none`() {
        val comments = listOf(Comment(0..0, "// keep: on"), Comment(3..3, "// keep: off"))
        val regexp = markers.copy(formatterOffTag = "keep: o[n]", formatterOnTag = "keep: of+", formatterTagsAcceptRegexp = true)
        assertThat(FormatterTags.regions(regexp, comments)).containsExactly(0..3)
        assertThat(FormatterTags.regions(regexp.copy(formatterTagsAcceptRegexp = false), comments)).isEmpty()
        assertThat(FormatterTags.regions(markers.copy(formatterOffTag = ""), comments)).isEmpty()
    }

    @Test
    fun `formatting keeps a marked region's lines and formats the others, in both adapters, as you type too`() {
        val marked = listOf("// @formatter:off", "Int[] m = [1, 0,", "            0, 1];", "// @formatter:on")
        val text = (listOf("module App {") + marked + listOf("void run() {", "}", "}", "")).joinToString("\n")
        // the marker comments are kept with the lines between them
        val expected = (listOf("module App {") + marked + listOf("    void run() {", "    }", "}", "")).joinToString("\n")
        val adapters = listOfNotNull(XdkAdapter(), runCatching { TreeSitterAdapter() }.getOrNull())
        adapters.forEach { adapter ->
            adapter.use {
                val uri = "untitled:Markers-${adapter.displayName}"
                adapter.editorFormattingConfig = markers
                adapter.compile(uri, text)
                assertThat(apply(text, adapter.formatDocument(uri, text, FormattingOptions(4, true))))
                    .describedAs(adapter.displayName)
                    .isEqualTo(expected)
                assertThat(adapter.onTypeFormatting(uri, 3, 12, ";", FormattingOptions(4, true))).describedAs(adapter.displayName).isEmpty()
                adapter.editorFormattingConfig = markers.copy(formatterTagsEnabled = false)
                assertThat(apply(text, adapter.formatDocument(uri, text, FormattingOptions(4, true))))
                    .describedAs(adapter.displayName)
                    .isNotEqualTo(expected)
            }
        }
    }
}
