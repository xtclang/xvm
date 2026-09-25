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

class XdkPropertyArgumentCompletionTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "take(|valueNumber", "take(va|valueNumber", "take(value=va|valueNumber",
            "widen(va|valueNumber", "box.take(va|valueText", "new Box<String>(va|valueText", "fn(va|valueText",
        ],
    )
    fun `property argument values use compiler fitting and exact edits`(
        call: String,
        expected: String,
    ) {
        val header =
            "module Editing { " +
                "Int valueNumber=1; String valueText=\"x\"; Boolean valueFlag=True; " +
                "void take(Int value) {} void widen(Int128 value) {} " +
                "class Box<T> { construct(T value) {} void take(T value) {} } " +
                "void run(Box<String> box, function void(String) fn) { "
        val prefix = "$header$call"
        val typed = if (call.endsWith("va")) "va" else ""
        XdkAdapter().use { adapter ->
            val original = "$prefix); } }"
            val cached = adapter.compile(URI, original)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(call).containsExactly(expected)
            val item = items.single()
            assertThat(item.kind).isEqualTo(CompletionItem.CompletionKind.PROPERTY)
            assertThat(item.textEdit).isEqualTo(
                TextEdit(Range(Position(0, prefix.length - typed.length), Position(0, prefix.length)), expected),
            )
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(
                adapter.compile(URI, original.replaceRange(prefix.length - typed.length, prefix.length, expected)).diagnostics,
            ).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "static "])
    fun `implicit reads respect inherited access and availability of this`(modifier: String) {
        val prefix =
            "module Editing { class Base { protected Int valueProtected=1; private Int valueHidden=2; } " +
                "class Child extends Base { private Int valueOwn=3; static Int valueConstant=4; static String valueText=\"x\"; " +
                "static void take(Int value) {} ${modifier}void run() { take(va"
        XdkAdapter().use { adapter ->
            val text = "$prefix); } } }"
            adapter.compile(URI, text)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            val expected = if (modifier.isEmpty()) listOf("valueConstant", "valueOwn", "valueProtected") else listOf("valueConstant")
            assertThat(items.map { it.label }).containsExactlyInAnyOrderElementsOf(expected)
            for (item in items) {
                assertThat(adapter.compile(URI, text.replaceRange(prefix.length - 2, prefix.length, item.label)).diagnostics).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int value;", "String value=\"x\";", "Int value=2;"])
    fun `unreadable or incompatible locals still shadow properties`(local: String) {
        val prefix = "module Editing { Int value=1; void take(Int input) {} void run() { $local take(va"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            if (local == "Int value=2;") {
                assertThat(items.map { it.label }).containsExactly("value")
                assertThat(items.single().kind).isEqualTo(CompletionItem.CompletionKind.VARIABLE)
            } else {
                assertThat(items).isEmpty()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "if (value.is(String)) { "])
    fun `property completion does not invent local variable narrowing`(guard: String) {
        val prefix = "module Editing { Object value=\"x\"; void take(String text) {} void run() { $guard take(va"
        val suffix = if (guard.isEmpty()) "); } }" else "); } } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, "${prefix.dropLast(2)}value$suffix").diagnostics.map { it.code }).contains("COMPILER-150")
            adapter.compile(URI, "$prefix$suffix")
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).isEmpty()
        }
    }

    @Test
    fun `generic inherited property has its receiver substitution`() {
        val prefix =
            "module Editing { class Base<T> { T value; } class Child extends Base<String> { " +
                "void take(String text) {} void run() { take(va"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } } }")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).containsExactly("value")
            assertThat(items.single().detail).contains("String")
            assertThat(adapter.compile(URI, "${prefix.dropLast(2)}value); } } }").diagnostics).isEmpty()
        }
    }

    @Test
    fun `properties preserve overload alternatives`() {
        val prefix =
            "module Editing { Int valueNumber=1; String valueText=\"x\"; Boolean valueFlag=True; " +
                "void choose(Int value) {} void choose(String value) {} void run() { choose(va"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactlyInAnyOrder("valueNumber", "valueText")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)!!.signatures).hasSize(2)
        }
    }

    @Test
    fun `formal type properties retain their semantic kind as argument values`() {
        val prefix = "module Editing { class Box<Value> { void take(Type value) {} void run() { take(Va"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } } }")
            val item = adapter.getCompletions(URI, 0, prefix.length).single()
            assertThat(item.label).isEqualTo("Value")
            assertThat(item.kind).isEqualTo(CompletionItem.CompletionKind.CLASS)
            assertThat(item.detail).contains("Type")
            assertThat(adapter.compile(URI, "${prefix.dropLast(2)}Value); } } }").diagnostics).isEmpty()
        }
    }

    @Test
    fun `property values follow unsaved dependency types and keep source identity`() {
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { Int value=1; } }")
        val prefix = "class Child extends Base { void take(String text) {} void run() { take(va"
        member.writeText("$prefix); } }")
        XdkAdapter().use { adapter ->
            val rootUri = root.toURI().toString()
            val uri = member.toURI().toString()
            adapter.compile(rootUri, root.readText())
            adapter.compile(uri, member.readText())
            assertThat(adapter.getCompletions(uri, 0, prefix.length)).isEmpty()
            adapter.compile(rootUri, root.readText().replace("Int value=1", "String value=\"x\""))
            val cached = adapter.getCachedResult(uri)
            assertThat(adapter.getCompletions(uri, 0, prefix.length).single().detail).contains("String")
            assertThat(adapter.getCachedResult(uri)).isEqualTo(cached)
            assertThat(adapter.compile(uri, "${prefix.dropLast(2)}value); } }").diagnostics).isEmpty()
            assertThat(adapter.findDefinition(uri, 0, prefix.length - 1)!!.uri).isEqualTo(rootUri)
            adapter.compile(rootUri, root.readText())
            adapter.compile(uri, member.readText())
            assertThat(adapter.getCompletions(uri, 0, prefix.length)).isEmpty()
            assertThat(root.readText()).contains("Int value=1")
        }
    }

    @Test
    fun `accepted property facts are immutable detached and do not change syntax or diagnostics`() {
        CompilerTestSupport.configure()
        val prefix = "module Editing { String value=\"x\"; void take(String text) {} void run() { take(va"
        val text = "$prefix); } }"
        val source = Source(text)
        val cursor = Source(text).apply { repeat(prefix.length) { next() } }.position
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        val site = analysis.sites().single()
        val binding = analysis.cursorBindings()[site]!!
        assertThat(binding.argumentValues()).isEmpty()
        assertThat(binding.argumentProperties().map { it.name() }).containsExactly("value")
        assertThatThrownBy { (binding.argumentProperties() as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(
            binding
                .withTypes(emptyList())
                .withCandidates(emptyList())
                .withFunctions(emptyList())
                .withArgumentValues(emptyList())
                .argumentProperties(),
        ).isEqualTo(binding.argumentProperties())
        assertThat(site.arguments).isEmpty()
        assertThat(source.toRawString()).isEqualTo(text)
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
            ).containsExactly("value")
        }
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
