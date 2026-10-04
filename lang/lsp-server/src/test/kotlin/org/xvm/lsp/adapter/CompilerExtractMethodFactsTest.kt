package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.projectRenameFacts

class CompilerExtractMethodFactsTest {
    @Test
    fun `capture evidence distinguishes stable values from writes and reference storage`() {
        CompilerTestSupport.configure()
        val text = """
            module Extract {
                Int read(Int input, Int changed) {
                    Int fixed = input + 1;
                    Int mutable = input;
                    @Volatile Int stored = input;
                    changed++;
                    mutable++;
                    return fixed + mutable + stored + changed;
                }
            }
        """.trimIndent()
        val errors = ErrorList()
        val compiled = EmbeddingSupport.instance().compileModule(Source(text, "Extract.x"), null, errors)
        assertThat(compiled.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val facts = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), errors, includeMembers = true)
        val model = facts.models.single()
        val stable = model.symbols.filter { symbol ->
            facts.extraction.stableValues.any { it.sourceName == symbol.declarationSource && it.range == symbol.declaration }
        }.map { it.name }
        assertThat(stable).contains("input", "fixed").doesNotContain("changed", "mutable", "stored")
        assertThat(facts.extraction.types).isNotEmpty()
    }

    @Test
    fun `normal rename facts do not collect extraction types`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compiled = EmbeddingSupport.instance().compileModule(Source("module Extract { Int read(Int x) = x; }", "Extract.x"), null, errors)
        assertThat(compiled.succeeded()).isTrue()
        val facts = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), errors)
        assertThat(facts.extraction.types).isEmpty()
        assertThat(facts.extraction.stableValues).isEmpty()
    }
}
