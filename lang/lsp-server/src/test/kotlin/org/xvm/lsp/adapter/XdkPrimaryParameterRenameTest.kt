package org.xvm.lsp.adapter

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkPrimaryParameterRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `header label and property select the same proven rename`(occurrence: Int) {
        val text =
            "module App { class Box(Int input) {} Box make() = new Box(input = 1); Int use(Box box) = box.input; }"
        workspace(mapOf("App" to text)) { adapter ->
            val at = Regex("\\binput\\b").findAll(text).toList()[occurrence].range.first
            val edit = requireNotNull(adapter.rename(uri("App"), 0, at, "value"))
            assertThat(apply(text, edit, "App")).isEqualTo(text.replace("input", "value"))
        }
    }

    @Test
    fun `closed consumer labels use the emitted shorthand property association`() {
        val library =
            "module Library { class Box(Int input, Int count = 1) {} class Other(Int input) {} }"
        val consumer =
            "module Consumer { package lib import Library; lib.Box make() = new lib.Box(count = 2, input = 1); " +
                "Int read(lib.Box box) = box.input; lib.Other other() = new lib.Other(input = 3); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer)) { adapter ->
            val edit =
                requireNotNull(adapter.rename(uri("Library"), 0, library.indexOf("input"), "value"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(uri("Library"), uri("Consumer"))
            assertThat(apply(library, edit, "Library"))
                .contains("Box(Int value", "Other(Int input)")
            assertThat(apply(consumer, edit, "Consumer"))
                .contains("count = 2, value = 1", "box.value", "Other(input = 3)")
        }
    }

    @Test
    fun `generic shorthand properties preserve type arguments and default slots`() {
        val text =
            "module App { class Box<Element>(Element input, Int count = 1) {} " +
                "Box<String> make() = new Box<String>(input = \"yes\"); }"
        workspace(mapOf("App" to text)) { adapter ->
            val edit =
                requireNotNull(adapter.rename(uri("App"), 0, text.lastIndexOf("input"), "value"))
            assertThat(apply(text, edit, "App")).isEqualTo(text.replace("input", "value"))
        }
    }

    @Test
    fun `colliding property and captured local changes are rejected`() {
        val text =
            "module App { class Box(Int input, Int count) { Int read() { Int local = 7; return input + local; } } }"
        workspace(mapOf("App" to text)) { adapter ->
            listOf("count", "local").forEach { name ->
                assertThat(adapter.rename(uri("App"), 0, text.indexOf("input"), name)).isNull()
            }
        }
    }

    private fun workspace(
        sources: Map<String, String>,
        check: (XdkAdapter) -> Unit,
    ) {
        directory = directory.toRealPath()
        sources.forEach { (module, text) ->
            directory.resolve("$module.x").toFile().writeText(text)
        }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val first = sources.entries.first()
            assertThat(adapter.compile(uri(first.key), first.value).diagnostics).isEmpty()
            check(adapter)
            sources.forEach { (module, text) ->
                assertThat(directory.resolve("$module.x").toFile().readText()).isEqualTo(text)
            }
        }
    }

    private fun apply(
        text: String,
        edit: WorkspaceEdit,
        module: String,
    ): String =
        edit.changes
            .getValue(uri(module))
            .sortedByDescending { it.range.start.column }
            .fold(text) { value, change ->
                value.replaceRange(
                    change.range.start.column,
                    change.range.end.column,
                    change.newText,
                )
            }

    private fun uri(module: String): String =
        directory.resolve("$module.x").toFile().toURI().toString()
}
