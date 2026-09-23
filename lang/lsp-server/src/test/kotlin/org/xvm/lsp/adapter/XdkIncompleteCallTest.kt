package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class XdkIncompleteCallTest {
    @Test
    fun `written argument types filter overloads without selecting an unfinished call`() {
        XdkAdapter().use { adapter ->
            for ((argument, expected) in listOf("\"x\"" to "String", "1" to "Int")) {
                val prefix = "$BOX void run(Box<String> box) { box.choose($argument, "
                adapter.compile(URI, "$prefix); } }")
                val cached = adapter.getCachedResult(URI)
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)
                assertThat(help).describedAs(prefix).isNotNull()
                requireNotNull(help)
                assertThat(help.signatures).hasSize(1)
                assertThat(help.signatures.single().label).isEqualTo("$expected choose($expected first, $expected second)")
                assertThat(help.signatures.single().activeParameter).isEqualTo(1)
                assertThat(help.signatures.single().documentation).contains("written arguments fit", "overload not selected")
                assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            }
        }
    }

    @Test
    fun `candidate inference copies expected types and named argument mappings`() {
        XdkAdapter().use { adapter ->
            for ((call, parameter) in listOf(
                "pair(\"x\", second=" to 1,
                "pair(second=\"x\", first=" to 0,
                "pair(second=\"x\"" to 1,
                "generic(\"x\", " to 1,
            )) {
                val prefix = "$BOX void run(Box<String> box) { box.$call"
                adapter.compile(URI, "$prefix); } }")
                val position = Position(0, prefix.length)
                val model = adapter.analyzeAtAsync(URI, position).get(10, SECONDS)!!
                val site = model.sites.single()
                val candidate = site.callCandidates!!.single()
                val cursor = SemanticModel.Position(0, prefix.length)
                assertThat(site.parameterAt(candidate, cursor)).describedAs(call).isEqualTo(parameter)
                assertThat(model.semantics.type(site.expectedTypeAt(candidate, cursor)!!)!!.displayName).isEqualTo("String")
                Executors.newSingleThreadExecutor().use { executor ->
                    assertThat(
                        executor
                            .submit<String> {
                                model.semantics.type(site.expectedTypeAt(candidate, cursor)!!)!!.displayName
                            }.get(),
                    ).isEqualTo("String")
                }
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
                assertThat(help.signatures.single().activeParameter).describedAs(call).isEqualTo(parameter)
                assertThat(help.signatures.single().label).contains("String first", "String second")
                assertThat(model.semantics.calls.map { model.semantics.symbol(it.method)?.name }).doesNotContain("pair", "generic")
            }
        }
    }

    @Test
    fun `invalid names duplicate bindings and incompatible arguments produce no applicable signatures`() {
        XdkAdapter().use { adapter ->
            for (call in listOf("pair(unknown=", "pair(first=\"x\", first=", "choose(True, ")) {
                val prefix = "$BOX void run(Box<String> box) { box.$call"
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(call).isNull()
            }
        }
    }

    @Test
    fun `unbound generic parameters remain formal and do not become Object`() {
        val prefix = "$BOX void run(Box<String> box) { box.generic("
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.single().label).contains("U first", "U second").doesNotContain("Pending", "Object")
        }
    }

    @Test
    fun `candidate fitting checks a captured lambda without selecting or emitting the call`() {
        val prefix =
            "module Editing { Int apply(function Int(Int) fn, Int second) = fn(second); " +
                "void run(Int outer) { apply((Int n) -> n + outer, "
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, "${prefix}1); } }").diagnostics).isEmpty()
            adapter.compile(URI, "$prefix); } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.single().label).contains("apply(", "Int second")
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            assertThat(help.signatures.single().documentation).contains("overload not selected")
        }
    }

    @Test
    fun `unqualified and static call candidates use compiler lookup`() {
        XdkAdapter().use { adapter ->
            for (receiver in listOf("", "Box.")) {
                val prefix =
                    "module Editing { class Box { static String pair(String first, String second) = first; " +
                        "void run() { ${receiver}pair(\"x\", "
                assertThat(adapter.compile(URI, "$prefix\"y\"); } } }").diagnostics).isEmpty()
                adapter.compile(URI, "$prefix); } } }")
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)
                assertThat(help).describedAs(receiver).isNotNull()
                requireNotNull(help)
                assertThat(help.signatures.single().label).isEqualTo("String pair(String first, String second)")
                assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
        const val BOX =
            "module Editing { class Box<T> { " +
                "String choose(String first, String second) = first; Int choose(Int first, Int second) = first; " +
                "T pair(T first, T second) = first; <U> U generic(U first, U second) = first; }"
    }
}
