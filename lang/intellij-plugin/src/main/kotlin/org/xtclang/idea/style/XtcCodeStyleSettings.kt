package org.xtclang.idea.style

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/**
 * XTC-specific code style settings that extend IntelliJ's standard settings.
 *
 * These settings appear under Settings > Editor > Code Style > Ecstasy and control
 * XTC-specific formatting options beyond what [com.intellij.psi.codeStyle.CommonCodeStyleSettings]
 * provides. The LSP server reads these values when no project-level `xtc-format.toml` is present.
 */
class XtcCodeStyleSettings(
    container: CodeStyleSettings,
) : CustomCodeStyleSettings("XtcCodeStyleSettings", container) {
    /**
     * Continuation indent for `extends`, `implements`, `incorporates`, `delegates` lines.
     * XTC convention: double the normal indent (8 spaces by default).
     */
    @JvmField
    @Suppress("ktlint:standard:property-naming") // IntelliJ settings serialization requires UPPER_SNAKE_CASE
    var CONTINUATION_INDENT_SIZE: Int = 8
}
