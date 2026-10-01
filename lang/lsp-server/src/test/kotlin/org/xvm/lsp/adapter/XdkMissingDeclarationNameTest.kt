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
            ).forEach { (header, expected) ->
                val marked = "module Editing { $header Int later = 1; }"
                val text = marked.replace("§", "")
                val at = Position(0, marked.indexOf('§'))
                val original = adapter.compile(URI, text)
                assertThat(original.diagnostics).describedAs(marked).isNotEmpty()
                val item = adapter.getCompletions(URI, at.line, at.column).single()
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

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
