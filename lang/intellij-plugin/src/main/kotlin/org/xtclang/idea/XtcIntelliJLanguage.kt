package org.xtclang.idea

import com.intellij.lang.Language

/**
 * IntelliJ [Language] registration for XTC (Ecstasy).
 *
 * This is a minimal Language singleton that enables IntelliJ platform features
 * requiring a Language instance, such as Code Style settings. Syntax highlighting
 * is provided by the TextMate bundle ([XtcTextMateBundleProvider]), not by this
 * Language registration. The two coexist: TextMate handles token coloring while
 * the Language provides structural features (code style, indentation settings).
 */
object XtcIntelliJLanguage : Language("xtc") {
    private fun readResolve(): Any = XtcIntelliJLanguage

    override fun getDisplayName(): String = "Ecstasy"

    override fun isCaseSensitive(): Boolean = true
}
