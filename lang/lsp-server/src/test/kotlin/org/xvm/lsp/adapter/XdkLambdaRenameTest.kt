package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkLambdaRenameTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["(Int input) -> input", "input -> input"])
    fun `typed and inferred parameters rename from declaration or use`(lambda: String) {
        val text = "module App { Int run() { function Int(Int) f = $lambda; return f(1); } }"
        workspace(text) { adapter, uri ->
            Regex("\\binput\\b").findAll(text).forEach { match ->
                assertThat(adapter.prepareRename(uri, 0, match.range.first)?.placeholder).isEqualTo("input")
                val edit = requireNotNull(adapter.rename(uri, 0, match.range.first, "value"))
                assertThat(apply(text, edit, uri)).isEqualTo(text.replace("input", "value"))
            }
        }
    }

    @Test
    fun `nested captures retain the parameter while sibling bindings stay unchanged`() {
        val text =
            "module App { Int run() { function Int(Int) f = (Int input) -> { " +
                "function Int() nested = () -> input; return nested(); }; " +
                "function Int(Int) g = (Int input) -> input; return f(1) + g(2); } }"
        workspace(text) { adapter, uri ->
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("input"), "value"))
            assertThat(
                apply(text, edit, uri),
            ).isEqualTo(text.replace("f = (Int input)", "f = (Int value)").replace("() -> input", "() -> value"))
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "function Int(Int) saved() = (Int input) -> input;",
            "Int consume(function Int(Int) f) = f(1); Int run() = consume((Int input) -> input);",
        ],
    )
    fun `returned and passed lambdas keep local parameter names`(body: String) {
        val text = "module App { $body }"
        workspace(text) { adapter, uri ->
            assertThat(apply(text, requireNotNull(adapter.rename(uri, 0, text.indexOf("input"), "value")), uri))
                .isEqualTo(text.replace("input", "value"))
        }
    }

    @Test
    fun `lambda rename rejects capture even if the resulting code still compiles`() {
        val text = "module App { Int run() { Int value = 7; function Int(Int) f = (Int input) -> input + value; return f(1); } }"
        workspace(text) { adapter, uri ->
            listOf("value", "return", "bad name").forEach { name ->
                assertThat(adapter.rename(uri, 0, text.indexOf("input"), name)).isNull()
            }
            assertThat(adapter.getCachedResult(uri)?.diagnostics).isEmpty()
        }
    }

    @Test
    fun `standalone compilation needs no generated callable identity for a lambda`() {
        val text = "module App { function Int(Int) saved() = input -> input; }"
        XdkAdapter().use { adapter ->
            val uri =
                directory
                    .resolve("App.x")
                    .toFile()
                    .toURI()
                    .toString()
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            assertThat(apply(text, requireNotNull(adapter.rename(uri, 0, text.indexOf("input"), "value")), uri))
                .isEqualTo(text.replace("input", "value"))
        }
    }

    private fun workspace(
        text: String,
        check: (XdkAdapter, String) -> Unit,
    ) {
        directory = directory.toRealPath()
        val file = directory.resolve("App.x").toFile().apply { writeText(text) }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val uri = file.toURI().toString()
            assertThat(adapter.compile(uri, text).diagnostics).describedAs(text).isEmpty()
            check(adapter, uri)
            assertThat(file.readText()).isEqualTo(text)
        }
    }

    private fun apply(
        text: String,
        edit: WorkspaceEdit,
        uri: String,
    ): String =
        edit.changes.getValue(uri).sortedByDescending { it.range.start.column }.fold(text) { value, change ->
            value.replaceRange(change.range.start.column, change.range.end.column, change.newText)
        }
}
