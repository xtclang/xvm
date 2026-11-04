package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

/**
 * Hover on a type's name says what the type is, as its declaration does, with its doc comment: not the type of the
 * expression that names it, which after `new` is a `Type<...>`.
 */
class XdkTypeHoverTest {
    @ParameterizedTest
    @CsvSource(
        "'public Tally', 8, service Tally",
        "'new Tally', 5, service Tally",
        "'(Tally other', 2, service Tally",
        "'new Board', 5, class Board",
        "'Shape shape', 2, interface Shape",
        "'Color c', 2, enum Color",
    )
    fun `hover names a type by what it is, wherever it is named`(
        written: String,
        delta: Int,
        label: String,
    ) {
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, SOURCE).success).isTrue()
            val lines = SOURCE.lines()
            val line = lines.indexOfFirst { written in it }
            val hover = adapter.getHoverInfo(URI, line, lines[line].indexOf(written) + delta)
            assertThat(hover).contains(label).doesNotContain("Type<")
            if (label == "service Tally") assertThat(hover).contains("Counts the games won.")
        }
    }

    private companion object {
        const val URI = "untitled:Shapes.x"

        val SOURCE =
            """
            module Shapes {
                /** Counts the games won. */
                service Tally {}
                interface Shape {}
                enum Color {Red, Blue}
                class Board {
                    public Tally tally = new Tally();
                    Tally copy(Tally other) = other;
                    Board next() = new Board();
                    void draw(Shape shape) {}
                    void paint(Color c) {}
                }
            }
            """.trimIndent()
    }
}
