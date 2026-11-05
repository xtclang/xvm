package org.xtclang.idea

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Splits an Ecstasy file into words, whitespace and single symbols, for [XtcParserDefinition]'s flat PSI.
 *
 * It knows no Ecstasy syntax, only where words end: a PSI leaf is then a word, and LSP4IJ, which takes an
 * identifier's range from the PSI leaf at the caret in a file of its own language, finds the same ranges as in
 * plain text. TextMate highlights the file and the language server analyzes it.
 */
class XtcLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var end = 0
    private var start = 0
    private var tokenEnd = 0
    private var type: IElementType? = null

    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int,
    ) {
        this.buffer = buffer
        end = endOffset
        tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = type

    override fun getTokenStart(): Int = start

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        start = tokenEnd
        if (start >= end) {
            type = null
            return
        }
        val first = buffer[start]
        val (kind, next) =
            when {
                first.isWhitespace() -> TokenType.WHITE_SPACE to skip { it.isWhitespace() }
                isWordPart(first) -> WORD to skip(::isWordPart)
                first.isHighSurrogate() && start + 1 < end && buffer[start + 1].isLowSurrogate() -> SYMBOL to start + 2
                else -> SYMBOL to start + 1
            }
        type = kind
        tokenEnd = next
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = end

    private fun skip(part: (Char) -> Boolean): Int = (start until end).firstOrNull { !part(buffer[it]) } ?: end

    companion object {
        /** A run of letters, digits and underscores: an identifier, a keyword or a number's digits. */
        val WORD = IElementType("WORD", XtcIntelliJLanguage)

        /** Any other character, by itself: an operator, a separator, a brace or a quote. */
        val SYMBOL = IElementType("SYMBOL", XtcIntelliJLanguage)

        private fun isWordPart(char: Char): Boolean = char.isLetterOrDigit() || char == '_'
    }
}
