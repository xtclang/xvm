package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkGenericHeaderTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(List<§> value) {}",
            "void damaged(Map<Int, §> value) {}",
            "void damaged(Map<§, Int> value) {}",
            "void damaged(Map<Int, List<§>> value) {}",
            "void damaged(List<List<List<§>>> value) {}",
            "List<§> property;",
            "List<§> damaged() = [\"x\"];",
            "interface Damaged extends List<§> {}",
        ],
    )
    fun `empty generic slots insert visible types at the cursor`(declaration: String) {
        completion("module Headers { $declaration }", "String", 0, 0)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(Str§ing value) {}",
            "void damaged(List<Str§ing> value) {}",
            "void damaged(Map<Int, ecstasy.text.Str§ingBuffer> value) {}",
            "void damaged((Int | Str§ing) value) {}",
            "interface Damaged extends List<Str§ing> {}",
        ],
    )
    fun `mid token queries replace the entire final identifier`(declaration: String) {
        val qualified = declaration.contains("ecstasy.text")
        completion("module Headers { $declaration }", if (qualified) "StringBuffer" else "String", 3, if (qualified) 9 else 3)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(Ele§ value) {}",
            "void damaged(List<Ele§> value) {}",
            "void damaged(List<§> value) {}",
            "Ele§ property;",
            "class Nested { void damaged(Ele§ value) {} }",
        ],
    )
    fun `registered class formals are type candidates in their real enclosing scope`(declaration: String) {
        completion("module Headers { class Container<Element> { $declaration } }", "Element", if (declaration.contains("Ele")) 3 else 0, 0)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Owner<String>.Ite§",
            "Owner<String>.Ite§m",
            "Owner<List<String>>.Ite§",
            "Owner<String>.Nested.Ite§",
            "Owner<String>.Ali§",
        ],
    )
    fun `parameterized qualifiers preserve compiler type substitution`(type: String) {
        val alias = type.contains("Ali")
        completion(
            "module Headers { class Owner<Element> { class Item {} static class Nested { static class Item {} } " +
                "typedef Element as Alias; } void damaged($type value) {} }",
            if (alias) "Alias" else "Item",
            3,
            if (type.endsWith("§m")) 1 else 0,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Missing<String>.Ite§", "Owner<Missing>.Ite§", "Value<String>.Ite§", "Owner<String>.Hid§"])
    fun `invalid or inaccessible parameterized qualifiers never fall back to local names`(type: String) {
        val marked =
            "module Headers { class ItemOutside {} class HiddenOutside {} String Value=\"x\"; " +
                "class Owner<Element> { class Item {} private class Hidden {} } void damaged($type value) {} }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§'))).isEmpty()
        }
    }

    @Test
    fun `a valid generic method exposes its formal as a type instead of a local value`() {
        val marked = "module Headers { <Element> void use(Element value) { Ele§; } }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            val candidates = adapter.getCompletions(URI, 0, marked.indexOf('§')).filter { it.label == "Element" }
            assertThat(candidates).hasSize(1)
            assertThat(candidates.single().kind).isEqualTo(CompletionItem.CompletionKind.CLASS)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "Type<String>", "Type<Other>"])
    fun `a value shadowing a formal keeps its value completion`(type: String) {
        val marked = "module Headers { class Container<Element, Other> { void use($type Element) { Ele§; } } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, marked.replace("Ele§;", "return;")).diagnostics).isEmpty()
            adapter.compile(URI, marked.replace("§", ""))
            val item = adapter.getCompletions(URI, 0, marked.indexOf('§')).single { it.label == "Element" }
            assertThat(item.kind).isEqualTo(CompletionItem.CompletionKind.VARIABLE)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["List<§", "Map<Int, §", "Map<Int, List<§"])
    fun `an empty generic slot at EOF keeps the source diagnostic`(type: String) {
        val text = "module Headers { void damaged(" + type.substringBefore('§')
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, text)
            assertThat(adapter.getCompletions(URI, 0, text.length).map { it.label }).contains("String")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(cached.diagnostics).isNotEmpty()
        }
    }

    @Test
    fun `parameterized query facts retain no qualifier binding or validation on source syntax`() {
        CompilerTestSupport.configure()
        val marked = "module Headers { class Owner<Element> { typedef Element as Alias; } void damaged(Owner<String>.Ali§ value) {} }"
        val text = marked.replace("§", "")
        val source = Source(text, URI)
        repeat(marked.indexOf('§')) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        val site = analysis.sites().single()
        val syntax = site.target as NamedTypeExpression
        assertThat(syntax.isValidated).isFalse()
        assertThat(syntax.nameBindings.map { it.target() }).containsOnlyNulls()
        val candidate = analysis.cursorBindings()[site]!!.types().single { it.name() == "Alias" }
        assertThat(candidate.type().valueString).isEqualTo("String")
        assertThat(source.toRawString()).isEqualTo(text)
        val stopped = EmbeddingSupport.instance().analyzeIncomplete(source.clone(), cursor, null, ErrorList(1))
        assertThat(stopped.cursorBindings()).isEmpty()
        val cancelled = ErrorListener.cancellable(ErrorList()) { true }
        assertThat(EmbeddingSupport.instance().analyzeIncomplete(source.clone(), cursor, null, cancelled).cursorBindings()).isEmpty()
    }

    private fun completion(
        marked: String,
        selected: String,
        before: Int,
        after: Int,
    ) {
        val at = marked.indexOf('§')
        val text = marked.replace("§", "")
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, text)
            val item = adapter.getCompletions(URI, 0, at).single { it.label == selected }
            assertThat(item.kind).isEqualTo(CompletionItem.CompletionKind.CLASS)
            assertThat(item.textEdit).isEqualTo(TextEdit(Range(Position(0, at - before), Position(0, at + after)), selected))
            assertThat(adapter.getSignatureHelp(URI, 0, at)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, text.take(at - before) + selected + text.substring(at + after)).diagnostics)
                .describedAs(marked)
                .isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
