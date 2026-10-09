package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test

class SemanticTokenReportsTest {
    @Test
    fun `delta reconstructs insertions removals integer edits and unchanged results`() {
        val reports = SemanticTokenReports()
        val samples =
            listOf(
                emptyList(),
                listOf(0, 2, 3, 1, 0),
                listOf(1, 2, 3, 1, 0),
                listOf(1, 2, 3, 1, 0, 0, 8, 4, 2, 0),
            )
        samples.forEach { old ->
            samples.forEach { next ->
                val before = reports.full("file:///test.x", old)
                val delta = reports.delta("file:///test.x", before.resultId, next).right
                val applied = old.toMutableList()
                delta.edits
                    .sortedByDescending { it.start }
                    .forEach { edit ->
                        applied.subList(edit.start, edit.start + edit.deleteCount).clear()
                        applied.addAll(edit.start, edit.data.orEmpty())
                    }
                assertThat(applied).isEqualTo(next)
                if (old == next) assertThat(delta.edits).isEmpty()
            }
        }
    }

    @Test
    fun `unknown foreign closed and restarted ids fall back to full`() {
        val reports = SemanticTokenReports()
        val previous = reports.full("file:///test.x", listOf(0, 0, 1, 0, 0))
        assertThat(reports.delta("file:///other.x", previous.resultId, emptyList()).isLeft).isTrue()
        assertThat(
            SemanticTokenReports()
                .delta("file:///test.x", previous.resultId, emptyList())
                .isLeft,
        ).isTrue()
        reports.retire("file:/test.x")
        assertThat(reports.delta("file:///test.x", previous.resultId, emptyList()).isLeft).isTrue()
    }

    @Test
    fun `history limits bound both report count and total token memory`() {
        listOf(SemanticTokenReports(maximumReports = 1), SemanticTokenReports(maximumIntegers = 5))
            .forEach { reports ->
                val first = reports.full("file:///test.x", listOf(0, 0, 1, 0, 0))
                reports.full("file:///test.x", listOf(1, 0, 1, 0, 0))
                assertThat(reports.delta("file:///test.x", first.resultId, emptyList()).isLeft)
                    .isTrue()
            }
    }

    @Test
    fun `range reencodes from document origin and includes intersecting UTF16 tokens`() {
        val full = listOf(2, 5, 3, 0, 1, 0, 5, 4, 1, 0, 3, 2, 7, 2, 0)
        assertThat(SemanticTokenReports.range(full, Range(Position(2, 8), Position(5, 3))).data)
            .containsExactly(2, 10, 4, 1, 0, 3, 2, 7, 2, 0)
        assertThat(SemanticTokenReports.range(full, Range(Position(2, 6), Position(2, 7))).data)
            .containsExactly(2, 5, 3, 0, 1)
        assertThat(SemanticTokenReports.range(full, Range(Position(2, 6), Position(2, 6))).data)
            .isEmpty()
    }
}
