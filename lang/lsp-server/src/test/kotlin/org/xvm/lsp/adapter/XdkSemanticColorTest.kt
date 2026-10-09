package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.treesitter.SemanticTokenLegend
import java.nio.file.Path
import kotlin.io.path.readText

class XdkSemanticColorTest {
    @Test
    fun `shared highlighting improvements preserve lexical gaps through damage and repair`() {
        val scenarios =
            JsonParser
                .parseString(root.resolve("lang/test-fixtures/compiler-playbook/scenarios.json").readText())
                .asJsonObject
                .getAsJsonObject("cases")
        XdkAdapter().use { adapter ->
            val categories = scenarios.getAsJsonObject("X276").getAsJsonObject("values")
            val source = categories["source"].asString
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(URI)))
            categories.getAsJsonArray("tokens").forEach { item ->
                val expected = item.asJsonObject
                val offset = source.indexOf(expected["anchor"].asString) + expected["offset"].asInt
                val line = source.take(offset).count { it == '\n' }
                val column = source.take(offset).substringAfterLast('\n').length
                val token = tokens.single { it.line == line && it.column == column }
                assertThat(token.text).isEqualTo(expected["text"].asString)
                assertThat(token.kind).isEqualTo(expected["type"].asString)
                assertThat("defaultLibrary" in token.modifiers).isEqualTo(expected["library"].asBoolean)
            }
            val lexical = scenarios.getAsJsonObject("X277").getAsJsonObject("values")
            val text = lexical["source"].asString
            assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
            val before = requireNotNull(adapter.getSemanticTokens(URI))
            val initial = decode(text, before)
            lexical.getAsJsonArray("lexical").forEach { item ->
                val start = text.indexOf(item.asString)
                val line = text.take(start).count { it == '\n' }
                val column = text.take(start).substringAfterLast('\n').length
                assertThat(initial).noneMatch {
                    it.line == line && it.column < column + item.asString.length && it.column + it.text.length > column
                }
            }
            assertThat(initial).anySatisfy {
                assertThat(it.text).isEqualTo("value")
                assertThat(it.line).isEqualTo(text.lines().indexOfFirst { line -> "interpolated()" in line })
                assertThat(it.kind).isEqualTo("property")
            }
            lexical.getAsJsonArray("recovery").forEach { item ->
                val edit = item.asJsonObject
                val damaged = text.replace(edit["from"].asString, edit["to"].asString)
                assertThat(adapter.compile(URI, damaged).success).isFalse()
                val current = adapter.getSemanticTokens(URI)?.let { decode(damaged, it) }.orEmpty()
                assertThat(current.filter { it.kind != "comment" }).allSatisfy {
                    assertThat(it.text).matches("[A-Za-z_][A-Za-z_0-9]*")
                }
                assertThat(adapter.compile(URI, text).diagnostics).isEmpty()
                assertThat(adapter.getSemanticTokens(URI)).isEqualTo(before)
            }
        }
    }

    @Test
    fun `annotations use resolved identities without recoloring ordinary type uses`() {
        val source =
            """
            module Annotations {
                import ecstasy.annotations.Override as Replaces;
                class Base { String describe() = "base"; }
                annotation Marked into Object {}
                @Marked class Child extends Base {
                    @Replaces String describe() = "child";
                }
                @ecstasy.annotations.Override String toString() = "annotations";
                class Other {
                    class Override {}
                    Override echo(Override value) = value;
                }
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(URI)))
            assertThat(tokens.filter { it.kind == "decorator" }.map { it.text })
                .containsExactly("Marked", "Replaces", "Override")
            assertThat(tokens.filter { it.kind == "decorator" && "defaultLibrary" in it.modifiers }.map { it.text })
                .containsExactly("Replaces", "Override")
            assertThat(tokens.filter { it.text == "Override" && it.line >= 8 })
                .isNotEmpty()
                .allSatisfy {
                    assertThat(it.kind).isEqualTo("class")
                    assertThat(it.modifiers).doesNotContain("defaultLibrary")
                }
        }
    }

    @Test
    fun `library modifier follows bundled ownership not type spelling or any binary dependency`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance().compileModule(
                Source("module External { class JsonObject {} }", "file:///External.x"),
                null,
                errors,
            )
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val binary = XdkDependency.fromBinary(compilation.toDependency().bytes())
        val source =
            """
            module Ownership {
                package json import json.xtclang.org;
                package external import External;
                import json.JsonObject as Document;
                class JsonObject {}
                Document library(Document value) = value;
                JsonObject local(JsonObject value) = value;
                external.JsonObject binary(external.JsonObject value) = value;
                List<String> core(List<String> value) = value;
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(binary))
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(URI)))
            assertThat(tokens.filter { it.text in setOf("Document", "List", "String") })
                .isNotEmpty()
                .allSatisfy { assertThat(it.modifiers).contains("defaultLibrary") }
            assertThat(tokens.filter { it.text == "JsonObject" && it.line >= 4 })
                .hasSize(5)
                .allSatisfy { assertThat(it.modifiers).doesNotContain("defaultLibrary") }
            assertThat(tokens.filter { it.text == "value" })
                .isNotEmpty()
                .allSatisfy { assertThat(it.modifiers).doesNotContain("defaultLibrary") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["class Marked {}", "mixin Marked into Object {}", ""])
    fun `annotation syntax alone never supplies decorator semantics`(declaration: String) {
        val source =
            """
            module InvalidAnnotation {
                $declaration
                @Marked class Child {}
            }
            """.trimIndent()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).success).isFalse()
            val tokens = adapter.getSemanticTokens(URI)?.let { decode(source, it) }.orEmpty()
            assertThat(tokens).noneMatch { it.kind == "decorator" }
        }
    }

    @Test
    fun `real platform buffer distinguishes resolved categories and lexical text`() {
        val source = javaClass.getResource("/platform/CircularBuffer.x")!!.readText()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val tokens = decode(source, requireNotNull(adapter.getSemanticTokens(URI)))
            listOf(
                "CircularBuffer" to "class",
                "Element" to "typeParameter",
                "UniformIndexed" to "interface",
                "Iterator" to "interface",
                "contents" to "property",
                "indexFor" to "method",
            ).forEach { (name, kind) ->
                assertThat(tokens.filter { it.text == name })
                    .describedAs(name)
                    .isNotEmpty()
                    .allSatisfy { assertThat(it.kind).isEqualTo(kind) }
            }
            // Identical spelling has different roles; a lexical naming rule cannot decide these.
            assertThat(tokens.filter { it.text == "index" }.map { it.kind })
                .contains("parameter", "property", "variable")
            assertThat(tokens.filter { it.text == "head" && "modification" in it.modifiers }).isNotEmpty()
            assertThat(tokens).anySatisfy {
                assertThat(it.kind).isEqualTo("comment")
                assertThat(it.modifiers).contains("documentation")
            }
            assertThat(tokens).noneMatch { it.text == "\"[]\"" } // Preserve the lexical annotation argument scope.
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["this.CircularBuffer.si", "this.CircularBuffer."])
    fun `platform editing never reuses stale tokens and repair restores classification`(replacement: String) {
        val source = javaClass.getResource("/platform/CircularBuffer.x")!!.readText()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val before = requireNotNull(adapter.getSemanticTokens(URI))
            val changed = "// Shift every declaration while editing.\n" + source.replace("this.CircularBuffer.size", replacement)
            assertThat(adapter.compile(URI, changed).success).isFalse()
            // If recovery cannot build an AST, an empty result lets the editor use TextMate.
            val tokens = adapter.getSemanticTokens(URI)?.let { decode(changed, it) }.orEmpty()
            assertThat(tokens.filter { it.kind != "comment" }).noneMatch { it.line == 0 }
            assertThat(tokens.filter { it.kind in setOf("class", "property", "method", "typeParameter") })
                .allSatisfy { assertThat(it.text).matches("[A-Za-z_][A-Za-z_0-9]*") }
            assertThat(tokens).noneMatch { it.text == "si" }
            tokens.zipWithNext().forEach { (previous, next) ->
                assertThat(previous.line < next.line || previous.column + previous.text.length <= next.column)
                    .describedAs("tokens must not overlap: %s then %s", previous, next)
                    .isTrue()
            }
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getSemanticTokens(URI)).isEqualTo(before)
        }
    }

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
