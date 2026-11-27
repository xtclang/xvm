package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import org.assertj.core.api.Assertions.assertThat
import org.editorconfig.settings.EditorConfigSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor

/** The formatter's `.editorconfig` switch follows IntelliJ's own, from the EditorConfig plugin that xtc-editorconfig.xml uses. */
class EditorConfigSwitchTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `the switch the server gets is IntelliJ's Enable EditorConfig support`() {
        val settings = CodeStyle.getSettings(editor.fixture.project)
        val plugin = settings.getCustomSettings(EditorConfigSettings::class.java)
        val before = plugin.ENABLED
        try {
            assertThat(EditorConfigSwitch.EP_NAME.extensionList).hasSize(1)
            plugin.ENABLED = true
            assertThat(EditorConfigSwitch.enabled(settings)).isTrue()
            plugin.ENABLED = false
            assertThat(EditorConfigSwitch.enabled(settings)).isFalse()
        } finally {
            plugin.ENABLED = before
        }
    }
}
