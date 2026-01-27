package org.xvm.lsp.lexer

/**
 * Scanner state as a sealed hierarchy - illegal states are unrepresentable.
 */
sealed interface ScanState {
    val pos: Int

    data class Normal(
        override val pos: Int,
    ) : ScanState

    data class Template(
        override val pos: Int,
        val multiline: Boolean,
        val start: Int = pos, // Track content start for token emission
    ) : ScanState

    data class Expr(
        override val pos: Int,
        val depth: Int,
        val multiline: Boolean,
        val inString: Char? = null,
        val inChar: Boolean = false,
    ) : ScanState
}

/**
 * A single step result: optional token + next state.
 */
private data class Step(
    val token: TemplateScannerToken?,
    val next: ScanState,
)

/**
 * Stateless scanner for XTC template string literals.
 *
 * Functional design:
 * - State machine with sealed states (illegal states unrepresentable)
 * - Pure step function: (source, state) -> Step
 * - Sequence-based tokenization (no mutable loops)
 * - Substring slices instead of StringBuilder
 */
class TemplateScanner {
    fun tokenize(source: CharSequence): List<TemplateScannerToken> =
        buildList {
            var state: ScanState = ScanState.Normal(0)

            while (state.pos <= source.length) {
                val step = step(source, state)
                step.token?.let { add(it) }

                // Prevent infinite loop
                if (step.next == state) break
                state = step.next

                // Done when back to Normal at or past end
                if (state is ScanState.Normal && state.pos >= source.length) break
            }
        }

    private fun step(
        source: CharSequence,
        state: ScanState,
    ): Step =
        when (state) {
            is ScanState.Normal -> scanTemplateStart(source, state)
            is ScanState.Template -> scanTemplateContent(source, state)
            is ScanState.Expr -> scanExpression(source, state)
        }

    private fun scanTemplateStart(
        source: CharSequence,
        state: ScanState.Normal,
    ): Step {
        val pos = state.pos
        if (pos + 1 >= source.length || source[pos] != '$') {
            return Step(null, ScanState.Normal(pos + 1))
        }

        return when (source[pos + 1]) {
            '"' ->
                Step(
                    TemplateScannerToken.templateStart(pos, pos + 2),
                    ScanState.Template(pos + 2, multiline = false),
                )
            '|' ->
                Step(
                    TemplateScannerToken.templateMultilineStart(pos, pos + 2),
                    ScanState.Template(pos + 2, multiline = true),
                )
            else -> Step(null, ScanState.Normal(pos + 1))
        }
    }

    // Compound conditions (e.g., ch == '"' && !state.multiline) require when { } style
    @Suppress("IntroduceWhenSubject")
    private fun scanTemplateContent(
        source: CharSequence,
        state: ScanState.Template,
    ): Step {
        val start = state.pos
        val content = StringBuilder()
        var pos = start

        while (pos < source.length) {
            val ch = source[pos]
            when {
                // Expression start - emit content then expression
                ch == '{' -> {
                    return if (content.isNotEmpty()) {
                        Step(
                            TemplateScannerToken.content(start, pos, content.toString()),
                            state.copy(pos = pos),
                        )
                    } else {
                        Step(
                            TemplateScannerToken.exprStart(pos, pos + 1),
                            ScanState.Expr(pos + 1, depth = 1, multiline = state.multiline),
                        )
                    }
                }

                // Template end (non-multiline)
                ch == '"' && !state.multiline -> {
                    return if (content.isNotEmpty()) {
                        Step(
                            TemplateScannerToken.content(start, pos, content.toString()),
                            state.copy(pos = pos),
                        )
                    } else {
                        Step(
                            TemplateScannerToken.templateEnd(pos, pos + 1),
                            ScanState.Normal(pos + 1),
                        )
                    }
                }

                // Escape sequence - accumulate unescaped value
                ch == '\\' && pos + 1 < source.length -> {
                    content.append(unescape(source[pos + 1]))
                    pos += 2
                }

                // Newline in multiline
                ch == '\n' || ch == '\r' -> {
                    if (!state.multiline) {
                        return Step(
                            TemplateScannerToken.error(start, pos, "Newline in string literal"),
                            ScanState.Normal(pos),
                        )
                    }
                    val (continued, newPos) = checkMultilineContinuation(source, pos)
                    if (continued) {
                        content.append(ch)
                        if (ch == '\r' && pos + 1 < source.length && source[pos + 1] == '\n') {
                            content.append('\n')
                        }
                        pos = newPos
                    } else {
                        // End of multiline template
                        return if (content.isNotEmpty()) {
                            Step(
                                TemplateScannerToken.content(start, pos, content.toString()),
                                state.copy(pos = pos),
                            )
                        } else {
                            Step(
                                TemplateScannerToken.templateEnd(pos, pos),
                                ScanState.Normal(pos),
                            )
                        }
                    }
                }

                // Regular character
                else -> {
                    content.append(ch)
                    pos++
                }
            }
        }

        // End of input
        return if (state.multiline) {
            if (content.isNotEmpty()) {
                Step(
                    TemplateScannerToken.content(start, pos, content.toString()),
                    state.copy(pos = pos),
                )
            } else {
                Step(
                    TemplateScannerToken.templateEnd(pos, pos),
                    ScanState.Normal(pos),
                )
            }
        } else {
            Step(
                TemplateScannerToken.error(start, pos, "Unterminated template string"),
                ScanState.Normal(pos),
            )
        }
    }

    private fun scanExpression(
        source: CharSequence,
        state: ScanState.Expr,
    ): Step {
        val pos = state.pos

        if (pos >= source.length) {
            return Step(
                TemplateScannerToken.error(pos, pos, "Unclosed expression in template"),
                ScanState.Normal(pos),
            )
        }

        // Inside nested string
        state.inString?.let { delim ->
            return skipNestedString(source, state, delim)
        }

        // Inside char literal
        if (state.inChar) {
            return skipCharLiteral(source, state)
        }

        return when (val ch = source[pos]) {
            '{' -> Step(null, state.copy(pos = pos + 1, depth = state.depth + 1))

            '}' -> {
                val newDepth = state.depth - 1
                if (newDepth == 0) {
                    Step(
                        TemplateScannerToken.exprEnd(pos, pos + 1),
                        ScanState.Template(pos + 1, multiline = state.multiline),
                    )
                } else {
                    Step(null, state.copy(pos = pos + 1, depth = newDepth))
                }
            }

            '"' -> Step(null, state.copy(pos = pos + 1, inString = '"'))

            '\'' -> Step(null, state.copy(pos = pos + 1, inChar = true))

            '/' -> skipComment(source, state)

            '\n', '\r' -> {
                if (!state.multiline) {
                    Step(
                        TemplateScannerToken.error(pos, pos, "Newline in template expression"),
                        ScanState.Normal(pos),
                    )
                } else {
                    val skip = if (ch == '\r' && pos + 1 < source.length && source[pos + 1] == '\n') 2 else 1
                    Step(null, state.copy(pos = pos + skip))
                }
            }

            else -> Step(null, state.copy(pos = pos + 1))
        }
    }

    private fun skipNestedString(
        source: CharSequence,
        state: ScanState.Expr,
        delim: Char,
    ): Step {
        val pos = state.pos
        if (pos >= source.length) {
            return Step(null, state.copy(inString = null))
        }

        return when (source[pos]) {
            '\\' -> Step(null, state.copy(pos = if (pos + 1 < source.length) pos + 2 else pos + 1))
            delim -> Step(null, state.copy(pos = pos + 1, inString = null))
            else -> Step(null, state.copy(pos = pos + 1))
        }
    }

    private fun skipCharLiteral(
        source: CharSequence,
        state: ScanState.Expr,
    ): Step {
        val pos = state.pos
        if (pos >= source.length) {
            return Step(null, state.copy(inChar = false))
        }

        return when (source[pos]) {
            '\\' -> Step(null, state.copy(pos = if (pos + 1 < source.length) pos + 2 else pos + 1))
            '\'' -> Step(null, state.copy(pos = pos + 1, inChar = false))
            else -> Step(null, state.copy(pos = pos + 1))
        }
    }

    private fun skipComment(
        source: CharSequence,
        state: ScanState.Expr,
    ): Step {
        val pos = state.pos
        if (pos + 1 >= source.length) {
            return Step(null, state.copy(pos = pos + 1))
        }

        return when (source[pos + 1]) {
            '/' -> {
                // Single-line comment - find end of line
                val end =
                    (pos + 2 until source.length)
                        .firstOrNull { source[it] == '\n' || source[it] == '\r' }
                        ?: source.length
                Step(null, state.copy(pos = end))
            }
            '*' -> {
                // Block comment - find */
                var p = pos + 2
                while (p + 1 < source.length) {
                    if (source[p] == '*' && source[p + 1] == '/') {
                        return Step(null, state.copy(pos = p + 2))
                    }
                    p++
                }
                Step(null, state.copy(pos = source.length))
            }
            else -> Step(null, state.copy(pos = pos + 1))
        }
    }

    private fun unescape(ch: Char): String =
        when (ch) {
            '{' -> "{"
            '\\' -> "\\"
            '"' -> "\""
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            '0' -> "\u0000"
            'b' -> "\b"
            'f' -> "\u000C"
            else -> "\\$ch"
        }

    private fun checkMultilineContinuation(
        source: CharSequence,
        pos: Int,
    ): Pair<Boolean, Int> {
        var p = pos
        if (p < source.length && source[p] == '\r') p++
        if (p < source.length && source[p] == '\n') p++
        while (p < source.length && (source[p] == ' ' || source[p] == '\t')) p++
        return if (p < source.length && source[p] == '|') (true to p + 1) else (false to pos)
    }

    companion object {
        /** Convenience factory method for one-off scanning */
        @Suppress("unused") // Public API for external callers
        fun scan(source: CharSequence): List<TemplateScannerToken> = TemplateScanner().tokenize(source)
    }
}
