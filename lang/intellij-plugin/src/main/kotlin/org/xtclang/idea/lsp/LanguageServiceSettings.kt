package org.xtclang.idea.lsp

import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings.LanguageServerDefinitionSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings

/** Reuse the LSP4IJ persistence and notification owner for both settings scopes. */
internal object LanguageServiceSettings {
    fun store(project: Project?): LanguageServerSettings =
        project?.let(ProjectLanguageServerSettings::getInstance)
            ?: GlobalLanguageServerSettings.getInstance()

    fun content(project: Project?): String? =
        store(project).getLanguageServerSettings(CompilerSettings.SERVER_ID)?.configurationContent

    fun effective(project: Project?): LanguageServiceConfiguration =
        LanguageServiceConfiguration.read(content(null), project?.let(::content))

    fun install(project: Project?, content: String) {
        val store = store(project)
        val current = store.getLanguageServerSettings(CompilerSettings.SERVER_ID)
        val copy = current?.let(XmlSerializerUtil::createCopy) ?: LanguageServerDefinitionSettings()
        copy.configurationContent = content
        store.updateSettings(CompilerSettings.SERVER_ID, copy)
    }
}
