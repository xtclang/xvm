package org.xtclang.idea

import com.intellij.lang.Language

/**
 * IntelliJ [Language] registration for XTC (Ecstasy).
 *
 * This is a minimal Language singleton that enables IntelliJ platform features
 * requiring a Language instance, such as Code Style settings. Syntax highlighting
 * is provided by the TextMate bundle ([XtcTextMateBundleProvider]), not by this
 * Language registration.
 *
 * **Important:** The Language ID must NOT be `"xtc"` — that ID is used by the TextMate
 * bundle (package.json `languages[0].id`). If both use the same ID, IntelliJ associates
 * `.x` files with this Language instead of TextMate, breaking syntax highlighting
 * (white background, no colors) and indentation (no PSI context for indent rules).
 * Using `"Ecstasy"` avoids the collision while still anchoring Code Style settings.
 */
object XtcIntelliJLanguage : Language("Ecstasy") {
    private fun readResolve(): Any = XtcIntelliJLanguage

    override fun getDisplayName(): String = "Ecstasy"

    override fun isCaseSensitive(): Boolean = true
}
