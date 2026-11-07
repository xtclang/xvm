package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings

/** One settings owner for configuration requests, the project UI and guarded rename history. */
internal object CompilerSettings {
    const val SERVER_ID = "xtcLanguageServer"

    fun store(
        project: Project,
        serverId: String = SERVER_ID,
    ): LanguageServerSettings =
        ProjectLanguageServerSettings.getInstance(project).takeIf {
            ownsGraph(it.getLanguageServerSettings(serverId)?.configurationContent)
        } ?: GlobalLanguageServerSettings.getInstance()

    /** A new service-only override must not steal ownership of an inherited compiler graph. */
    internal fun ownsGraph(content: String?): Boolean =
        content != null &&
            !runCatching {
                val settings = JsonParser.parseString(content).asJsonObject
                val xtc = settings.getAsJsonObject("xtc")
                settings.keySet() == setOf("xtc") &&
                    xtc != null &&
                    xtc.keySet().all { it == "languageService" }
            }.getOrDefault(false)

    fun content(project: Project): String? = store(project).getLanguageServerSettings(SERVER_ID)?.configurationContent
}
