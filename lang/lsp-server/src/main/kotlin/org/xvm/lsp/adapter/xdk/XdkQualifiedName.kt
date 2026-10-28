package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.util.ExecutionTrace

/** Spelling only: callers must supply the compiler-resolved identity and desired qualification. */
internal class XdkQualifiedName private constructor(
    private val text: String,
    private val tokens: List<Part>,
) {
    val names = tokens.filterIndexed { index, _ -> index % 2 == 0 }.map { it.text }

    /** Replace prefix syntax, preserving every comment and whitespace character between tokens. */
    fun prefix(
        count: Int,
        replacement: List<String>,
    ): XdkRename.Edit {
        val end = tokens[count * 2].start
        val prefix =
            tokens.take(count * 2).withIndex().toList().asReversed().fold(text.substring(0, end)) { value, (index, part) ->
                val name = replacement.getOrNull(index / 2)
                val spelling =
                    if (index % 2 == 0) {
                        name.orEmpty()
                    } else if (name == null) {
                        ""
                    } else {
                        "."
                    }
                value.replaceRange(part.start, part.end, spelling)
            }
        val extra = replacement.drop(count).joinToString("") { "$it." }
        return XdkRename.Edit(0, end, prefix + extra)
    }

    private data class Part(
        val id: Token.Id,
        val start: Int,
        val end: Int,
        val text: String,
    )

    companion object {
        private val comments = setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT)

        fun parse(text: String): XdkQualifiedName? {
            val tokens = lex(text) ?: return null
            if (tokens.isEmpty() || tokens.size % 2 == 0 ||
                tokens.withIndex().any { (index, token) ->
                    token.id != if (index % 2 == 0) Token.Id.IDENTIFIER else Token.Id.DOT
                }
            ) {
                return null
            }
            return XdkQualifiedName(text, tokens)
        }

        /** A removed namespace can leave a comment before the call's first surviving token. */
        fun leadingTrivia(text: String): Int? = lex(text)?.let { it.firstOrNull()?.start ?: text.length }

        private fun lex(text: String): List<Part>? =
            ExecutionTrace.api("Lexer.qualification(rename)") {
                val errors = ErrorList()
                val tokens =
                    try {
                        Lexer(Source(text), errors).asSequence().filter { it.id !in comments }.toList()
                    } catch (_: CompilerException) {
                        return@api null
                    }
                if (errors.hasSeriousErrors()) return@api null
                tokens.map { token ->
                    fun offset(position: Long) =
                        XdkRename.offset(
                            text,
                            SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)),
                        )
                    val start = offset(token.startPosition) ?: return@api null
                    val end = offset(token.endPosition) ?: return@api null
                    Part(token.id, start, end, text.substring(start, end))
                }
            }
    }
}
