package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.URI

class SourceGraphConfigurationTest {
    private val base = URI("file:///workspace/")
    private val before = listOf(SourceModuleConfiguration("Library", "file:///workspace/Library.x"))
    private val after = listOf(SourceModuleConfiguration("Renamed", "file:///workspace/Renamed.x"))
    private val original = """{"xtc":{"compiler":{"sourceModules":[{"name":"Library","uri":"Library.x"}],"other":42},"formatting":null}}"""

    @Test
    fun `replacement and undo preserve unrelated settings and omitted dependency lists`() {
        val changed = SourceGraphConfiguration.replace(original, before, after, base)
        assertThat(changed).contains("Renamed", "42", "\"formatting\": null")
        val restored = SourceGraphConfiguration.replace(changed, after, before, base)
        val compiler =
            JsonParser
                .parseString(restored)
                .asJsonObject["xtc"]
                .asJsonObject["compiler"]
                .asJsonObject
        assertThat(
            compiler["sourceModules"]
                .asJsonArray
                .single()
                .asJsonObject["name"]
                .asString,
        ).isEqualTo("Library")
        assertThat(compiler["other"].asInt).isEqualTo(42)
        assertThat(SourceGraphConfiguration.replace(restored, before, after, base)).isEqualTo(changed)
    }

    @Test
    fun `a different graph cannot be overwritten by a late proposal or undo`() {
        assertThatThrownBy { SourceGraphConfiguration.replace(original.replace("Library", "Elsewhere"), before, after, base) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SourceGraphConfiguration.replace(original, after, before, base) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `malformed intervening settings become a guarded refusal`() {
        listOf("{", "[]", """{"xtc":1}""", original.replace("\"Library.x\"", "null"), original.replace("\"Library\"", "1"))
            .forEach { content ->
                assertThatThrownBy { SourceGraphConfiguration.replace(content, before, after, base) }
                    .isInstanceOf(IllegalArgumentException::class.java)
            }
    }

    @Test
    fun `discovery and duplicate graph entries are not equivalent to an explicit graph`() {
        assertThatThrownBy { SourceGraphConfiguration.replace("""{"xtc":{"compiler":{"sourceModules":null}}}""", before, after, base) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val entry = """{"name":"Library","uri":"Library.x"}"""
        assertThatThrownBy { SourceGraphConfiguration.replace(original.replace("[$entry]", "[$entry,$entry]"), before, after, base) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
