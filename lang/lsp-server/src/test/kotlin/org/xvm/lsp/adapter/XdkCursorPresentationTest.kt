package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkCursorPresentationTest {
    @Test
    fun `completion carries source documentation and stable ordering across fresh attempts`() {
        val prefix =
            """
            module Editing {
                /** Keeps the selected text. */
                String choose(String value, String backup = "") = value;
                Int choose(Int value) = value;
                void run() { ch
            """
                .trimIndent()
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix; } }")
            val at = Position(prefix.lines().lastIndex, prefix.lines().last().length)
            val first = adapter.getCompletions(URI, at.line, at.column)
            assertThat(first.filter { it.label == "choose" }).hasSize(2)
            assertThat(first.single { it.detail.startsWith("String choose") }.documentation)
                .contains("Keeps the selected text.")
            assertThat(first.map { it.sortText }).doesNotContainNull().isSorted()
            adapter.compile(URI, "$prefix; } } ")
            assertThat(adapter.getCompletions(URI, at.line, at.column)).isEqualTo(first)
        }
    }

    @Test
    fun `candidate documentation retains named active parameter and default information`() {
        val prefix =
            """
            module Editing {
                /** Keeps the selected text. */
                String choose(String value, String backup = "") = value;
                void run() { choose(backup = "x", value =
            """
                .trimIndent()
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val help =
                adapter.getSignatureHelp(
                    URI,
                    prefix.lines().lastIndex,
                    prefix.lines().last().length,
                )!!
            val signature = help.signatures.single()
            assertThat(signature.documentation)
                .contains("Keeps the selected text.", "overload not selected")
            assertThat(signature.activeParameter).isZero()
            assertThat(signature.parameters[0].documentation)
                .contains("Parameter 1: value", "Required")
            assertThat(signature.parameters[1].documentation)
                .contains("Parameter 2: backup", "Optional")
        }
    }

    @Test
    fun `selected call documentation survives completed named argument mapping`() {
        val source =
            """
            module Editing {
                /** Keeps the selected text. */
                String choose(String value, String backup = "") = value;
                void run() { choose(backup = "x", value = "y"); }
            }
            """
                .trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val line = source.lines()[3]
            val signature =
                adapter.getSignatureHelp(URI, 3, line.indexOf("\"y\"") + 1)!!.signatures.single()
            assertThat(signature.documentation)
                .contains("Keeps the selected text.")
                .doesNotContain("overload not selected")
            assertThat(signature.activeParameter).isZero()
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
