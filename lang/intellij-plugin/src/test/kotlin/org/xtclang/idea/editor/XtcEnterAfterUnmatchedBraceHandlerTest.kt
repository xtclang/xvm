package org.xtclang.idea.editor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.editor.XtcEnterAfterUnmatchedBraceHandler.Companion.unclosed
import org.xtclang.idea.editor.XtcEnterAfterUnmatchedBraceHandler.Token

/** How many `}` Enter adds, from the braces of the tokens that are no string or comment. */
class XtcEnterAfterUnmatchedBraceHandlerTest {
    /** One code token over [text], with the ranges of [literals] as string or comment tokens. */
    private fun tokens(
        text: String,
        vararg literals: String,
    ): Sequence<Token> {
        val ranges = literals.map { text.indexOf(it).let { start -> start until start + it.length } }
        val bounds = (listOf(0, text.length) + ranges.flatMap { listOf(it.first, it.last + 1) }).distinct().sorted()
        return bounds.zipWithNext().asSequence().map { (start, end) -> Token(start, end, ranges.any { start in it }) }
    }

    @Test
    fun `a block the code leaves open gets one brace for each open one`() {
        val text = "module M {\n    void run() {\n}\n"
        assertThat(unclosed(text, tokens(text), text.indexOf("{\n}") + 1)).isEqualTo(1)
        val nested = "module M {\n    void run() {"
        assertThat(unclosed(nested, tokens(nested), nested.length)).isEqualTo(2)
    }

    @Test
    fun `a closed block gets none, nor does a caret after anything but an opening brace`() {
        val closed = "module M {\n    void run() {}\n}\n"
        assertThat(unclosed(closed, tokens(closed), closed.indexOf("{}") + 1)).isZero()
        val open = "module M {\n    Int x = 1;\n"
        assertThat(unclosed(open, tokens(open), open.indexOf(";") + 1)).isZero()
        assertThat(unclosed("", emptySequence(), 0)).isZero()
    }

    @Test
    fun `braces in strings and comments count for nothing`() {
        val text = "module M {\n    String s = \"{\"; // {\n    void run() {\n}\n"
        assertThat(unclosed(text, tokens(text, "\"{\"", "// {"), text.indexOf("{\n}") + 1)).isEqualTo(1)
        assertThat(unclosed(text, tokens(text), text.indexOf("{\n}") + 1)).isEqualTo(3)
        val quoted = "String s = \"{\""
        assertThat(unclosed(quoted, tokens(quoted, "\"{\""), quoted.length - 1)).isZero()
    }
}
