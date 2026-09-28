package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkInferredPresentationTest {
    @Test
    fun `lambda parameter and return hints use the function signature without captures`() {
        val source = "module Inferred { Int run(Int captured) { function Int(Int) fn = (value) -> value + captured; return fn(1); } }"
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, source)
            assertThat(result.diagnostics).isEmpty()
            val types = adapter.getInlayHints(URI, ALL).filter { it.kind == InlayHint.InlayHintKind.TYPE }
            assertThat(types.map { it.position.column }).containsExactly(source.indexOf("value)") + 5, source.indexOf("->"))
            assertThat(types.map { it.label }).containsExactly(": Int", ": Int")
            assertThat(adapter.getHoverInfo(URI, 0, source.indexOf("value)"))).contains("Int")
            assertThat(adapter.getInlayHints(URI, Range(Position(0, source.indexOf("->")), Position(0, source.indexOf("->") + 2))))
                .hasSize(1)
        }
    }

    @Test
    fun `explicit lambda parameters omit type hints but retain the inferred return`() {
        val source = "module Inferred { void run() { function String(String) fn = (String value) -> value; } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getInlayHints(URI, ALL).map { it.label }).containsExactly(": String")
        }
    }

    @Test
    fun `destructured declarations show their independently inferred result types`() {
        val source = "module Inferred { (Int, String) pair() = (1, \"x\"); void run() { (var number, var text) = pair(); } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getInlayHints(URI, ALL).map { it.label }).containsExactly(": Int", ": String")
            assertThat(adapter.getHoverInfo(URI, 0, source.indexOf("number,"))).contains("Int")
            assertThat(adapter.getHoverInfo(URI, 0, source.indexOf("text)"))).contains("String")
        }
    }

    @Test
    fun `replacement failure repair and close never retain inferred lambda facts`() {
        val source = "module Inferred { void run() { function Int(Int) fn = (value) -> value; } }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getInlayHints(URI, ALL).map { it.label }).containsExactly(": Int", ": Int")
            assertThat(adapter.compile(URI, source.replace("-> value", "-> missing")).success).isFalse()
            assertThat(adapter.getInlayHints(URI, ALL)).isEmpty()
            assertThat(adapter.compile(URI, source.replace("Int", "String")).diagnostics).isEmpty()
            assertThat(adapter.getInlayHints(URI, ALL).map { it.label }).containsExactly(": String", ": String")
            adapter.closeDocument(URI)
            assertThat(adapter.getInlayHints(URI, ALL)).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Inferred.x"
        val ALL = Range(Position(0, 0), Position(100, 0))
    }
}
