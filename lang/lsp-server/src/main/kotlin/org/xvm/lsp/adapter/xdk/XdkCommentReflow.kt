package org.xvm.lsp.adapter.xdk

/**
 * Refills the paragraphs of a doc or block comment that run past the margin, for [XdkLexical.format]. Only a comment of
 * the XDK's shape is refilled: its opening, a slash and one or two stars, alone on its first line, its closing star
 * and slash alone on its last, and a `*` starting each line between them.
 *
 * A paragraph is a run of lines of text. An `@` tag or a list item starts one, and the lines after its first align with
 * each other, as the XDK aligns a `@param` line's with its description; a paragraph of plain text keeps one indentation.
 * Blank lines, code indented four or more past the comment's text, fenced code, `<pre>` blocks and the lines of tables,
 * headings, quotes and HTML end a paragraph and stay as they are. A paragraph whose lines all fit stays as it is; one
 * that does not is filled again, a word at a time with single spaces between, and a word is never broken: a URL or a
 * word wider than the margin gets a line of its own. A word that would start a line as a tag, a list item or markup
 * stays on the line before.
 */
internal object XdkCommentReflow {
    /** Code is indented this many columns past the comment's text, or more, as in Markdown. */
    private const val CODE_INDENT = 4

    /** A tag line's words that stay together: the tag, a name for the tags in [namingTags], and the first word after. */
    private const val TAG_HEAD = 2

    private const val PRE_END = "</pre>"

    private val words = Regex("\\S+")
    private val item = Regex("([-*+]|\\d+[.)])(\\s.*)?")
    private val rule = Regex("[-=*_]{3,}")
    private val markup = listOf("|", "#", ">", "<")
    private val fences = listOf("```", "~~~")

    /** The tags whose first word is a name, which their description follows. */
    private val namingTags = setOf("@param", "@throws", "@exception")

    /** Lines [first] to [last] of the text, replaced by [lines]. */
    data class Refill(
        val first: Int,
        val last: Int,
        val lines: List<String>,
    )

    private enum class Kind { TEXT, TAG, ITEM }

    /** A line between the comment's first and last: its whitespace, its `*` with the whitespace after it, and its text. */
    private data class Line(
        val number: Int,
        val leading: String,
        val star: String,
        val text: String,
    ) {
        val column: Int get() = leading.length + star.length
    }

    private data class Paragraph(
        val kind: Kind,
        val lines: List<Line>,
    )

    /** The paragraphs so far, whether the last of them may go on, and what ends the verbatim block the lines are in. */
    private data class Scan(
        val paragraphs: List<Paragraph> = emptyList(),
        val open: Boolean = false,
        val verbatim: String? = null,
    )

    /**
     * The paragraphs to refill in the comment on lines [first] to [last] of [lines]: none when it is not of the XDK's
     * shape or each of its paragraphs fits in [width]. A line whose whitespace is n characters gets the [indentation]
     * of n + [shift] columns, as [XdkLexical.format] moves a comment's margin.
     */
    fun refills(
        lines: List<String>,
        first: Int,
        last: Int,
        width: Int,
        shift: Int,
        indentation: (Int) -> String,
    ): List<Refill> {
        val opening = lines[first].trim()
        if ((opening != "/**" && opening != "/*") || lines[last].trim() != "*/") return emptyList()
        val body = (first + 1 until last).map { line(it, lines[it]) ?: return emptyList() }
        val base = body.filter { it.text.isNotEmpty() }.minOfOrNull { it.column } ?: return emptyList()
        return body
            .fold(Scan()) { scan, line -> next(scan, line, base) }
            .paragraphs
            .mapNotNull { refill(it, width, shift, indentation) }
            .filter { it.lines != lines.subList(it.first, it.last + 1) }
    }

    private fun line(
        number: Int,
        value: String,
    ): Line? {
        val leading = value.takeWhile { it == ' ' || it == '\t' }
        val rest = value.substring(leading.length)
        if (rest.isEmpty()) return Line(number, leading, "", "")
        if (!rest.startsWith("*")) return null
        val star = "*" + rest.drop(1).takeWhile { it == ' ' || it == '\t' }
        return Line(number, leading, star, rest.substring(star.length).trimEnd())
    }

    private fun next(
        scan: Scan,
        line: Line,
        base: Int,
    ): Scan {
        val text = line.text
        val open = scan.paragraphs.lastOrNull()?.takeIf { scan.open }
        val verbatim = fences.firstOrNull { text.startsWith(it) } ?: PRE_END.takeIf { opensPre(text) }
        return when {
            scan.verbatim != null -> {
                val closed = if (scan.verbatim == PRE_END) text.contains(PRE_END, true) else text.startsWith(scan.verbatim)
                scan.copy(open = false, verbatim = scan.verbatim.takeUnless { closed })
            }

            text.isEmpty() -> {
                scan.copy(open = false)
            }

            verbatim != null -> {
                scan.copy(open = false, verbatim = verbatim)
            }

            open != null && continues(open, line) -> {
                scan.copy(paragraphs = scan.paragraphs.dropLast(1) + open.copy(lines = open.lines + line))
            }

            line.column >= base + CODE_INDENT || markup(text) -> {
                scan.copy(open = false)
            }

            else -> {
                scan.copy(paragraphs = scan.paragraphs + Paragraph(kind(text), listOf(line)), open = true)
            }
        }
    }

    private fun opensPre(text: String): Boolean = text.startsWith("<pre", true) && !text.contains(PRE_END, true)

    private fun kind(text: String): Kind =
        when {
            text.startsWith("@") && text.getOrNull(1)?.isLetter() == true -> Kind.TAG
            item.matches(text) -> Kind.ITEM
            else -> Kind.TEXT
        }

    private fun markup(text: String): Boolean = markup.any { text.startsWith(it) } || rule.matches(text)

    /** Whether [word] at the start of a line would make it another block, a tag, a list item or markup. */
    private fun startsBlock(word: String): Boolean = kind(word) != Kind.TEXT || markup(word) || fences.any { word.startsWith(it) }

    /** Whether [line] goes on [paragraph]: text aligned with its lines after the first, or past a tag or list item. */
    private fun continues(
        paragraph: Paragraph,
        line: Line,
    ): Boolean {
        if (kind(line.text) != Kind.TEXT || markup(line.text)) return false
        val head = paragraph.lines.first()
        return when {
            paragraph.lines.size > 1 -> line.column == paragraph.lines[1].column
            paragraph.kind == Kind.TEXT -> line.column == head.column
            else -> line.column > head.column
        }
    }

    private fun refill(
        paragraph: Paragraph,
        width: Int,
        shift: Int,
        indentation: (Int) -> String,
    ): Refill? {
        fun indent(line: Line) = (line.leading.length + shift).coerceAtLeast(0)

        fun columns(
            line: Line,
            text: String,
        ) = indent(line) + line.star.length + text.length
        val lines = paragraph.lines
        if (lines.all { columns(it, it.text) <= width }) return null
        val head = lines.first()
        val headWords = words.findAll(head.text).toList()
        val size =
            when (paragraph.kind) {
                Kind.TEXT -> 1
                Kind.ITEM -> 2
                Kind.TAG -> TAG_HEAD + if (headWords.first().value in namingTags) 1 else 0
            }.coerceAtMost(headWords.size)
        val units =
            (headWords.drop(size).map { it.value } + lines.drop(1).flatMap { line -> words.findAll(line.text).map { it.value } })
                .fold(listOf(head.text.substring(0, headWords[size - 1].range.last + 1))) { units, word ->
                    if (startsBlock(word)) units.dropLast(1) + "${units.last()} $word" else units + word
                }
        // lines after the first align with the paragraph's second, or with a tag's description or an item's text
        val continuation = lines.getOrNull(1) ?: head.copy(star = head.star + " ".repeat(headWords[size - 1].range.first))
        val filled =
            units.drop(1).fold(listOf(units.first())) { texts, unit ->
                val joined = "${texts.last()} $unit"
                if (columns(if (texts.size == 1) head else continuation, joined) <= width) texts.dropLast(1) + joined else texts + unit
            }
        return Refill(
            head.number,
            lines.last().number,
            filled.mapIndexed { index, text -> (if (index == 0) head else continuation).let { indentation(indent(it)) + it.star + text } },
        )
    }
}
