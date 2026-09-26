package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.treesitter.SemanticTokenLegend

class XdkPresentationTest {
    @Test
    fun `semantic tokens classify resolved declarations references modifiers and writes`() {
        val source =
            """
            module Presentation {
                interface /*api*/Reader { Int read(); }
                class Box { Int value = 0; }
                static Int /*method*/run(Box /*parameter*/box) {
                    Int /*local*/count = 0;
                    /*write*/count = 1;
                    /*compound*/count += 2;
                    ++/*increment*/count;
                    /*receiver*/box. /*field*/value = /*read*/count;
                    return box.value;
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val tokens = decode(adapter.getSemanticTokens(URI)!!)

            fun token(marker: String): List<Int> = tokens.single { it.take(2) == at(source, marker).let { listOf(it.line, it.column) } }
            assertThat(SemanticTokenLegend.tokenTypes[token("api")[3]]).isEqualTo("interface")
            assertThat(SemanticTokenLegend.tokenTypes[token("parameter")[3]]).isEqualTo("parameter")
            assertThat(SemanticTokenLegend.tokenTypes[token("local")[3]]).isEqualTo("variable")
            assertThat(token("method")[4] and SemanticTokenLegend.modifierBitmask("static", "declaration"))
                .isEqualTo(SemanticTokenLegend.modifierBitmask("static", "declaration"))
            listOf("write", "compound", "increment", "field").forEach {
                assertThat(token(it)[4] and SemanticTokenLegend.modifierBitmask("modification")).isNotZero()
            }
            listOf("receiver", "read").forEach {
                assertThat(token(it)[4] and SemanticTokenLegend.modifierBitmask("modification")).isZero()
            }
            val local = at(source, "local")
            val highlights = adapter.getDocumentHighlights(URI, local.line, local.column)
            assertThat(highlights.map { it.kind }).containsExactly(
                DocumentHighlight.HighlightKind.TEXT,
                DocumentHighlight.HighlightKind.WRITE,
                DocumentHighlight.HighlightKind.WRITE,
                DocumentHighlight.HighlightKind.WRITE,
                DocumentHighlight.HighlightKind.READ,
            )
        }
    }

    @Test
    fun `hints use selected parameter mapping omit named and defaulted arguments and show inferred locals`() {
        val source =
            """
            module Presentation {
                Int pick(Int first, Int second = 2) = first + second;
                String pick(String text) = text;
                Int run() {
                    var /*inferred*/number = pick(/*positional*/1);
                    val /*text*/label = pick(/*string*/"text");
                    Int explicit = pick(second=3, first=4);
                    return number + explicit + label.size;
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val hints = adapter.getInlayHints(URI, ALL)
            assertThat(hints.map { it.label }).containsExactly(": Int", "first:", ": String", "text:")
            assertThat(hints.map { it.position }).containsExactly(
                at(source, "inferred").let { it.copy(column = it.column + "number".length) },
                at(source, "positional"),
                at(source, "text").let { it.copy(column = it.column + "label".length) },
                at(source, "string"),
            )
            assertThat(adapter.getInlayHints(URI, Range(Position(5, 0), Position(6, 0))).map { it.label })
                .containsExactly(": String", "text:")
            val readonly =
                decode(adapter.getSemanticTokens(URI)!!).single {
                    it.take(2) == at(source, "text").let { listOf(it.line, it.column) }
                }
            assertThat(readonly[4] and SemanticTokenLegend.modifierBitmask("readonly")).isNotZero()
        }
    }

    @Test
    fun `captured reads retain parameter identity and shadowed names stay separate`() {
        val source =
            """
            module Presentation {
                Int run(Int /*outer*/value) {
                    function Int() fn = () -> /*capture*/value;
                    function Int(Int) other = (Int value) -> value;
                    return fn() + other(1);
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val outer = at(source, "outer")
            val highlights = adapter.getDocumentHighlights(URI, outer.line, outer.column)
            assertThat(highlights.map { it.range.start }).containsExactly(outer, at(source, "capture"))
            val capture =
                decode(adapter.getSemanticTokens(URI)!!).single {
                    it.take(2) == at(source, "capture").let { listOf(it.line, it.column) }
                }
            assertThat(SemanticTokenLegend.tokenTypes[capture[3]]).isEqualTo("parameter")
        }
    }

    @Test
    fun `failed inference never becomes an Object type hint or a token for the unresolved name`() {
        XdkAdapter().use { adapter ->
            val source = "module Presentation { void run() { var value=/*unknown*/missing; } }"
            assertThat(adapter.compile(URI, source).success).isFalse()
            assertThat(adapter.getInlayHints(URI, ALL)).isEmpty()
            val unknown = at(source, "unknown")
            assertThat(decode(adapter.getSemanticTokens(URI)!!).map { it.take(2) })
                .doesNotContain(listOf(unknown.line, unknown.column))
        }
    }

    @Test
    fun `failed replacement and close never keep previous tokens or hints`() {
        val source = "module Presentation { Int run() { var count = 1; return count; } }"
        withSource(source) { adapter ->
            assertThat(adapter.getInlayHints(URI, ALL)).hasSize(1)
            assertThat(adapter.compile(URI, "module Presentation {").success).isFalse()
            assertThat(adapter.getInlayHints(URI, ALL)).isEmpty()
            // Current lexical keywords remain; no previous resolved names may leak into the failed replacement.
            assertThat(
                adapter
                    .getSemanticTokens(URI)
                    ?.let(::decode)
                    .orEmpty()
                    .map { it.take(2) },
            ).doesNotContain(listOf(0, source.indexOf("count")))
            adapter.closeDocument(URI)
            assertThat(adapter.getSemanticTokens(URI)).isNull()
        }
    }

    private fun withSource(
        source: String,
        test: (XdkAdapter) -> Unit,
    ) {
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, source)
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
            test(adapter)
        }
    }

    private fun at(
        source: String,
        marker: String,
    ): Position {
        val token = "/*$marker*/"
        val offset = source.indexOf(token).also { check(it >= 0) } + token.length
        return Position(source.take(offset).count { it == '\n' }, offset - source.lastIndexOf('\n', offset - 1) - 1)
    }

    private fun decode(tokens: SemanticTokens): List<List<Int>> =
        tokens.data
            .chunked(5)
            .runningFold(listOf(0, 0, 0, 0, 0)) { previous, delta ->
                listOf(previous[0] + delta[0], if (delta[0] == 0) previous[1] + delta[1] else delta[1]) + delta.drop(2)
            }.drop(1)

    private companion object {
        const val URI = "file:///Presentation.x"
        val ALL = Range(Position(0, 0), Position(100, 0))
    }
}
