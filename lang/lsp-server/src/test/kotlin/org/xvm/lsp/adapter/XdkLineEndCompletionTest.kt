package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Completion where a member access, a name or a value is typed at the end of a line, as it is in the middle of a
 * method: the next line's statement is not part of what is typed, whatever it starts with.
 */
class XdkLineEndCompletionTest {
    @ParameterizedTest
    @ValueSource(strings = ["Int after = 1;", "if (current.size > 0) {}", "report(current);"])
    fun `a name typed at the end of a line completes from the names in scope`(next: String) {
        XdkAdapter().use { adapter ->
            mapOf(
                "Score" to listOf("Scoreboard"),
                "scoreb" to listOf("scoreboard"),
                "repo" to listOf("report"),
                "curr" to listOf("current", "currentRound"),
                "String next = curr" to listOf("current", "currentRound"),
            ).forEach { (typed, expected) ->
                val (line, column) = compileInScope(adapter, typed, next)
                val items = adapter.getCompletions(URI, line, column)
                assertThat(items.map { it.label }).describedAs(typed).containsAll(expected).doesNotContain("text")
                val prefix = typed.substringAfterLast(' ')
                assertThat(items.filter { it.label in expected }).describedAs(typed).allSatisfy {
                    assertThat(
                        it.textEdit,
                    ).isEqualTo(TextEdit(Range(Position(line, column - prefix.length), Position(line, column)), it.label))
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int after = 1;", "report(current);"])
    fun `an empty value or statement at the end of a line offers every name in scope`(next: String) {
        XdkAdapter().use { adapter ->
            listOf("String next = ", "").forEach { typed ->
                val (line, column) = compileInScope(adapter, typed, next)
                assertThat(adapter.getCompletions(URI, line, column).map { it.label })
                    .describedAs(typed)
                    .contains("current", "currentRound", "scoreboard", "report", "Scoreboard")
            }
        }
    }

    @Test
    fun `a name typed at the end of a line gets its inline completion`() {
        XdkAdapter().use { adapter ->
            val (line, column) = compileInScope(adapter, "String next = curr", "report(current);", local = "Int round = 1;")
            val edits =
                adapter
                    .getInlineCompletionsAsync(URI, Position(line, column), InlineCompletionContext(true))
                    .get(HANG_SECONDS, SECONDS)
            assertThat(edits).containsExactly(TextEdit(Range(Position(line, column - 4), Position(line, column)), "current"))
        }
    }

    @Test
    fun `a cursor in a comment at the end of a line offers no names`() {
        XdkAdapter().use { adapter ->
            val (line, column) = compileInScope(adapter, "report(current); // curr", "Int after = 1;")
            assertThat(adapter.getCompletions(URI, line, column)).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int after = 1;", "if (text.size > 0) {}", "report(text);"])
    fun `a member access at the end of a line completes before the next line's statement`(next: String) {
        XdkAdapter().use { adapter ->
            val lines = listOf("module Editing {", "    void report(String text) {}", "    void run(String text) {", "        text.")
            val source = (lines + listOf("        $next", "    }", "}")).joinToString("\n")
            adapter.compile(URI, source)
            val line = lines.size - 1
            assertThat(adapter.getCompletions(URI, line, lines[line].length, ".").map { it.label }).contains("size", "trim")
            assertThat(adapter.getCompletions(URI, line, lines[line].length).map { it.label }).contains("size", "trim")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int after = 1;", "report(text);"])
    fun `a member name typed at the end of a line completes from what is typed`(next: String) {
        XdkAdapter().use { adapter ->
            val lines = listOf("module Editing {", "    void report(String text) {}", "    void run(String text) {", "        text.tr")
            val source = (lines + listOf("        $next", "    }", "}")).joinToString("\n")
            adapter.compile(URI, source)
            val line = lines.size - 1
            assertThat(adapter.getCompletions(URI, line, lines[line].length).map { it.label }).contains("trim").doesNotContain("size")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["text.trim();", "Int length = text.size;", "report(text.trim());"])
    fun `a cursor right after the dot of a written member completes the receiver's members`(statement: String) {
        XdkAdapter().use { adapter ->
            val lines = listOf("module Editing {", "    void report(String text) {}", "    void run(String text) {", "        $statement")
            val source = (lines + listOf("    }", "}")).joinToString("\n")
            adapter.compile(URI, source)
            val line = lines.size - 1
            val column = lines[line].indexOf("text.") + "text.".length
            assertThat(adapter.getCompletions(URI, line, column).map { it.label }).contains("size", "trim")
        }
    }

    /** Compiles [typed] on a line of its own before [next] in a method with names of each kind; the cursor ends [typed]. */
    private fun compileInScope(
        adapter: XdkAdapter,
        typed: String,
        next: String,
        local: String = "Int currentRound = 1;",
    ): Pair<Int, Int> {
        val lines =
            listOf(
                "module Editing {",
                "    class Scoreboard {}",
                "    Scoreboard scoreboard = new Scoreboard();",
                "    void report(String text) {}",
                "    void run(String current) {",
                "        $local",
                "        $typed",
            )
        adapter.compile(URI, (lines + listOf("        $next", "    }", "}")).joinToString("\n"))
        return lines.size - 1 to lines.last().length
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
