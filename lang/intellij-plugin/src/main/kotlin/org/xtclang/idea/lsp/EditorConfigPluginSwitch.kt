package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.editorconfig.settings.EditorConfigListener
import org.editorconfig.settings.EditorConfigSettings

/** The EditorConfig plugin's switch, registered only while that plugin is loaded (xtc-editorconfig.xml). */
internal class EditorConfigPluginSwitch : EditorConfigSwitch {
    override fun enabled(settings: CodeStyleSettings): Boolean = settings.getCustomSettings(EditorConfigSettings::class.java).ENABLED

    override fun onChange(
        project: Project,
        parent: Disposable,
        changed: () -> Unit,
    ) {
        project.messageBus.connect(parent).subscribe(EditorConfigSettings.EDITOR_CONFIG_ENABLED_TOPIC, EditorConfigListener { changed() })
    }
}
