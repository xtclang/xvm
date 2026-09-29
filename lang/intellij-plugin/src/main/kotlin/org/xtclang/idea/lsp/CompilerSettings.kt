package org.xtclang.idea.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings

/** One settings owner for configuration requests, the project UI and guarded rename history. */
internal object CompilerSettings {
    const val SERVER_ID = "xtcLanguageServer"

    fun store(project: Project, serverId: String = SERVER_ID): LanguageServerSettings =
        ProjectLanguageServerSettings.getInstance(project).takeIf {
            it.getLanguageServerSettings(serverId)?.configurationContent != null
        } ?: GlobalLanguageServerSettings.getInstance()

    fun content(project: Project): String? =
        store(project).getLanguageServerSettings(SERVER_ID)?.configurationContent
}
