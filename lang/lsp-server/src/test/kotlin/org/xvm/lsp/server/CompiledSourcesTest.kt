package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The line maps a debugger uses between the text a program was compiled from and the editor's text now: unchanged
 * lines keep their order and find their compiled line, a changed one has none, and the program's lines are shown
 * where they are now.
 */
class CompiledSourcesTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `lines inserted above move the lines after them, and an inserted line has no compiled line`() {
        val lines = LineMap.of("a\nb\nc\n", "x\ny\na\nb\nc\n")
        assertThat((1..5).map(lines::compiled)).containsExactly(null, null, 1, 2, 3)
        assertThat((1..3).map(lines::now)).containsExactly(3, 4, 5)
    }

    @Test
    fun `a deleted or changed line has no line now of its own, so it is where its change is`() {
        val deleted = LineMap.of("a\nb\nc", "a\nc")
        assertThat((1..2).map(deleted::compiled)).containsExactly(1, 3)
        assertThat((1..3).map(deleted::now)).containsExactly(1, 2, 2)

        val changed = LineMap.of("a\nb\nc", "a\nB\nB2\nc")
        assertThat((1..4).map(changed::compiled)).containsExactly(1, null, null, 3)
        assertThat((1..3).map(changed::now)).containsExactly(1, 2, 4)
    }

    @Test
    fun `whitespace at a line's ends and the line separator do not change it`() {
        val lines = LineMap.of("  a\r\nb\r\n    c\r\n", "a  \nb\n\tc\n")
        assertThat((1..3).map(lines::compiled)).containsExactly(1, 2, 3)
        assertThat((1..3).map(lines::now)).containsExactly(1, 2, 3)
    }

    @Test
    fun `lines that keep their order are matched around changes, and a line beyond the text maps to itself`() {
        val lines = LineMap.of("a\nb\nc\nd\ne", "a\nc\nX\nd\nb\ne")
        // a longest run in order: a, c, d, e; b moved, so it is a change
        assertThat((1..6).map(lines::compiled)).containsExactly(1, 3, null, 4, null, 5)
        assertThat(lines.compiled(40)).isEqualTo(40)
        assertThat(lines.now(40)).isEqualTo(40)
    }

    @Test
    fun `a changed part too large to match leaves its lines changed, and the same start and end still match`() {
        val built = listOf("start") + (1..1_200).map { "old $it" } + listOf("end")
        val now = listOf("start") + (1..1_100).map { "new $it" } + listOf("end")
        val lines = LineMap.of(built.joinToString("\n"), now.joinToString("\n"))
        assertThat(lines.compiled(1)).isEqualTo(1)
        assertThat(lines.compiled(500)).isNull()
        assertThat(lines.compiled(now.size)).isEqualTo(built.size)
        assertThat(lines.now(built.size)).isEqualTo(now.size)
    }

    @Test
    fun `a run's sources map a compiled file that changed, and the same lines for one unchanged, unknown or unread`() {
        val edited = File(directory, "Edited.x").canonicalFile
        val same = File(directory, "Same.x").canonicalFile
        val gone = File(directory, "Gone.x").canonicalFile
        val texts = mapOf(edited to "x\na\n", same to "s\n")
        val sources = CompiledSources(mapOf(edited to "a\n", same to "s\n", gone to "g\n")) { texts[it] }

        assertThat(sources.lines(edited.path).compiled(2)).isEqualTo(1)
        assertThat(sources.lines(edited.path).compiled(1)).isNull()
        // a path of the same file that is not canonical finds it too
        assertThat(sources.lines("${directory.path}/./Edited.x").compiled(1)).isNull()
        assertThat(sources.lines(same.path)).isSameAs(LineMap.SAME)
        assertThat(sources.lines(gone.path)).isSameAs(LineMap.SAME)
        assertThat(sources.lines(File(directory, "Other.x").path)).isSameAs(LineMap.SAME)
        assertThat(sources.lines(null)).isSameAs(LineMap.SAME)
    }
}
