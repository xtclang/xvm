package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import java.nio.file.Path
import java.util.concurrent.Executors

class XdkArgumentCompletionTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        ignoreLeadingAndTrailingWhitespace = false,
        value = [
            "pair(|number", "pair(1, |text", "pair(second=|text", "pair(second=\"x\", first=|number",
            "box.pair(|text", "new Box<String>(|text", "fn(|number", "fn(1, |text",
        ],
    )
    fun `missing positional and named values offer compatible visible variables`(
        call: String,
        expected: String,
    ) {
        val prefix = "$HEADER $call"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); Int later = 1; } }")
            val cached = adapter.getCachedResult(URI)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(call).containsExactly(expected)
            assertThat(items.single().textEdit).isEqualTo(
                TextEdit(Range(Position(0, prefix.length), Position(0, prefix.length)), expected),
            )
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            // Inserting the offered source name produces a real compilable call when all slots
            // are supplied; the probe itself never claims that selection.
            if (call.endsWith(", ") || call.contains("first=")) {
                assertThat(adapter.compile(URI, "$prefix$expected); } }").diagnostics).isEmpty()
            }
        }
    }

    @Test
    fun `overload alternatives contribute values without choosing a winner`() {
        val prefix = "$HEADER choose("
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactlyInAnyOrder("text", "number")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures).hasSize(2)
        }
    }

    @Test
    fun `no value is inserted into a completed unknown or incompatible argument slot`() {
        XdkAdapter().use { adapter ->
            for (call in listOf("pair(1", "pair(1, \"x\", ", "pair(unknown=", "pair(first=1, first=", "pair(True, ", "missing(")) {
                val prefix = "$HEADER $call"
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(call).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["generic(", "genericPair(\"x\", ", "widen("])
    fun `inference and conversions agree with compiling each proposed source value`(call: String) {
        val prefix = "$HEADER $call"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val offered = adapter.getCompletions(URI, 0, prefix.length).map { it.label }
            val compilable =
                listOf("number", "text", "flag", "box", "fn").filter { variable ->
                    adapter.compile(URI, "$prefix$variable); } }").diagnostics.isEmpty()
                }
            assertThat(offered).describedAs(call).isNotEmpty().containsExactlyInAnyOrderElementsOf(compilable)
            if (call == "widen(") assertThat(offered).containsExactly("number")
        }
    }

    @Test
    fun `argument values honor narrowing assignment state and lexical shadowing`() {
        XdkAdapter().use { adapter ->
            for ((setup, expected) in listOf(
                "" to listOf("text"),
                "if (value.is(String)) { " to listOf("text", "value"),
                "Int text; " to emptyList(),
                "{ String closed = \"x\"; } " to listOf("text"),
            )) {
                val prefix = "module Editing { void take(String value) {} void run(Object value, String text) { ${setup}take("
                val suffix = if (setup.startsWith("if")) "); } } }" else "); } }"
                adapter.compile(URI, prefix + suffix)
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                    .describedAs(setup)
                    .containsExactlyInAnyOrderElementsOf(expected)
            }
        }
    }

    @Test
    fun `proposed values leave original arguments diagnostics and selected call facts untouched`() {
        CompilerTestSupport.configure()
        val prefix = "$HEADER pair(second="
        val text = "$prefix); } }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        assertThat(site.arguments).isEmpty()
        assertThat(site.source.toRawString()).isEqualTo(text)
        val binding = analysis.cursorBindings()[site]!!
        assertThat(binding.argumentValues().map { it.name() }).containsExactly("text")
        assertThat(binding.candidates()).hasSize(1)
        assertThatThrownBy { (binding.argumentValues() as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        val snapshot = analysis.semanticSnapshot(errors)
        assertThat(snapshot.semantics.calls).isEmpty()
        assertThat(snapshot.semantics.functionCalls).isEmpty()
        XdkAdapter().use { adapter -> adapter.compile("untitled:Other.x", "module Other {}") }
        Executors.newSingleThreadExecutor().use { executor ->
            assertThat(
                executor
                    .submit<List<String>> {
                        snapshot.sites
                            .single()
                            .argumentValues
                            .map { it.name }
                    }.get(),
            ).containsExactly("text")
        }
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
    }

    @Test
    fun `argument completion tracks unsaved module signatures`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { void take(Int value) {} } }")
        val prefix = "class Child extends Base { void run(Int number, String text) { take("
        member.writeText("$prefix); } }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val uri = member.toURI().toString()
            adapter.compile(rootUri, root.readText().replace("Int value", "String value"))
            adapter.compile(uri, member.readText())
            val cached = adapter.getCachedResult(uri)
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("text")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("number")
            assertThat(root.readText()).contains("Int value")
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
        const val HEADER =
            "module Editing { void pair(Int first, String second) {} " +
                "void choose(Int value) {} void choose(String value) {} " +
                "<U> void generic(U value) {} <U> void genericPair(U first, U second) {} void widen(Int128 value) {} " +
                "class Box<T> { construct(T value) {} void pair(T value) {} } " +
                "void run(Int number, String text, Boolean flag, Box<String> box, function Int(Int, String) fn) { Int unread;"
    }
}
