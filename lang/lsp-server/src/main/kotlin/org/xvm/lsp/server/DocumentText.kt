package org.xvm.lsp.server

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextEdit

/** UTF-16 coordinates against one immutable document, including CRLF and bare CR line endings. */
internal class DocumentText(
    private val text: String,
) {
    private val breaks = Regex("\r\n|\r|\n").findAll(text).toList()
    private val starts = listOf(0) + breaks.map { it.range.last + 1 }
    private val ends = breaks.map { it.range.first } + text.length

    fun offset(position: Position): Int {
        require(position.line in starts.indices && position.character >= 0) {
            "Invalid text position"
        }
        val start = starts[position.line]
        val offset = start + position.character.coerceAtMost(ends[position.line] - start)
        require(
            offset == 0 ||
                offset == text.length ||
                !Character.isSurrogatePair(text[offset - 1], text[offset]),
        ) {
            "Text position splits a surrogate pair"
        }
        return offset
    }

    fun bounds(range: Range): Pair<Int, Int> {
        val start = offset(range.start)
        val end = offset(range.end)
        require(start <= end) { "Reversed text range" }
        return start to end
    }

    /** Validate the complete batch before publishing any of its intermediate versions. */
    fun change(
        changes: List<TextDocumentContentChangeEvent>,
        incremental: Boolean,
    ): String =
        changes.fold(text) { current, change ->
            val range = change.range
            if (range == null) {
                change.text
            } else {
                require(incremental) { "Incremental changes were not negotiated" }
                val (start, end) = DocumentText(current).bounds(range)
                current.replaceRange(start, end, change.text)
            }
        }

    /**
     * Range formatters may expand to whole lines; deduplicate identical edits, refuse conflicts.
     */
    fun nonOverlapping(edits: List<TextEdit>): List<TextEdit> {
        val ordered =
            edits
                .distinct()
                .map { it to bounds(it.range) }
                .sortedWith(compareBy({ it.second.first }, { it.second.second }))
        require(
            ordered.zipWithNext().all { (a, b) ->
                a.second.second <= b.second.first && a.second.first != b.second.first
            },
        ) {
            "Formatter returned overlapping edits"
        }
        return ordered.map { it.first }
    }
}
