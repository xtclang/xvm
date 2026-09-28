package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/** Explicit refusal and scope boundaries; compiling a fixture alone is not evidence for rename. */
class XdkRenameBoundaryTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int use() { function Int(Int) f = &pick; return f(1); }",
            "function Int(Int) saved() = &pick;",
            "Int consume(function Int(Int) f) = f(1); Int use() = consume(&pick);",
        ],
    )
    fun `stored returned and passed method values retain positional invocation`(use: String) {
        val text = "module App { Int pick(Int input) = input; $use }"
        workspace(text) { adapter, uri ->
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("input"), "value"))
            val changed =
                edit.changes.getValue(uri).sortedByDescending { it.range.start.column }.fold(text) { value, change ->
                    value.replaceRange(change.range.start.column, change.range.end.column, change.newText)
                }
            assertThat(changed).isEqualTo(text.replace("input", "value"))
        }
    }

    @Test
    fun `union receiver dispatch does not become a guessed method family`() {
        val text =
            "module App { class First { Int read() = 1; } class Second { Int read() = 2; } " +
                "Int use(First | Second target) = target.read(); }"
        workspace(text) { adapter, uri -> assertThat(adapter.rename(uri, 0, text.indexOf("read"), "fetch")).isNull() }
    }

    @Test
    fun `an explicit graph does not claim or edit omitted consumers`() {
        directory = directory.toRealPath()
        val text = "module App { class Box { Int pick(Int input) = input; } }"
        val consumer = "module Consumer { package lib import App; Int run(lib.Box box) = box.pick(input = 1); }"
        val outside = directory.resolve("Consumer.x").toFile().apply { writeText(consumer) }
        val source = directory.resolve("App.x").toFile().apply { writeText(text) }
        XdkAdapter().use { adapter ->
            val uri = source.toURI().toString()
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri)))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("pick"), "choose"))
            assertThat(edit.changes.keys).containsExactly(uri)
            assertThat(outside.readText()).isEqualTo(consumer)
            // Registering the omitted consumer changes the proof boundary and includes its call.
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("App", uri), XdkSourceModule("Consumer", outside.toURI().toString(), setOf("App"))),
            )
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            assertThat(requireNotNull(adapter.rename(uri, 0, text.indexOf("pick"), "choose")).changes.keys)
                .containsExactlyInAnyOrder(uri, outside.toURI().toString())
        }
    }

    private fun workspace(
        text: String,
        check: (XdkAdapter, String) -> Unit,
    ) {
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
