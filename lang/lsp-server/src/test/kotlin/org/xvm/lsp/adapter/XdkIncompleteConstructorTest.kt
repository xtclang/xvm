package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkIncompleteConstructorTest {
    @Test
    fun `constructor signatures fit written arguments without selecting an overload`() {
        val declarations =
            "module Editing { class Box { construct(Int first, String second) {} " +
                "construct(String first, Int second=0) {} } void run() { new Box("
        XdkAdapter().use { adapter ->
            for ((argument, labels) in listOf(
                "" to listOf("new Box(Int first, String second)", "new Box(String first, Int second = …)"),
                "1, " to listOf("new Box(Int first, String second)"),
                "\"x\", " to listOf("new Box(String first, Int second = …)"),
            )) {
                val prefix = declarations + argument
                adapter.compile(URI, "$prefix); } }")
                val cached = adapter.getCachedResult(URI)
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)
                assertThat(help).describedAs(prefix).isNotNull()
                assertThat(help!!.signatures.map { it.label }).containsExactlyInAnyOrderElementsOf(labels)
                assertThat(help.signatures.map { it.activeParameter }).containsOnly(if (argument.isEmpty()) 0 else 1)
                assertThat(help.signatures.map { it.documentation }).allMatch { it!!.contains("overload not selected") }
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            }
        }
    }

    @Test
    fun `generic constructors retain argument names and inferred expected types in value positions`() {
        val declarations = "module Editing { class Box<T> { construct(T first, T second) {} } void run() { "
        XdkAdapter().use { adapter ->
            for ((call, parameter) in listOf(
                "new Box<String>(\"x\", " to 1,
                "Box<String> box = new Box<String>(second=\"x\", first=" to 0,
            )) {
                val prefix = declarations + call
                adapter.compile(URI, "$prefix); } }")
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)
                assertThat(help).describedAs(prefix).isNotNull()
                assertThat(help!!.signatures.single().label).isEqualTo("new Box(String first, String second)")
                assertThat(help.signatures.single().activeParameter).isEqualTo(parameter)
            }
        }
    }

    @Test
    fun `inaccessible abstract and incompatible constructors have no candidates`() {
        val declarations =
            "module Editing { class Box { construct(Int first, String second) {} } " +
                "class Hidden { private construct(Int value) {} } @Abstract class AbstractBox { construct(Int value) {} } " +
                "void run() { "
        XdkAdapter().use { adapter ->
            for (call in listOf("new Box(True, ", "new Box(unknown=", "new Hidden(", "new AbstractBox(", "new Missing(")) {
                val prefix = declarations + call
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(call).isNull()
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
