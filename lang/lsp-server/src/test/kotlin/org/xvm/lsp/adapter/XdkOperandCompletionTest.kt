package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkOperandCompletionTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "module Operands { Int number = 1; String numberText = \"x\"; class Inner { " +
                "void pair(Int count) {} void run() { pair(nu§); } } }",
            "module Operands { class Values { static Int number = 1; static String numberText = \"x\"; } " +
                "import Values.number; import Values.numberText; void pair(Int count) {} void run() { pair(nu§); } }",
        ],
    )
    fun `enclosing and imported values still require ordinary read and argument validation`(marked: String) {
        val at = marked.indexOf('§')
        XdkAdapter().use { adapter ->
            val source = marked.replace("§", "")
            adapter.compile(URI, source)
            assertThat(adapter.getCompletions(URI, 0, at).map { it.label }).contains("number").doesNotContain("numberText")
            assertThat(adapter.compile(URI, source.replaceRange(at - 2, at, "number")).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "pair(1 + nu§, \"x\")", "pair(1 + §, \"x\")", "pair(-nu§, \"x\")",
            "pair(2 * (1 + nu§), \"x\")", "pair(number = 1 + nu§, text = \"x\")",
            "pair(1 + box.nu§, \"x\")", "fn(1 + nu§, \"x\")", "new Pair(1 + nu§, \"x\")",
        ],
    )
    fun `candidate insertion must fit the whole compound argument and later slots`(call: String) {
        val marked = XdkArgumentContextFixture.HEADER + call + "; } }"
        val at = marked.indexOf('§')
        val source = marked.replace("§", "")
        val prefix = if (marked[at - 1] == 'u') 2 else 0
        XdkAdapter().use { adapter ->
            val normal = adapter.compile(URI, source)
            val items = adapter.getCompletions(URI, 0, at)
            assertThat(items.map { it.label }).describedAs(call).contains("number").doesNotContain("numberText", "numberHidden")
            assertThat(items.single { it.label == "number" }.textEdit)
                .isEqualTo(TextEdit(Range(Position(0, at - prefix), Position(0, at)), "number"))
            assertThat(adapter.getCachedResult(URI)).isEqualTo(normal)
            assertThat(adapter.compile(URI, source.replaceRange(at - prefix, at, "number")).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["pair(1 + nu§, True)", "pair(-nu§, True)", "pair(number = 1 + nu§, unknown = 1)"])
    fun `incompatible later arguments reject compound suggestions`(call: String) {
        val marked = XdkArgumentContextFixture.HEADER + call + "; } }"
        XdkAdapter().use { adapter ->
            adapter.compile(URI, marked.replace("§", ""))
            assertThat(adapter.getCompletions(URI, 0, marked.indexOf('§'))).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["type.accept(nu§)", "box.acceptReceiver(nu§)"])
    fun `type value and receiver rewritten functions retain visible parameter mapping`(call: String) {
        val marked =
            "module Operands { class Box { static void accept(Int count) {} " +
                "static void acceptReceiver(Box receiver, Int count) {} } " +
                "void run(Type<Box> type, Box box, Int number, String numberText) { $call; } }"
        val at = marked.indexOf('§')
        XdkAdapter().use { adapter ->
            val source = marked.replace("§", "")
            adapter.compile(URI, source)
            assertThat(adapter.getCompletions(URI, 0, at).map { it.label }).contains("number").doesNotContain("numberText")
            val help = adapter.getSignatureHelp(URI, 0, at)
            assertThat(
                help
                    ?.signatures
                    ?.single()
                    ?.parameters
                    ?.map { it.label },
            ).containsExactly("Int count")
            assertThat(adapter.compile(URI, source.replaceRange(at - 2, at, "number")).diagnostics).isEmpty()
        }
    }

    private companion object {
        const val URI = "untitled:Operands.x"
    }
}

private object XdkArgumentContextFixture {
    const val HEADER =
        "module Operands { class Pair(Int number, String text) {} " +
            "class Box { Int number = 1; String numberText = \"x\"; private Int numberHidden = 2; } " +
            "void pair(Int number, String text) {} void run(Int number, String numberText, Box box, " +
            "function void(Int, String) fn) { "
}
