package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.semanticSnapshot

/** Compiler facts required by bounded rename: successful recompilation alone is insufficient. */
class CompilerRenameRequirementsTest {
    @Test
    fun `identity edits rename captures without touching a shadowing lambda parameter`() {
        val source =
            "module Rename { Int run() { Int local=1; function Int() f=()->local; " +
                "function Int(Int) g=(Int local)->local; return f()+g(2); } }"
        val before = compile(source)
        val renamed = rename(source, before, source.indexOf("local"), "renamed")
        val after = compile(renamed)
        assertThat(renamed).contains("Int renamed=1", "f=()->renamed", "(Int local)->local")
        assertThat(sourceBindings(after)).isEqualTo(sourceBindings(before))
    }

    @Test
    fun `a compiling rename can silently capture an unrelated property reference`() {
        val source = "module Rename { Int value=10; Int run() { Int local=1; return local+value; } }"
        val before = compile(source)
        val renamed = rename(source, before, source.indexOf("local"), "value")
        val after = compile(renamed)
        assertThat(sourceBindings(after)).isNotEqualTo(sourceBindings(before))
        assertThat(before.symbolAt(0, source.lastIndexOf("value"))!!.kind).isEqualTo(SemanticModel.SymbolKind.PROPERTY)
        assertThat(after.symbolAt(0, renamed.lastIndexOf("value"))!!.kind).isEqualTo(SemanticModel.SymbolKind.VARIABLE)
    }

    @Test
    fun `named argument labels bind to their selected method parameter before argument rewriting`() {
        val source = "module Rename { Int pick(Int input)=input; Int run()=pick(input=1); }"
        val before = compile(source)
        val declaration = source.indexOf("input")
        assertThat(before.referencesAt(0, declaration, true)).hasSize(3)
        assertThat(before.symbolAt(0, source.lastIndexOf("input"))).isEqualTo(before.symbolAt(0, declaration))
        val renamed = rename(source, before, declaration, "other")
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(renamed, URI), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.hasSeriousErrors()).isFalse()
    }

    private fun compile(text: String): SemanticModel {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, URI), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.semanticSnapshot()
    }

    /** Single-line fixtures make edit mapping explicit; production rename also needs version checks. */
    private fun rename(
        text: String,
        model: SemanticModel,
        column: Int,
        name: String,
    ): String =
        model.referencesAt(0, column, true).sortedByDescending { it.start.column }.fold(text) { edited, range ->
            edited.replaceRange(range.start.column, range.end.column, name)
        }

    /** Compare edges to declaration ordinals, which survive changed spelling and shifted offsets. */
    private fun sourceBindings(model: SemanticModel): List<Int> =
        model.occurrences.map { occurrence ->
            val target = occurrence.symbol?.let(model::symbol)
            model.occurrences.indexOfFirst { it.role == SemanticModel.Role.DECLARATION && it.range == target?.declaration }
        }

    private companion object {
        const val URI = "file:///Rename.x"
    }
}
