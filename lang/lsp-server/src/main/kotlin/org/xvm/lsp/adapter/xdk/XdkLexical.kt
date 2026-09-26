package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.adapter.DocumentLink
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.treesitter.SemanticTokenLegend

/** Java-lexer editing helpers. A failed lex never authorizes a source rewrite. */
internal object XdkLexical {
    private data class Span(val id: Token.Id, val range: Range, val start: Int, val end: Int, val text: String)
    private val newlines = Regex("\r\n|\r|\n")
    private val urls = Regex("https?://[^\\s<>\"'`\\\\]+")
    private val comments = setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT)
    private val strings = setOf(Token.Id.LIT_STRING, Token.Id.LIT_CHAR, Token.Id.LIT_BINSTR, Token.Id.TEMPLATE)

    fun format(text: String, config: FormattingConfig, options: FormattingOptions, range: Range? = null): List<TextEdit> {
        val tokens = lex(text) ?: return emptyList()
        val lines = text.split(newlines)
        val start = range?.start?.line ?: 0
        val end = range?.end?.let { if (it.column == 0 && it.line > start) it.line - 1 else it.line } ?: lines.lastIndex
        val edits = (start..minOf(end, lines.lastIndex)).flatMap { line ->
            val value = lines[line]
            if (tokens.any { it.range.start.line < it.range.end.line && line in it.range.start.line..it.range.end.line }) {
                return@flatMap emptyList()
            }
            val before = tokens.filter { it.range.end.line < line }
            val onLine = tokens.filter { it.range.start.line == line }
            fun depth(open: Token.Id, close: Token.Id): Int =
                (before.count { it.id == open } - before.count { it.id == close } - onLine.takeWhile {
                    it.id in setOf(Token.Id.R_CURLY, Token.Id.R_PAREN, Token.Id.R_SQUARE)
                }.count { it.id == close }).coerceAtLeast(0)
            val indent = depth(Token.Id.L_CURLY, Token.Id.R_CURLY) * config.indentSize +
                if (depth(Token.Id.L_PAREN, Token.Id.R_PAREN) + depth(Token.Id.L_SQUARE, Token.Id.R_SQUARE) > 0) config.continuationIndentSize else 0
            val leading = value.takeWhile { it == ' ' || it == '\t' }.length
            val whitespace = if (config.insertSpaces) " ".repeat(indent) else {
                val size = options.tabSize.coerceAtLeast(1)
                "\t".repeat(indent / size) + " ".repeat(indent % size)
            }
            buildList {
                if (value.isNotBlank() && value.take(leading) != whitespace) {
                    add(TextEdit(Range(Position(line, 0), Position(line, leading)), whitespace))
                }
                val trimmed = value.trimEnd(' ', '\t').length
                if (options.trimTrailingWhitespace && trimmed < value.length &&
                    onLine.none { it.range.end.column > trimmed }
                ) add(TextEdit(Range(Position(line, trimmed), Position(line, value.length)), ""))
            }
        }.toMutableList()
        if (range == null && options.insertFinalNewline && text.isNotEmpty() && !text.endsWith('\n') && !text.endsWith('\r')) {
            val at = Position(lines.lastIndex, lines.last().length)
            edits += TextEdit(Range(at, at), newlines.find(text)?.value ?: "\n")
        }
        val proposed = edits.sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { result, edit -> result.replaceRange(offset(text, edit.range.start), offset(text, edit.range.end), edit.newText) }
        // Whitespace around a token can be language-significant. Reject any changed spelling or tokenization.
        return if (lex(proposed)?.map { it.id to it.text } == tokens.map { it.id to it.text }) edits else emptyList()
    }

    fun links(text: String): List<DocumentLink> = lex(text).orEmpty()
        .filter { it.id in comments || it.id in strings }.flatMap { host ->
            urls.findAll(host.text).map { match ->
                val target = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}')
                val start = host.start + match.range.first
                DocumentLink(Range(XdkRename.position(text, start), XdkRename.position(text, start + target.length)), target, target)
            }.toList()
        }

    /** Absolute token tuples; semantic name bindings take precedence when the streams are merged. */
    fun tokens(text: String): List<List<Int>> = lex(text).orEmpty().flatMap { token ->
        val kind = when {
            token.id in comments -> "comment"
            token.id in strings -> "string"
            token.id.name.startsWith("LIT_") -> "number"
            token.id != Token.Id.IDENTIFIER && token.id.TEXT?.firstOrNull()?.isLetter() == true -> "keyword"
            else -> return@flatMap emptyList()
        }
        val lines = text.split(newlines)
        (token.range.start.line..token.range.end.line).mapNotNull { line ->
            val start = if (line == token.range.start.line) token.range.start.column else 0
            val end = if (line == token.range.end.line) token.range.end.column else lines[line].length
            if (end == start) null else listOf(line, start, end - start, SemanticTokenLegend.typeIndex.getValue(kind), 0)
        }
    }

    private fun lex(text: String): List<Span>? {
        val errors = ErrorList()
        val tokens = try { Lexer(Source(text), errors).asSequence().toList() } catch (_: CompilerException) { return null }
        if (errors.hasSeriousErrors()) return null
        return tokens.map { token ->
            fun position(value: Long) = Position(Source.calculateLine(value), Source.calculateOffset(value))
            val range = Range(position(token.startPosition), position(token.endPosition))
            val start = offset(text, range.start)
            val end = offset(text, range.end)
            Span(token.id, range, start, end, text.substring(start, end))
        }
    }

    private fun offset(text: String, at: Position): Int =
        requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
}
