package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

/**
 * Member completion where a member access is typed at the end of a line, as it is in the middle of a method: the
 * next line's statement is not part of the access, whatever it starts with.
 */
class XdkLineEndCompletionTest {
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

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
