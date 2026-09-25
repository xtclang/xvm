package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

/** Test-only Driver proxies; no automation hooks are added to the shipped plugin. */
@Remote("com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings", plugin = "com.redhat.devtools.lsp4ij")
interface LspSettings {
    fun updateSettings(
        serverId: String,
        settings: LspServerSettings,
    )
}

@Remote(
    "com.redhat.devtools.lsp4ij.settings.LanguageServerSettings\$LanguageServerDefinitionSettings",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface LspServerSettings {
    fun setConfigurationContent(content: String)
}

@Remote("com.intellij.codeInsight.lookup.LookupManager")
interface EditorLookupManager {
    fun getInstance(project: Project): EditorLookupManager

    fun getActiveLookup(): EditorLookup?
}

@Remote(value = "com.intellij.codeInsight.lookup.impl.LookupImpl", serviceInterface = "com.intellij.codeInsight.lookup.Lookup")
interface EditorLookup {
    fun getItems(): List<CompletionItem>

    fun setCurrentItem(item: CompletionItem)
}

@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface CompletionItem {
    fun getLookupString(): String
}
