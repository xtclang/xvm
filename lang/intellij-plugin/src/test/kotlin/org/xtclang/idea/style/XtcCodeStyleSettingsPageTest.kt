package org.xtclang.idea.style

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Settings | Editor | Code Style | Ecstasy exists: the platform makes a language's Code Style page only for a provider
 * whose class overrides createConfigurable, and leaves out the others, whose settings then cannot be edited.
 */
class XtcCodeStyleSettingsPageTest {
    @Test
    fun `the provider makes its own Code Style page`() {
        val method =
            XtcLanguageCodeStyleSettingsProvider::class.java
                .getMethod("createConfigurable", CodeStyleSettings::class.java, CodeStyleSettings::class.java)
        assertThat(method.declaringClass)
            .isNotEqualTo(LanguageCodeStyleSettingsProvider::class.java)
            .isEqualTo(XtcLanguageCodeStyleSettingsProvider::class.java)
    }
}
