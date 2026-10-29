package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/**
 * Read what IntelliJ's language client already received; never issue a diagnostic request from the
 * test.
 */
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

/**
 * What IntelliJ's language client received for the editor's document, once it is the server's report for the document
 * as it is now; null while the latest report is for an earlier state, as from before an edit, or the client does not
 * hold it yet. When the server pushes its diagnostics, no report is asked for a known state, and what the client
 * received is taken as it is.
 */
fun Driver.currentDiagnostics(editor: JEditorUiComponent): List<ReceivedDiagnostic>? {
    val received = receivedDiagnostics(editor)
    val items = utility(DiagnosticReportsProbe::class).currentItems(singleProject(), editor.editor.getVirtualFile().getPath())
    return received.takeIf { items == PUSHED_DIAGNOSTICS || items == received.size }
}

/** What [DiagnosticReportsProbe.currentItems] answers when the server pushes its diagnostics. */
private const val PUSHED_DIAGNOSTICS = -2

/** Whether the diagnostics the client holds for a document are the server's report for it as it is now. */
@Remote("org.xtclang.idea.playbook.probe.DiagnosticReports", plugin = "org.xtclang.playbook.probe")
interface DiagnosticReportsProbe {
    fun currentItems(
        project: Project,
        path: String,
    ): Int
}

/** Copies the client's received diagnostic coordinates and classification into the test process. */
data class ReceivedDiagnostic(
    val code: String?,
    val source: String?,
    val severity: String?,
    val start: Pair<Int, Int>,
    val end: Pair<Int, Int>,
)

/** Exposes the project's started LSP4IJ servers through the IDE driver. */
@Remote("com.redhat.devtools.lsp4ij.LanguageServiceAccessor", plugin = "com.redhat.devtools.lsp4ij")
interface LanguageClients {
    // Driver transports collection results as lists even when the IDE method returns a Set.
    fun getStartedServers(): Collection<StartedLanguageServer>
}

/** Gives the driver access to a running client's documents, lifecycle and protocol trace. */
@Remote("com.redhat.devtools.lsp4ij.LanguageServerWrapper", plugin = "com.redhat.devtools.lsp4ij")
interface StartedLanguageServer {
    fun getOpenedDocuments(): Collection<ClientDocument>

    fun getLanguageServer(): RemoteLanguageServer

    fun getServerStatus(): ClientServerStatus

    fun getServerCapabilitiesSync(): ClientValue

    fun stop(): ClientFuture

    fun stopAndDisable()

    fun restart()

    fun getCurrentProcessId(): Long?

    fun getTraces(): List<ClientTraceEntry>

    fun getServerTrace(): ClientTraceLevel
}

/** Reads the client's lifecycle state without loading its enum into the test process. */
@Remote("com.redhat.devtools.lsp4ij.ServerStatus", plugin = "com.redhat.devtools.lsp4ij")
interface ClientServerStatus {
    fun name(): String
}

/** Exposes one recorded protocol message from the IDE-side LSP trace. */
@Remote(
    "com.redhat.devtools.lsp4ij.LanguageServerWrapper\$LSPTrace",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface ClientTraceEntry {
    fun message(): ClientValue
}

/** Reads the file, diagnostics and synchronizer already owned by the IDE client. */
@Remote("com.redhat.devtools.lsp4ij.OpenedDocument", plugin = "com.redhat.devtools.lsp4ij")
interface ClientDocument {
    fun getFile(): VirtualFile

    fun getDiagnostics(): Collection<ClientDiagnostic>

    fun getSynchronizer(): DocumentSynchronizer?
}

/** Lets the driver observe document-open completion and flush pending native text changes. */
@Remote(
    "com.redhat.devtools.lsp4ij.DocumentContentSynchronizer",
    plugin = "com.redhat.devtools.lsp4ij",
)
interface DocumentSynchronizer {
    fun getDidOpenFuture(): DocumentOpenFuture

    fun flushPendingChanges(): ClientFuture

    fun getDocument(): ParityDocument
}

/** Polls native document synchronization without blocking the driver on a remote future. */
@Remote("java.util.concurrent.CompletableFuture")
interface DocumentOpenFuture {
    fun isDone(): Boolean

    fun isCompletedExceptionally(): Boolean
}

/** Reads the diagnostic delivered to the native client rather than issuing a separate request. */
@Remote("org.eclipse.lsp4j.Diagnostic", plugin = "com.redhat.devtools.lsp4ij")
interface ClientDiagnostic {
    fun getCode(): DiagnosticCode?

    fun getSource(): String?

    fun getSeverity(): DiagnosticSeverity?

    fun getRange(): SourceRange
}

/** Exposes both protocol representations of a diagnostic code across the driver boundary. */
@Remote("org.eclipse.lsp4j.jsonrpc.messages.Either", plugin = "com.redhat.devtools.lsp4ij")
interface DiagnosticCode {
    fun getLeft(): String?

    fun getRight(): Int?
}

/** Reads the native diagnostic severity by enum name. */
@Remote("org.eclipse.lsp4j.DiagnosticSeverity", plugin = "com.redhat.devtools.lsp4ij")
interface DiagnosticSeverity {
    fun name(): String
}

/** Exposes a protocol range from the IDE process for exact diagnostic assertions. */
@Remote("org.eclipse.lsp4j.Range", plugin = "com.redhat.devtools.lsp4ij")
interface SourceRange {
    fun getStart(): SourcePosition

    fun getEnd(): SourcePosition
}

/** Reads zero-based protocol line and character coordinates through the driver. */
@Remote("org.eclipse.lsp4j.Position", plugin = "com.redhat.devtools.lsp4ij")
interface SourcePosition {
    fun getLine(): Int

    fun getCharacter(): Int
}

/** Exposes the IDE document modification stamp used to await a fresh diagnostic snapshot. */
@Remote("com.intellij.openapi.editor.Document")
interface DocumentVersion {
    fun getModificationStamp(): Long
}
