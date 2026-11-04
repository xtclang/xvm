package org.xtclang.idea.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.xtclang.idea.XtcIntelliJLanguage

/**
 * Closes the block that Enter opens after an unmatched `{` in an Ecstasy file: it adds a `}` for each `{` that the
 * file's code does not close, after the rest of the line, which moves into the block.
 *
 * IntelliJ's own handler, which plugin.xml orders after this one, counts the braces a brace matcher finds in the
 * highlighter's tokens, and puts the `}` after the PSI element where the line goes on. TextMate's matcher finds a
 * brace only where one starts a token, and the Ecstasy grammar's tokens run a brace into the whitespace around it;
 * an Ecstasy file's PSI has only words, after the first of which the block would close.
 */
class XtcEnterAfterUnmatchedBraceHandler : EnterAfterUnmatchedBraceHandler() {
    override fun isApplicable(
        file: PsiFile,
        caretOffset: Int,
    ): Boolean = file.language == XtcIntelliJLanguage

    override fun getMaxRBraceCount(
        file: PsiFile,
        editor: Editor,
        caretOffset: Int,
    ): Int =
        if (CodeInsightSettings.getInstance().INSERT_BRACE_ON_ENTER) {
            unclosed(editor.document.immutableCharSequence, tokens(editor.highlighter), caretOffset)
        } else {
            0
        }

    override fun calculateOffsetToInsertClosingBrace(
        file: PsiFile,
        fileText: CharSequence,
        offset: Int,
    ): Pair<PsiElement?, Int> = Pair.create(null, CharArrayUtil.shiftForwardUntil(fileText, offset, "\n"))

    /** A highlighter token: its range and whether TextMate scopes it a string or a comment. */
    internal data class Token(
        val start: Int,
        val end: Int,
        val literal: Boolean,
    )

    internal companion object {
        /**
         * How many `}` Enter at [caretOffset] adds to [text]: none unless a `{` of the code, the [tokens] that are
         * no string or comment, ends just before the caret, and then as many as the code has `{` more than `}`.
         */
        fun unclosed(
            text: CharSequence,
            tokens: Sequence<Token>,
            caretOffset: Int,
        ): Int {
            val braces =
                tokens
                    .filterNot { it.literal }
                    .flatMap { (it.start until it.end).asSequence() }
                    .filter { text[it] == '{' || text[it] == '}' }
                    .toList()
            if (caretOffset - 1 !in braces || text[caretOffset - 1] != '{') return 0
            val opened = braces.count { text[it] == '{' }
            return (opened - (braces.size - opened)).coerceAtLeast(0)
        }

        private fun tokens(highlighter: EditorHighlighter): Sequence<Token> =
            sequence {
                val iterator = highlighter.createIterator(0)
                while (!iterator.atEnd()) {
                    yield(Token(iterator.start, iterator.end, literal(iterator.tokenType)))
                    iterator.advance()
                }
            }

        private fun literal(type: IElementType): Boolean =
            generateSequence((type as? TextMateElementType)?.scope) { it.parent }
                .mapNotNull { it.scopeName?.toString() }
                .any { it.startsWith("string.") || it.startsWith("comment.") }
    }
}
