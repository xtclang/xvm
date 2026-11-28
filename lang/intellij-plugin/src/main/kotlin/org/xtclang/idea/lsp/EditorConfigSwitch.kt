package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * IntelliJ's switch for its EditorConfig support (**Code Style | General | Enable EditorConfig support**), which the
 * language server's formatter follows: it reads a file's `.editorconfig` files only while IntelliJ applies them. The
 * switch belongs to the bundled EditorConfig plugin, which a user can disable, so its implementation is registered only
 * while that plugin is loaded (xtc-editorconfig.xml); without it, IntelliJ applies no `.editorconfig` file.
 */
interface EditorConfigSwitch {
    fun enabled(settings: CodeStyleSettings): Boolean

    /** Calls [changed] whenever the switch is turned, until [parent] is disposed. */
    fun onChange(
        project: Project,
        parent: Disposable,
        changed: () -> Unit,
    )

    companion object {
        val EP_NAME = ExtensionPointName<EditorConfigSwitch>("org.xtclang.idea.editorConfigSwitch")

        /** Whether IntelliJ applies `.editorconfig` files: never while the EditorConfig plugin is not loaded. */
        fun enabled(settings: CodeStyleSettings): Boolean = EP_NAME.extensionList.any { it.enabled(settings) }
    }
}
