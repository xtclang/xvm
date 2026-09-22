package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source

class EmbeddingDiagnosticsTest {
    @Test
    fun `compound assignment with no operator reports an invalid operation before code generation`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            Source(
                "module MissingOperator { class Counter {} void run() { Counter value = new Counter(); value += 1; } }",
                "file:///MissingOperator.x",
            )
        val result = EmbeddingSupport.instance().compileModule(source, null, errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code })
            .describedAs(errors.errors.toString())
            .contains("COMPILER-50")
            .doesNotContain("COMPILER-69", "EMB-5")
    }

    @Test
    fun `unexpected compiler failures are not hidden by an earlier source error`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            object : Source("module Broken {}", "file:///Broken.x") {
                override fun next(): Char {
                    errors.error("PARSER-03", ErrorListener.`in`(this, 0, 1), "injected source error")
                    throw IllegalStateException("injected compiler failure")
                }
            }
        val result = EmbeddingSupport.instance().compileModule(source, null, errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code }).contains("PARSER-03", "EMB-5")
    }

    @Test
    fun `cancellation before compilation does not read source or invent a diagnostic`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            object : Source("module Unneeded {}") {
                override fun next(): Char = error("cancelled compilation must not read source")
            }
        val result = EmbeddingSupport.instance().compileModule(source, null, ErrorListener.cancellable(errors) { true })
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `an empty editor buffer reports a source problem without an internal failure`() {
        CompilerTestSupport.configure()
        for (text in listOf("", "// no module yet")) {
            val errors = ErrorList()
            val result = EmbeddingSupport.instance().compileModule(Source(text, "file:///Empty.x"), null, errors)
            assertThat(result.succeeded()).isFalse()
            assertThat(errors.hasSeriousErrors()).isTrue()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }
    }
}
