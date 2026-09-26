package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkArgumentContextTest {
    @ParameterizedTest
    @ValueSource(strings = [
        "pair(nu§, \"x\")", "pair((nu§), \"x\")", "pair(((nu§)), \"x\")",
        "pair(§, \"x\")", "pair(first=nu§, second=\"x\")", "pair(first=§, second=\"x\")",
        "pair(box.nu§, \"x\")", "pair((box.nu§), \"x\")", "pair(first=box.nu§, second=\"x\")",
        "fn(nu§, \"x\")", "fn((nu§), \"x\")", "new Pair(nu§, \"x\")",
    ])
    fun `argument candidates fit the entire written call and preserve surrounding syntax`(call: String) {
        val marked = HEADER + call + "; } }"
        val at = marked.indexOf('§')
        val text = marked.replace("§", "")
        val typed = if (marked[at - 1] == 'u') 2 else 0
        XdkAdapter().use { adapter ->
            val cached = adapter.compile(URI, text)
            val items = adapter.getCompletions(URI, 0, at)
            assertThat(items.map { it.label }).describedAs(call).contains("number").doesNotContain("numberText")
            val edit = items.single { it.label == "number" }.textEdit
            assertThat(edit).isEqualTo(TextEdit(Range(Position(0, at - typed), Position(0, at)), "number"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(cached)
            assertThat(adapter.compile(URI, text.replaceRange(at - typed, at, "number")).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["pair(nu§, True)", "pair((nu§), True)", "pair(box.nu§, True)", "pair(first=nu§, unknown=1)"])
    fun `later incompatible arguments cannot produce a fitting suggestion`(call: String) {
        val marked = HEADER + call + "; } }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§'))).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Arguments.x"
        const val HEADER = "module Arguments { class Pair(Int number, String text) {} " +
            "class Box { Int number=1; String numberText=\"x\"; private Int numberHidden=2; } " +
            "void pair(Int number, String text) {} void run(Int number, String numberText, Box box, " +
            "function void(Int, String) fn) { "
    }
}
