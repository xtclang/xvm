package org.xtclang.idea.playbook.probe

import com.google.gson.JsonParser
import com.intellij.application.options.CodeStyle
import com.intellij.application.options.GeneralCodeStyleOptionsProviderEP
import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import org.xtclang.idea.XtcIntelliJLanguage
import org.xtclang.idea.lsp.EditorConfigSwitch
import org.xtclang.idea.lsp.LanguageServiceProjectConfigurable
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox
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
            check(saving.isEnabled == hints.isEnabled) { "Save formatting must be offered like the other settings" }
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

    @JvmStatic
    fun saveOwner(
        project: Project,
        value: String,
    ) {
        choiceSetting(project, "xtc.service.save", value)
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

    /**
     * Turns Code Style | General | Formatter | Turn formatter on/off with markers in code comments on or off, as that page
     * applies it, and answers whether it was on.
     */
    @JvmStatic
    fun formatterTags(
        project: Project,
        enabled: Boolean,
    ): Boolean {
        val settings = CodeStyle.getProjectOrDefaultSettings(project)
        val previous = settings.FORMATTER_TAGS_ENABLED
        settings.FORMATTER_TAGS_ENABLED = enabled
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        return previous
    }

    /**
     * Turns Ecstasy's Code Style | Wrapping and Braces | Comments | Wrap at right margin on or off, as that page applies
     * it, and answers whether it was on.
     */
    @JvmStatic
    fun wrapComments(
        project: Project,
        enabled: Boolean,
    ): Boolean {
        val common = CodeStyle.getProjectOrDefaultSettings(project).getCommonSettings(XtcIntelliJLanguage)
        val previous = common.WRAP_COMMENTS
        common.WRAP_COMMENTS = enabled
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        return previous
    }

    /**
     * Turns Code Style | General | Enable EditorConfig support on or off with that page's own component, the EditorConfig
     * plugin's, whose apply tells the IDE of the change, and answers whether it was on. The component is found as the
     * page finds it, among the general code style options, so that the probe needs none of that plugin's classes.
     */
    @JvmStatic
    fun editorConfig(
        project: Project,
        enabled: Boolean,
    ): Boolean {
        val settings = CodeStyle.getProjectOrDefaultSettings(project)
        val previous = EditorConfigSwitch.enabled(settings)
        // only the EditorConfig plugin's options, by the class that its extension names, as making the others' would
        // leave them to dispose of
        val options =
            requireNotNull(GENERAL_OPTIONS.extensionList.single { it.instanceClass == EDITOR_CONFIG_OPTIONS }.createConfigurable()) {
                "The EditorConfig options have no configurable"
            }
        try {
            val component = requireNotNull(options.createComponent()) { "The EditorConfig options have no component" }
            options.reset(settings)
            children(component).filterIsInstance<JCheckBox>().single { "EditorConfig" in it.text }.isSelected = enabled
            options.apply(settings)
        } finally {
            options.disposeUIResources()
        }
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
        check(EditorConfigSwitch.enabled(settings) == enabled) { "EditorConfig support did not turn ${if (enabled) "on" else "off"}" }
        return previous
    }

    /** The options of Code Style | General, which other plugins than the platform add to, the EditorConfig plugin among them. */
    private val GENERAL_OPTIONS = ExtensionPointName<GeneralCodeStyleOptionsProviderEP>("com.intellij.generalCodeStyleOptionsProvider")

    /** The EditorConfig plugin's options there, whose check box is Enable EditorConfig support. */
    private const val EDITOR_CONFIG_OPTIONS = "org.editorconfig.settings.EditorConfigConfigurable"
}
