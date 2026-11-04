package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

/**
 * Protects method folding boundaries around closing braces and adjacent declarations. Damaged syntax must not
 * make a fold consume the following declaration.
 */
class XdkFoldingTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n"])
    fun `method folds stop before their closing brace and following same-line declarations`(newline: String) {
        listOf("Int value", "Int", "Int value, Str").forEach { parameters ->
            val closingLine = " /* 😀 */ } Int later = 1; }"
            val text =
                listOf(
                    "module Folds {",
                    " void damaged($parameters) {",
                    " Int hidden = 1;",
                    closingLine,
                ).joinToString(newline)
            XdkAdapter().use { adapter ->
                val result = adapter.compile(URI, text)
                assertThat(result.success)
                    .describedAs(parameters)
                    .isEqualTo(parameters == "Int value")
                val folds = adapter.getFoldingRanges(URI)
                val method = folds.single { it.startLine == 1 }
                assertThat(method.endLine).isEqualTo(3)
                assertThat(method.startCharacter).isNull()
                assertThat(method.endCharacter).isEqualTo(closingLine.indexOf('}'))
                val module = folds.single { it.startLine == 0 }
                assertThat(module.endCharacter).isEqualTo(closingLine.lastIndexOf('}'))
            }
        }
    }

    /**
     * VS Code takes all of a file's folds from the server, so a region that the server leaves out does not fold at all:
     * the doc comments, as the tree-sitter adapter folds them, and a run of imports.
     */
    @Test
    fun `doc comments, block comments, runs of line comments and of imports fold`() {
        val text =
            """
            module Folds {
                import ecstasy.maps.HashMap;
                import ecstasy.maps.ListMap;

                /**
                 * Documented, on
                 * more than one line.
                 */
                void documented() {
                }

                // one
                // two
                void commented() {}

                /* one line */
                void single() {} // not a run
                // of two
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            adapter.compile(URI, text)
            val folds = adapter.getFoldingRanges(URI).map { Triple(it.startLine, it.endLine, it.kind) }
            assertThat(folds).contains(
                Triple(1, 2, FoldingRange.FoldingKind.IMPORTS),
                Triple(4, 7, FoldingRange.FoldingKind.COMMENT),
                Triple(11, 12, FoldingRange.FoldingKind.COMMENT),
            )
            assertThat(folds.filter { it.third == FoldingRange.FoldingKind.COMMENT }.map { it.first }).containsExactly(4, 11)
        }
    }

    private companion object {
        const val URI = "file:///Folds.x"
    }
}
