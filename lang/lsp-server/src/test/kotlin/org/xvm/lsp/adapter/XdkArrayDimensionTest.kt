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
import org.xvm.compiler.ast.NewExpression
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkArrayDimensionTest {
    @ParameterizedTest
    @ValueSource(strings = ["new Int[nu|]", "new Int[nu|", "Int[] result = new Int[nu|]", "new String[nu|](\"x\")"])
    fun `dimension prefixes fit the size parameter and replace only the original token`(expression: String) {
        val prefix = HEADER + expression.substringBefore('|')
        val suffix = expression.substringAfter('|') + "; } }"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, prefix + suffix)
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)
            assertThat(help).describedAs(expression).isNotNull()
            assertThat(help!!.signatures.map { it.activeParameter }).containsOnly(0)
            assertThat(help.signatures.map { it.parameters.first().label }).containsOnly("Int size")
            val items = adapter.getCompletions(URI, 0, prefix.length)
            assertThat(items.map { it.label }).describedAs(expression).containsExactly("number")
            assertThat(
                items.single().textEdit,
            ).isEqualTo(TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), "number"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            val correctedSuffix = if (']' in suffix) suffix else "]$suffix"
            assertThat(adapter.compile(URI, prefix.dropLast(2) + "number" + correctedSuffix).diagnostics).isEmpty()
        }
    }

    @Test
    fun `empty size slot exposes the fixed size constructor without copy or mutability overloads`() {
        val prefix = HEADER + "new String["
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix]; } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures).hasSize(1)
            assertThat(
                help.signatures
                    .single()
                    .parameters
                    .first()
                    .label,
            ).isEqualTo("Int size")
            assertThat(help.signatures.single().activeParameter).isZero()
            val names = adapter.getCompletions(URI, 0, prefix.length).map { it.label }
            assertThat(names).contains("number").doesNotContain("numberText", "numberArray")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["new String[\"x\"|]", "new String[2, nu|]", "new Missing[nu|]"])
    fun `invalid dimensions do not claim an applicable constructor`(expression: String) {
        val prefix = HEADER + expression.substringBefore('|')
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + expression.substringAfter('|') + "; } }")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(expression).isNull()
            assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(expression).isEmpty()
        }
    }

    @Test
    fun `a prefix before another dimension keeps lexical completion without constructor fitting`() {
        val prefix = HEADER + "new Int[nu"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix, 2]; } }")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .containsExactlyInAnyOrder("number", "numberText", "numberArray")
        }
    }

    @Test
    fun `dimensions use compiler readability narrowing and property validation`() {
        val prefix =
            "module Dimensions { Int countProperty=2; String countText=\"x\"; " +
                "void run(Int? countMaybe, Int countParameter) { Int countUnassigned; " +
                "if (countMaybe != Null) { new Int[co"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix]; } } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label })
                .containsExactlyInAnyOrder("countMaybe", "countParameter", "countProperty")
            val changed = prefix.replace("Int countProperty", "String countProperty").replace("=2;", "=\"two\";")
            adapter.compile(URI, "$changed]; } } }")
            assertThat(adapter.getCompletions(URI, 0, changed.length).map { it.label })
                .containsExactlyInAnyOrder("countMaybe", "countParameter")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["new Int[word.si|]", "new Int[word.si|"])
    fun `member cursor inside a dimension retains its own receiver`(expression: String) {
        val prefix = "module Dimensions { void run(String word) { " + expression.substringBefore('|')
        XdkAdapter().use { adapter ->
            adapter.compile(URI, prefix + expression.substringAfter('|') + "; } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).contains("size")
        }
    }

    @Test
    fun `complete numeric size fits while an incompatible literal is rejected`() {
        val prefix = HEADER + "new Int[2"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix]; } }")
            assertThat(
                adapter
                    .getSignatureHelp(URI, 0, prefix.length)!!
                    .signatures
                    .single()
                    .activeParameter,
            ).isZero()
        }
    }

    @Test
    fun `embedding dimension query preserves source ownership and reports its hole once`() {
        CompilerTestSupport.configure()
        val prefix = HEADER + "new String[nu"
        val text = "$prefix]; } Int later = 42; }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        assertThat(site.isCall).isTrue()
        assertThat(site.source.toRawString()).isEqualTo(text)
        assertThat((site.target as NewExpression).sourceBindings).isNull()
        assertThat(site.target.parent).isSameAs(site)
        assertThat(site.leadingArguments).isEmpty()
        assertThat(analysis.cursorBindings()[site]!!.candidates()).hasSize(1)
        assertThat(analysis.cursorBindings()[site]!!.argumentValues().map { it.name() }).containsExactly("number")
        for (listener in listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })) {
            source.reset()
            val stopped = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener)
            assertThat(stopped.pool()).isEmpty()
            assertThat(stopped.cursorBindings()).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Dimensions.x"
        const val HEADER = "module Dimensions { void run(Int number, String numberText, String[] numberArray) { "
    }
}
