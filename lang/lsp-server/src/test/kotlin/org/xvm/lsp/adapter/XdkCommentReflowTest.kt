package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLexical

/**
 * The compiler formatter's comment reflow, `wrapComments`: a doc or block comment's paragraph that runs past the margin
 * is filled again, keeping paragraphs, `@param` and `@return` lines, list items and code as they are laid out. Each
 * case formats once to its expected text, and again to no edits, with LF and CRLF line ends.
 */
class XdkCommentReflowTest {
    @Test
    fun `a paragraph past the margin is filled again and one within it stays`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * This paragraph runs well past the forty column
                 * margin, and its second line is joined.
                 *
                 * A short paragraph stays
                 * as it is.
                 */
                void run() {}
            }
            """,
            """
            module Reflow {
                /**
                 * This paragraph runs well past the
                 * forty column margin, and its
                 * second line is joined.
                 *
                 * A short paragraph stays
                 * as it is.
                 */
                void run() {}
            }
            """,
        )
    }

    @Test
    fun `tag lines align with their description and each tag is a paragraph`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * @param first  the first number to add, which may be negative
                 * @param second the second
                 *
                 * @return the sum of the two numbers, which may overflow
                 */
                void add(Int first, Int second) {}
            }
            """,
            """
            module Reflow {
                /**
                 * @param first  the first number to
                 *               add, which may be
                 *               negative
                 * @param second the second
                 *
                 * @return the sum of the two
                 *         numbers, which may
                 *         overflow
                 */
                void add(Int first, Int second) {}
            }
            """,
        )
    }

    @Test
    fun `a tag paragraph keeps the alignment of its second line`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * @param value  the value to search for, from the
                 *     start of the list to its end
                 */
                void find(Int value) {}
            }
            """,
            """
            module Reflow {
                /**
                 * @param value  the value to search
                 *     for, from the start of the
                 *     list to its end
                 */
                void find(Int value) {}
            }
            """,
        )
    }

    @Test
    fun `list items are paragraphs whose lines align with the item's text`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * One of:
                 *  - a number written in decimal digits without a sign
                 *  - a name
                 *  1. an ordered item whose text runs past it too
                 */
                void run() {}
            }
            """,
            """
            module Reflow {
                /**
                 * One of:
                 *  - a number written in decimal
                 *    digits without a sign
                 *  - a name
                 *  1. an ordered item whose text
                 *     runs past it too
                 */
                void run() {}
            }
            """,
        )
    }

    @Test
    fun `indented and fenced code, pre blocks and markup stay as they are`() {
        assertUnchanged(
            """
            module Reflow {
                /**
                 * For example:
                 *
                 *     Int total = values.reduce(0, (sum, value) -> sum + value);
                 *     @Iff("test".defined) class TestOnly {}
                 *
                 * ```
                 * Int total = values.reduce(0, (sum, value) -> sum + value);
                 * ```
                 * <pre>
                 * Int total = values.reduce(0, (sum, value) -> sum + value);
                 * </pre>
                 * | a table row that is much wider than the margin is |
                 * # a heading that is much wider than the margin is
                 */
                void run() {}
            }
            """,
        )
    }

    @Test
    fun `a URL or a word wider than the margin gets a line of its own and is never broken`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * See https://xtclang.org/a/long/path/that/cannot/break for more.
                 */
                void run() {}
            }
            """,
            """
            module Reflow {
                /**
                 * See
                 * https://xtclang.org/a/long/path/that/cannot/break
                 * for more.
                 */
                void run() {}
            }
            """,
        )
    }

    @Test
    fun `a word that would start a tag a list item or markup stays on the line before`() {
        assertReflows(
            """
            module Reflow {
                /**
                 * The text names an annotation, as in @Override, and a sign -
                 */
                void run() {}
            }
            """,
            """
            module Reflow {
                /**
                 * The text names an annotation, as
                 * in @Override, and a sign -
                 */
                void run() {}
            }
            """,
        )
    }

    @Test
    fun `comments within the margin, of other shapes, and with reflow off are unchanged`() {
        assertUnchanged(
            """
            module Reflow {
                /**
                 * Short lines that
                 * could be joined
                 * stay as they are.
                 */
                void run() {}
            }
            """,
        )
        // one line, text on the opening line, and lines without a star, as commented out code
        assertUnchanged(
            """
            module Reflow {
                /** A doc comment on one line that is far wider than the margin. */
                void run() {}
                /* A block comment whose text starts on its first line and runs past
                 * the margin.
                 */
                void walk() {}
                /*
                Int total = values.reduce(0, (sum, value) -> sum + value);
                */
                void jump() {}
            }
            """,
        )
        val long =
            """
            module Reflow {
                /**
                 * This paragraph runs well past the forty column margin.
                 */
                void run() {}
            }
            """.trimIndent() + "\n"
        assertThat(XdkLexical.format(long, CONFIG.copy(wrapComments = false), OPTIONS)).isEmpty()
        assertThat(XdkLexical.format(long, CONFIG, OPTIONS, wrapLines = false)).isEmpty()
    }

    @Test
    fun `a refilled paragraph moves with its comment's margin`() {
        assertReflows(
            """
            module Reflow {
                class Inner {
                /**
                 * This paragraph is filled at its new indentation.
                 */
                void run() {}
                }
            }
            """,
            """
            module Reflow {
                class Inner {
                    /**
                     * This paragraph is filled at
                     * its new indentation.
                     */
                    void run() {}
                }
            }
            """,
        )
    }

    @Test
    fun `a range format refills only the paragraphs it holds whole`() {
        val text =
            """
            module Reflow {
                /**
                 * This first paragraph runs well past the forty column margin,
                 * on two lines.
                 *
                 * This second paragraph runs well past the forty column margin.
                 */
                void run() {}
            }
            """.trimIndent()
        // lines 3 and 4, part of the first paragraph and the blank line after it: nothing is refilled
        assertThat(XdkLexical.format(text, CONFIG, OPTIONS, Range(Position(3, 0), Position(5, 0)))).isEmpty()
        // line 5, the whole second paragraph
        val second = apply(text, XdkLexical.format(text, CONFIG, OPTIONS, Range(Position(5, 0), Position(5, 10))))
        assertThat(second).isEqualTo(
            """
            module Reflow {
                /**
                 * This first paragraph runs well past the forty column margin,
                 * on two lines.
                 *
                 * This second paragraph runs well
                 * past the forty column margin.
                 */
                void run() {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a refilled module compiles to the same declarations`() {
        val text =
            """
            module Reflow {
                /**
                 * This paragraph runs well past the forty column margin, and is filled again.
                 *
                 * @param value  the value, which the method takes and gives back as it is
                 */
                Int same(Int value) = value;
            }
            """.trimIndent()
        val formatted = apply(text, XdkLexical.format(text, CONFIG, OPTIONS))
        assertThat(formatted.lines()).hasSizeGreaterThan(text.lines().size)
        XdkAdapter().use { adapter -> assertThat(adapter.compile("untitled:Reflow.x", formatted).diagnostics).isEmpty() }
    }

    private fun assertReflows(
        source: String,
        expected: String,
    ) {
        listOf("\n", "\r\n").forEach { newline ->
            val text = (source.trimIndent() + "\n").replace("\n", newline)
            val formatted = apply(text, XdkLexical.format(text, CONFIG, OPTIONS))
            assertThat(formatted).isEqualTo((expected.trimIndent() + "\n").replace("\n", newline))
            assertThat(XdkLexical.format(formatted, CONFIG, OPTIONS)).isEmpty()
        }
    }

    private fun assertUnchanged(source: String) = assertThat(XdkLexical.format(source.trimIndent() + "\n", CONFIG, OPTIONS)).isEmpty()

    private companion object {
        val CONFIG = FormattingConfig(maxLineWidth = 40, wrapComments = true)
        val OPTIONS = FormattingOptions(4, true)
    }
}
