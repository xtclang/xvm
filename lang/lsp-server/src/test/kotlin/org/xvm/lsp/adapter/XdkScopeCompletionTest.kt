package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkScopeCompletionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `an empty statement cursor offers parameters without changing the source`() {
        val prefix = "module Editing { void run(String item) { "
        XdkAdapter().use { adapter ->
            val original = adapter.compile(URI, "$prefix} }")
            assertThat(original.diagnostics).isEmpty()
            val item = adapter.getCompletions(URI, 0, prefix.length).single { it.label == "item" }
            assertThat(item.textEdit!!.range).isEqualTo(Range(Position(0, prefix.length), Position(0, prefix.length)))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(original)
        }
    }

    @Test
    fun `bare names expose visible parameters and preceding assigned locals`() {
        val prefix = "module Editing { void run(String itemParameter) { Int itemLocal = 1; Int itemUnassigned; item"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; Int itemLater = 2; } }")
            val cached = adapter.getCachedResult(URI)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).containsExactlyInAnyOrder("itemParameter", "itemLocal")
            assertThat(items.single { it.label == "itemParameter" }.detail).isEqualTo("String itemParameter")
            assertThat(items.single { it.label == "itemLocal" }.detail).isEqualTo("Int itemLocal")
            assertThat(items).allSatisfy {
                assertThat(it.kind).isEqualTo(CompletionItem.CompletionKind.VARIABLE)
                assertThat(it.textEdit!!.range).isEqualTo(Range(Position(0, prefix.length - 4), Position(0, prefix.length)))
            }
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `completion uses flow narrowed variables in the selected branch`() {
        val prefix = "module Editing { void run(Object item) { if (item.is(String)) { ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).single { it.label == "item" }.detail).isEqualTo("String item")
        }
    }

    @Test
    fun `locals from a closed block are absent and local declarations shadow properties`() {
        val prefix = "module Editing { String item = \"outer\"; void run() { { Int itemClosed = 1; } Int item = 2; ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).containsExactly("item")
            assertThat(items.single().detail).isEqualTo("Int item")
        }
    }

    @Test
    fun `scope completion retains returns and assignment value context`() {
        XdkAdapter().use { adapter ->
            for (statement in listOf("return ite", "Int result = ite", "Int item = ite")) {
                val prefix = "module Editing { Int run(Int item) { $statement"
                adapter.compile(URI, "$prefix; } }")
                val items = adapter.getCompletions(URI, 0, prefix.length)
                if (statement.startsWith("Int item")) {
                    assertThat(items).isEmpty()
                } else {
                    assertThat(items.map { it.label }).describedAs(statement).contains("item")
                    assertThat(items.single { it.label == "item" }.detail).isEqualTo("Int item")
                }
            }
        }
    }

    @Test
    fun `implicit members preserve generic substitution and local shadowing`() {
        val prefix = "module Editing { class Box<T> { T item; private T itemMethod() = item; void run(T itemLocal) { ite"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).contains("item", "itemMethod", "itemLocal")
            assertThat(items.single { it.label == "itemMethod" }.detail).contains("T itemMethod()")
        }
    }

    @Test
    fun `static receivers expose functions constants and nested types with access checks`() {
        val prefix =
            "module Editing { class Box { static Int itemFunction() = 1; static Int itemConstant = 2; " +
                "Int itemProperty = 3; Int itemMethod() = 4; private static Int itemHidden() = 5; " +
                "static class itemType {} private static class itemPrivate {} } void run() { Box.item"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label })
                .contains("itemFunction", "itemConstant", "itemType")
                .doesNotContain("itemProperty", "itemMethod", "itemHidden", "itemPrivate")
            assertThat(items.single { it.label == "itemType" }.kind).isEqualTo(CompletionItem.CompletionKind.CLASS)
        }
    }

    @Test
    fun `a static function has no implicit instance members`() {
        val prefix =
            "module Editing { class Box { Int itemProperty = 1; Int itemMethod() = 2; " +
                "static Int itemFunction() = 3; static void run() { item"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).contains("itemFunction").doesNotContain("itemProperty", "itemMethod")
        }
    }

    @Test
    fun `type names come from contextual lookup including imports and enclosing types`() {
        XdkAdapter().use { adapter ->
            for ((setup, name) in listOf(
                "" to "String",
                "import ecstasy.text.StringBuffer as Buffer;" to "Buffer",
                "import ecstasy.text.*;" to "StringBuffer",
                "class ItemType {}" to "ItemType",
            )) {
                val prefix = "module Editing { $setup class Nested { void run() { ${name.dropLast(1)}"
                adapter.compile(URI, "$prefix; } } }")
                val items = adapter.getCompletions(URI, 0, prefix.length)
                assertThat(items.map { it.label }).describedAs(setup).contains(name)
            }
        }
    }

    @Test
    fun `implicit member completion follows unsaved module overlays and root invalidation`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val child = directory.resolve("Editing/Child.x").toFile().canonicalFile
        child.parentFile.mkdirs()
        root.writeText("module Editing { class Base { Int item = 1; } }")
        val prefix = "class Child extends Base { void run() { ite"
        child.writeText("$prefix; } }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val childUri = child.toURI().toString()
            adapter.compile(rootUri, "module Editing { class Base { String item = \"overlay\"; } }")
            adapter.compile(childUri, child.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).single { it.label == "item" }.detail).isEqualTo("String item")
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(childUri, 0, prefix.length).single { it.label == "item" }.detail).isEqualTo("Int item")
            assertThat(root.readText()).contains("Int item")
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
