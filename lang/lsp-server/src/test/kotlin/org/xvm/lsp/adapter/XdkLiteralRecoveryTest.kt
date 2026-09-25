package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkLiteralRecoveryTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "(1, value.si|)", "Tuple<Int, Int>:(1, value.si|)", "[value.si|]", "List<Int>:[value.si|]",
            "Set<Int>:[value.si|]", "[\"key\"=value.si|]", "Map<String, Int>:[\"key\"=value.si|]", "[(1, value.si|)]",
        ],
    )
    fun `missing literal closers preserve member completion and following declarations`(expression: String) {
        val prefix = "module Editing { Object run(String value) { return " + expression.substringBefore('|')
        val suffix = "; } Int later() = 42; }"
        XdkAdapter().use { adapter ->
            val complete = prefix.dropLast(2) + "size" + expression.substringAfter('|') + suffix
            assertThat(adapter.compile(URI, complete).diagnostics).describedAs(complete).isEmpty()
            val cached = adapter.compile(URI, prefix + suffix)
            assertThat(cached.diagnostics).isNotEmpty()
            val item = adapter.getCompletions(URI, 0, prefix.length).single { it.label == "size" }
            assertThat(item.textEdit).isEqualTo(TextEdit(Range(Position(0, prefix.length - 2), Position(0, prefix.length)), "size"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int run(String value) = value.si|;",
            "Int size = \"x\".si|;",
            "void run(Int size = Int64.Ma|) {}",
            "class Holder(Int size = \"x\".si|) {}",
        ],
    )
    fun `declaration values retain their context with missing terminators`(declaration: String) {
        val prefix = "module Editing { " + declaration.substringBefore('|')
        val closing = declaration.substringAfter('|')
        XdkAdapter().use { adapter ->
            assertThat(
                adapter
                    .compile(
                        URI,
                        prefix.dropLast(2) + (if (declaration.contains("Int64")) "MaxValue" else "size") + closing + " }",
                    ).diagnostics,
            ).isEmpty()
            val cached = adapter.compile(URI, prefix + closing.drop(1) + " }")
            val source = Source(prefix + closing.drop(1) + " }", URI)
            repeat(prefix.length) { source.next() }
            val cursor = source.position
            source.reset()
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, errors)
            assertThat(analysis.cursorBindings())
                .describedAs("%s: %s", declaration, errors.errors.map { it.code })
                .isNotEmpty()
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            assertThat(
                analysis
                    .sites()
                    .single()
                    .source
                    .toRawString(),
            ).isEqualTo(source.toRawString())
            val owner =
                generateSequence(analysis.sites().single().parent) { it.parent }
                    .filterIsInstance<MethodDeclarationStatement>()
                    .firstOrNull()
            if (owner != null) assertThat((owner.component as MethodStructure).ast).isNull()
            assertThat(
                adapter.getCompletions(URI, 0, prefix.length).map {
                    it.label
                },
            ).describedAs(declaration).contains(if (declaration.contains("Int64")) "MaxValue" else "size")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["(1, pair(1, va", "[pair(1, va", "[1=pair(1, va", "Tuple<Int, Int>:(1, pair(1, va"])
    fun `literal nesting retains the inner argument fitter and signature`(expression: String) {
        val prefix =
            "module Editing { Int pair(Int first, Int second) = first; " +
                "Object run(Int value, String valueText) { return $expression"
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, "$prefix; } }")
            assertThat(adapter.getCompletions(URI, 0, prefix.length).map { it.label }).containsExactly("value")
            assertThat(
                adapter
                    .getSignatureHelp(URI, 0, prefix.length)!!
                    .signatures
                    .single()
                    .activeParameter,
            ).isEqualTo(1)
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `unrelated missing syntax is not repaired by a literal cursor`() {
        for (expression in listOf("[1 + , value.si", "[1=value.si +", "(, value.si", "[1=, value.si")) {
            val prefix = "module Editing { Object run(String value) { return $expression"
            XdkAdapter().use { adapter ->
                adapter.compile(URI, "$prefix; } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).describedAs(expression).isEmpty()
            }
        }
    }

    @Test
    fun `declaration recovery respects cancellation and a first error budget`() {
        CompilerTestSupport.configure()
        val prefix = "module Editing { void run(Int size = Int64.Ma"
        val text = "$prefix {} }"
        val source = Source(text, URI)
        repeat(prefix.length) { source.next() }
        val cursor = source.position
        source.reset()
        for (errors in listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })) {
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), cursor, null, errors)
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.cursorBindings()).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
