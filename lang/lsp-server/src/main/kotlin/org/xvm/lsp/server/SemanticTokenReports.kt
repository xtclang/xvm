package org.xvm.lsp.server

import java.net.URI
import java.nio.file.Path
import java.util.UUID
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/** Detached token arrays only. Access is serialized by the document lifecycle lock. */
internal class SemanticTokenReports(
    private val maximumReports: Int = 128,
    private val maximumIntegers: Int = 1_000_000,
) {
    private data class Report(val uri: String, val data: List<Int>)

    private val reports = linkedMapOf<String, Report>()

    fun full(uri: String, data: List<Int>): SemanticTokens {
        val id = UUID.randomUUID().toString()
        val snapshot = data.toList()
        reports[id] = Report(key(uri), snapshot)
        while (
            reports.size > maximumReports || reports.values.sumOf { it.data.size } > maximumIntegers
        ) {
            reports.remove(reports.keys.first())
        }
        return SemanticTokens(id, snapshot.toList())
    }

    fun delta(
        uri: String,
        previousId: String,
        data: List<Int>,
    ): Either<SemanticTokens, SemanticTokensDelta> {
        val previous = reports[previousId]?.takeIf { it.uri == key(uri) }
        val current = full(uri, data)
        if (previous == null) return Either.forLeft(current)
        val old = previous.data
        val next = current.data
        val prefix =
            old.indices.asSequence().takeWhile { it < next.size && old[it] == next[it] }.count()
        val suffix =
            (0 until minOf(old.size, next.size) - prefix)
                .asSequence()
                .takeWhile { old[old.lastIndex - it] == next[next.lastIndex - it] }
                .count()
        val edits =
            if (old == next) emptyList()
            else
                listOf(
                    SemanticTokensEdit(
                        prefix,
                        old.size - prefix - suffix,
                        next.subList(prefix, next.size - suffix).toList(),
                    )
                )
        return Either.forRight(SemanticTokensDelta(edits, current.resultId))
    }

    fun retire(uri: String) {
        reports.entries.removeIf { it.value.uri == key(uri) }
    }

    fun clear() = reports.clear()

    private fun key(uri: String): String = runCatching {
        Path.of(URI(uri)).normalize().toUri().toString()
    }
        .getOrDefault(uri)

    companion object {
        /**
         * Filter intersecting single-line UTF-16 tokens, then encode relative to document start.
         */
        fun range(data: List<Int>, range: Range): SemanticTokens {
            var line = 0
            var column = 0
            var previousLine = 0
            var previousColumn = 0
            val filtered = buildList {
                data.chunked(5).forEach { token ->
                    line += token[0]
                    column = if (token[0] == 0) column + token[1] else token[1]
                    if (
                        line >= range.start.line &&
                            line <= range.end.line &&
                            (line != range.start.line ||
                                column + token[2] > range.start.character) &&
                            (line != range.end.line || column < range.end.character) &&
                            range.start != range.end
                    ) {
                        add(line - previousLine)
                        add(if (line == previousLine) column - previousColumn else column)
                        addAll(token.drop(2))
                        previousLine = line
                        previousColumn = column
                    }
                }
            }
            return SemanticTokens(filtered)
        }
    }
}
