package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source

/** Direct consumers of ordinary compiler facts, without an adapter or partial analysis. */
class CompilerBindingFactsTest {
    @Test
    fun `selected calls retain instantiated types and written argument order`() {
        val result =
            compile(
                "module Calls { <T> T echo(T value, T backup) = value; " +
                    "void run() { String text = echo(backup = \"b\", value = \"a\"); } }",
            )
        val call = result.callBindings().values.single { it.method().name == "echo" }
        // The compiler signature includes the hidden type parameter; argument indexes are visible slots.
        assertThat(
            call
                .signature()
                .rawParams
                .first()
                .isTypeOfType,
        ).isTrue()
        assertThat(call.signature().rawParams.drop(1)).containsExactly(result.pool().typeString(), result.pool().typeString())
        assertThat(call.signature().rawReturns).containsExactly(result.pool().typeString())
        assertThat(call.arguments().map { it.parameterIndex() }).containsExactly(1, 0)
        val node = result.callBindings().keys.single()
        assertThat(result.callBindings()[node.copyTree()]).isNull()
        assertThatThrownBy { (result.callBindings() as MutableMap).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `function calls have signatures without invented method identities`() {
        val result = compile("module Calls { Int apply(function Int(Int) fn) = fn(42); }")
        assertThat(result.callBindings()).isEmpty()
        val function = result.functionBindings().values.single()
        assertThat(result.pool().extractFunctionParams(function.type())).containsExactly(result.pool().typeInt64())
        assertThat(result.pool().extractFunctionReturns(function.type())).containsExactly(result.pool().typeInt64())
    }

    @Test
    fun `constructors retain written argument provenance`() {
        val result = compile("module Calls { const Box(Int value) {} Box make() = new Box(42); }")
        val call = result.constructorBindings().values.single()
        assertThat(call.method().name).isEqualTo("construct")
        assertThat(call.arguments().map { it.parameterIndex() }).containsExactly(0)
    }

    @Test
    fun `folded property initializers retain source references`() {
        val result = compile("module Calls { static Int base = 1; static Int answer = base + 1; }")
        val facts =
            result
                .initializerBindings()
                .entries
                .single { it.key.nameToken.valueText == "answer" }
                .value
        assertThat(facts.references().map { it.name() }).contains("base")
        assertThat(facts.expressions()).isNotEmpty()
    }

    @Test
    fun `invalid calls do not publish successful function bindings`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result =
            EmbeddingSupport.instance().compileModule(
                Source("module Calls { Int apply(function Int(Int) fn) = fn(\"bad\"); }", "Calls.x"),
                null,
                errors,
            )
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.hasSeriousErrors()).isTrue()
        assertThat(result.functionBindings()).isEmpty()
    }

    private fun compile(source: String): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(source, "Calls.x"), null, errors)
        assertThat(errors.errors).isEmpty()
        assertThat(result.succeeded()).isTrue()
        return result
    }
}
