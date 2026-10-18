package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

/**
 * On-type formatting through the compiler adapter, as an editor sends it: the text right after the
 * keystroke, with the editor's own auto-indent already applied (or not), and the cursor where it is
 * (`§`). The cursor line must end at the indentation the code gives.
 */
class XdkOnTypeFormattingTest {
    /** A keystroke's result: [text] with the cursor at `§`, the typed [ch], and the indentation the line must get. */
    data class Typed(
        val name: String,
        val text: String,
        val ch: String,
        val indent: Int,
    ) {
        override fun toString() = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("typed")
    fun `the cursor line ends at the indentation the code gives`(typed: Typed) {
        val marker = typed.text.indexOf('§')
        val text = typed.text.replace("§", "")
        val line = text.take(marker).count { it == '\n' }
        val column = marker - text.take(marker).lastIndexOf('\n') - 1
        for (trim in listOf(true, false)) {
            XdkAdapter().use { adapter ->
                adapter.compile(URI, text)
                val edits = adapter.onTypeFormatting(URI, line, column, typed.ch, FormattingOptions(4, true, trimTrailingWhitespace = trim))
                val result = apply(text, edits).lines()[line]
                assertThat(result.length - result.trimStart().length)
                    .describedAs("%s (trimTrailingWhitespace=%s): %s", typed.name, trim, edits)
                    .isEqualTo(typed.indent)
            }
        }
    }

    @Test
    fun `Enter after a comment keeps the editor's comment continuation`() {
        val text = "module OnType {\n    void run() {\n        // a note\n        // \n    }\n}\n"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, text)
            val edits = adapter.onTypeFormatting(URI, 3, 11, "\n", FormattingOptions(4, true, trimTrailingWhitespace = true))
            assertThat(apply(text, edits).lines()[3]).isEqualTo("        // ")
        }
    }

    @Test
    fun `a request in a large file is quick`() {
        val source = largeSource()
        XdkAdapter().use { adapter ->
            adapter.compile(URI, source)
            val times = timeRequests(source) { line -> adapter.onTypeFormatting(URI, line, 0, "\n", FormattingOptions(4, true)) }
            println(
                "compiler adapter on-type formatting, ${source.lines().size} lines: median ${times.median} ms, slowest ${times.slowest} ms",
            )
            // the target is 5 ms; this bound catches a regression without failing on a slower build machine
            assertThat(times.median).isLessThan(20.0)
        }
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

    companion object {
        const val URI = "untitled:OnType.x"

        /** Median and slowest request time, in milliseconds. */
        data class Times(
            val median: Double,
            val slowest: Double,
        )

        /** A module of about 1,200 lines: 100 classes, each with methods, statements and blank lines. */
        fun largeSource(): String =
            buildString {
                append("module Large {\n")
                repeat(100) { index ->
                    append("    class C$index {\n        Int value = $index;\n\n")
                    append("        Int twice() {\n            Int x = value;\n            if (x > 0) {\n")
                    append("                x = x * 2;\n            }\n            return x;\n        }\n    }\n\n")
                }
                append("}\n")
            }

        /** Times one request on each of 100 blank lines spread through [source], after 20 to warm up. */
        fun timeRequests(
            source: String,
            request: (Int) -> Unit,
        ): Times {
            val blanks =
                source
                    .lines()
                    .withIndex()
                    .filter { it.value.isBlank() }
                    .map { it.index }
            repeat(20) { request(blanks[it % blanks.size]) }
            val times =
                (0 until 100)
                    .map {
                        val start = System.nanoTime()
                        request(blanks[(it * 7) % blanks.size])
                        (System.nanoTime() - start) / 1_000_000.0
                    }.sorted()
            return Times(times[times.size / 2], times.last())
        }

        @JvmStatic
        fun typed() =
            listOf(
                Typed("Enter after '{' in a class, auto-indented", "module OnType {\n    class Point {\n        §\n    }\n}\n", "\n", 8),
                Typed("Enter after '{' in a class, not indented", "module OnType {\n    class Point {\n§\n    }\n}\n", "\n", 8),
                Typed("Enter after '{' in a method", "module OnType {\n    void run() {\n        §\n    }\n}\n", "\n", 8),
                Typed("Enter after a statement", "module OnType {\n    void run() {\n        Int x = 1;\n        §\n    }\n}\n", "\n", 8),
                Typed("'}' closing a class", "module OnType {\n    class Point {\n        Int x = 1;\n        }§\n}\n", "}", 4),
                Typed(
                    "'}' closing an if block",
                    "module OnType {\n    void run() {\n        if (True) {\n            Int x = 1;\n            }§\n    }\n}\n",
                    "}",
                    8,
                ),
                Typed(
                    "Enter after the '{' of a continued declaration, auto-indented from the continuation",
                    "module OnType {\n    class Point\n            implements Const {\n                §\n    }\n}\n",
                    "\n",
                    8,
                ),
                Typed(
                    "Enter after 'case'",
                    "module OnType {\n    void run(Int x) {\n        switch (x) {\n        case 1:\n        §\n        }\n    }\n}\n",
                    "\n",
                    12,
                ),
                Typed(
                    "Enter after '{' three levels deep",
                    "module OnType {\n    class Point {\n        void run() {\n            if (True) {\n                §\n            }\n        }\n    }\n}\n",
                    "\n",
                    16,
                ),
                Typed(
                    "Enter inside a string",
                    "module OnType {\n    void run() {\n        String s = \"hel\n        §lo\";\n    }\n}\n",
                    "\n",
                    8,
                ),
                Typed(
                    "Enter after a '//' comment",
                    "module OnType {\n    void run() {\n        // a note\n        // §\n    }\n}\n",
                    "\n",
                    8,
                ),
            )
    }
}
