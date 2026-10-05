package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkInlineCompletions
import java.util.concurrent.TimeUnit.SECONDS

class XdkInlineCompletionTest {
    @ParameterizedTest
    @ValueSource(strings = ["consume(ans§);", "consume(ans§", "consume(value = ans§);", "Int result = ans§;"])
    fun `compiler suggests compatible names in complete and incomplete source`(statement: String) {
        val marked = """
            module App {
                void consume(Int value) {}
                void run() {
                    Int answer = 42;
                    String answerText = "wrong type";
                    $statement
                }
            }
        """.trimIndent()
        val source = marked.replace("§", "")
        val before = marked.substringBefore('§')
        val position = Position(before.count { it == '\n' }, before.substringAfterLast('\n').length)
        XdkAdapter().use { adapter ->
            adapter.compile(URI, source)
            val cached = adapter.getCachedResult(URI)
            val items = adapter.getInlineCompletionsAsync(URI, position, InlineCompletionContext(false)).get(30, SECONDS)
            assertThat(items.map { it.newText }).contains("answer")
            if (statement.startsWith("consume")) assertThat(items.map { it.newText }).doesNotContain("answerText")
            assertThat(items.map { it.range }).containsOnly(Range(position.copy(column = position.column - 3), position))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `automatic ambiguity disappears as the user continues typing`() {
        val marked = """
            module App {
                void run() {
                    Int answer = 1;
                    Int another = 2;
                    Int result = an§;
                }
            }
        """.trimIndent()
        val before = marked.substringBefore('§')
        val at = Position(before.count { it == '\n' }, before.substringAfterLast('\n').length)
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(adapter.getInlineCompletionsAsync(URI, at, InlineCompletionContext(true)).get(30, SECONDS)).isEmpty()
            assertThat(adapter.getInlineCompletionsAsync(URI, at, InlineCompletionContext(false)).get(30, SECONDS).map { it.newText })
                .contains("answer", "another")
            adapter.compile(URI, marked.replace("an§", "ans"))
            val next = at.copy(column = at.column + 1)
            assertThat(adapter.getInlineCompletionsAsync(URI, next, InlineCompletionContext(true)).get(30, SECONDS).map { it.newText })
                .containsExactly("answer")
        }
    }

    @Test
    fun `projection excludes extra edits snippets middle tokens and invalid positions`() {
        val at = Position(0, 2)
        val range = Range(Position(0, 0), at)
        val item = CompletionItem("answer", CompletionItem.CompletionKind.VARIABLE, "Int", "answer", TextEdit(range, "answer"))
        val automatic = InlineCompletionContext(true)
        fun project(text: String, items: List<CompletionItem> = listOf(item)) = XdkInlineCompletions.project(text, at, automatic, items)
        assertThat(project("an;")).containsExactly(TextEdit(range, "answer"))
        assertThat(project("another")).isEmpty()
        assertThat(project("an;", listOf(item.copy(snippet = "answer")))).isEmpty()
        assertThat(project("an;", listOf(item.copy(additionalTextEdits = listOf(TextEdit(range, "import")))))).isEmpty()
        assertThat(project("an;", listOf(item, item))).hasSize(1)
        listOf(Position(-1, 0), Position(0, -1), Position(0, 4), Position(1, 0)).forEach {
            assertThat(XdkInlineCompletions.eligible("an;", it, automatic)).isFalse()
        }
    }

    @Test
    fun `popup selection must be extended with exactly its replacement range`() {
        val at = Position(0, 2)
        val range = Range(Position(0, 0), at)
        val edit = TextEdit(range, "answer")
        val item = CompletionItem("answer", CompletionItem.CompletionKind.VARIABLE, "Int", "answer", edit)
        fun selected(selection: TextEdit) = XdkInlineCompletions.project("an;", at, InlineCompletionContext(false, selection), listOf(item))
        assertThat(selected(TextEdit(range, "ans"))).containsExactly(edit)
        assertThat(selected(edit)).isEmpty()
        assertThat(selected(TextEdit(range, "another"))).isEmpty()
        assertThat(selected(TextEdit(Range(at, at), "ans"))).isEmpty()
    }

    companion object {
        private const val URI = "file:///App.x"
    }
}
