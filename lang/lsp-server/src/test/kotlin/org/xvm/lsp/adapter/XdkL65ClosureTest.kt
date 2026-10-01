package org.xvm.lsp.adapter

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.selectLibraryDeclaration
import org.xvm.lsp.treesitter.SemanticTokenLegend
import java.nio.file.Path

/** Backend assertions consume exactly the sources and locations used by both native drivers. */
class XdkL65ClosureTest {
    @Test
    fun `shared composed implementation variants navigate only to written bodies`() {
        val data = scenario("X153")
        val uri = "file:///${data["file"].asString}"
        XdkAdapter().use { adapter ->
            data.getAsJsonArray("variants").forEach { row ->
                val variant = row.asJsonObject
                val source = variant["source"].asString
                assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
                val at = position(source, variant["anchor"].asString, variant["offset"].asInt)
                val targets = adapter.findImplementation(uri, at.line, at.column)
                assertThat(targets.map { Position(it.startLine, it.startColumn) })
                    .describedAs(source)
                    .containsExactlyInAnyOrderElementsOf(variant.getAsJsonArray("targets").map { position(source, it.asString) })
                assertThat(targets).allMatch { it.uri == uri }
            }
        }
    }

    @Test
    fun `shared destructuring and indexed mutations distinguish reads from writes`() {
        val data = scenario("X154")
        val uri = "file:///${data["file"].asString}"
        val source = data["source"].asString
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            data.getAsJsonArray("accesses").forEach { row ->
                val access = row.asJsonObject
                val at = position(source, access["anchor"].asString, access["offset"].asInt)
                val highlights = adapter.getDocumentHighlights(uri, at.line, at.column)
                val highlight = highlights.single { it.range.start == at }
                assertThat(highlight.kind).describedAs(access.toString()).isEqualTo(
                    if (access["write"].asBoolean) {
                        DocumentHighlight.HighlightKind.WRITE
                    } else {
                        DocumentHighlight.HighlightKind.READ
                    },
                )
            }
            val tokens =
                requireNotNull(adapter.getSemanticTokens(uri))
                    .data
                    .chunked(5)
                    .fold(emptyList<List<Int>>()) { result, tuple ->
                        val previous = result.lastOrNull()
                        val line = (previous?.get(0) ?: 0) + tuple[0]
                        val column = (if (tuple[0] == 0) previous?.get(1) ?: 0 else 0) + tuple[1]
                        result + listOf(listOf(line, column, tuple[2], tuple[3], tuple[4]))
                    }
            data.getAsJsonArray("accesses").forEach { row ->
                val access = row.asJsonObject
                val at = position(source, access["anchor"].asString, access["offset"].asInt)
                val token = tokens.single { it[0] == at.line && it[1] == at.column }
                assertThat(token[4] and SemanticTokenLegend.modifierBitmask("modification") != 0)
                    .describedAs(access.toString())
                    .isEqualTo(access["write"].asBoolean)
            }
        }
    }

    @Test
    fun `binary overload matching requires unique debug source evidence`() {
        val declarations = listOf(3..7, 10..14)
        assertThat(selectLibraryDeclaration(declarations, 4) { it }).isEqualTo(3..7)
        assertThat(selectLibraryDeclaration(declarations, 14) { it }).isEqualTo(10..14)
        assertThat(selectLibraryDeclaration(declarations, null) { it }).isNull()
        assertThat(selectLibraryDeclaration(declarations, 8) { it }).isNull()
        assertThat(selectLibraryDeclaration(listOf(3..7, 7..10), 7) { it }).isNull()
        assertThat(selectLibraryDeclaration(listOf(3..7), null) { it }).isEqualTo(3..7)
    }

    private fun scenario(id: String): JsonObject {
        val file =
            Path
                .of(System.getProperty("xtc.composite.root"))
                .resolve("lang/test-fixtures/compiler-playbook/scenarios.json")
        return JsonParser
            .parseString(file.toFile().readText())
            .asJsonObject
            .getAsJsonObject("cases")
            .getAsJsonObject(id)
            .getAsJsonObject("values")
    }

    private fun position(
        source: String,
        anchor: String,
        offset: Int = 0,
    ): Position {
        val before = source.substring(0, source.indexOf(anchor) + offset)
        return Position(before.count { it == '\n' }, before.substringAfterLast('\n').length)
    }
}
