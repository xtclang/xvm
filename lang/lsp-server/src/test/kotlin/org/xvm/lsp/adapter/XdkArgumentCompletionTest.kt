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

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "pair(nu|number", "pair(1, te|text", "pair(second=te|text", "pair(second=\"x\", first=nu|number",
            "box.pair(te|text", "new Box<String>(te|text", "new Box<String>(value=te|text", "fn(nu|number", "fn(1, te|text",
        ],
    )
    fun `typed argument prefixes offer fitting values and replace exactly their token`(
        call: String,
        expected: String,
    ) {
        val prefix = "$HEADER Int textNumber=1; String numberText=\"x\"; $call"
        XdkAdapter().use { adapter ->
            val original = "$prefix); Int later=1; } }"
            adapter.compile(URI, original)
            val cached = adapter.getCachedResult(URI)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(call).containsExactly(expected)
            assertThat(items.single().textEdit).isEqualTo(
                TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), expected),
            )
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures).hasSize(1)
            if (call !in listOf("pair(nu", "pair(second=te", "fn(nu")) {
                assertThat(adapter.compile(URI, original.replaceRange(prefix.length - 2, prefix.length, expected)).diagnostics).isEmpty()
            }
        }
    }

    @Test
    fun `typed prefix preserves fitting overload alternatives and rejects other same prefix values`() {
        val prefix = "$HEADER Int valueNumber=1; String valueText=\"x\"; Boolean valueFlag=True; choose(va"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactlyInAnyOrder("valueNumber", "valueText")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures).hasSize(2)
        }
    }

    @Test
    fun `no value is inserted into a completed unknown or incompatible argument slot`() {
        XdkAdapter().use { adapter ->
            for (call in listOf(
                "pair(1",
                "pair(1, \"x\", ",
                "pair(unknown=",
                "pair(first=1, first=",
                "pair(True, ",
                "missing(",
                "pair(unknown=te",
                "pair(first=1, first=nu",
                "pair(True, te",
                "pair(1, missing",
                "missing(te",
            )) {
                val prefix = "$HEADER $call"
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(call).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["generic(", "genericPair(\"x\", ", "widen(", "generic(t", "genericPair(\"x\", t", "widen(n"])
    fun `inference and conversions agree with compiling each proposed source value`(call: String) {
        val prefix = "$HEADER $call"
        val typed = call.substringAfterLast('(').substringAfterLast(',').trim()
        val before = prefix.dropLast(typed.length)
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val offered = adapter.getCompletions(URI, 0, prefix.length).map { it.label }
            val compilable =
                listOf("number", "text", "flag", "box", "fn")
                    .filter { it.startsWith(typed) }
                    .filter { variable -> adapter.compile(URI, "$before$variable); } }").diagnostics.isEmpty() }
            assertThat(offered).describedAs(call).isNotEmpty().containsExactlyInAnyOrderElementsOf(compilable)
            if (call == "widen(") assertThat(offered).containsExactly("number")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "v", "t"])
    fun `argument values honor narrowing assignment state and lexical shadowing`(typed: String) {
        XdkAdapter().use { adapter ->
            for ((setup, expected) in listOf(
                "" to listOf("text"),
                "if (value.is(String)) { " to listOf("text", "value"),
                "Int text; " to emptyList(),
                "{ String closed = \"x\"; } " to listOf("text"),
            )) {
                val prefix = "module Editing { void take(String value) {} void run(Object value, String text) { ${setup}take($typed"
                val suffix = if (setup.startsWith("if")) "); } } }" else "); } }"
                adapter.compile(URI, prefix + suffix)
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                    .describedAs(setup)
                    .containsExactlyInAnyOrderElementsOf(expected.filter { it.startsWith(typed) })
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "te"])
    fun `proposed values leave original arguments diagnostics and selected call facts untouched`(typed: String) {
        CompilerTestSupport.configure()
        val prefix = "$HEADER pair(second=$typed"
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
        val copied = snapshot.sites.single()
        assertThat(copied.memberPrefix!!.text).isEqualTo(typed)
        assertThat(copied.memberPrefix.range.start.column).isEqualTo(prefix.length - typed.length)
        assertThat(copied.memberPrefix.range.end.column).isEqualTo(prefix.length)
        assertThat(copied.pendingArgumentName).isEqualTo("second")
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

    @ParameterizedTest
    @ValueSource(strings = ["", "value"])
    fun `argument completion tracks unsaved module signatures`(typed: String) {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { void take(Int value) {} } }")
        val prefix = "class Child extends Base { void run(Int valueNumber, String valueText) { take($typed"
        member.writeText("$prefix); } }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val uri = member.toURI().toString()
            adapter.compile(rootUri, root.readText().replace("Int value", "String value"))
            adapter.compile(uri, member.readText())
            val cached = adapter.getCachedResult(uri)
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("valueText")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            adapter.compile(rootUri, root.readText())
            assertThat(adapter.getCompletions(uri, 0, prefix.length).map { it.label }).containsExactly("valueNumber")
            assertThat(root.readText()).contains("Int value")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["te", "\\u0074e"])
    fun `prefix edits use original UTF16 ranges with CRLF and escaped identifiers`(typed: String) {
        val line = "void run(String text, Int textNumber) { /* 😀 */ take(value=$typed"
        val header = "module Editing {\r\nvoid take(String value) {}\r\n"
        XdkAdapter().use { adapter ->
            val original = "$header$line); } }"
            val cached = adapter.compile(URI, original)
            val edit = adapter.getCompletions(URI, 2, line.length).single().textEdit!!
            assertThat(edit).isEqualTo(TextEdit(Range(Position(2, line.length - typed.length), Position(2, line.length)), "text"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, "$header${line.dropLast(typed.length)}${edit.newText}); } }").diagnostics).isEmpty()
        }
    }

    @Test
    fun `member compound and earlier argument prefixes retain their ordinary completion context`() {
        XdkAdapter().use { adapter ->
            for ((call, suffix, expected) in listOf(
                Triple("pair(nu", ", text)", "numberText"),
                Triple("pair(1, (te", "))", "textNumber"),
                Triple("pair(1 + nu", ", text)", "numberText"),
                Triple("pair(1, text.si", ")", "size"),
            )) {
                val prefix = "$HEADER Int textNumber=1; String numberText=\"x\"; $call"
                adapter.compile(URI, "$prefix$suffix; } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).describedAs(call).contains(expected)
            }
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
