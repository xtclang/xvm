package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.partial.IncompleteStatement

/** Method and constructor probes must share fitting rules without publishing speculative syntax. */
class XdkCandidateProbeTest {
    @ParameterizedTest
    @ValueSource(strings = ["pair", "new Box<String>"])
    fun `named generic candidates preserve source order and leave defaults unwritten`(callee: String) {
        val analysis = analyze("$callee(second = text, first = text, §)")
        val site = analysis.sites().single()
        val candidates = analysis.cursorBindings().getValue(site).candidates()
        val candidate = candidates.single()
        assertThat(
            candidate
                .signature()
                .rawParams
                .takeLast(3)
                .map { it.valueString },
        ).containsExactly("String", "String", "Int")
        assertThat(candidate.arguments().map { it.parameterIndex() }).containsExactly(1, 0)
        assertThat(candidate.arguments().map { it.label().name() }).containsExactly("second", "first")
        assertThat(site.arguments).hasSize(2)
        site.arguments.zip(candidate.arguments()).forEach { (written, mapping) ->
            assertThat(mapping.startPosition()).isEqualTo(written.startPosition)
            assertThat(mapping.endPosition()).isEqualTo(written.endPosition)
            assertThat(written.parent).isSameAs(site)
        }
        assertThat(candidate.converting()).isFalse()
        assertThatThrownBy { (candidates as MutableList).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { (candidate.arguments() as MutableList).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertNoSelectedCall(analysis, site)
    }

    @ParameterizedTest
    @ValueSource(strings = ["widen", "new Wide"])
    fun `conversion candidates retain their expected type without converting source arguments`(callee: String) {
        val analysis = analyze("$callee(number, §)")
        val site = analysis.sites().single()
        val candidate =
            analysis
                .cursorBindings()
                .getValue(site)
                .candidates()
                .single()
        assertThat(candidate.converting()).isTrue()
        assertThat(candidate.signature().rawParams.map { it.valueString })
            .containsExactly("Int128", "String")
        assertThat(candidate.arguments().single().parameterIndex()).isZero()
        assertThat(
            site.arguments
                .single()
                .type.valueString,
        ).isEqualTo("Int")
        assertThat(site.arguments.single().parent).isSameAs(site)
        assertNoSelectedCall(analysis, site)
    }

    @ParameterizedTest
    @ValueSource(strings = ["apply", "new Apply"])
    fun `captured lambda trials leave the written lambda unvalidated and independently cloned`(callee: String) {
        val analysis = analyze("$callee((Int value) -> value + number, §)")
        val site = analysis.sites().single()
        val candidate =
            analysis
                .cursorBindings()
                .getValue(site)
                .candidates()
                .single()
        assertThat(
            candidate
                .signature()
                .rawParams
                .last()
                .valueString,
        ).isEqualTo("Int")
        val lambda = site.arguments.single() as LambdaExpression
        assertThat(lambda.isValidated).isFalse()
        assertThat(lambda.parent).isSameAs(site)
        val copiedSite = site.clone() as IncompleteStatement
        val copiedLambda = copiedSite.arguments.single() as LambdaExpression
        assertThat(copiedLambda).isNotSameAs(lambda)
        assertThat(copiedLambda.parent).isSameAs(copiedSite)
        assertThat(copiedLambda.isValidated).isFalse()
        assertThat(site.toDumpString()).isEqualTo(copiedSite.toDumpString())
        assertNoSelectedCall(analysis, site)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pair", "new Box<String>"])
    fun `aborted candidate attempts publish no facts and do not poison the next attempt`(callee: String) {
        val call = "$callee(second = text, first = text, §)"
        listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })
            .forEach { listener ->
                val stopped = analyze(call, listener)
                assertThat(stopped.pool()).isNull()
                assertThat(stopped.cursorBindings()).isEmpty()
                assertThat(stopped.callBindings()).isEmpty()
                val recovered = analyze(call)
                assertThat(recovered.cursorBindings().getValue(recovered.sites().single()).candidates())
                    .hasSize(1)
            }
    }

    private fun assertNoSelectedCall(
        analysis: EmbeddingSupport.PartialAnalysis,
        site: IncompleteStatement,
    ) {
        assertThat(analysis.callBindings()).isEmpty()
        // Ordinary calls elsewhere in the module still publish their real selected bindings.
        val ordinaryCall = analysis.functionBindings().keys.single()
        assertThat(ordinaryCall.source.toString(ordinaryCall.startPosition, ordinaryCall.endPosition))
            .isEqualTo("fn(second)")
        assertThat(ordinaryCall.endPosition).isLessThan(site.startPosition)
        assertThat(site.target.isValidated).isFalse()
    }

    private fun analyze(
        call: String,
        listener: ErrorListener? = null,
    ): EmbeddingSupport.PartialAnalysis {
        CompilerTestSupport.configure()
        val marked =
            """
            module CandidateProbe {
                class Box<T> { construct(T first, T second, Int count = 0) {} }
                class Wide { construct(Int128 first, String second) {} }
                class Apply { construct(function Int(Int) fn, Int second) {} }
                <T> T pair(T first, T second, Int count = 0) = first;
                void widen(Int128 first, String second) {}
                Int apply(function Int(Int) fn, Int second) = fn(second);
                void run(String text, Int number) {
                    $call;
                }
            }
            """.trimIndent()
        val text = marked.replace("§", "")
        val source = Source(text, "untitled:CandidateProbe.x")
        repeat(marked.indexOf('§')) { source.next() }
        val cursor = source.position
        source.reset()
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener ?: errors)
        if (listener == null) {
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            assertThat(analysis.pool()).isNotNull()
            assertThat(
                analysis
                    .sites()
                    .single()
                    .source
                    .toRawString(),
            ).isEqualTo(text)
        }
        return analysis
    }
}
