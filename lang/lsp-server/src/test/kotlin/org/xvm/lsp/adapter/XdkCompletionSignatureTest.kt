package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

class XdkCompletionSignatureTest {
    @Test
    fun `member completion preserves overloads substitutes receiver types and excludes private members`() {
        val prefix = "$BOX void run(Box<String> box) { box."
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix } }")
            val diagnostics = adapter.getCachedResult(URI)
            val items = adapter.getCompletionsAsync(URI, 0, prefix.length, ".").get(10, SECONDS)
            assertThat(items.map { it.label }).contains("echo", "choose", "label").doesNotContain("secret", "hidden")
            assertThat(items.single { it.label == "echo" }.detail).isEqualTo("String echo(String value)")
            assertThat(items.filter { it.label == "choose" }).hasSize(2)
            assertThat(items.single { it.label == "label" }.kind).isEqualTo(CompletionItem.CompletionKind.PROPERTY)
            assertThat(items).allSatisfy { assertThat(it.insertText).isEqualTo(it.label) }
            assertThat(adapter.getCachedResult(URI)).isEqualTo(diagnostics)
        }
    }

    @Test
    fun `incomplete qualified calls return candidate overloads without inventing selection`() {
        XdkAdapter().use { adapter ->
            for (name in listOf("echo", "choose")) {
                val prefix = "$BOX void run(Box<String> box) { box.$name("
                adapter.compile(URI, "$prefix } }")
                val help = adapter.getSignatureHelpAsync(URI, 0, prefix.length).get(10, SECONDS)!!
                assertThat(help.signatures).hasSize(if (name == "echo") 1 else 2)
                assertThat(help.signatures).allSatisfy {
                    assertThat(it.documentation).contains("overload not selected")
                    assertThat(it.activeParameter).isZero()
                }
                if (name == "echo") assertThat(help.signatures.single().label).isEqualTo("String echo(String value)")
            }
        }
    }

    @Test
    fun `partial positional slots exclude commas in strings and nested calls`() {
        val prefix = "module Editing { void run(String text) { text.indexOf(text.replace(\"a,b\", \"c\"), "
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix } }")
            val help = adapter.getSignatureHelpAsync(URI, 0, prefix.length).get(10, SECONDS)!!
            assertThat(help.signatures).isNotEmpty().allSatisfy {
                assertThat(it.label).startsWith("conditional Int indexOf(")
                assertThat(it.activeParameter).isEqualTo(1)
            }
        }
    }

    @Test
    fun `partial named arguments show labels without a fabricated parameter highlight`() {
        val prefix = "module Editing { void run(String text) { text.indexOf(\"x\", startAt=2, "
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix } }")
            val help = adapter.getSignatureHelpAsync(URI, 0, prefix.length).get(10, SECONDS)!!
            assertThat(help.signatures).isNotEmpty().allSatisfy {
                assertThat(it.label).contains("indexOf(", "startAt")
                assertThat(it.parameters).isEmpty()
                assertThat(it.activeParameter).isNull()
            }
        }
    }

    @Test
    fun `completed unqualified generic calls map named arguments to their actual parameters`() {
        val source =
            "module Editing { <T> T echo(T value, T backup) { return value; } " +
                "void run() { String text = echo(backup=\"b\", value=\"a\"); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            for ((argument, expected) in listOf("backup=\"b\"" to 1, "value=\"a\"" to 0)) {
                val help = adapter.getSignatureHelp(URI, 0, source.indexOf(argument) + argument.length)!!
                assertThat(help.signatures.single().label).isEqualTo("String echo(String value, String backup)")
                assertThat(help.activeParameter).isEqualTo(expected)
                assertThat(help.signatures.single().activeParameter).isEqualTo(expected)
                assertThat(help.signatures.single().documentation).isNull()
            }
            assertThat(adapter.getSignatureHelp(URI, 0, source.indexOf("echo"))).isNull()
        }
    }

    @Test
    fun `selected defaulted arguments and nested calls use compiler mappings`() {
        val source =
            "module Editing { Int add(Int left=1, Int right=2) { return left; } " +
                "void run() { Int n=add(right=add(left=3)); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val inner = adapter.getSignatureHelp(URI, 0, source.indexOf("left=3") + "left=3".length)!!
            assertThat(inner.activeParameter).isZero()
            assertThat(inner.signatures.single().label).isEqualTo("Int add(Int left = …, Int right = …)")
            val outer = adapter.getSignatureHelp(URI, 0, source.lastIndexOf("right=") + "right=".length)!!
            assertThat(outer.activeParameter).isEqualTo(1)
        }
    }

    @Test
    fun `unsupported or unresolved cursor contexts return no invented results`() {
        XdkAdapter().use { adapter ->
            for (body in listOf("missing.", "box.ec", "ec", "1 + box.")) {
                val prefix = "$BOX void run(Box<String> box) { $body"
                adapter.compile(URI, "$prefix } }")
                assertThat(adapter.getCompletions(URI, 0, prefix.length)).isEmpty()
            }
            val prefix = "$BOX void run(Box<String> box) { missing("
            adapter.compile(URI, "$prefix } }")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
        const val BOX =
            "module Editing { class Box<Element> { " +
                "Element echo(Element value) { return value; } " +
                "Int choose(Int n) { return n; } String choose(String s) { return s; } " +
                "String label=\"box\"; private String hidden=\"hidden\"; private Int secret() { return 1; } }"
    }
}
