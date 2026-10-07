package org.xtclang.idea

import com.intellij.lang.Language

/**
 * IntelliJ [Language] registration for XTC (Ecstasy).
 *
 * This is a minimal Language singleton that enables IntelliJ platform features requiring a Language
 * instance, such as Code Style settings. plugin.xml explicitly registers TextMate's syntax and
 * editor highlighter providers for this language/file type, using [XtcTextMateBundleProvider].
 *
 * **Important:** The Language ID must NOT be `"xtc"` — that ID is used by the TextMate bundle
 * (package.json `languages[0].id`). If both use the same ID, IntelliJ associates `.x` files with
 * this Language instead of TextMate. Using `"Ecstasy"` keeps the bundle language and native file
 * ownership distinct while anchoring Code Style settings. Native ownership requires the explicit
 * highlighter registrations; registering the bundle alone does not supply a lexical highlighter.
 */
object XtcIntelliJLanguage : Language("Ecstasy") {
    private fun readResolve(): Any = XtcIntelliJLanguage

    override fun getDisplayName(): String = "Ecstasy"

    override fun isCaseSensitive(): Boolean = true
}
