package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/** Read what IntelliJ's language client already received; never issue a diagnostic request from the test. */
fun Driver.receivedDiagnostics(editor: JEditorUiComponent): List<ReceivedDiagnostic> {
    val path = editor.editor.getVirtualFile().getPath()
    return service<LanguageClients>(singleProject())
        .getStartedServers()
        .flatMap { it.getOpenedDocuments() }
        .filter { it.getFile().getPath() == path }
        .flatMap { it.getDiagnostics() }
        .map {
            val range = it.getRange()
            ReceivedDiagnostic(
                it.getCode()?.let { code -> code.getLeft() ?: code.getRight()?.toString() },
                it.getSource(),
                it.getSeverity()?.name(),
                range.getStart().let { position -> position.getLine() to position.getCharacter() },
                range.getEnd().let { position -> position.getLine() to position.getCharacter() },
            )
        }
}

data class ReceivedDiagnostic(
    val code: String?,
    val source: String?,
    val severity: String?,
    val start: Pair<Int, Int>,
    val end: Pair<Int, Int>,
)

@Remote("com.redhat.devtools.lsp4ij.LanguageServiceAccessor", plugin = "com.redhat.devtools.lsp4ij")
interface LanguageClients {
    // Driver transports collection results as lists even when the IDE method returns a Set.
    fun getStartedServers(): Collection<StartedLanguageServer>
}

@Remote("com.redhat.devtools.lsp4ij.LanguageServerWrapper", plugin = "com.redhat.devtools.lsp4ij")
interface StartedLanguageServer {
    fun getOpenedDocuments(): Collection<ClientDocument>
}

@Remote("com.redhat.devtools.lsp4ij.OpenedDocument", plugin = "com.redhat.devtools.lsp4ij")
interface ClientDocument {
    fun getFile(): VirtualFile

    fun getDiagnostics(): Collection<ClientDiagnostic>
}

@Remote("org.eclipse.lsp4j.Diagnostic", plugin = "com.redhat.devtools.lsp4ij")
interface ClientDiagnostic {
    fun getCode(): DiagnosticCode?

    fun getSource(): String?

    fun getSeverity(): DiagnosticSeverity?

    fun getRange(): SourceRange
}

@Remote("org.eclipse.lsp4j.jsonrpc.messages.Either", plugin = "com.redhat.devtools.lsp4ij")
interface DiagnosticCode {
    fun getLeft(): String?

    fun getRight(): Int?
}

@Remote("org.eclipse.lsp4j.DiagnosticSeverity", plugin = "com.redhat.devtools.lsp4ij")
interface DiagnosticSeverity {
    fun name(): String
}

@Remote("org.eclipse.lsp4j.Range", plugin = "com.redhat.devtools.lsp4ij")
interface SourceRange {
    fun getStart(): SourcePosition

    fun getEnd(): SourcePosition
}

@Remote("org.eclipse.lsp4j.Position", plugin = "com.redhat.devtools.lsp4ij")
interface SourcePosition {
    fun getLine(): Int

    fun getCharacter(): Int
}

@Remote("com.intellij.openapi.editor.Document")
interface DocumentVersion {
    fun getModificationStamp(): Long
}
