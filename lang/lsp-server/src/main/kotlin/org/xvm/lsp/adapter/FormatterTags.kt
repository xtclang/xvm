package org.xvm.lsp.adapter

/**
 * IntelliJ's formatter markers (**Code Style | General | Formatter | Turn formatter on/off with markers in code
 * comments**), for both adapters' formatting: with [FormattingConfig.formatterTagsEnabled], the lines from a comment with
 * the off tag through the next comment with the on tag, or to the end of the file, keep their text, so formatting drops
 * every edit that touches them. A tag is text that a comment contains or, with
 * [FormattingConfig.formatterTagsAcceptRegexp], a regular expression that it has a match of; an empty tag matches none.
 */
internal object FormatterTags {
    /** A comment's lines, first to last, and its text. */
    data class Comment(
        val lines: IntRange,
        val text: String,
    )

    /** [edits] without the ones that touch a line the markers in [comments] keep; [comments] is asked only when needed. */
    fun kept(
        config: FormattingConfig,
        comments: () -> List<Comment>,
        edits: List<TextEdit>,
    ): List<TextEdit> {
        if (!config.formatterTagsEnabled || edits.isEmpty()) return edits
        val kept = regions(config, comments())
        return edits.filterNot { edit -> kept.any { edit.range.start.line <= it.last && it.first <= edit.range.end.line } }
    }

    /** The lines that the markers in [comments], in the order of the text, keep as they are. */
    fun regions(
        config: FormattingConfig,
        comments: List<Comment>,
    ): List<IntRange> {
        val off = matcher(config.formatterOffTag, config.formatterTagsAcceptRegexp)
        val on = matcher(config.formatterOnTag, config.formatterTagsAcceptRegexp)
        val (regions, open) =
            comments.fold(emptyList<IntRange>() to null as Int?) { (regions, start), comment ->
                when {
                    start == null && off(comment.text) -> regions to comment.lines.first
                    start != null && on(comment.text) -> regions.plusElement(start..comment.lines.last) to null
                    else -> regions to start
                }
            }
        return open?.let { regions.plusElement(it..Int.MAX_VALUE) } ?: regions
    }

    /** Whether a comment's text has [tag]; a tag that is not a regular expression is taken as text. */
    private fun matcher(
        tag: String,
        regex: Boolean,
    ): (String) -> Boolean {
        val pattern = if (regex) runCatching { Regex(tag) }.getOrNull() else null
        return when {
            tag.isEmpty() -> { _ -> false }
            pattern != null -> pattern::containsMatchIn
            else -> { text -> tag in text }
        }
    }
}
