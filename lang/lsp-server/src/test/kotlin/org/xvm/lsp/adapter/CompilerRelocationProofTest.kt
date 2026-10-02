package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkLocalExtraction
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.projectRenameFacts

/** A compiling relocation can silently capture names; its original edges must be compared. */
class CompilerRelocationProofTest {
    @Test
    fun `expression relocation preserves its parameter binding only in the original scope`() {
        val text =
            """
            module Extract {
                Int first(Int input) {
                    return input + 1;
                }
                Int second(Int input) {
                    return 0;
                }
            }
            """.trimIndent()
        val expression = "input + 1"
        val at = text.positionOf("return $expression", expression)
        val candidate = requireNotNull(XdkLocalExtraction.candidate(text, Range(at, Position(at.line, at.column + expression.length))))
        val plan =
            XdkRename.Plan(
                mapOf(SOURCE to text),
                mapOf(SOURCE to candidate.edits),
                relocations =
                    mapOf(SOURCE to listOf(candidate.relocation)),
            )
        val before = facts(text)
        assertThat(XdkRename.preservesKnownBindings(before, facts(plan.proposed.getValue(SOURCE)), plan)).isTrue()

        // Same text, but a different method's identically named parameter: both programs compile.
        val start = text.indexOf(expression)
        val destination = text.indexOf("return 0;")
        val prefix = "Int extractedValue = "
        val insertion = XdkRename.Edit(destination, destination, "$prefix$expression;\n        ")
        val moved = XdkRename.Relocation(start, start + expression.length, insertion, prefix.length)
        val captured =
            XdkRename.Plan(
                mapOf(SOURCE to text),
                mapOf(SOURCE to listOf(insertion, XdkRename.Edit(start, start + expression.length, "0"))),
                relocations = mapOf(SOURCE to listOf(moved)),
            )
        assertThat(XdkRename.preservesKnownBindings(before, facts(captured.proposed.getValue(SOURCE)), captured)).isFalse()
    }

    private fun facts(text: String): CompilerRenameFacts {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, SOURCE), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.projectRenameFacts(XdkDependencies(emptyList()).open(), errors)
    }

    private companion object {
        const val SOURCE = "Extract.x"
    }
}
