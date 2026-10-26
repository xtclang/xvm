package org.xvm.lsp.server

import java.io.File

/**
 * The text each source file of a debugged run was compiled from, and the text the server has of it now, which differs
 * once the file is edited: [lines] matches the two, so that a breakpoint is placed on the line the program has for it,
 * and the program's lines are shown where they are now.
 */
internal class CompiledSources(
    private val compiled: Map<File, String>,
    private val text: (File) -> String?,
) {
    /** The line map of the file at [path], the same lines if it was not compiled, cannot be read or is unchanged. */
    fun lines(path: String?): LineMap {
        val file = path?.let { runCatching { File(it).canonicalFile }.getOrNull() } ?: return LineMap.SAME
        val built = compiled[file] ?: return LineMap.SAME
        val now = text(file)
        return if (now == null || now == built) LineMap.SAME else LineMap.of(built, now)
    }
}

/**
 * The lines of a source file as it was compiled, matched to the lines of its text now, which keep their order: a line
 * now that is unchanged since has a [compiled] line, and each compiled line a line [now], which for one changed since
 * is where its change is. Lines are 1-based, and one beyond either text maps to itself. Whitespace at the start or end
 * of a line does not change it.
 */
internal class LineMap private constructor(
    /** The compiled line of each line now, or 0 for one changed since. */
    private val toCompiled: IntArray,
    /** The line now of each compiled line. */
    private val toNow: IntArray,
) {
    /** The compiled line of the line now, or null if the line changed since it was compiled. */
    fun compiled(line: Int): Int? = if (line - 1 in toCompiled.indices) toCompiled[line - 1].takeIf { it > 0 } else line

    /** The line now of the compiled line, or, if it changed since, where its change is. */
    fun now(line: Int): Int = if (line - 1 in toNow.indices) toNow[line - 1] else line

    companion object {
        /** The lines of a text that is the same as when it was compiled. */
        val SAME = LineMap(IntArray(0), IntArray(0))

        /** The cells of the largest table [common] fills, about 4 MB, which a changed part of a file seldom needs. */
        private const val MAX_CELLS = 1_000_000L

        /** Matches the lines of the [built] text to those of the text [now], keeping as many as keep their order. */
        fun of(
            built: String,
            now: String,
        ): LineMap {
            val before = built.lines().map(String::trim)
            val after = now.lines().map(String::trim)
            val toCompiled = IntArray(after.size)
            val toNow = IntArray(before.size)
            matches(before, after).forEach { (old, new) ->
                toCompiled[new] = old + 1
                toNow[old] = new + 1
            }
            // a changed line is where its change is now: after the line now of the unchanged line before it
            var next = 1
            for (old in toNow.indices) {
                if (toNow[old] > 0) next = toNow[old] + 1 else toNow[old] = minOf(next, after.size)
            }
            return LineMap(toCompiled, toNow)
        }

        /**
         * The indexes of the lines the two texts have in common, in order: the same lines at their start and their end,
         * and between them those of a longest common subsequence.
         */
        private fun matches(
            before: List<String>,
            after: List<String>,
        ): List<Pair<Int, Int>> {
            var start = 0
            while (start < before.size && start < after.size && before[start] == after[start]) start++
            var end = 0
            while (end < before.size - start && end < after.size - start &&
                before[before.lastIndex - end] == after[after.lastIndex - end]
            ) {
                end++
            }
            val middle = common(before.subList(start, before.size - end), after.subList(start, after.size - end))
            return (0 until start).map { it to it } +
                middle.map { (old, new) -> old + start to new + start } +
                (end - 1 downTo 0).map { before.lastIndex - it to after.lastIndex - it }
        }

        /**
         * The indexes of a longest common subsequence of the lines, or none if the table it takes has more than
         * [MAX_CELLS] cells, which leaves every line between the same start and end changed.
         */
        private fun common(
            before: List<String>,
            after: List<String>,
        ): List<Pair<Int, Int>> {
            val (rows, columns) = before.size to after.size
            if (rows == 0 || columns == 0 || rows.toLong() * columns > MAX_CELLS) return emptyList()
            // lengths[i][j] is the length of a longest common subsequence of before from i and after from j
            val lengths = Array(rows + 1) { IntArray(columns + 1) }
            for (i in rows - 1 downTo 0) {
                for (j in columns - 1 downTo 0) {
                    lengths[i][j] = if (before[i] == after[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
                }
            }
            val pairs = mutableListOf<Pair<Int, Int>>()
            var (i, j) = 0 to 0
            while (i < rows && j < columns) {
                when {
                    before[i] == after[j] -> pairs += i++ to j++
                    lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                    else -> j++
                }
            }
            return pairs
        }
    }
}
