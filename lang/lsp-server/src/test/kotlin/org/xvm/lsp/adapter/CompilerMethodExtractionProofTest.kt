package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkMethodExtraction
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.projectRenameFacts

class CompilerMethodExtractionProofTest {
    @Test
    fun `compiling helper with swapped arguments fails capture proof`() {
        val text =
            """
            module Extract {
                Int read(Int first, Int other) {
                    return first - other;
                }
            }
            """.trimIndent()
        val before = facts(text)
        val at = text.positionOf("return first - other", "first - other")
        val candidate =
            requireNotNull(
                XdkMethodExtraction.candidate(
                    text,
                    Range(at, Position(at.line, at.column + "first - other".length)),
                    before.models.single(),
                    before.extraction,
                ),
            )

        fun plan(value: XdkMethodExtraction.Candidate) =
            XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to value.edits), relocations = mapOf(SOURCE to listOf(value.relocation)))
        val correct = plan(candidate)
        assertThat(XdkRename.preservesMethodExtraction(before, facts(correct.proposed.getValue(SOURCE)), correct, candidate)).isTrue()

        val swapped = candidate.copy(replacement = candidate.replacement.copy(text = "extractedMethod(other, first)"))
        val wrong = plan(swapped)
        assertThat(XdkRename.preservesMethodExtraction(before, facts(wrong.proposed.getValue(SOURCE)), wrong, swapped)).isFalse()
    }

    @Test
    fun `helper relocation cannot silently change a same named call target`() {
        val text =
            """
            module Extract {
                class First {
                    Int step() = 1;
                    Int read() {
                        return step();
                    }
                }
                class Other {
                    Int step() = 2;
                }
            }
            """.trimIndent()
        val before = facts(text)
        val at = text.positionOf("return step()", "step()")
        val candidate =
            requireNotNull(
                XdkMethodExtraction.candidate(
                    text,
                    Range(at, Position(at.line, at.column + "step()".length)),
                    before.models.single(),
                    before.extraction,
                ),
            )
        val correct =
            XdkRename.Plan(
                mapOf(SOURCE to text),
                mapOf(SOURCE to candidate.edits),
                relocations = mapOf(SOURCE to listOf(candidate.relocation)),
            )
        assertThat(XdkRename.preservesMethodExtraction(before, facts(correct.proposed.getValue(SOURCE)), correct, candidate)).isTrue()
        // Rename the original step and add a new step at a different source location. The moved
        // call still compiles but binds to the new method, so its relocation must be rejected.
        val rename = XdkRename.Edit(text.indexOf("Int step()") + 4, text.indexOf("Int step()") + 8, "changed")
        val redirected = candidate.insertion.copy(text = candidate.insertion.text + "\n        Int step() = 3;")
        val altered = candidate.copy(insertion = redirected, relocation = candidate.relocation.copy(destination = redirected))
        val wrong =
            XdkRename.Plan(
                mapOf(SOURCE to text),
                mapOf(SOURCE to altered.edits + rename),
                relocations = mapOf(SOURCE to listOf(altered.relocation)),
            )
        assertThat(XdkRename.preservesMethodExtraction(before, facts(wrong.proposed.getValue(SOURCE)), wrong, altered)).isFalse()
    }

    private fun facts(text: String): CompilerRenameFacts {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, SOURCE), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.projectRenameFacts(XdkDependencies(emptyList()).open(), errors, includeMembers = true)
    }

    private companion object {
        const val SOURCE = "Extract.x"
    }
}
