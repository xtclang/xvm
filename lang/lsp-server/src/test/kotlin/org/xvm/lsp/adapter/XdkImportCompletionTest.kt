package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/**
 * Checks atomic completion/import edits against actual dependency visibility. Whole-token replacement must
 * compile, or for a name typed as a statement of its own the code without the name must, without inventing
 * dependencies, conflating same-named types or hiding unrelated errors.
 */
class XdkImportCompletionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `completion replaces the whole token and imports a bundled type atomically`() {
        val text = "module App {\r\n    // 😀 retain UTF-16 positions\r\n    Doc§ument value;\r\n}"
        withCompletions(text) { adapter, uri, original, items ->
            val item = items.single { it.label == "Document" && it.additionalTextEdits.isNotEmpty() }
            assertThat(item.detail).contains("xml.xtclang.org")
            assertThat(item.textEdit?.newText).isEqualTo("Document")
            assertThat(item.textEdit?.range).isEqualTo(Range(Position(2, 4), Position(2, 12)))
            val changed = apply(original, listOf(requireNotNull(item.textEdit)) + item.additionalTextEdits)
            assertThat(changed).contains("import xml.Document;", "// 😀 retain UTF-16 positions\r\n")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `same named source candidates retain separate imports and private types are absent`() {
        source("First", "module First { class Widget {} private class Widden {} }")
        source("Second", "module Second { class Widget {} }")
        withCompletions("module App { Wid§ value; }") { _, _, _, items ->
            val imported = items.filter { it.additionalTextEdits.isNotEmpty() }
            assertThat(imported.map { it.label }).containsExactly("Widget", "Widget")
            assertThat(imported.map { it.detail }).containsExactly(
                "Widget — import from First",
                "Widget — import from Second",
            )
        }
    }

    @Test
    fun `a name typed as a statement of its own imports a type of another module`() {
        source("Library", "module Library { class Widget {} }")
        withCompletions("module App {\n    void run() {\n        Wid§\n        Int after = 1;\n    }\n}") { adapter, uri, original, items ->
            val item = items.single { it.label == "Widget" }
            assertThat(item.detail).isEqualTo("Widget — import from Library")
            assertThat(item.textEdit).isEqualTo(TextEdit(Range(Position(2, 8), Position(2, 11)), "Widget"))
            val changed = apply(original, listOf(requireNotNull(item.textEdit)) + item.additionalTextEdits)
            assertThat(changed).contains("import library.Widget;", "        Widget\n        Int after = 1;")
            val finished = changed.replace("        Widget\n", "        Widget widget = new Widget();\n")
            assertThat(adapter.compile(uri, finished).diagnostics).isEmpty()
        }
    }

    @Test
    fun `an import of a type reuses the package that already imports its module`() {
        source("Library", "module Library { class Widget {} class Gadget {} }")
        val marked =
            "module App {\n    package lib import Library;\n    lib.Gadget gadget = new lib.Gadget();\n" +
                "    void run() {\n        Wid§\n        Int after = 1;\n    }\n}"
        withCompletions(marked) { adapter, uri, original, items ->
            val item = items.single { it.label == "Widget" }
            assertThat(item.additionalTextEdits.map { it.newText.trim() }).containsExactly("import lib.Widget;")
            val changed = apply(original, listOf(requireNotNull(item.textEdit)) + item.additionalTextEdits)
            val finished = changed.replace("        Widget\n", "        Widget widget = new Widget();\n")
            assertThat(adapter.compile(uri, finished).diagnostics).isEmpty()
        }
    }

    @Test
    fun `a name typed as a statement of its own imports a bundled type`() {
        withCompletions("module App {\n    void run() {\n        Doc§\n        Int after = 1;\n    }\n}") { _, _, original, items ->
            val item = items.single { it.label == "Document" && it.additionalTextEdits.isNotEmpty() }
            assertThat(item.detail).contains("xml.xtclang.org")
            assertThat(apply(original, listOf(requireNotNull(item.textEdit)) + item.additionalTextEdits))
                .contains("import xml.Document;", "        Document\n        Int after = 1;")
        }
    }

    @Test
    fun `explicit graph does not gain an undeclared dependency through completion`() {
        val library = source("Library", "module Library { class Widget {} }")
        val text = "module App { Wid value; }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library), XdkSourceModule("App", uri)))
            adapter.compile(uri, text)
            assertThat(adapter.getCompletions(uri, 0, text.indexOf("Wid") + 3).filter { it.additionalTextEdits.isNotEmpty() }).isEmpty()
        }
    }

    @Test
    fun `unrelated errors and qualified member sites do not get import edits`() {
        listOf(
            "module App { Doc§ value; Missing broken; }",
            "module App { void run(String text) { text.Doc§; } }",
            "module App {\n    void run() {\n        Doc§\n        Missing broken;\n    }\n}",
        ).forEach { marked ->
            withCompletions(marked) { _, _, _, items ->
                assertThat(items.filter { it.additionalTextEdits.isNotEmpty() }).isEmpty()
            }
        }
    }

    private fun withCompletions(
        marked: String,
        check: (XdkAdapter, String, String, List<CompletionItem>) -> Unit,
    ) {
        val offset = marked.indexOf('§')
        val text = marked.replace("§", "")
        val before = text.take(offset)
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.compile(uri, text)
            val items = adapter.getCompletions(uri, before.count { it == '\n' }, before.substringAfterLast('\n').length)
            check(adapter, uri, text, items)
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory
            .resolve("$name.x")
            .toFile()
            .also { it.writeText(text) }
            .canonicalFile
            .toURI()
            .toString()

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
