package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.adapter.DocumentLink
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.FormatterTags
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.treesitter.SemanticTokenLegend
import org.xvm.lsp.util.ExecutionTrace

/** Java-lexer editing helpers. A failed lex never authorizes a source rewrite. */
internal object XdkLexical {
    private val newlines = Regex("\r\n|\r|\n")
    private val urls = Regex("https?://[^\\s<>\"'`\\\\]+")

    /** A template's expressions, `{...}`, which are code. */
    private val expressions = Regex("\\{[^{}]*}")
    private val comments = setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT)
    private val strings =
        setOf(Token.Id.LIT_STRING, Token.Id.LIT_CHAR, Token.Id.LIT_BINSTR, Token.Id.TEMPLATE)

    /** Tokens after which a `switch` starts a statement rather than an expression; null is the start. */
    private val statementStarts = setOf(null, Token.Id.SEMICOLON, Token.Id.L_CURLY, Token.Id.R_CURLY, Token.Id.COLON)

    private val closers = setOf(Token.Id.R_CURLY, Token.Id.R_PAREN, Token.Id.R_SQUARE)

    /** Whether each text may use resources, as [mayUseResources] found, for the texts that modules' captures read. */
    private val resourceUses = XdkTextMemo<Boolean>()

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

    /** Compiler token with source offsets and editor range for syntax-aware edits. */
    private data class Span(
        val id: Token.Id,
        val range: Range,
        val start: Int,
        val end: Int,
        val text: String,
    )

    /**
     * Over-approximate path expressions without interpreting paths or suppressing compiler errors. Each capture of a
     * module asks for each of its files, and an unchanged file's text is lexed once.
     */
    fun mayUseResources(text: String): Boolean =
        resourceUses.of(text) {
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
        }

    /**
     * Brace, parenthesis and bracket depths used while scanning a token sequence. [statementSwitches] marks, for each open brace, whether it is
     * the body of a switch statement, whose `case` and `default` labels align with the `switch` in XDK
     * style; a switch expression's labels are indented like any other content. [pendingSwitch] holds the
     * parenthesis depth of a switch statement whose body has not opened yet, and [last] the previous
     * token that is not a comment.
     */
    private data class Nesting(
        val braces: Int = 0,
        val parentheses: Int = 0,
        val brackets: Int = 0,
        val statementSwitches: List<Boolean> = emptyList(),
        val pendingSwitch: Int? = null,
        val last: Token.Id? = null,
    ) {
        /** Whether the innermost open brace is a switch statement's body. */
        val inStatementSwitch: Boolean get() = statementSwitches.lastOrNull() == true

        fun after(id: Token.Id): Nesting =
            when (id) {
                Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT -> {
                    this
                }

                Token.Id.SWITCH -> {
                    copy(pendingSwitch = parentheses.takeIf { last in statementStarts }, last = id)
                }

                Token.Id.L_CURLY -> {
                    copy(
                        braces = braces + 1,
                        statementSwitches = statementSwitches + (pendingSwitch == parentheses),
                        pendingSwitch = null,
                        last = id,
                    )
                }

                Token.Id.R_CURLY -> {
                    copy(braces = (braces - 1).coerceAtLeast(0), statementSwitches = statementSwitches.dropLast(1), last = id)
                }

                Token.Id.L_PAREN, Token.Id.ASYNC_PAREN -> {
                    copy(parentheses = parentheses + 1, last = id)
                }

                Token.Id.R_PAREN -> {
                    copy(parentheses = (parentheses - 1).coerceAtLeast(0), last = id)
                }

                Token.Id.L_SQUARE -> {
                    copy(brackets = brackets + 1, last = id)
                }

                Token.Id.R_SQUARE -> {
                    copy(brackets = (brackets - 1).coerceAtLeast(0), last = id)
                }

                Token.Id.SEMICOLON -> {
                    copy(pendingSwitch = null, last = id)
                }

                else -> {
                    copy(last = id)
                }
            }
    }

    fun format(
        text: String,
        config: FormattingConfig,
        options: FormattingOptions,
        range: Range? = null,
        wrapLines: Boolean = true,
    ): List<TextEdit> = formatOrNull(text, config, options, range, wrapLines).orEmpty()

    /** [format]'s edits, or null when [text] cannot be lexed. */
    private fun formatOrNull(
        text: String,
        config: FormattingConfig,
        options: FormattingOptions,
        range: Range?,
        wrapLines: Boolean,
    ): List<TextEdit>? {
        val tokens = lex(text) ?: return null
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
        // max_line_length = off sets no width, which refills no comment
        val refill = wrapLines && config.wrapComments && config.maxLineWidth > 0
        val refills = if (refill) refills(tokens, lines, nesting, start..end, config, options) else emptyMap()
        val refilled = refills.values.flatten()
        val edits =
            buildList {
                // a refilled paragraph replaces its lines at once, margins included
                refilled.forEach { refill ->
                    val replaced = Range(Position(refill.first, 0), Position(refill.last, lines[refill.last].length))
                    add(TextEdit(replaced, refill.lines.joinToString(newline)))
                }
                val kept = (start..minOf(end, lines.lastIndex)).filterNot { line -> refilled.any { line in it.first..it.last } }
                for (line in kept) {
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
                        continue
                    }
                    val onLine = lineTokens[line].orEmpty()
                    val depth = onLine.takeWhile { it.id in closers }.fold(nesting[line]) { current, token -> current.after(token.id) }
                    val continuation =
                        depth.parentheses + depth.brackets > 0 ||
                            previous[line] in continuations || onLine.firstOrNull()?.id == Token.Id.DOT
                    // in XDK style a switch statement's labels align with the `switch`, one level out
                    val label =
                        onLine.firstOrNull()?.id == Token.Id.CASE ||
                            (onLine.firstOrNull()?.id == Token.Id.DEFAULT && onLine.getOrNull(1)?.id == Token.Id.COLON)
                    val base = (depth.braces - if (label && depth.inStatementSwitch) 1 else 0) * config.indentSize
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

        // Comment margins may move as a unit, and a refilled comment keeps its words in order. Every other token,
        // including every literal, must have identical spelling and tokenization; whitespace can be language-significant.
        fun signature(items: List<Span>) =
            items.mapIndexed { index, item ->
                item.id to
                    when {
                        index in refills -> {
                            item.text
                                .split(newlines)
                                .mapIndexed { number, line -> if (number == 0) line else line.trimStart(' ', '\t').removePrefix("*") }
                                .flatMap { line -> line.split(' ', '\t') }
                                .filter(String::isNotEmpty)
                        }

                        item.id == Token.Id.ENC_COMMENT -> {
                            item.text.split(newlines).map { line -> line.trimStart(' ', '\t') }
                        }

                        else -> {
                            listOf(item.text)
                        }
                    }
            }
        return if (lex(proposed)?.let(::signature) == signature(tokens)) edits else emptyList()
    }

    /**
     * The refilled paragraphs of each doc and block comment, by the comment's index in [tokens], when the paragraph is
     * on [lines] in [range]; comments with none are left out.
     */
    private fun refills(
        tokens: List<Span>,
        lines: List<String>,
        nesting: List<Nesting>,
        range: IntRange,
        config: FormattingConfig,
        options: FormattingOptions,
    ): Map<Int, List<XdkCommentReflow.Refill>> =
        tokens
            .withIndex()
            .filter { (_, token) -> token.id == Token.Id.ENC_COMMENT && token.range.end.line > token.range.start.line }
            .associate { (index, host) ->
                // a comment's lines after its first keep their place relative to it, as format moves its margin
                val shift = nesting[host.range.start.line].braces * config.indentSize - host.range.start.column
                index to
                    XdkCommentReflow
                        .refills(lines, host.range.start.line, host.range.end.line, config.maxLineWidth, shift) {
                            indentation(it, config, options)
                        }.filter { it.first in range && it.last in range }
            }.filterValues { it.isNotEmpty() }

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
     * Where a comment or a string literal spells a name: the offset in its text, the offset where the comment or literal
     * starts, and whether it is a comment.
     */
    data class Spelling(
        val offset: Int,
        val token: Int,
        val comment: Boolean,
    )

    /**
     * Where the comments and the string literals of [text] spell [name] as a word, not as part of a longer name; a
     * template's expressions are code, not its literal. None when [text] cannot be lexed.
     */
    fun spellings(
        text: String,
        name: String,
    ): List<Spelling> {
        val word = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(name)}(?![\\p{L}\\p{N}_])")
        return lex(text)
            .orEmpty()
            .filter { it.id in comments || it.id == Token.Id.LIT_STRING || it.id == Token.Id.TEMPLATE }
            .flatMap { host ->
                val literal =
                    if (host.id == Token.Id.TEMPLATE) host.text.replace(expressions) { " ".repeat(it.value.length) } else host.text
                word.findAll(literal).map { Spelling(host.start + it.range.first, host.start, host.id in comments) }.toList()
            }
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

    /**
     * The comments that span more than one line, as regions to collapse: a block or doc comment, and a run of end-of-line
     * comments, each alone on its line, on consecutive lines; none when [text] cannot be lexed. A comment after code
     * on its line is not part of a run.
     */
    fun commentRegions(text: String): List<FoldingRange> {
        val spans = lex(text) ?: return emptyList()
        val blocks =
            spans
                .filter { it.id == Token.Id.ENC_COMMENT && it.range.end.line > it.range.start.line }
                .map { FoldingRange(it.range.start.line, it.range.end.line, FoldingRange.FoldingKind.COMMENT) }
        // each run of end-of-line comments alone on their lines, as consecutive tokens on consecutive lines
        val runs = mutableListOf<MutableList<Span>>()
        spans.forEachIndexed { index, span ->
            val previous = spans.getOrNull(index - 1)
            if (span.id != Token.Id.EOL_COMMENT || (previous != null && previous.range.end.line == span.range.start.line)) {
                return@forEachIndexed
            }
            val run = runs.lastOrNull()
            if (run != null && previous === run.last() && span.range.start.line == previous.range.start.line + 1) {
                run += span
            } else {
                runs += mutableListOf(span)
            }
        }
        val lines =
            runs
                .filter { it.size > 1 }
                .map {
                    FoldingRange(
                        it
                            .first()
                            .range.start.line,
                        it
                            .last()
                            .range.start.line,
                        FoldingRange.FoldingKind.COMMENT,
                    )
                }
        return (blocks + lines).sortedBy { it.startLine }
    }

    /**
     * The comments of [text], with their lines, for the formatter markers ([FormatterTags]); where the lexer refuses the
     * text, each line's part from where its first line or block comment opens stands in.
     */
    fun commentsOf(text: String): List<FormatterTags.Comment> =
        lex(text)
            ?.filter { it.id in comments }
            ?.map { FormatterTags.Comment(it.range.start.line..it.range.end.line, it.text) }
            ?: text.lines().mapIndexedNotNull { line, content ->
                listOf("//", "/*")
                    .mapNotNull { marker -> content.indexOf(marker).takeIf { it >= 0 } }
                    .minOrNull()
                    ?.let { FormatterTags.Comment(line..line, content.substring(it)) }
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

    /**
     * The indentation [format] gives a statement on the blank [line] of [text], or null when the text
     * cannot be lexed. A blank line has no token to indent, so this asks for a stand-in one: after Enter,
     * the new line gets the indentation of the code that will be typed there.
     */
    fun blankLineIndentation(
        text: String,
        line: Int,
        config: FormattingConfig,
        options: FormattingOptions,
    ): String? {
        val start = offset(text, Position(line, 0))
        val end = start + text.split(newlines)[line].length
        val probe = text.replaceRange(start, end, "x")
        return formatOrNull(probe, config, options, Range(Position(line, 0), Position(line, 1)), wrapLines = false)
            ?.let { edits -> edits.firstOrNull { it.range.start == Position(line, 0) }?.newText ?: "" }
    }

    private fun offset(
        text: String,
        at: Position,
    ): Int = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
}
