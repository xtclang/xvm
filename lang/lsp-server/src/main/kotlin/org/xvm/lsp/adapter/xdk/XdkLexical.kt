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
import org.xvm.lsp.util.ExecutionTrace

/** Java-lexer editing helpers. A failed lex never authorizes a source rewrite. */
internal object XdkLexical {
    private data class Span(
        val id: Token.Id,
        val range: Range,
        val start: Int,
        val end: Int,
        val text: String,
    )

    private val newlines = Regex("\r\n|\r|\n")
    private val urls = Regex("https?://[^\\s<>\"'`\\\\]+")
    private val comments = setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT)
    private val strings =
        setOf(Token.Id.LIT_STRING, Token.Id.LIT_CHAR, Token.Id.LIT_BINSTR, Token.Id.TEMPLATE)

    /**
     * Over-approximate path expressions without interpreting paths or suppressing compiler errors.
     */
    fun mayUseResources(text: String): Boolean =
        lex(text)?.any {
            it.id in
                setOf(
                    Token.Id.DIV,
                    Token.Id.DIR_CUR,
                    Token.Id.DIR_PARENT,
                    Token.Id.BIN_FILE,
                    Token.Id.STR_FILE,
                    Token.Id.TEMPLATE,
                )
        } ?: true

    private data class Nesting(
        val braces: Int = 0,
        val parentheses: Int = 0,
        val brackets: Int = 0,
    ) {
        fun after(id: Token.Id): Nesting =
            when (id) {
                Token.Id.L_CURLY -> copy(braces = braces + 1)
                Token.Id.R_CURLY -> copy(braces = (braces - 1).coerceAtLeast(0))
                Token.Id.L_PAREN, Token.Id.ASYNC_PAREN -> copy(parentheses = parentheses + 1)
                Token.Id.R_PAREN -> copy(parentheses = (parentheses - 1).coerceAtLeast(0))
                Token.Id.L_SQUARE -> copy(brackets = brackets + 1)
                Token.Id.R_SQUARE -> copy(brackets = (brackets - 1).coerceAtLeast(0))
                else -> this
            }
    }

    private val closers = setOf(Token.Id.R_CURLY, Token.Id.R_PAREN, Token.Id.R_SQUARE)
    private val continuations =
        setOf(
            Token.Id.ASN,
            Token.Id.ADD,
            Token.Id.SUB,
            Token.Id.MUL,
            Token.Id.DIV,
            Token.Id.MOD,
            Token.Id.DIVREM,
            Token.Id.COND_AND,
            Token.Id.COND_OR,
            Token.Id.COND_XOR,
            Token.Id.COND_ELSE,
            Token.Id.ADD_ASN,
            Token.Id.SUB_ASN,
            Token.Id.MUL_ASN,
            Token.Id.DIV_ASN,
            Token.Id.COND_ASN,
            Token.Id.COND_NN_ASN,
            Token.Id.LAMBDA,
        )

    fun format(
        text: String,
        config: FormattingConfig,
        options: FormattingOptions,
        range: Range? = null,
        wrapLines: Boolean = true,
    ): List<TextEdit> {
        val tokens = lex(text) ?: return emptyList()
        val lines = text.split(newlines)
        val lineTokens = tokens.groupBy { it.range.start.line }
        val nesting =
            lines.indices.runningFold(Nesting()) { depth, line ->
                lineTokens[line].orEmpty().fold(depth) { current, token -> current.after(token.id) }
            }
        val previous =
            lines.indices.runningFold(null as Token.Id?) { last, line ->
                lineTokens[line].orEmpty().lastOrNull { it.id !in comments }?.id ?: last
            }
        val multiline =
            tokens
                .filter { it.range.start.line < it.range.end.line }
                .flatMap { token -> (token.range.start.line..token.range.end.line).map { it to token } }
                .toMap()
        val newline = newlines.find(text)?.value ?: "\n"
        val start = range?.start?.line ?: 0
        val end = range?.end?.let { if (it.column == 0 && it.line > start) it.line - 1 else it.line } ?: lines.lastIndex
        val edits =
            buildList {
                (start..minOf(end, lines.lastIndex)).forEach { line ->
                    val value = lines[line]
                    val host = multiline[line]
                    // Literal bytes (including multiline/template indentation) are never reformatted.
                    // Shift only standalone block-comment margins, preserving their relative layout.
                    if (host != null && (
                            host.id != Token.Id.ENC_COMMENT ||
                                lines[host.range.start.line].take(host.range.start.column).isNotBlank() ||
                                lines[host.range.end.line].drop(host.range.end.column).isNotBlank()
                        )
                    ) {
                        return@forEach
                    }
                    val onLine = lineTokens[line].orEmpty()
                    val depth = onLine.takeWhile { it.id in closers }.fold(nesting[line]) { current, token -> current.after(token.id) }
                    val continuation =
                        depth.parentheses + depth.brackets > 0 ||
                            previous[line] in continuations || onLine.firstOrNull()?.id == Token.Id.DOT
                    val base = depth.braces * config.indentSize
                    val leading = value.takeWhile { it == ' ' || it == '\t' }.length
                    val indent =
                        if (host != null && line > host.range.start.line) {
                            nesting[host.range.start.line].braces * config.indentSize +
                                (leading - host.range.start.column).coerceAtLeast(0)
                        } else {
                            base + if (continuation) config.continuationIndentSize else 0
                        }
                    val whitespace = indentation(indent, config, options)
                    if (value.isNotBlank() && value.take(leading) != whitespace) {
                        add(TextEdit(Range(Position(line, 0), Position(line, leading)), whitespace))
                    }
                    val trimmed = value.trimEnd(' ', '\t').length
                    if (options.trimTrailingWhitespace && trimmed < value.length &&
                        host == null && onLine.none { it.range.end.column > trimmed }
                    ) {
                        add(TextEdit(Range(Position(line, trimmed), Position(line, value.length)), ""))
                    }
                    if (wrapLines && host == null && config.maxLineWidth > 0) {
                        addAll(
                            wrap(
                                line,
                                value,
                                onLine,
                                nesting[line],
                                leading,
                                indent,
                                newline,
                                config,
                                options,
                            ),
                        )
                    }
                }
                if (range == null && options.insertFinalNewline && text.isNotEmpty() && !text.endsWith('\n') && !text.endsWith('\r')) {
                    val at = Position(lines.lastIndex, lines.last().length)
                    add(TextEdit(Range(at, at), newline))
                }
            }
        val proposed =
            edits
                .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
                .fold(text) { result, edit ->
                    result.replaceRange(offset(text, edit.range.start), offset(text, edit.range.end), edit.newText)
                }

        // Comment margins may move as a unit. Every other token, including every literal, must
        // have identical spelling and tokenization; whitespace can be language-significant.
        fun signature(items: List<Span>) =
            items.map {
                it.id to
                    if (it.id == Token.Id.ENC_COMMENT) {
                        it.text.split(newlines).map { line -> line.trimStart(' ', '\t') }
                    } else {
                        listOf(it.text)
                    }
            }
        return if (lex(proposed)?.let(::signature) == signature(tokens)) edits else emptyList()
    }

    private fun indentation(
        size: Int,
        config: FormattingConfig,
        options: FormattingOptions,
    ): String =
        if (config.insertSpaces) {
            " ".repeat(size.coerceAtLeast(0))
        } else {
            val tab = options.tabSize.coerceAtLeast(1)
            "\t".repeat(size.coerceAtLeast(0) / tab) + " ".repeat(size.coerceAtLeast(0) % tab)
        }

    /** Greedy width wrapping only at existing expression/list token boundaries. */
    private data class WrapPoint(
        val start: Int,
        val end: Int,
        val indent: Int,
    )

    private fun wrap(
        line: Int,
        value: String,
        tokens: List<Span>,
        before: Nesting,
        leading: Int,
        indent: Int,
        newline: String,
        config: FormattingConfig,
        options: FormattingOptions,
    ): List<TextEdit> {
        val contexts = tokens.runningFold(before) { depth, token -> depth.after(token.id) }
        val gaps =
            tokens.zipWithNext().mapIndexedNotNull { index, (left, right) ->
                val nested = contexts[index + 1]
                val breakable =
                    left.id in continuations ||
                        (left.id == Token.Id.COMMA && nested.parentheses + nested.brackets > 0)
                val start = left.range.end.column
                val end = right.range.start.column
                WrapPoint(start, end, nested.braces * config.indentSize + config.continuationIndentSize).takeIf {
                    breakable && right.id !in closers && right.id !in comments && right.id != Token.Id.SEMICOLON &&
                        value.substring(start, end).all { it == ' ' || it == '\t' }
                }
            }

        fun next(
            start: Int,
            width: Int,
        ): WrapPoint? {
            if (width + value.length - start <= config.maxLineWidth) return null
            val remaining = gaps.filter { it.start > start }
            return remaining.lastOrNull { width + it.start - start <= config.maxLineWidth } ?: remaining.firstOrNull()
        }
        return generateSequence(next(leading, indent)) { gap -> next(gap.end, gap.indent) }
            .map { (start, end, continuedIndent) ->
                TextEdit(Range(Position(line, start), Position(line, end)), newline + indentation(continuedIndent, config, options))
            }.toList()
    }

    fun links(text: String): List<DocumentLink> =
        lex(text)
            .orEmpty()
            .filter { it.id in comments || it.id in strings }
            .flatMap { host ->
                urls
                    .findAll(host.text)
                    .map { match ->
                        val target = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}')
                        val start = host.start + match.range.first
                        DocumentLink(
                            Range(
                                XdkRename.position(text, start),
                                XdkRename.position(text, start + target.length),
                            ),
                            target,
                            target,
                        )
                    }.toList()
            }

    /**
     * Documentation adds a semantic modifier absent from the lexical grammar. Leave ordinary
     * syntax to the editor's grammar: broad string/keyword/number overlays erase finer escape,
     * interpolation, control-keyword and literal scopes. No TextMate dependency is needed here.
     */
    fun tokens(text: String): List<List<Int>> {
        val lines = text.split(newlines)
        return lex(text).orEmpty().flatMap { token ->
            if (token.id != Token.Id.ENC_COMMENT || !token.text.startsWith("/**")) return@flatMap emptyList()
            (token.range.start.line..token.range.end.line).mapNotNull { line ->
                val start = if (line == token.range.start.line) token.range.start.column else 0
                val end =
                    if (line == token.range.end.line) token.range.end.column else lines[line].length
                if (end == start) {
                    null
                } else {
                    listOf(
                        line,
                        start,
                        end - start,
                        SemanticTokenLegend.typeIndex.getValue("comment"),
                        SemanticTokenLegend.modifierBitmask("documentation"),
                    )
                }
            }
        }
    }

    private fun lex(text: String): List<Span>? {
        val errors = ErrorList()
        val tokens =
            try {
                ExecutionTrace.api("Lexer.lex(presentation)") {
                    Lexer(Source(text), errors).asSequence().toList()
                }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null
        // Index this immutable source once. Scanning all line breaks for every token makes even
        // resource detection quadratic in file size, before compilation has started.
        val lineStarts = listOf(0) + newlines.findAll(text).map { it.range.last + 1 }.toList()
        return tokens.map { token ->
            fun position(value: Long) = Position(Source.calculateLine(value), Source.calculateOffset(value))
            val range = Range(position(token.startPosition), position(token.endPosition))
            val start = lineStarts[range.start.line] + range.start.column
            val end = lineStarts[range.end.line] + range.end.column
            Span(token.id, range, start, end, text.substring(start, end))
        }
    }

    private fun offset(
        text: String,
        at: Position,
    ): Int = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
}
