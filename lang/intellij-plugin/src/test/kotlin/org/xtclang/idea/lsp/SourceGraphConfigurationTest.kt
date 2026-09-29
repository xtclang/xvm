package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SourceGraphConfigurationTest {
    @Test
    fun `project configuration preserves unrelated settings and distinguishes discovery from empty graph`() {
        val explicit = SourceGraphConfiguration.configure(original, after, base)
        assertThat(SourceGraphConfiguration.read(explicit)).isEqualTo(after)
        assertThat(explicit).contains("42")
        val automatic = SourceGraphConfiguration.configure(explicit, null, base)
        assertThat(SourceGraphConfiguration.read(automatic)).isNull()
        assertThat(automatic).contains("\"sourceModules\": null", "42")
        val empty = SourceGraphConfiguration.configure(automatic, emptyList(), base)
        assertThat(SourceGraphConfiguration.read(empty)).isEmpty()
        assertThat(SourceGraphConfiguration.read(null)).isNull()
    }

    @Test
    fun `new project settings reject aliased roots and blank names before persistence`() {
        val graph =
            listOf(
                SourceModuleConfiguration("Library", "Library.x"),
                SourceModuleConfiguration("Other", "file:///workspace/Library.x"),
            )
        assertThatThrownBy { SourceGraphConfiguration.configure(null, graph, base) }
            .hasMessageContaining("Duplicate source module roots")
        assertThatThrownBy {
                SourceGraphConfiguration.configure(
                    null,
                    listOf(SourceModuleConfiguration("", "Library.x")),
                    base,
                )
            }
            .hasMessageContaining("non-blank")
        assertThat(
                SourceGraphConfiguration.read(
                    SourceGraphConfiguration.configure(null, before, base)
                )
            )
            .isEqualTo(before)
    }

    private val base = URI("file:///workspace/")
    private val before = listOf(SourceModuleConfiguration("Library", "file:///workspace/Library.x"))
    private val after = listOf(SourceModuleConfiguration("Renamed", "file:///workspace/Renamed.x"))
    private val original =
        """{"xtc":{"compiler":{"sourceModules":[{"name":"Library","uri":"Library.x"}],"other":42},"formatting":null}}"""

    @Test
    fun `replacement and undo preserve unrelated settings and omitted dependency lists`() {
        val changed = SourceGraphConfiguration.replace(original, before, after, base)
        assertThat(changed).contains("Renamed", "42", "\"formatting\": null")
        val restored = SourceGraphConfiguration.replace(changed, after, before, base)
        val compiler =
            JsonParser.parseString(restored)
                .asJsonObject["xtc"]
                .asJsonObject["compiler"]
                .asJsonObject
        assertThat(compiler["sourceModules"].asJsonArray.single().asJsonObject["name"].asString)
            .isEqualTo("Library")
        assertThat(compiler["other"].asInt).isEqualTo(42)
        assertThat(SourceGraphConfiguration.replace(restored, before, after, base))
            .isEqualTo(changed)
    }

    @Test
    fun `a different graph cannot be overwritten by a late proposal or undo`() {
        assertThatThrownBy {
                SourceGraphConfiguration.replace(
                    original.replace("Library", "Elsewhere"),
                    before,
                    after,
                    base,
                )
            }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SourceGraphConfiguration.replace(original, after, before, base) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `malformed intervening settings become a guarded refusal`() {
        listOf(
                "{",
                "[]",
                """{"xtc":1}""",
                original.replace("\"Library.x\"", "null"),
                original.replace("\"Library\"", "1"),
            )
            .forEach { content ->
                assertThatThrownBy {
                        SourceGraphConfiguration.replace(content, before, after, base)
                    }
                    .isInstanceOf(IllegalArgumentException::class.java)
            }
    }

    @Test
    fun `discovery and duplicate graph entries are not equivalent to an explicit graph`() {
        assertThatThrownBy {
                SourceGraphConfiguration.replace(
                    """{"xtc":{"compiler":{"sourceModules":null}}}""",
                    before,
                    after,
                    base,
                )
            }
            .isInstanceOf(IllegalArgumentException::class.java)
        val entry = """{"name":"Library","uri":"Library.x"}"""
        assertThatThrownBy {
                SourceGraphConfiguration.replace(
                    original.replace("[$entry]", "[$entry,$entry]"),
                    before,
                    after,
                    base,
                )
            }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `history preserves unrelated edits but refuses independently replaced dependency edges`() {
        val withEdge =
            original.replace(
                "\"uri\":\"Library.x\"",
                "\"uri\":\"Library.x\",\"dependencies\":[\"Other\"]",
            )
        val graph =
            listOf(
                SourceModuleConfiguration("Library", "file:///workspace/Library.x", listOf("Other"))
            )
        val changed =
            SourceGraphConfiguration.replace(withEdge, graph, after, base).replace("42", "17")
        val restored = SourceGraphConfiguration.replace(changed, after, graph, base)
        assertThat(restored).contains("17", "Other")
        assertThatThrownBy {
                SourceGraphConfiguration.replace(
                    restored.replace("Other", "Elsewhere"),
                    graph,
                    after,
                    base,
                )
            }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `absolute roots in separate projects preserve the complete graph and refuse duplicate roots`() {
        val graph =
            listOf(
                SourceModuleConfiguration("Library", "file:///libraries/Library.x"),
                SourceModuleConfiguration("App", "file:///apps/App.x", listOf("Library")),
            )
        val content =
            """
            {"xtc":{"compiler":{"sourceModules":[
                {"name":"Library","uri":"file:///libraries/Library.x"},
                {"name":"App","uri":"file:///apps/App.x","dependencies":["Library"]}
            ]}}}
            """
                .trimIndent()
        val next =
            listOf(
                SourceModuleConfiguration("Renamed", "file:///libraries/Renamed.x"),
                graph[1].copy(dependencies = listOf("Renamed")),
            )
        val changed = SourceGraphConfiguration.replace(content, graph, next, base)
        assertThat(changed).contains("file:///apps/App.x", "file:///libraries/Renamed.x")
        assertThat(SourceGraphConfiguration.replace(changed, next, graph, base))
            .contains("file:///libraries/Library.x")
        assertThatThrownBy {
                SourceGraphConfiguration.replace(
                    content.replace("file:///apps/App.x", "file:///libraries/Library.x"),
                    graph,
                    next,
                    base,
                )
            }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Duplicate source module roots")
    }
}
