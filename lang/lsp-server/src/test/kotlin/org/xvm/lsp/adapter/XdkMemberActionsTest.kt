package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkMemberActionsTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Int read(Int value);", "void read();", "String read(String value);"])
    fun `implement ordinary abstract source contracts with compiler proven stubs`(signature: String) {
        val text = "module App { interface Api { $signature } class Box implements Api {} }"
        workspace(text) { adapter, uri ->
            val action = actions(adapter, uri, text).single { it.title.startsWith("Implement ") }
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("@Override", "TODO();")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(actions(adapter, uri, changed).filter { it.title.startsWith("Implement ") }).isEmpty()
        }
    }

    @Test
    fun `override inherited concrete source method without changing unrelated bindings`() {
        val text = "module App { class Base { Int read(Int value) = value; } class Box extends Base { String keep = \"ok\"; } }"
        workspace(text) { adapter, uri ->
            val action = actions(adapter, uri, text).single { it.title.startsWith("Override ") }
            assertThat(action.kind).isEqualTo(CodeAction.CodeActionKind.REFACTOR_REWRITE)
            assertThat(adapter.compile(uri, apply(text, requireNotNull(action.edit).changes.getValue(uri))).diagnostics).isEmpty()
        }
    }

    @Test
    fun `overloads remain separate compiler selected actions`() {
        val text = "module App { interface Api { Int read(Int value); String read(String value); } class Box implements Api {} }"
        workspace(text) { adapter, uri ->
            val actions = actions(adapter, uri, text).filter { it.title.startsWith("Implement ") }
            assertThat(actions).hasSize(2)
            actions.forEach { action ->
                assertThat(adapter.compile(uri, apply(text, requireNotNull(action.edit).changes.getValue(uri))).diagnostics).isEmpty()
            }
        }
    }

    @Test
    fun `specialized generic contract uses the compiler substituted signature`() {
        val text = "module App { interface Api<T> { T read(T value); } class Box implements Api<String> {} }"
        workspace(text) { adapter, uri ->
            val action = actions(adapter, uri, text).single { it.title.startsWith("Implement String read(String value)") }
            assertThat(adapter.compile(uri, apply(text, requireNotNull(action.edit).changes.getValue(uri))).diagnostics).isEmpty()
        }
    }

    @Test
    fun `CRLF indentation and Unicode text survive generation`() {
        val text = "module App {\r\n    interface Api { void read(); }\r\n    // café\r\n    class Box implements Api {\r\n    }\r\n}\r\n"
        workspace(text) { adapter, uri ->
            val action = actions(adapter, uri, text).single { it.title.startsWith("Implement ") }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).contains("    // café\r\n", "        @Override\r\n", "            TODO();\r\n")
            assertThat(changed.replace("\r\n", "")).doesNotContain("\n")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "conditional Int read();", "(Int, Int) read();", "Int read(Int value = 1);",
            "List<Int> read();", "<T> T read(T value);",
        ],
    )
    fun `unsupported signatures do not become speculative stubs`(signature: String) {
        val text = "module App { interface Api { $signature } class Box implements Api {} }"
        workspace(text) { adapter, uri -> assertThat(actions(adapter, uri, text)).isEmpty() }
    }

    @Test
    fun `existing call rebinding requires broader proof and is withheld`() {
        val text = "module App { class Base { Int read() = 1; } class Box extends Base {} Int use(Box box) = box.read(); }"
        workspace(text) { adapter, uri -> assertThat(actions(adapter, uri, text)).isEmpty() }
    }

    @Test
    fun `code generation only appears at the selected class declaration`() {
        val text = "module App { interface Api { Int read(); } class Box implements Api {} }"
        workspace(text) { adapter, uri ->
            assertThat(adapter.getCodeActions(uri, Range(Position(0, 0), Position(0, 0)), emptyList())).isEmpty()
        }
    }

    private fun actions(adapter: XdkAdapter, uri: String, text: String): List<CodeAction> {
        val offset = text.indexOf("Box")
        val at = position(text, offset)
        return adapter.getCodeActions(uri, Range(at, at), emptyList())
    }

    private fun position(text: String, offset: Int): Position =
        Position(text.take(offset).count { it == '\n' }, offset - text.lastIndexOf('\n', offset - 1) - 1)

    private fun apply(text: String, edits: List<TextEdit>): String {
        fun offset(at: Position): Int = text.split('\n').take(at.line).sumOf { it.length + 1 } + at.column
        return edits.sortedByDescending { offset(it.range.start) }.fold(text) { result, edit ->
            result.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
        }
    }

    private fun workspace(text: String, check: (XdkAdapter, String) -> Unit) {
        val source = directory.resolve("App.x").toFile().apply { writeText(text) }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val uri = source.toURI().toString()
            assertThat(adapter.compile(uri, text).diagnostics).describedAs(text).isEmpty()
            check(adapter, uri)
            assertThat(source.readText()).isEqualTo(text)
        }
    }
}
