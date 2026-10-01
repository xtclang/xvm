package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

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
