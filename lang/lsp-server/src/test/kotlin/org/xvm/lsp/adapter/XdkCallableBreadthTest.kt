package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

/** Callable patterns used by platform callbacks; only a resolved function type authorizes a call. */
class XdkCallableBreadthTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "function Int(Int) fn = &inc; fn(§);",
            "function Int(Int)[] callbacks = [n -> n + 1]; callbacks[0](§);",
            "function Int(Int)? fn = candidate; if (fn != Null) { fn(§); }",
            "make()(§);",
        ],
    )
    fun `function values returned indexed captured and narrowed preserve signatures and argument fitting`(body: String) {
        val marked =
            "module Editing { Int inc(Int n) = n + 1; function Int(Int) make() = n -> n + 1; " +
                "void run(function Int(Int)? candidate) { $body } }"
        val at = marked.indexOf('§')
        val source = marked.replace("§", "")
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, marked.replace("§", "0")).diagnostics).describedAs(body).isEmpty()
            val cached = adapter.compile(URI, source)
            val help = adapter.getSignatureHelp(URI, 0, at)!!
            assertThat(
                help.signatures
                    .single()
                    .parameters
                    .single()
                    .label,
            ).isEqualTo("Int")
            assertThat(help.activeParameter).isZero()
            assertThat(adapter.getCompletions(URI, 0, at).map { it.label }).contains("0").doesNotContain("True", "Null", "\"\"")
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
