package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source

/** Direct compiler consumers: no production LSP adapter is present in this branch. */
class CompilerPartialFactsTest {
    @Test
    fun `cursor queries retain readable local types`() {
        val analysis = analyze("module Editing { void run(String text, Int number) { tex|; } }")
        val binding = analysis.cursorBindings().getValue(analysis.sites().single())
        assertThat(binding.variables().filter { it.readable() }.map { it.name() })
            .contains("text", "number")
        assertThat(binding.variables().single { it.name() == "text" }.type())
            .isEqualTo(requireNotNull(analysis.pool()).typeString())
    }

    @Test
    fun `incomplete calls expose candidates without claiming a selected call`() {
        val analysis = analyze("module Editing { String echo(String value) = value; void run() { echo(")
        val binding = analysis.cursorBindings().getValue(analysis.sites().single())
        assertThat(binding.callsInspected()).isTrue()
        assertThat(binding.candidates().map { it.method().name }).contains("echo")
        assertThat(analysis.callBindings().values.map { it.method().name }).doesNotContain("echo")
    }

    @Test
    fun `incomplete construction exposes compiler fitted constructors`() {
        val analysis = analyze("module Editing { class Box(String text) {} void run() { new Box(")
        val binding = analysis.cursorBindings().getValue(analysis.sites().single())
        assertThat(binding.callsInspected()).isTrue()
        assertThat(binding.candidates().map { it.method().name }).contains("construct")
    }

    @Test
    fun `cancellation produces no partial graph or candidates`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result =
            EmbeddingSupport.instance().analyzeIncomplete(
                Source("module Editing { void run() {", "Editing.x"),
                null,
                ErrorListener.cancellable(errors) { true },
            )
        assertThat(result.sourceTrees()).isEmpty()
        assertThat(result.cursorBindings()).isEmpty()
        assertThat(errors.errors).isEmpty()
    }

    private fun analyze(text: String): EmbeddingSupport.PartialAnalysis {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result =
            if ("|" in text) {
                val prefix = Source(text.substringBefore("|"))
                while (prefix.hasNext()) prefix.next()
                EmbeddingSupport.instance().analyzeIncomplete(Source(text.replace("|", ""), "Editing.x"), prefix.position, null, errors)
            } else {
                EmbeddingSupport.instance().analyzeIncomplete(Source(text, "Editing.x"), null, errors)
            }
        assertThat(result.pool()).describedAs(errors.errors.toString()).isNotNull()
        assertThat(result.sites()).describedAs(errors.errors.toString()).hasSize(1)
        return result
    }
}
