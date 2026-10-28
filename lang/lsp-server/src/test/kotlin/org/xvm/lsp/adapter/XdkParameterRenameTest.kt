package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkParameterRenameTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `public parameter rename includes closed callers and only the selected overload`() {
        val library =
            "module Library { class Box { Int pick(Int input, Int count = 1) = input + count; " +
                "String pick(String input) = input; } }"
        val consumer =
            "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(count = 2, input = 1); }"
        val other =
            "module Other { package lib import Library; String run(lib.Box box) = box.pick(input = \"text\"); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer, "Other" to other)) { adapter ->
            assertThat(adapter.compile(uri("Library"), library).diagnostics).isEmpty()
            val edit =
                requireNotNull(adapter.rename(uri("Library"), 0, library.indexOf("input"), "value"))
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(uri("Library"), uri("Consumer"))
            assertThat(apply(library, edit, "Library"))
                .contains("Int value", "= value + count", "String pick(String input) = input")
            assertThat(apply(consumer, edit, "Consumer")).contains("count = 2, value = 1")
        }
    }

    @Test
    fun `override slot rename joins differently named declarations and named callers`() {
        val library = "module Library { interface Mapper<T> { T map(T input); } }"
        val consumer =
            "module Consumer { package lib import Library; class Mapper implements lib.Mapper<String> { " +
                "@Override String map(String argument) = argument; } " +
                "String run(lib.Mapper<String> api, Mapper impl) = api.map(input = \"a\") + impl.map(argument = \"b\"); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer)) { adapter ->
            assertThat(adapter.compile(uri("Consumer"), consumer).diagnostics).isEmpty()
            val edit =
                requireNotNull(
                    adapter.rename(uri("Consumer"), 0, consumer.indexOf("argument"), "value"),
                )
            assertThat(apply(library, edit, "Library")).contains("T map(T value)")
            assertThat(apply(consumer, edit, "Consumer"))
                .contains(
                    "String value) = value",
                    "api.map(value = \"a\")",
                    "impl.map(value = \"b\")",
                )
        }
    }

    @Test
    fun `constructor parameter rename starts at a cross-module named argument`() {
        val library =
            "module Library { class Box { construct(Int input, Int count = 1) { total = input + count; } Int total; } }"
        val consumer =
            "module Consumer { package lib import Library; lib.Box make() = new lib.Box(count = 2, input = 1); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer)) { adapter ->
            assertThat(adapter.compile(uri("Consumer"), consumer).diagnostics).isEmpty()
            val at = consumer.indexOf("input")
            assertThat(adapter.prepareRename(uri("Consumer"), 0, at)).isNotNull()
            val edit = requireNotNull(adapter.rename(uri("Consumer"), 0, at, "value"))
            assertThat(apply(library, edit, "Library"))
                .contains("construct(Int value", "total = value + count")
            assertThat(apply(consumer, edit, "Consumer")).contains("count = 2, value = 1")
        }
    }

    @Test
    fun `generic method parameter uses its visible slot and refuses local capture`() {
        val text =
            "module App { <T> T pick(T input, Int count = 1) = input; String run() = pick(count = 2, input = \"yes\"); }"
        workspace(mapOf("App" to text)) { adapter ->
            assertThat(adapter.compile(uri("App"), text).diagnostics).isEmpty()
            val edit =
                requireNotNull(adapter.rename(uri("App"), 0, text.lastIndexOf("input"), "value"))
            assertThat(apply(text, edit, "App")).contains("T value", "= value", "value = \"yes\"")
            assertThat(adapter.rename(uri("App"), 0, text.indexOf("input"), "count")).isNull()
        }
    }

    @Test
    fun `escaped method values retain their bindings during public parameter rename`() {
        val text =
            "module App { Int pick(Int input) = input; Int run() { function Int(Int) f = &pick; return f(1); } }"
        workspace(mapOf("App" to text)) { adapter ->
            assertThat(adapter.compile(uri("App"), text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri("App"), 0, text.indexOf("input"), "value"))
            assertThat(apply(text, edit, "App")).isEqualTo(text.replace("input", "value"))
        }
    }

    @Test
    fun `function values reject named arguments independently of the rename implementation`() {
        val text =
            "module App { Int pick(Int input) = input; Int run() { function Int(Int) f = &pick; return f(input = 1); } }"
        XdkAdapter().use { adapter ->
            val result = adapter.compile(uri("App"), text)
            assertThat(result.diagnostics.map { it.code }).contains("COMPILER-141")
            assertThat(adapter.rename(uri("App"), 0, text.indexOf("input"), "value")).isNull()
        }
    }

    @Test
    fun `closed consumers mix selected method values with direct named calls`() {
        val library =
            "module Library { class Box { Int pick(Int input) = input; } class Other { String pick(String input) = input; } }"
        val consumer =
            "module Consumer { package lib import Library; Int run(lib.Box box) { " +
                "function Int(Int) f = box.pick; return f(1) + box.pick(input = 2); } " +
                "String text(lib.Other box) = box.pick(input = \"text\"); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer)) { adapter ->
            assertThat(adapter.compile(uri("Consumer"), consumer).diagnostics).isEmpty()
            assertThat(adapter.compile(uri("Library"), library).diagnostics).isEmpty()
            val edit =
                requireNotNull(adapter.rename(uri("Library"), 0, library.indexOf("input"), "value"))
            assertThat(apply(library, edit, "Library"))
                .isEqualTo(library.replace("Int input) = input", "Int value) = value"))
            assertThat(apply(consumer, edit, "Consumer"))
                .isEqualTo(consumer.replace("input = 2", "value = 2"))
        }
    }

    @Test
    fun `delegated parameter slots include differently named receiver declarations`() {
        val library = "module Library { interface Api { Int read(Int input); } }"
        val consumer =
            "module Consumer { package lib import Library; " +
                "class Actual implements lib.Api { @Override Int read(Int argument) = argument; } " +
                "class Forward(Actual target) delegates lib.Api(target) {} " +
                "Int use(Forward value, Actual actual) = value.read(input = 1) + actual.read(argument = 2); }"
        workspace(mapOf("Library" to library, "Consumer" to consumer)) { adapter ->
            assertThat(adapter.compile(uri("Consumer"), consumer).diagnostics).isEmpty()
            assertThat(adapter.compile(uri("Library"), library).diagnostics).isEmpty()
            val edit =
                requireNotNull(
                    adapter.rename(uri("Library"), 0, library.indexOf("input"), "number"),
                )
            assertThat(apply(library, edit, "Library"))
                .isEqualTo(library.replace("input", "number"))
            assertThat(apply(consumer, edit, "Consumer"))
                .isEqualTo(consumer.replace("input", "number").replace("argument", "number"))
        }
    }

    private fun workspace(
        sources: Map<String, String>,
        action: (XdkAdapter) -> Unit,
    ) {
        sources.forEach { (name, text) -> directory.resolve("$name.x").toFile().writeText(text) }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            action(adapter)
            sources.forEach { (name, text) ->
                assertThat(directory.resolve("$name.x").toFile().readText()).isEqualTo(text)
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
        directory
            .resolve("$module.x")
            .toFile()
            .canonicalFile
            .toURI()
            .toString()
}
