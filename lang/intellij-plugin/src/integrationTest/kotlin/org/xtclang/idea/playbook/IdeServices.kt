package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.PsiFile
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation

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

    fun isCalculating(): Boolean

    fun setCurrentItem(item: CompletionItem)
}

@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface CompletionItem {
    fun getLookupString(): String

    fun renderElement(presentation: LookupElementPresentation)
}

@Remote("com.redhat.devtools.lsp4ij.LSPFileSupport", plugin = "com.redhat.devtools.lsp4ij")
interface LspFileSupport {
    fun getSupport(file: PsiFile): LspFileSupport

    fun getSignatureHelpSupport(): SignatureSupport
}

@Remote("com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelpSupport", plugin = "com.redhat.devtools.lsp4ij")
interface SignatureSupport {
    fun getValidLSPFuture(): SignatureFuture?
}

@Remote("java.util.concurrent.CompletableFuture")
interface SignatureFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): SignatureHelp?
}

@Remote("org.eclipse.lsp4j.SignatureHelp", plugin = "com.redhat.devtools.lsp4ij")
interface SignatureHelp {
    fun getSignatures(): List<SignatureInformation>

    fun getActiveParameter(): Int?
}

@Remote("org.eclipse.lsp4j.SignatureInformation", plugin = "com.redhat.devtools.lsp4ij")
interface SignatureInformation {
    fun getLabel(): String

    fun getActiveParameter(): Int?

    fun getParameters(): List<ParameterInformation>
}

@Remote("org.eclipse.lsp4j.ParameterInformation", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterInformation {
    fun getLabel(): ParameterLabel
}

@Remote("org.eclipse.lsp4j.jsonrpc.messages.Either", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterLabel {
    fun getLeft(): String?

    fun getRight(): ParameterRange
}

@Remote("org.eclipse.lsp4j.jsonrpc.messages.Tuple\$Two", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterRange {
    fun getFirst(): Int

    fun getSecond(): Int
}

@Remote("com.intellij.ui.components.JBHtmlPane")
interface ParameterHintText {
    fun getText(): String
}
