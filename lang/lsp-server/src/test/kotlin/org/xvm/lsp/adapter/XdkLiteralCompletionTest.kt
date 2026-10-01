package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkLiteralCompletionTest {
    @ParameterizedTest
    @ValueSource(strings = ["Int", "String", "Boolean", "String?"])
    fun `literal proposals fit and compile in positional and named slots`(type: String) {
        val expected =
            when (type) {
                "Int" -> listOf("0")
                "String" -> listOf("\"\"")
                "Boolean" -> listOf("False", "True")
                else -> listOf("Null", "\"\"")
            }
        listOf("", "value = ").forEach { label ->
            val source =
                "module Editing { void take($type value) {} void run() { take($label§); } }"
            verify(source, expected)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void run() { take(1 + §); }",
                "void run() { take(§, 2); }",
                "void run() { function void(Int) fn = value -> {}; fn(§); }",
                "class Box(Int value) {} void run() { Box box = new Box(§); }",
            ],
    )
    fun `literal fitting preserves compound later function and constructor arguments`(body: String) {
        verify("module Editing { void take(Int value, Int later = 2) {} $body }", listOf("0"))
    }

    @Test
    fun `literals come from all fitting overloads without selecting one`() {
        verify(
            "module Editing { void take(Int value) {} void take(String value) {} void run() { take(§); } }",
            listOf("0", "\"\""),
        )
    }

    @Test
    fun `literal insertion must validate the entire operator expression`() {
        verify(
            "module Editing { void take(Boolean value) {} void run() { take(1 == §); } }",
            listOf("0"),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "void run() { take(§, 1); }",
                "void run() { missing(§); }",
                "void run(String text) { take(text.§, \"\"); }",
            ],
    )
    fun `invalid other arguments and qualified reads cannot acquire literal proposals`(body: String) {
        verify("module Editing { void take(String value, String later) {} $body }", emptyList())
    }

    @Test
    fun `a literal prefix replaces the whole token in UTF16 source`() {
        val marked =
            "module Editing {\r\n void take(Boolean value) {} void run() { /* 😀 */ take(Tr§ue); }\r\n}"
        val source = marked.replace("§", "")
        val line = source.lines()[1]
        val start = line.indexOf("True")
        XdkAdapter().use { adapter ->
            adapter.compile(URI, source)
            val literal =
                adapter.getCompletions(URI, 1, start + 2).single {
                    it.kind == CompletionItem.CompletionKind.VALUE
                }
            assertThat(literal.label).isEqualTo("True")
            assertThat(literal.textEdit)
                .isEqualTo(TextEdit(Range(Position(1, start), Position(1, start + 4)), "True"))
        }
    }

    private fun verify(
        marked: String,
        expected: List<String>,
    ) {
        val at = marked.indexOf('§')
        val source = marked.replace("§", "")
        XdkAdapter().use { adapter ->
            val baseline = adapter.compile(URI, source)
            val literals =
                adapter.getCompletions(URI, 0, at).filter {
                    it.kind == CompletionItem.CompletionKind.VALUE
                }
            assertThat(literals.map { it.label })
                .describedAs(marked)
                .containsExactlyInAnyOrderElementsOf(expected)
            assertThat(adapter.getCachedResult(URI)).isEqualTo(baseline)
            literals.forEach { item ->
                assertThat(item.textEdit)
                    .isEqualTo(TextEdit(Range(Position(0, at), Position(0, at)), item.label))
                assertThat(
                    adapter.compile(URI, source.replaceRange(at, at, item.label)).diagnostics,
                ).isEmpty()
                adapter.compile(URI, source)
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
