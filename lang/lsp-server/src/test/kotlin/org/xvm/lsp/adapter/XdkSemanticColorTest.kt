package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.treesitter.SemanticTokenLegend
import java.nio.file.Path
import kotlin.io.path.readText

class XdkSemanticColorTest {
    @Test
    fun `demo classifies resolved identities regardless of spelling or source availability`() {
        val source = root.resolve("lang/test-fixtures/semantic-highlighting/SemanticColors.x").readText()
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, source)
            assertThat(result.diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(URI)))
            listOf(
                "counter" to "class",
                "Reader" to "interface",
                "Mode" to "enum",
                "Quiet" to "enumMember",
                "Loud" to "enumMember",
                "Count" to "parameter",
                "count" to "property",
                "next" to "variable",
                "create" to "function",
                "read" to "method",
                // List comes from the bundled binary library, with no local type declaration.
                "List" to "interface",
            ).forEach { (name, kind) ->
                assertThat(tokens.filter { it.text == name })
                    .describedAs(name)
                    .isNotEmpty()
                    .allSatisfy { assertThat(it.kind).isEqualTo(kind) }
            }
            assertThat(tokens.filter { it.text == "Quiet" }).hasSize(2)
            assertThat(tokens.filter { it.text == "create" }).hasSize(2)
            assertThat(tokens.filter { it.text == "create" })
                .allSatisfy { assertThat(it.modifiers).contains("static") }
            assertThat(tokens.single { it.text == "construct" }.kind).isEqualTo("method")
            assertThat(tokens.single { it.text.startsWith("/**") }.modifiers).containsExactly("documentation")
            assertThat(tokens.single { it.text == "count" && "modification" in it.modifiers }.line)
                .isEqualTo(source.lines().indexOfFirst { "count += 1" in it })
        }
    }

    @Test
    fun `shared semantic playbook classifications remain valid`() {
        val cases =
            JsonParser
                .parseString(root.resolve("lang/test-fixtures/compiler-playbook/scenarios.json").readText())
                .asJsonObject
                .getAsJsonObject("cases")
        val data = cases.getAsJsonObject("X154").getAsJsonObject("values")
        val source = data["source"].asString
        val uri = "file:///${data["file"].asString}"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(uri)))
            data.getAsJsonArray("tokenKinds").forEach { row ->
                val expected = row.asJsonObject
                assertThat(tokens).anySatisfy {
                    assertThat(it.text).isEqualTo(expected["name"].asString)
                    assertThat(it.kind).isEqualTo(expected["type"].asString)
                }
            }
        }
    }

    private data class Token(
        val line: Int,
        val column: Int,
        val text: String,
        val kind: String,
        val modifiers: Set<String>,
    )

    private fun decode(
        source: String,
        tokens: SemanticTokens,
    ): List<Token> {
        val lines = source.lines()
        return tokens.data
            .chunked(5)
            .runningFold(Token(0, 0, "", "", emptySet())) { previous, delta ->
                val line = previous.line + delta[0]
                val column = (if (delta[0] == 0) previous.column else 0) + delta[1]
                Token(
                    line,
                    column,
                    lines[line].substring(column, column + delta[2]),
                    SemanticTokenLegend.tokenTypes[delta[3]],
                    SemanticTokenLegend.tokenModifiers.filterIndexed { index, _ -> delta[4] and (1 shl index) != 0 }.toSet(),
                )
            }.drop(1)
    }

    private companion object {
        const val URI = "file:///SemanticColors.x"
        val root: Path = Path.of(System.getProperty("xtc.composite.root"))
    }
}
