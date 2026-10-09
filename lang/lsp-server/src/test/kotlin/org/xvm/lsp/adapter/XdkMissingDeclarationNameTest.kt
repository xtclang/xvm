package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.PartialSemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkMissingDeclarationNameTest {
    @Test
    fun `missing property and parameter names use written types without registering bindings`() {
        XdkAdapter().use { adapter ->
            listOf(
                "String § = \"\";" to "string",
                "String string = \"\"; String § = \"\";" to "string1",
                "List<String> § = [];" to "list",
                "void run(String §) {}" to "string",
                "void run(Int count, List<String> § = []) {}" to "list",
                "class Item { construct(String §) {} }" to "string",
                "class Item(String §) {}" to "string",
                "String? § = Null;" to "string",
                "String[] § = [];" to "stringArray",
                "immutable String § = \"\";" to "string",
                "void run((String | Int) §) {}" to "value",
                "void run(function String(Int)? §) {}" to "fn",
                "class Item(String?[] §) {}" to "stringArray",
            ).forEach { (header, expected) ->
                val marked = "module Editing { $header Int later = 1; }"
                val text = marked.replace("§", "")
                val at = Position(0, marked.indexOf('§'))
                val original = adapter.compile(URI, text)
                assertThat(original.diagnostics).describedAs(marked).isNotEmpty()
                val items = adapter.getCompletions(URI, at.line, at.column)
                assertThat(items).describedAs(marked).hasSize(1)
                val item = items.single()
                assertThat(item.label).describedAs(marked).isEqualTo(expected)
                assertThat(item.textEdit).isEqualTo(TextEdit(Range(at, at), expected))
                assertThat(adapter.getCachedResult(URI)).isEqualTo(original)
                val partial = adapter.analyzeAtAsync(URI, at).join()!!
                val site = partial.sites.single()
                assertThat(site.kind).isEqualTo(PartialSemanticModel.Kind.DECLARATION_NAME)
                assertThat(site.members).isEmpty()
                assertThat(site.receiverType).isNull()
                assertThat(site.callCandidates).isNull()
                assertThat(adapter.compile(URI, text.replaceRange(at.column, at.column, item.insertText)).diagnostics)
                    .describedAs(marked)
                    .isEmpty()
            }
        }
    }

    @Test
    fun `EOF and Unicode CRLF slots keep exact insertion coordinates`() {
        XdkAdapter().use { adapter ->
            listOf(
                "module Editing { String §",
                "module Editing { void run(String §",
                "module Editing { class Item(String §",
                "module Editing {\r\n    /* 😀 */ String §;\r\n}",
            ).forEach { marked ->
                val before = marked.substringBefore('§')
                val position = Position(before.count { it == '\n' }, before.substringAfterLast('\n').length)
                adapter.compile(URI, marked.replace("§", ""))
                val item = adapter.getCompletions(URI, position.line, position.column).single()
                assertThat(item.textEdit).describedAs(marked).isEqualTo(TextEdit(Range(position, position), "string"))
            }
        }
    }

    @Test
    fun `written names and ambiguous value positions do not become empty declaration slots`() {
        XdkAdapter().use { adapter ->
            listOf(
                "String §already;",
                "void run(String §already) {}",
                "void run() { String §; }",
                "void run(String value) { value §; }",
                "void run() { call(String §); }",
                "String text = \"String §\";",
                "String /* § */ value;",
                "List<String §;",
            ).forEach { header ->
                val marked = "module Editing { $header }"
                adapter.compile(URI, marked.replace("§", ""))
                assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§')))
                    .describedAs(marked)
                    .noneMatch { it.detail.startsWith("Name from written type") }
            }
        }
    }

    @Test
    fun `inferred local names use retained initializer syntax and exact edits`() {
        listOf(
            "val § = \"hello\";" to "text",
            "var § = 42;" to "number",
            "val § = True;" to "flag",
            "val § = new StringBuffer();" to "stringBuffer",
            "val te§ = \"hello\";" to "text",
            "String st§ = \"hello\";" to "string",
            "String[] st§ = [];" to "stringArray",
        ).forEach { (declaration, expected) ->
            val marked = "module Editing { void run() { $declaration Int later = 1; } }"
            val source = marked.replace("§", "")
            XdkAdapter().use { adapter ->
                val cached = adapter.compile(URI, source)
                val item = adapter.getCompletions(URI, 0, marked.indexOf('§')).single { it.label == expected }
                assertThat(item.detail).startsWith("Name from written")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
                val edit = item.textEdit!!
                assertThat(
                    adapter.compile(URI, source.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)).diagnostics,
                ).describedAs(marked)
                    .isEmpty()
            }
        }
    }

    @Test
    fun `inference without a useful written clue does not invent a type or name`() {
        listOf("val § = Null;", "var § = unknown();", "val §;", "value § = 1;").forEach { declaration ->
            val marked = "module Editing { void run() { $declaration } }"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, marked.replace("§", ""))
                assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§')))
                    .describedAs(marked)
                    .noneMatch { it.detail.startsWith("Name from written") }
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
