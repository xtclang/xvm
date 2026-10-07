package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkDocumentationTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r"])
    fun `generic method skeleton preserves tabs newlines and compiler parameter names`(newline: String) {
        val text =
            """
            module Documentation {
            	<T> T echo(T input, Int count = 1) = input;
            }
            """.trimIndent().replace("\n", newline)
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            val action = docs(adapter, text, "echo").single()
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            val insertion = edit.changes.getValue(URI).single()
            assertThat(insertion.range.start).isEqualTo(Position(1, 0))
            assertThat(insertion.newText).isEqualTo(
                listOf(
                    "/**",
                    " * TODO: add description.",
                    " * @param T TODO",
                    " * @param input TODO",
                    " * @param count TODO",
                    " * @return TODO",
                    " */",
                ).joinToString(newline, postfix = newline) { "\t$it" },
            )
            val changed = text.replace("\t<T>", insertion.newText + "\t<T>")
            assertThat(adapter.compile(URI, changed).diagnostics).isEmpty()
            assertThat(docs(adapter, changed, "echo")).isEmpty()
        }
    }

    @Test
    fun `declarations get comments while bodies locals and same-line siblings do not`() {
        val text =
            """
            module Documentation {
                class Item {}
                Int value = 1;
                void run() {
                    Int local = value;
                    assert local == 1;
                }
                Int first() = 1; Int second() = 2;
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            listOf("Documentation", "Item", "value", "run").forEach { assertThat(docs(adapter, text, it)).hasSize(1) }
            listOf("local", "assert", "second").forEach { assertThat(docs(adapter, text, it)).isEmpty() }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/** Existing documentation. */", "/** */", "/* Keep this note. */"])
    fun `existing comments are never duplicated or replaced`(comment: String) {
        val text =
            """
            module Documentation {
                $comment
                Int value = 1;
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            assertThat(docs(adapter, text, "value")).isEmpty()
        }
    }

    private fun docs(
        adapter: XdkAdapter,
        text: String,
        anchor: String,
    ): List<CodeAction> {
        val prefix = text.take(text.indexOf(anchor)).split(Regex("\r\n|\r|\n"))
        val position = Position(prefix.lastIndex, prefix.last().length)
        return adapter.getCodeActions(URI, Range(position, position), emptyList()).filter { it.title == "Generate documentation comment" }
    }

    private companion object {
        const val URI = "untitled:Documentation.x"
    }
}
