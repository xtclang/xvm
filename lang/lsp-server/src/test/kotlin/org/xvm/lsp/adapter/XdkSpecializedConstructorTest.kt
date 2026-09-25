package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkSpecializedConstructorTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "outer.new Part(\"x\", te", "box.new(\"x\", te", "new @Tagged Box<String>(\"x\", te",
            "Box<String> result = new Box(\"x\", te", "new Box(\"x\", te", "new String[2](te",
            "outer.new @Tagged Part(\"x\", te", "outer.new Outer<String>.Part(\"x\", te",
            "new String[2](supply=te", "new @Tagged Box<String>(second=\"x\", first=te",
        ],
    )
    fun `specialized constructor arguments preserve compiler rules and source edits`(call: String) {
        val prefix = HEADER + call
        val complete = "${prefix.dropLast(2)}text); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, complete).diagnostics).describedAs(call).isEmpty()
            val cached = adapter.compile(URI, "$prefix); } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help).describedAs(call).isNotNull()
            assertThat(help!!.signatures.map { it.label }).allMatch { it.contains("String") }
            assertThat(help.signatures.map { it.activeParameter }).containsOnly(if (call.contains("first=te")) 0 else 1)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).containsExactlyInAnyOrderElementsOf(
                if (call == "new Box(\"x\", te") listOf("text", "textNumber") else listOf("text"),
            )
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            for (suggestion in items) {
                // Omitted class parameters can be inferred again as more arguments are supplied.
                assertThat(adapter.compile(URI, "${prefix.dropLast(2)}${suggestion.label}); } }").diagnostics).isEmpty()
            }
            val item = items.single { it.label == "text" }
            assertThat(item.label).isEqualTo("text")
            assertThat(item.textEdit).isEqualTo(TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), "text"))
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "module Editing { class Box<T> { construct(T first, T second) {} } Box<String> run(String text, Int textNumber) { return new Box(\"x\", te",
            "module Editing { class Outer<T> { class Part { construct(T first, T second) {} } void run(T text, Int textNumber) { new Part(text, te",
            "module Editing { class Box { construct(String first, String second) {} } <T extends Box> void run(String text, Int textNumber) { new T(\"x\", te",
        ],
    )
    fun `expected return types implicit parents and formal constructors use real contexts`(prefix: String) {
        val suffix = if (prefix.contains("class Outer")) "); } } }" else "); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "text" + suffix).diagnostics).isEmpty()
            val cached = adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures.map { it.activeParameter }).containsOnly(1)
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("text")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `array supplier supports elements without a default and rejects invalid dimensions`() {
        val header =
            "module Editing { class Item { construct(String text) {} } " +
                "void run(Item valueItem, function Item(Int) valueSupplier, Int valueNumber) { "
        XdkAdapter().use { adapter ->
            for (argument in listOf("valueItem", "valueSupplier")) {
                assertThat(adapter.compile(URI, header + "new Item[2]($argument); } }").diagnostics).isEmpty()
            }
            val prefix = header + "new Item[2](va"
            adapter.compile(URI, "$prefix); } }")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures.map { it.activeParameter }).containsOnly(1)
            assertThat(
                adapter.getCompletions(URI, 0, prefix.length).map { it.label },
            ).containsExactlyInAnyOrder("valueItem", "valueSupplier")
            for (call in listOf("new Item[True](va", "new Item[2, 3](va", "new Item[2](unknown=va")) {
                val invalid = header + call
                adapter.compile(URI, "$invalid); } }")
                assertThat(adapter.getSignatureHelp(URI, 0, invalid.length)).describedAs(call).isNull()
            }
        }
    }

    @Test
    fun `specialized constructors reject unavailable parents and inaccessible or incompatible arguments`() {
        XdkAdapter().use { adapter ->
            for (call in listOf(
                "outer.new Part(True, te",
                "box.new(True, te",
                "text.new Part(\"x\", te",
                "new Outer.Part(\"x\", te",
                "new @Missing Box<String>(\"x\", te",
            )) {
                val prefix = HEADER + call
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(call).isNull()
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(call).isEmpty()
            }
        }
    }

    @Test
    fun `anonymous construction exposes its inherited constructor`() {
        val prefix = HEADER + "new Box<String>(\"x\", te"
        val suffix = ") { String extra = \"value\"; }; } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "text" + suffix).diagnostics).isEmpty()
            adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNotNull()
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("text")
        }
    }

    @Test
    fun `constructor probes use unsaved sibling type definitions`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Member.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Box<T> { construct(T first, Int second) {} } }")
        val prefix = "class Member { void run(String text, Int textNumber) { Box<String> value = new Box(\"x\", te"
        member.writeText("$prefix); } }")
        val uri = member.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.compile(root.toURI().toString(), root.readText().replace("Int second", "T second"))
            adapter.compile(uri, member.readText())
            val cached = adapter.getCachedResult(uri)
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("text")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            adapter.compile(root.toURI().toString(), root.readText())
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("textNumber")
            assertThat(root.readText()).contains("Int second")
        }
    }

    @Test
    fun `array initializer still has a signature after the written supplier`() {
        val prefix = HEADER + "new String[2](\"supplied\""
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, "$prefix); } }")
            assertThat(cached.diagnostics).isEmpty()
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help).isNotNull()
            assertThat(help!!.signatures.single().activeParameter).isEqualTo(1)
            assertThat(help.signatures.single().label).contains("supply")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
        const val HEADER =
            "module Editing { class Box<T> { construct(T first, T second) {} } annotation Tagged into Object {} " +
                "class Outer<T> { class Part { construct(T first, T second) {} } } " +
                "void run(Outer<String> outer, Box<String> box, String text, Int textNumber) { "
    }
}
