package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LanguageServiceConfigurationTest {
    @Test
    fun `adapter selection preserves inherited preferences and compiler paths`() {
        val global = """{"xtc":{"languageService":{"adapter":"compiler","inlayHints":false}}}"""
        val project = """{"xtc":{"compiler":{"sourceModules":[]},"languageService":{"futureSetting":17}}}"""
        val selected = LanguageServiceConfiguration.selectAdapter(project, LanguageAdapter.TREE_SITTER)
        val before = LanguageServiceConfiguration.read(global, project)
        val after = LanguageServiceConfiguration.read(global, selected)
        assertThat(after).isEqualTo(before.copy(adapter = "treesitter"))
        assertThat(selected).contains("\"sourceModules\": []", "\"futureSetting\": 17")
        assertThat(selected).doesNotContain("inlayHints")
        assertThat(after.requiresRestart(before)).isTrue()
        assertThat(after.requiresRestart(after)).isFalse()
        assertThat(after.copy(inlayHints = true).requiresRestart(after)).isFalse()
        assertThat(LanguageAdapter.TREE_SITTER.launchArguments()).containsExactly("-Dxtc.lsp.adapter=treesitter")
        assertThat(LanguageAdapter.COMPILER.launchArguments()).containsExactly("-Dxtc.lsp.adapter=compiler")
        assertThat(LanguageAdapter.DEFAULT.launchArguments()).isEmpty()
        val restored = LanguageServiceConfiguration.selectAdapter(selected, LanguageAdapter.DEFAULT)
        assertThat(LanguageServiceConfiguration.read(global, restored).adapter).isEqualTo("default")
    }

    @Test
    fun `service preferences do not claim ownership of an inherited source graph`() {
        assertThat(CompilerSettings.ownsGraph(null)).isFalse()
        assertThat(
            CompilerSettings.ownsGraph("""{"xtc":{"languageService":{"inlayHints":false,"referenceCodeLens":false}}}"""),
        ).isFalse()
        assertThat(CompilerSettings.ownsGraph("""{"xtc":{"compiler":{"sourceModules":[]}}}"""))
            .isTrue()
        assertThat(CompilerSettings.ownsGraph("{}")).isTrue()
        assertThat(CompilerSettings.ownsGraph("{broken")).isTrue()
    }

    @Test
    fun `project fields inherit global defaults without replacing the compiler graph`() {
        val global =
            """{"xtc":{"languageService":{"textSynchronization":"incremental","inlayHints":false,"referenceCodeLens":false}}}"""
        val project =
            """{"xtc":{"languageService":{"saveFormatting":"server"},"compiler":{"sourceModules":[]}}}"""
        assertThat(LanguageServiceConfiguration.read(global, project))
            .isEqualTo(LanguageServiceConfiguration("incremental", "server", false, false))
        val replaced =
            LanguageServiceConfiguration.replace(
                project,
                LanguageServiceConfiguration.section(project),
                null,
            )
        assertThat(replaced).contains("sourceModules")
        assertThat(LanguageServiceConfiguration.read(global, replaced))
            .isEqualTo(LanguageServiceConfiguration("incremental", "editor", false, false))
    }

    @Test
    fun `stale service edits refuse while concurrent graph edits are preserved`() {
        val original = """{"xtc":{"compiler":{"sourceModules":[]}}}"""
        val changedGraph = original.replace("[]", "null")
        val replaced =
            LanguageServiceConfiguration.replace(changedGraph, null, LanguageServiceConfiguration())
        assertThat(replaced).contains("\"sourceModules\": null")
        assertThatThrownBy { LanguageServiceConfiguration.replace(replaced, null, null) }
            .hasMessageContaining("Reset before applying")
    }

    @Test
    fun `invalid values fail before persistence and native save formatting wins`() {
        listOf(
            """{"xtc":{"languageService":{"adapter":"mock"}}}""",
            """{"xtc":{"languageService":{"adapter":null}}}""",
            """{"xtc":{"languageService":{"referenceCodeLens":"false"}}}""",
            "[]",
            "{",
            """{"xtc":{"languageService":{"textSynchronization":"patch"}}}""",
            """{"xtc":{"languageService":{"inlayHints":"false"}}}""",
        ).forEach { content ->
            assertThatThrownBy { LanguageServiceConfiguration.read(content) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        val server = LanguageServiceConfiguration("incremental", "server")
        assertThat(server.initializationOptions(true))
            .isEqualTo(
                mapOf("xtcDocumentSync" to mapOf("incremental" to true, "formatOnSave" to false)),
            )
        assertThat(server.initializationOptions(false))
            .isEqualTo(
                mapOf("xtcDocumentSync" to mapOf("incremental" to true, "formatOnSave" to true)),
            )
    }
}
