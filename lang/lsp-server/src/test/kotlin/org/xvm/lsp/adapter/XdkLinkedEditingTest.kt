package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename

class XdkLinkedEditingTest {
    @ParameterizedTest
    @ValueSource(strings = ["(Int input) -> input", "input -> input"])
    fun `lambda parameters link only their own declaration and uses`(lambda: String) {
        val text =
            """
            module Linked {
                Int run() {
                    function Int(Int) first = $lambda;
                    function Int(Int) second = $lambda;
                    return first(1) + second(2);
                }
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            listOf(2, 3).forEach { line ->
                val ranges = requireNotNull(adapter.getLinkedEditingRanges(URI, line, text.lines()[line].indexOf("input"))).ranges
                assertThat(ranges).hasSize(2)
                assertThat(ranges.map { it.start.line }).containsOnly(line)
            }
            adapter.compile(URI, text.replace("first(1)", "missing(1)"))
            assertThat(adapter.getLinkedEditingRanges(URI, 2, text.lines()[2].indexOf("input"))).isNull()
            adapter.closeDocument(URI)
            assertThat(adapter.getLinkedEditingRanges(URI, 3, text.lines()[3].indexOf("input"))).isNull()
        }
    }

    @Test
    fun `nested lambda capture preserves lexical identity and UTF16 positions`() {
        val text =
            """
            module Linked {
                Int run() {
                    /* 😀 */ function Int(Int) first = (Int input) -> {
                        function Int() nested = () -> input;
                        return nested();
                    };
                    function Int(Int) second = (Int input) -> input;
                    return first(1) + second(2);
                }
            }
            """.trimIndent().replace("\n", "\r\n")
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            val at = XdkRename.position(text, text.indexOf("input"))
            val ranges = requireNotNull(adapter.getLinkedEditingRanges(URI, at.line, at.column)).ranges
            assertThat(ranges.map { it.start }).containsExactly(at, XdkRename.position(text, text.indexOf("input;")))
        }
    }

    @Test
    fun `callable parameters and member names require graph rename rather than linked editing`() {
        val text =
            """
            module Linked {
                private Int pick(Int privateInput) = privateInput;
                Int exposed(Int publicInput) = publicInput;
                Int run() = pick(privateInput = 1) + exposed(publicInput = 2);
                class Box(Int value) { Int read() = value; }
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            listOf("privateInput", "publicInput", "value", "pick", "exposed").forEach { name ->
                val at = XdkRename.position(text, text.indexOf(name))
                assertThat(adapter.getLinkedEditingRanges(URI, at.line, at.column)).describedAs(name).isNull()
            }
        }
    }

    @Test
    fun `explicit same-spelling aliases retain their lexical ownership`() {
        val text =
            """
            module Linked {
                package types { class Box {} }
                Int first() { import types.Box as Box; Box value = new Box(); return 1; }
                Int second() { import types.Box as Box; Box value = new Box(); return 2; }
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            listOf(2, 3).forEach { line ->
                val at = text.lines()[line].indexOf("as Box") + 3
                val ranges = requireNotNull(adapter.getLinkedEditingRanges(URI, line, at)).ranges
                assertThat(ranges).hasSize(3)
                assertThat(ranges.map { it.start.line }).containsOnly(line)
                assertThat(ranges.first().start.column).isEqualTo(at)
            }
        }
    }

    private companion object {
        const val URI = "untitled:Linked.x"
    }
}
