package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.IncompleteDeclarationStatement
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkCompoundHeaderTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(List<Str§> value) {}",
            "void damaged(Map<Int, List<Str§>> value) {}",
            "void damaged(Map<Str§, Int> value) {}",
            "List<Str§> property;",
            "List<Str§> damaged() = [\"x\"] ;",
            "void damaged(Str§ | Int value) {}",
            "void damaged(Int | Str§ value) {}",
            "void damaged(Object + Str§ value) {}",
            "void damaged(Object - Str§ value) {}",
            "void damaged((Int | Str§) value) {}",
            "void damaged(List<(Int | Str§)> value) {}",
            "void damaged(List<Str§?> value) {}",
            "void damaged(List<Str§[]> value) {}",
            "void damaged(immutable List<Str§> value) {}",
        ],
    )
    fun `written leaf type prefixes complete inside parameterized and compound headers`(declaration: String) {
        val prefix = "module Headers { String StringValue=\"x\"; " + declaration.substringBefore('§')
        val suffix = declaration.substringAfter('§') + " Int later=1; }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(declaration).contains("String").doesNotContain("StringValue")
            assertThat(items.single { it.label == "String" }.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, prefix.length - 3), Position(0, prefix.length)), "String"))
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "String" + suffix).diagnostics)
                .describedAs(declaration)
                .isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["List<ecstasy.text.Str§>", "Map<Int, List<ecstasy.text.Str§>>", "Object + ecstasy.text.Str§"])
    fun `qualified leaf types use compiler lookup and replace only their final token`(type: String) {
        val prefix = "module Headers { void damaged(" + type.substringBefore('§')
        val suffix = type.substringAfter('§') + " value) {} }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + suffix)
            val item = adapter.getCompletions(URI, 0, prefix.length).single { it.label == "StringBuffer" }
            assertThat(item.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, prefix.length - 3), Position(0, prefix.length)), "StringBuffer"))
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "StringBuffer" + suffix).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(List<Str§ value) {}",
            "void damaged(Map<Int, List<Str§ value) {}",
            "void damaged(List<(Int | Str§ value) {}",
            "void damaged((Int | Str§ value) {}",
            "List<Str§ property;",
            "List<Str§ damaged() = [\"x\"];",
            "void damaged(List<Str§ {}",
        ],
    )
    fun `bounded missing type closers allow completion without hiding ordinary errors`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('§')
        val suffix = declaration.substringAfter('§') + " Int later=1; }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).describedAs(declaration).contains("String")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(cached.diagnostics).isNotEmpty()
            assertThat(adapter.compile(URI, prefix.dropLast(3) + "String" + suffix).diagnostics).isNotEmpty()
        }
    }

    @Test
    fun `a selected nested leaf at actual EOF does not require generated source`() {
        val text = "module Headers { void damaged(Map<Int, List<Str"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, text)
            assertThat(adapter.getCompletions(URI, 0, text.length).map { it.label }).contains("String")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, text.dropLast(3) + "String>> value) {} }").diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "void damaged(Lis§<String> value) {}",
            "void damaged((Int | §) value) {}",
            "void damaged(function Str§() value) {}",
            "<T> void damaged(List<Str§> value) {}",
        ],
    )
    fun `unsupported shapes do not invent a generic owner operand or formal scope`(declaration: String) {
        val prefix = "module Headers { " + declaration.substringBefore('§')
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + declaration.substringAfter('§') + " }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(declaration).isEmpty()
        }
    }

    @Test
    fun `embedding retains only the owned written leaf and preserves listener stopping`() {
        CompilerTestSupport.configure()
        val prefix = "module Headers { void damaged(Map<Int, List<ecstasy.text.Str"
        val text = "$prefix>> value) { Int hidden=1; } Int later=1; }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        val leaf = site.target as NamedTypeExpression
        assertThat(leaf.parent).isSameAs(site)
        assertThat(leaf.names).containsExactly("ecstasy", "text", "Str")
        assertThat(leaf.source.toRawString()).isEqualTo(text)
        assertThat(leaf.isValidated).isFalse()
        assertThat(leaf.nameBindings.map { it.target() }).containsOnlyNulls()
        val declaration = site.parent as IncompleteDeclarationStatement
        assertThat(declaration.component.getChild("damaged")).isNull()
        assertThat(declaration.component.getChild("hidden")).isNull()
        assertThat(analysis.cursorBindings()[site]!!.types().map { it.name() }).contains("StringBuffer")
        for (listener in listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })) {
            source.reset()
            val stopped = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener)
            assertThat(stopped.pool()).isEmpty()
            assertThat(stopped.cursorBindings()).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Headers.x"
    }
}
