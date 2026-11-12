package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.PsiFile
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation

/** Test-only Driver proxies; no automation hooks are added to the shipped plugin. */
@Remote(
    "com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface LspSettings {
    fun getLanguageServerSettings(serverId: String): LspServerSettings?

    fun updateSettings(
        serverId: String,
        settings: LspServerSettings,
    )
}

/** Applies project LSP client settings through the IDE driver. */
@Remote(
    "com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface ProjectLspSettings {
    fun updateSettings(
        serverId: String,
        settings: LspServerSettings,
    )
}

/** Exposes the selected server definition's native client settings. */
@Remote(
    "com.redhat.devtools.lsp4ij.settings.LanguageServerSettings\$LanguageServerDefinitionSettings",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface LspServerSettings {
    fun getConfigurationContent(): String?

    fun setConfigurationContent(content: String)

    fun setServerTrace(trace: ClientTraceLevel): LspServerSettings
}

/** Finds the active completion popup in the IDE process. */
@Remote("com.intellij.codeInsight.lookup.LookupManager")
interface EditorLookupManager {
    fun getInstance(project: Project): EditorLookupManager

    fun getActiveLookup(): EditorLookup?
}

/** Inspects and selects entries in the native completion popup. */
@Remote(
    value = "com.intellij.codeInsight.lookup.impl.LookupImpl",
    serviceInterface = "com.intellij.codeInsight.lookup.Lookup",
)
interface EditorLookup {
    fun getItems(): List<CompletionItem>

    fun isCalculating(): Boolean

    fun getCurrentItem(): CompletionItem?

    fun setCurrentItem(item: CompletionItem)
}

/** Reads the native lookup label and renders its presentation for popup assertions. */
@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface CompletionItem {
    fun getLookupString(): String

    fun renderElement(presentation: LookupElementPresentation)
}

/** Locates LSP4IJ feature support attached to a real IDE file. */
@Remote("com.redhat.devtools.lsp4ij.LSPFileSupport", plugin = "com.redhat.devtools.lsp4ij")
interface LspFileSupport {
    fun getSupport(file: PsiFile): LspFileSupport

    fun getSignatureHelpSupport(): SignatureSupport

    fun getHighlightSupport(): HighlightSupport

    fun getSelectionRangeSupport(): SelectionRangeSupport
}

/** Requests native signature help using the editor client's feature support. */
@Remote(
    "com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelpSupport",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface SignatureSupport {
    fun getValidLSPFuture(): SignatureFuture?
}

/** Polls a native signature-help request without blocking the UI thread. */
@Remote("java.util.concurrent.CompletableFuture")
interface SignatureFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun get(): SignatureHelp?
}

/** Exposes the signatures and active selection returned to the native client. */
@Remote("org.eclipse.lsp4j.SignatureHelp", plugin = "com.redhat.devtools.lsp4ij")
interface SignatureHelp {
    fun getSignatures(): List<SignatureInformation>

    fun getActiveSignature(): Int?

    fun getActiveParameter(): Int?
}

/** Reads one displayed signature and its parameter metadata. */
@Remote("org.eclipse.lsp4j.SignatureInformation", plugin = "com.redhat.devtools.lsp4ij")
interface SignatureInformation {
    fun getLabel(): String

    fun getDocumentation(): ClientValue?

    fun getActiveParameter(): Int?

    fun getParameters(): List<ParameterInformation>
}

/** Exposes a signature parameter's label through the IDE driver. */
@Remote("org.eclipse.lsp4j.ParameterInformation", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterInformation {
    fun getLabel(): ParameterLabel
}

/** Reads either the string label or its offset-pair protocol representation. */
@Remote("org.eclipse.lsp4j.jsonrpc.messages.Either", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterLabel {
    fun getLeft(): String?

    fun getRight(): ParameterRange
}

/** Exposes the start and end offsets of a signature parameter label. */
@Remote("org.eclipse.lsp4j.jsonrpc.messages.Tuple\$Two", plugin = "com.redhat.devtools.lsp4ij")
interface ParameterRange {
    fun getFirst(): Int

    fun getSecond(): Int
}

/** Reads rendered parameter-hint text to verify what the user actually sees. */
@Remote("com.intellij.ui.components.JBHtmlPane")
interface ParameterHintText {
    fun getText(): String
}

/** Dismisses native editor hints between scenarios. */
@Remote("com.intellij.codeInsight.hint.HintManager")
interface EditorHints {
    fun hideAllHints()
}
