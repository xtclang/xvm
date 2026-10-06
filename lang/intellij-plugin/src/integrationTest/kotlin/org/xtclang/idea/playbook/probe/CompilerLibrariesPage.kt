package org.xtclang.idea.playbook.probe

import com.google.gson.JsonParser
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import org.xtclang.idea.lsp.CompilerBuildModel
import org.xtclang.idea.lsp.CompilerProjectConfigurable
import java.awt.Component
import java.awt.Container
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JList
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

/** Operates the installed settings components; no replacement compiler settings implementation. */
object CompilerLibrariesPage {
    private const val SERVER = "xtcLanguageServer"

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    @JvmStatic
    fun content(project: Project): String? =
        ProjectLanguageServerSettings.getInstance(project).getLanguageServerSettings(SERVER)?.configurationContent

    @JvmStatic
    fun edit(
        project: Project,
        json: String,
        action: String,
    ): String? {
        val before = content(project)
        val page = CompilerProjectConfigurable(project)
        val component = page.createComponent()
        val values = JsonParser.parseString(json).asJsonObject
        val controls = descendants(component).toList()
        val inherit = controls.filterIsInstance<JCheckBox>().single { it.text == "Inherit binary libraries from Gradle" }
        inherit.isSelected = values["modulePath"].isJsonNull
        val list = controls.filterIsInstance<JList<*>>().single { it.name == "Binary library paths" }

        @Suppress("UNCHECKED_CAST")
        val paths = list.model as DefaultListModel<String>
        paths.clear()
        values["modulePath"].takeUnless { it.isJsonNull }?.asJsonArray?.forEach { paths.addElement(it.asString) }
        val table = controls.filterIsInstance<JTable>().single { it.name == "Library source attachments" }
        val rows = table.model as DefaultTableModel
        rows.rowCount = 0
        values["sourceAttachments"].asJsonArray.forEach { attachment ->
            val value = attachment.asJsonObject
            value["roots"].asJsonArray.forEach { rows.addRow(arrayOf(value["module"].asString, it.asString)) }
        }
        when (action) {
            "cancel" -> {
                check(content(project) == before)
            }

            "reset" -> {
                page.reset()
                check(!page.isModified())
                check(content(project) == before)
            }

            "invalid" -> {
                check(runCatching { page.apply() }.exceptionOrNull() is ConfigurationException)
                check(content(project) == before)
            }

            "order" -> {
                list.selectedIndex = 1
                controls.filterIsInstance<JButton>().single { it.text == "Up" }.doClick()
                page.apply()
                page.reset()
                check(!page.isModified())
            }

            "apply" -> {
                page.apply()
                page.reset()
                check(!page.isModified())
            }

            else -> {
                error("Unknown library page action: $action")
            }
        }
        check(CompilerBuildModel.describe(project).contains("Bundled XDK: read-only"))
        page.disposeUIResources()
        return content(project)
    }

    @JvmStatic
    fun restore(
        project: Project,
        content: String?,
    ) {
        val store = ProjectLanguageServerSettings.getInstance(project)
        val current = store.getLanguageServerSettings(SERVER) ?: return
        val copy = XmlSerializerUtil.createCopy(current)
        copy.configurationContent = content
        store.updateSettings(SERVER, copy)
    }
}
