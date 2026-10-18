package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter

/**
 * Document formatting of switches in XDK style, in both adapters: a switch statement's `case` and
 * `default` labels align with the `switch`, with their statements one level in; a switch expression's
 * labels are indented one level inside it.
 */
class SwitchFormattingTest {
    private fun adapter(name: String): Adapter =
        when (name) {
            "compiler" -> XdkAdapter()

            else ->
                runCatching { TreeSitterAdapter() }.getOrNull().also {
                    assumeTrue(
                        it != null,
                        "Tree-sitter native library not available",
                    )
                }!!
        }

    private fun format(
        name: String,
        text: String,
    ): String =
        adapter(name).use { adapter ->
            adapter.compile(URI, text)
            apply(text, adapter.formatDocument(URI, text, FormattingOptions(4, true)))
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["compiler", "treesitter"])
    fun `a switch statement in XDK style is left as it is`(name: String) {
        assertThat(format(name, STATEMENT)).isEqualTo(STATEMENT)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["compiler", "treesitter"])
    fun `a switch statement's labels move to the switch's indentation`(name: String) {
        val indented = STATEMENT.replace("        case 1:", "            case 1:").replace("        default:", "            default:")
        assertThat(format(name, indented)).isEqualTo(STATEMENT)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["compiler", "treesitter"])
    fun `a switch expression's labels stay one level inside it`(name: String) {
        assertThat(format(name, EXPRESSION)).isEqualTo(EXPRESSION)
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String {
        val lineStarts = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

        fun offset(position: Position) = lineStarts[position.line] + position.column
        return edits.sortedByDescending { offset(it.range.start) }.fold(text) { result, edit ->
            result.substring(0, offset(edit.range.start)) + edit.newText + result.substring(offset(edit.range.end))
        }
    }

    private companion object {
        const val URI = "untitled:Switches.x"

        val STATEMENT =
            """
            module Switches {
                Int pick(Int x) {
                    switch (x) {
                    case 1:
                        return 10;
                    default:
                        return 0;
                    }
                }
            }

            """.trimIndent()

        val EXPRESSION =
            """
            module Switches {
                Boolean small(Int x) {
                    return switch (x) {
                        case 1: True;
                        default: False;
                    };
                }
            }

            """.trimIndent()
    }
}
