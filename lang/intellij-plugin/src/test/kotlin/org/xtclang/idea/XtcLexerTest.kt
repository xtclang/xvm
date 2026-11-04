package org.xtclang.idea

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The flat PSI's tokens: every word, whitespace run and other character of the text, in order. */
class XtcLexerTest {
    private fun tokens(
        text: String,
        start: Int = 0,
        end: Int = text.length,
    ): List<Pair<IElementType, String>> =
        buildList {
            val lexer = XtcLexer()
            lexer.start(text, start, end, 0)
            while (lexer.tokenType != null) {
                add(requireNotNull(lexer.tokenType) to text.substring(lexer.tokenStart, lexer.tokenEnd))
                lexer.advance()
            }
        }

    @Test
    fun `words, whitespace and symbols are tokens of their own`() {
        assertThat(tokens("Int run_2() {\n\treturn 42;\n}"))
            .containsExactly(
                XtcLexer.WORD to "Int",
                TokenType.WHITE_SPACE to " ",
                XtcLexer.WORD to "run_2",
                XtcLexer.SYMBOL to "(",
                XtcLexer.SYMBOL to ")",
                TokenType.WHITE_SPACE to " ",
                XtcLexer.SYMBOL to "{",
                TokenType.WHITE_SPACE to "\n\t",
                XtcLexer.WORD to "return",
                TokenType.WHITE_SPACE to " ",
                XtcLexer.WORD to "42",
                XtcLexer.SYMBOL to ";",
                TokenType.WHITE_SPACE to "\n",
                XtcLexer.SYMBOL to "}",
            )
    }

    @Test
    fun `a character outside the basic plane is one symbol`() {
        assertThat(tokens("x=\"😀\"")).containsExactly(
            XtcLexer.WORD to "x",
            XtcLexer.SYMBOL to "=",
            XtcLexer.SYMBOL to "\"",
            XtcLexer.SYMBOL to "😀",
            XtcLexer.SYMBOL to "\"",
        )
    }

    @Test
    fun `lexing a part of the text covers just that part`() {
        val text = "module M { void run() {} }"
        assertThat(tokens(text, text.indexOf("void"), text.indexOf("()")).map { it.second })
            .containsExactly("void", " ", "run")
        assertThat(tokens(text).joinToString("") { it.second }).isEqualTo(text)
        assertThat(tokens("")).isEmpty()
    }
}
