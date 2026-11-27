package org.xtclang.idea.style

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import javax.swing.JCheckBox

/**
 * Settings | Editor | Code Style | Ecstasy exists: the platform makes a language's Code Style page only for a provider
 * whose class overrides createConfigurable, and leaves out the others, whose settings then cannot be edited.
 */
class XtcCodeStyleSettingsPageTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `the provider makes its own Code Style page`() {
        val method =
            XtcLanguageCodeStyleSettingsProvider::class.java
                .getMethod("createConfigurable", CodeStyleSettings::class.java, CodeStyleSettings::class.java)
        assertThat(method.declaringClass)
            .isNotEqualTo(LanguageCodeStyleSettingsProvider::class.java)
            .isEqualTo(XtcLanguageCodeStyleSettingsProvider::class.java)
    }

    @Test
    fun `Tabs and Indents shows only the indentation options that the formatter honors`() {
        val checkBoxes =
            runInEdtAndGet {
                val panel = XtcLanguageCodeStyleSettingsProvider().indentOptionsEditor.createPanel()
                UIUtil.findComponentsOfType(panel, JCheckBox::class.java).associate { it.text to it.isVisible }
            }
        // the indent, continuation indent and tab size fields stay, and Use tab character, which the formatter honors
        assertThat(checkBoxes.filterValues { it }.keys).containsExactly("Use tab character")
        assertThat(checkBoxes.keys).contains("Smart tabs", "Keep indents on empty lines")
    }
}
