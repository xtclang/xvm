package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSyntaxCompletions
import java.util.concurrent.CancellationException

class XdkSyntaxCompletionTest {
    @Test
    fun `declaration names use written types and replace the entire identifier`() {
        listOf(
            "module Editing { String str§ange; }" to "string",
            "module Editing { void run(String str§ange) {} }" to "string",
            "module Editing { void run() { String str§ange = \"\"; } }" to "string",
            "module Editing { ecstasy.text.StringBuffer str§ange; }" to "stringBuffer",
            "module Editing { List<String> li§stName; }" to "list",
            "module Editing { HTTPClient ht§; class HTTPClient {} }" to "httpClient",
        ).forEach { (marked, expected) ->
            val item = complete(marked).single()
            assertThat(item.label).describedAs(marked).isEqualTo(expected)
            assertThat(
                item.textEdit!!
                    .range.end.column - item.textEdit.range.start.column,
            ).isEqualTo(
                marked.substringAfter('§').takeWhile(Char::isJavaIdentifierPart).length +
                    marked.substringBefore('§').takeLastWhile(Char::isJavaIdentifierPart).length,
            )
            assertThat(item.documentation).contains("not a resolved reference")
        }
    }

    @Test
    fun `name proposals avoid existing identifiers and do not invent inferred or method names`() {
        assertThat(complete("module Editing { String string; String st§; }").single().label).isEqualTo("string1")
        listOf(
            "module Editing { void ru§n() {} }",
            "module Editing { class Cl§assName {} }",
            "module Editing { void run() { val va§lue = 1; } }",
            "module Editing { String string; void run() { string§; } }",
        ).forEach { assertThat(complete(it)).describedAs(it).isEmpty() }
    }

    @Test
    fun `file member and statement contexts have distinct keywords and templates`() {
        assertThat(complete("mo§").map { it.label }).containsExactly("module", "module declaration")
        assertThat(complete("module Editing { cl§ }").map { it.label }).containsExactly("class", "class declaration")
        assertThat(complete("module Editing { void run() { wh§ } }").map { it.label }).containsExactly("while", "while loop")
        assertThat(
            complete("module Editing { § }").map {
                it.label
            },
        ).contains("class", "void method").doesNotContain("return", "if block", "module")
        assertThat(
            complete("module Editing { void run() { § } }").map {
                it.label
            },
        ).contains("return", "if block").doesNotContain("void method", "module")
    }

    @Test
    fun `keywords preserve written syntax and templates only fill vacant slots`() {
        val keyword = complete("module Editing { void run() { ret§urn; } }").single()
        assertThat(keyword.label).isEqualTo("return")
        assertThat(
            keyword.textEdit!!
                .range.end.column - keyword.textEdit.range.start.column,
        ).isEqualTo(6)
        assertThat(keyword.snippet).isNull()
        assertThat(complete("module Editing { void run() { wh§ile (False) {} } }").map { it.label }).containsExactly("while")
        assertThat(complete("module Editing { void run() { Int value = 1; if§ } }").map { it.label }).contains("if block")
    }

    @Test
    fun `syntax completion stays out of literals comments member accesses arguments and types`() {
        listOf(
            "module Editing { // cl§\n}",
            "module Editing { /* cl§ */ }",
            "module Editing { String text = \"cl§\"; }",
            "module Editing { String text = \"unterminated cl§ }",
            "module Editing { void run() { text.cl§; } }",
            "module Editing { void run() { call(cl§); } }",
            "module Editing { void run() { Int value = cl§; } }",
            "module Editing { void run() { return cl§; } }",
            "module Editing { List<cl§> value; }",
            "module Editing { class Type extends cl§ {} }",
            "module Editing { void run() { if (cl§) {} } }",
        ).forEach { assertThat(complete(it)).describedAs(it).isEmpty() }
    }

    @Test
    fun `template edits preserve CRLF Unicode columns and indentation`() {
        val marked = "module Editing {\r\n    void run() {\r\n        // 😀\r\n        if§\r\n    }\r\n}"
        val template = complete(marked).single { it.label == "if block" }
        assertThat(
            template.textEdit,
        ).isEqualTo(TextEdit(Range(Position(3, 8), Position(3, 10)), "if (True) {\r\n            \r\n        }"))
        assertThat(template.snippet).isEqualTo("if (${'$'}{1:True}) {\r\n            ${'$'}0\r\n        }")
        val name = complete("module Editing { /* 😀 */ String st§uff; }").single()
        assertThat(
            name.textEdit!!
                .range.start.column,
        ).isEqualTo("module Editing { /* 😀 */ String ".length)
    }

    @Test
    fun `parser work honors cancellation`() {
        assertThatThrownBy { XdkSyntaxCompletions.complete("module Editing {}", Position(0, 0)) { true } }
            .isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `adjacent punctuation and unfinished block boundaries retain statement context`() {
        listOf(
            "module Editing { void run() {§} }",
            "module Editing { void run() { Int value = 1;§} }",
            "module Editing { void run() { if§",
        ).forEach { assertThat(complete(it).map { item -> item.label }).describedAs(it).contains("if block") }
        assertThat(complete("module Editing { void run() { broken + ; if§ } }")).isEmpty()
    }

    @Test
    fun `lone carriage returns use the same original token coordinates`() {
        val text = "module Editing {\r    void run() {\r        if\r    }\r}"
        val item = XdkSyntaxCompletions.complete(text, Position(2, 10)) { false }.single { it.label == "if block" }
        assertThat(item.textEdit!!.range).isEqualTo(Range(Position(2, 8), Position(2, 10)))
        assertThat(item.insertText).isEqualTo("if (True) {\r            \r        }")
    }

    @Test
    fun `adapter publishes syntax templates alongside compiler facts without changing diagnostics`() {
        XdkAdapter().use { adapter ->
            val marked = "module Editing { void run(String item) { § } }"
            val text = marked.replace("§", "")
            val original = adapter.compile(URI, text)
            assertThat(original.diagnostics).isEmpty()
            val items = adapter.getCompletions(URI, 0, marked.indexOf('§'))
            assertThat(items.map { it.label }).contains("item", "if block", "return")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(original)
            val template = items.single { it.label == "if block" }
            val edited = text.replaceRange(marked.indexOf('§'), marked.indexOf('§'), template.insertText)
            assertThat(adapter.compile(URI, edited).diagnostics).isEmpty()
        }
    }

    @Test
    fun `adapter offers a name in a parsed declaration and a template in an unfinished file`() {
        XdkAdapter().use { adapter ->
            val text = "module Editing { String st; }"
            adapter.compile(URI, text)
            assertThat(adapter.getCompletions(URI, 0, text.indexOf("st;") + 2).map { it.label }).contains("string")
            adapter.compile(URI, "mo")
            assertThat(adapter.getCompletions(URI, 0, 2).map { it.label }).contains("module declaration")
        }
    }

    private fun complete(marked: String): List<CompletionItem> {
        val before = marked.substringBefore('§')
        return XdkSyntaxCompletions.complete(
            marked.replace("§", ""),
            Position(
                before.count {
                    it == '\n'
                },
                before.substringAfterLast('\n').length,
            ),
        ) { false }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
