package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class XdkIncompleteFunctionTest {
    @ParameterizedTest
    @ValueSource(strings = ["fn(", "fn(1", "fn(1, "])
    fun `function parameters supply signatures and positional expected types`(call: String) {
        val prefix = "module Editing { void run(function Int(Int, String) fn) { $call"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val cached = adapter.getCachedResult(URI)
            val position = Position(0, prefix.length)
            val model = adapter.analyzeAtAsync(URI, position).get(10, SECONDS)!!
            val site = model.sites.single()
            val candidate = site.functions.single()
            val cursor = SemanticModel.Position(position.line, position.column)
            val parameter = if (call.endsWith(", ")) 1 else 0
            assertThat(site.callCandidates).isEmpty()
            assertThat(candidate.signature.parameters.map { it.name }).containsOnlyNulls()
            assertThat(site.parameterAt(candidate, cursor)).isEqualTo(parameter)
            Executors.newSingleThreadExecutor().use { executor ->
                assertThat(executor.submit<String> { model.semantics.type(site.expectedTypeAt(candidate, cursor)!!)!!.displayName }.get())
                    .isEqualTo(if (parameter == 0) "Int" else "String")
            }
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.single().label).isEqualTo("Int fn(Int, String)")
            assertThat(help.signatures.single().activeParameter).isEqualTo(parameter)
            assertThat(help.signatures.single().documentation).contains("runtime target unknown")
            assertThat(model.semantics.calls).isEmpty()
            assertThat(model.semantics.functionCalls).isEmpty()
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    @Test
    fun `function properties and narrowed nullable locals use the current compiler type`() {
        for ((prefix, suffix) in listOf(
            "module Editing { class Holder<T>(function T(T, String) fn) {} void run(Holder<Int> holder) { holder.fn(1, " to "); } }",
            "module Editing { void run(function Int(Int, String)? fn) { if (fn != Null) { fn(1, " to "); } } }",
        )) {
            XdkAdapter().use { adapter ->
                adapter.compile(URI, prefix + suffix)
                val help = adapter.getSignatureHelp(URI, 0, prefix.length)
                assertThat(help).describedAs(prefix).isNotNull()
                assertThat(help!!.signatures.single().label).isEqualTo("Int fn(Int, String)")
                assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            }
        }
    }

    @Test
    fun `function producing expressions supply types without inventing a callee name`() {
        val prefix =
            "module Editing { function Int(Int, String) make(function Int(Int, String) fn)=fn; " +
                "void run(function Int(Int, String) fn) { make(fn)(1, "
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.single().label).isEqualTo("Int function(Int, String)")
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
        }
    }

    @Test
    fun `captured lambda arguments are fitted against the known function parameter`() {
        val prefix =
            "module Editing { void run(function Int(function Int(Int), String) fn, Int captured) { " +
                "fn((Int n) -> n + captured, "
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            val help = adapter.getSignatureHelp(URI, 0, prefix.length)!!
            assertThat(help.signatures.single().label).contains("fn(", "String)")
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["fn(True, ", "fn(name=", "fn(name=1, ", "fn(1, \"x\", True, "])
    fun `function signatures reject incompatible named and excess written arguments`(call: String) {
        val prefix = "module Editing { void run(function Int(Int, String) fn) { $call"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, "$prefix); } }")
            assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).isNull()
        }
    }

    @Test
    fun `unreadable nullable and shadowing nonfunctions do not acquire signatures`() {
        for (prefix in listOf(
            "module Editing { void run() { function Int(Int) fn; fn(",
            "module Editing { void run(function Int(Int)? fn) { fn(",
            "module Editing { Int fn(Int n)=n; void run() { Int fn=1; fn(",
        )) {
            XdkAdapter().use { adapter ->
                adapter.compile(URI, "$prefix); } }")
                assertThat(adapter.getSignatureHelp(URI, 0, prefix.length)).describedAs(prefix).isNull()
            }
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
