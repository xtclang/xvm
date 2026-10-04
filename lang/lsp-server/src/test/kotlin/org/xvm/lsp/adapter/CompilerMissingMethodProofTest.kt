package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.memberActionFacts
import org.xvm.lsp.adapter.xdk.missingMethodLocalTypes
import org.xvm.lsp.adapter.xdk.projectRenameFacts
import org.xvm.lsp.adapter.xdk.renameFacts

class CompilerMissingMethodProofTest {
    @Test
    fun `inferred local evidence remains detached and repair must bind the exact argument declaration`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                Int read(Int value) {
                    var first = value;
                    var other = value + 1;
                    Int result = missing(first);
                    return result;
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val types = failed.missingMethodLocalTypes()
        assertThat(types.values).containsExactlyInAnyOrder("Int64", "Int64", "Int64")
        val declarationErrors = ErrorList()
        val analysis = embedding.analyzeDeclarations(Source(text, SOURCE), null, declarationErrors).orElseThrow()
        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors, types)
        assertThat(declarationErrors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("private Int64 missing(Int64 arg1)")
        assertThat(candidate.arguments).hasSize(1)
        val edit = requireNotNull(candidate.edit(text))

        fun proves(plan: XdkRename.Plan): Boolean {
            val repairedErrors = ErrorList()
            val repaired = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, repairedErrors)
            assertThat(repaired.succeeded()).describedAs(repairedErrors.errors.toString()).isTrue()
            return candidate.bindsNewMethod(repaired.projectRenameFacts(XdkDependencies(emptyList()).open(), repairedErrors), plan, edit)
        }
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit))))).isTrue()
        // This compiles with the same signature, but the argument now denotes a different local.
        val argument = text.indexOf("first);")
        val redirect = XdkRename.Edit(argument, argument + "first".length, "other")
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit))))).isFalse()
    }

    @Test
    fun `fresh parameter signature repairs a failed body without changing known bindings`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                Int keep(Int value) = value;
                Int keep(String value) = value.size;
                Int read(Int value) {
                    Int previous = keep(value);
                    return missing(value);
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val partial = failed.renameFacts(XdkDependencies(emptyList()).open())
        val declarationErrors = ErrorList()
        val analysis =
            embedding
                .analyzeDeclarations(Source(text, SOURCE), null, declarationErrors)
                .orElseThrow()

        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors)
        assertThat(declarationErrors.errors).isEmpty()
        assertThat(headers.missingMethods.map { it.declaration }).containsExactly("private Int64 missing(Int64 arg1)")
        val candidate = headers.missingMethods.single()
        val edit = requireNotNull(candidate.edit(text))
        val plan = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit)))
        val compiledErrors = ErrorList()
        val compiled = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, compiledErrors)
        assertThat(compiled.succeeded()).describedAs(compiledErrors.errors.toString()).isTrue()
        val after = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), compiledErrors)
        assertThat(candidate.bindsNewMethod(after, plan, edit)).isTrue()
        val before = CompilerRenameFacts.merge(mapOf("headers" to headers, "partial" to partial))
        assertThat(XdkRename.preservesKnownBindings(before, after, plan)).isTrue()

        // The graph still compiles if an existing call switches overloads, but that is not a
        // repair of the missing method. The old compiler-selected call must remain unchanged.
        val call = text.indexOf("keep(value)")
        val redirect = XdkRename.Edit(call, call + "keep(value)".length, "keep(\"text\")")
        val wrong = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit)))
        val redirectedErrors = ErrorList()
        val redirected = embedding.compileModule(Source(wrong.proposed.getValue(SOURCE), SOURCE), null, redirectedErrors)
        assertThat(redirected.succeeded()).describedAs(redirectedErrors.errors.toString()).isTrue()
        val redirectedFacts = redirected.projectRenameFacts(XdkDependencies(emptyList()).open(), redirectedErrors)
        assertThat(XdkRename.preservesKnownBindings(before, redirectedFacts, wrong)).isFalse()
    }

    private companion object {
        const val SOURCE = "Missing.x"
    }
}
