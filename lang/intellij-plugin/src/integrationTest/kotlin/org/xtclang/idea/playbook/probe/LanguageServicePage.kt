package org.xtclang.idea.playbook.probe

import com.google.gson.JsonParser
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import org.xtclang.idea.XtcIntelliJLanguage
import org.xtclang.idea.lsp.LanguageServiceProjectConfigurable
import java.awt.Component
import java.awt.Container
import javax.swing.JComboBox

/** Exercise actual settings components on the EDT, without replaying editor mutations. */
object LanguageServicePage {
    private fun children(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(children(it)) }
        }

    @JvmStatic
    fun exercise(project: Project) {
        val before = content(project)
        val page = LanguageServiceProjectConfigurable(project)
        try {
            val components = children(page.createComponent()).toList()
            val inherit =
                components.filterIsInstance<JBCheckBox>().single {
                    it.name == "xtc.service.inherit"
                }
            val hints =
                components.filterIsInstance<JBCheckBox>().single { it.name == "xtc.service.hints" }
            val saving =
                components.filterIsInstance<JComboBox<*>>().single { it.name == "xtc.service.save" }
            check(!saving.isEnabled) { "Unavailable LSP4IJ save hook must not be offered" }
            inherit.isSelected = false
            hints.isSelected = !hints.isSelected
            check(page.isModified())
            page.reset()
            check(!page.isModified() && content(project) == before)
            val initial = hints.isSelected
            inherit.isSelected = false
            hints.isSelected = !initial
            page.apply()
            check(
                !page.isModified() &&
                    JsonParser
                        .parseString(content(project))
                        .asJsonObject["xtc"]
                        .asJsonObject["languageService"]
                        .asJsonObject["inlayHints"]
                        .asBoolean != initial,
            )
        } finally {
            page.disposeUIResources()
            restore(project, before)
        }
    }

    @JvmStatic
    fun content(project: Project): String? =
        ProjectLanguageServerSettings
            .getInstance(project)
            .getLanguageServerSettings("xtcLanguageServer")
            ?.configurationContent

    @JvmStatic
    fun restore(
        project: Project,
        content: String?,
    ) {
        val store = ProjectLanguageServerSettings.getInstance(project)
        val current = store.getLanguageServerSettings("xtcLanguageServer") ?: return
        val copy = XmlSerializerUtil.createCopy(current)
        copy.configurationContent = content
        store.updateSettings("xtcLanguageServer", copy)
    }

    @JvmStatic
    fun inlayHints(
        project: Project,
        enabled: Boolean,
    ) {
        booleanSetting(project, "xtc.service.hints", enabled)
    }

    @JvmStatic
    fun referenceCodeLens(
        project: Project,
        enabled: Boolean,
    ) {
        booleanSetting(project, "xtc.service.references", enabled)
    }

    private fun booleanSetting(
        project: Project,
        name: String,
        enabled: Boolean,
    ) {
        val page = LanguageServiceProjectConfigurable(project)
        try {
            val components = children(page.createComponent()).filterIsInstance<JBCheckBox>().toList()
            components.single { it.name == "xtc.service.inherit" }.isSelected = false
            components.single { it.name == name }.isSelected = enabled
            page.apply()
        } finally {
            page.disposeUIResources()
        }
    }

    @JvmStatic
    fun transport(
        project: Project,
        value: String,
    ) {
        choiceSetting(project, "xtc.service.sync", value)
    }

    @JvmStatic
    fun adapter(
        project: Project,
        value: String,
    ) {
        choiceSetting(project, "xtc.service.adapter", value)
    }

    private fun choiceSetting(
        project: Project,
        name: String,
        value: String,
    ) {
        val page = LanguageServiceProjectConfigurable(project)
        try {
            val components = children(page.createComponent()).toList()
            components
                .filterIsInstance<JBCheckBox>()
                .single { it.name == "xtc.service.inherit" }
                .isSelected = false
            val choices =
                components
                    .filterIsInstance<JComboBox<*>>()
                    .single { it.name == name }
            choices.selectedIndex = (0 until choices.itemCount).single { choices.getItemAt(it).toString() == value }
            page.apply()
        } finally {
            page.disposeUIResources()
        }
    }

    @JvmStatic
    fun saveFormatting(
        project: Project,
        enabled: Boolean,
    ): Boolean {
        val options = FormatOnSaveOptions.getInstance(project)
        val previous = options.isRunOnSaveEnabled
        options.isRunOnSaveEnabled = enabled
        return previous
    }

    @JvmStatic
    fun indent(
        project: Project,
        value: Int,
    ): Int {
        val options =
            requireNotNull(
                CodeStyle
                    .getProjectOrDefaultSettings(project)
                    .getCommonSettings(XtcIntelliJLanguage)
                    .indentOptions,
            )
        val previous = options.INDENT_SIZE
        options.INDENT_SIZE = value
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        return previous
    }
}
