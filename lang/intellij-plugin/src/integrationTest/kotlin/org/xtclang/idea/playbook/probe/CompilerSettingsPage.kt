package org.xtclang.idea.playbook.probe

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import java.awt.Component
import java.awt.Container
import java.net.URI
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JTable
import org.xtclang.idea.lsp.CompilerBuildModel
import org.xtclang.idea.lsp.CompilerProjectConfigurable

/** Exercise the real project settings component and its Apply/Reset contract on the EDT. */
object CompilerSettingsPage {
    private const val SERVER = "xtcLanguageServer"

    @JvmStatic
    fun useBuildModel(project: Project): String {
        val page = CompilerProjectConfigurable(project)
        fun descendants(component: Component): Sequence<Component> = sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }
        descendants(page.createComponent()).filterIsInstance<JCheckBox>().single().isSelected = true
        if (page.isModified()) page.apply()
        CompilerBuildModel.publish(project)
        return CompilerBuildModel.describe(project)
    }

    @JvmStatic
    fun refreshBuildModel(project: Project) {
        val before =
            ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(SERVER)
                ?.configurationContent
        CompilerBuildModel.publish(project)
        check(
            ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(SERVER)
                ?.configurationContent == before
        )
    }

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
        val table = descendants(component).filterIsInstance<JTable>().single()
        val before =
            GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(SERVER)
                ?.configurationContent
        table.model.setValueAt("", 0, 0)
        check(runCatching { page.apply() }.exceptionOrNull() is ConfigurationException)
        check(
            GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(SERVER)
                ?.configurationContent == before
        )
        page.reset()
        // Edit actual table cells, preserving the graph's meaning while exercising relative roots.
        val root = Path.of(requireNotNull(project.basePath))
        (0 until table.rowCount).forEach { row ->
            val absolute = Path.of(URI(table.model.getValueAt(row, 1).toString()))
            table.model.setValueAt(root.relativize(absolute).toString(), row, 1)
        }
        check(page.isModified())
        page.apply()
        check(!page.isModified())
        check(
            GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(SERVER)
                ?.configurationContent == before
        )
        check(
            ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(SERVER)
                ?.configurationContent != null
        )
    }

    @JvmStatic
    fun dismissExpectedConfigurationError(project: Project) {
        NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)
            .filter { it.content.contains("Cyclic source dependencies:") }
            .forEach(Notification::expire)
    }

    @JvmStatic
    fun resourceRootsRoundTrip(project: Project, roots: String) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val page = CompilerProjectConfigurable(project)
        fun descendants(component: Component): Sequence<Component> = sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }
        val table = descendants(page.createComponent()).filterIsInstance<JTable>().single()
        val original = table.model.getValueAt(0, 3)
        table.model.setValueAt("[]", 0, 3)
        page.reset()
        check(table.model.getValueAt(0, 3) == original && !page.isModified())
        table.model.setValueAt(roots, 0, 3)
        page.apply()
        page.reset()
        check(table.model.getValueAt(0, 3) == roots && !page.isModified())
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
