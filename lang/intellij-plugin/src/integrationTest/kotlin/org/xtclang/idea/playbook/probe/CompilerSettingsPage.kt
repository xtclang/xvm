package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox
import org.xtclang.idea.lsp.CompilerProjectConfigurable

/** Exercise the real project settings component and its Apply/Reset contract on the EDT. */
object CompilerSettingsPage {
    private const val SERVER = "xtcLanguageServer"

    @JvmStatic
    fun installProjectGraph(project: Project) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val page = CompilerProjectConfigurable(project)
        val component = page.createComponent()
        check(!page.isModified())
        fun descendants(component: Component): Sequence<Component> = sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }
        val discovery = descendants(component).filterIsInstance<JCheckBox>().single()
        check(!discovery.isSelected)
        discovery.doClick()
        check(page.isModified())
        page.reset()
        check(!page.isModified() && !discovery.isSelected)
        // Copy the existing graph into the project store through the actual UI Apply path.
        page.apply()
        check(
            ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(SERVER)
                ?.configurationContent != null
        )
    }

    @JvmStatic
    fun content(project: Project): String? =
        ProjectLanguageServerSettings.getInstance(project)
            .getLanguageServerSettings(SERVER)
            ?.configurationContent
            ?: GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(SERVER)
                ?.configurationContent

    @JvmStatic
    fun clearProjectGraph(project: Project) {
        val store = ProjectLanguageServerSettings.getInstance(project)
        val current = store.getLanguageServerSettings(SERVER) ?: return
        val copy = XmlSerializerUtil.createCopy(current)
        copy.configurationContent = null
        store.updateSettings(SERVER, copy)
    }
}
