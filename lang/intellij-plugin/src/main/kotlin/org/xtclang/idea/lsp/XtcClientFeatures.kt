package org.xtclang.idea.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.OpenedDocument
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature
import com.redhat.devtools.lsp4ij.client.features.LSPInlayHintFeature
import com.redhat.devtools.lsp4ij.client.features.LSPRenameFeature
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.MessageIssueHandler
import org.eclipse.lsp4j.jsonrpc.MessageProducer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageServer
import org.xtclang.idea.dap.DebugSessions
import org.xtclang.idea.run.EmbeddedRunSessions
import java.io.InputStream
import java.net.URI
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

/** Client features, and the rename and request ownership and the diagnostic reports of the current transport. */
class XtcClientFeatures : LSPClientFeatures() {
    internal val preflightedRenames = PreflightedRenames()

    /** The current connection's pull-diagnostic results, which say what each document's latest report was asked for. */
    internal val diagnosticResults = AtomicReference<DiagnosticResultMessages?>()

    /** The connected documents whose marks [markConnected] has refreshed, each once. */
    private val marked = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<OpenedDocument, Boolean>()))

    /**
     * The number of items of the latest diagnostics report of the open document at [path], if that report was asked
     * for the document as it is now; null if there is none, or the document has changed since, as a test waiting for
     * the diagnostics of an edit needs to know.
     */
    fun currentDiagnosticItems(path: String): Int? {
        val results = diagnosticResults.get() ?: return null
        val uri = results.reported().firstOrNull { serverWrapper.getOpenedDocument(URI(it))?.file?.path == path } ?: return null
        return results.currentItems(uri)
    }

    /**
     * The project's embedded runs and debug sessions run in the language server, so it stays up while one does, also
     * with no Ecstasy file open: LSP4IJ stops a server a few seconds after its last open file closes, which ended them.
     */
    override fun keepServerAlive(): Boolean =
        !project.isDisposed &&
            (
                project.getServiceIfCreated(EmbeddedRunSessions::class.java)?.active == true ||
                    project.getServiceIfCreated(DebugSessions::class.java)?.active == true
            )

    override fun dispose() {
        diagnosticResults.set(null)
        super.dispose()
    }

    init {
        // each file's diagnostics, open or closed, as LSP4IJ keeps them, are its errors in the Problems window's
        // Project Errors, which LSP4IJ does not fill
        setDiagnosticFeature(
            object : LSPDiagnosticFeature() {
                override fun diagnosticsUpdated(
                    file: VirtualFile,
                    diagnostics: List<Diagnostic>,
                ) {
                    if (project.isDisposed) return
                    ProjectErrors.getInstance(project).reported(file, diagnostics, ::getMessage)
                    markConnected(file)
                }
            },
        )
        setInlayHintFeature(
            object : LSPInlayHintFeature() {
                override fun isInlayHintSupported(file: PsiFile): Boolean =
                    LanguageServiceSettings.validated(file.project).inlayHints &&
                        super.isInlayHintSupported(file)
            },
        )
        // Ecstasy symbols rename with XtcRenameHandler, which carries a change of the configured source modules
        // with the edit (xtc/rename); LSP4IJ's own rename can't. Keep LSP4IJ's independent file-operation support
        // enabled.
        setRenameFeature(
            object : LSPRenameFeature() {
                override fun isRenameSupported(file: PsiFile): Boolean = false
            },
        )
    }

    override fun <S : LanguageServer> createLauncherBuilder(): Launcher.Builder<S> =
        object : DefaultLauncherBuilder<S>(this) {
            private val diagnostics =
                DiagnosticResultMessages(::snapshot).also {
                    this@XtcClientFeatures.diagnosticResults.set(it)
                }
            private val pending = PendingRequests()

            @Suppress("UNCHECKED_CAST") // LSP4J's multiple-interface proxy factory returns Object.
            override fun createProxy(remoteEndpoint: RemoteEndpoint): S {
                val endpoint = pending.endpoint(preflightedRenames.endpoint(remoteEndpoint))
                // Match Launcher.Builder: the single-interface overload derives its loader from
                // that interface. The optional builder classLoader is normally null in LSP4IJ.
                return if (localServices.size == 1 && remoteInterfaces.size == 1) {
                    ServiceEndpoints.toServiceObject(endpoint, remoteInterfaces.single())
                } else {
                    ServiceEndpoints.toServiceObject(endpoint, remoteInterfaces.toList<Class<*>>(), classLoader) as S
                }
            }

            private fun snapshot(uri: String): DiagnosticResultMessages.Snapshot? {
                if (project.isDisposed || serverWrapper.isDisposed) return null
                val opened = serverWrapper.getOpenedDocument(URI(uri)) ?: return null
                val document = opened.synchronizer?.document ?: return null
                // File rename waits for didOpen while holding the IDE write lock.
                // Transport hooks must use the document's lock-free immutable text;
                // acquiring a read action here deadlocks that rename on the EDT.
                return DiagnosticResultMessages.Snapshot(
                    opened,
                    document.modificationStamp,
                    document.immutableCharSequence.toString(),
                )
            }

            override fun createMessageProducer(
                input: InputStream,
                jsonHandler: MessageJsonHandler,
                issueHandler: MessageIssueHandler,
            ): MessageProducer = pending.producer(super.createMessageProducer(input, jsonHandler, issueHandler))

            override fun wrapMessageConsumer(consumer: MessageConsumer): MessageConsumer {
                val wrapped = super.wrapMessageConsumer(consumer)
                return if (consumer is RemoteEndpoint) diagnostics.incoming(wrapped) else diagnostics.outgoing(wrapped)
            }
        }

    /**
     * Marks in the editor of [file] what LSP4IJ holds of its connected document, once, in place of what the editor marked
     * before, at the first update of any document of [file] after which the connected document holds exactly the items
     * of its own pull's latest report for its text as it is ([DiagnosticResultMessages.currentReport]): the marks then
     * show that report, as LSP4IJ applies it, whichever update brought its items. LSP4IJ calls
     * [LSPDiagnosticFeature.diagnosticsUpdated] after each update, changed or not, also one that it rejects for its
     * version or that goes into a document without a connection, and a new connection's first pull names no result ID,
     * so its report is full. Before that report comes, no update marks anything, a related or workspace report or a
     * rejected one, so the marks of an error still there stay. A refresh that fails is tried again at the next update;
     * none fails the update that it follows.
     *
     * TODO LSP4IJ: UP86 — LSP4IJ 0.30.1 keeps a report that reaches a file whose editor is open before it connects the
     *  file's document, a workspace report or a related document's, in a document without a connection, and the editor
     *  marks its errors. The connection replaces that document with an empty one, and LSP4IJ marks an editor again as
     *  its document's diagnostics change, or as a server stops or refreshes all its features, not as reports come: when
     *  the connected document's reports have none, as once the error is fixed, nothing takes the marks back. Filed as
     *  redhat-developer/lsp4ij#2231, for 0.30.2; our fix is redhat-developer/lsp4ij#2232, open. Not as the document
     *  connects, nor at its first update: its diagnostics are none until its own report, so the marks of an error
     *  still there would go until that report comes.
     */
    private fun markConnected(file: VirtualFile) {
        try {
            val document = getFileUri(file)?.let { serverWrapper.getOpenedDocument(it) }?.takeIf { it.synchronizer != null } ?: return
            if (project.isDisposed || document in marked) return
            val results = diagnosticResults.get() ?: return
            val own =
                results.reported().firstNotNullOfOrNull { uri ->
                    if (serverWrapper.getOpenedDocument(URI(uri)) === document) results.currentReport(uri) else null
                } ?: return
            if (document.diagnostics.toSet() != own.toSet()) return
            LSPDiagnosticsApplier.getInstance(project).scheduleRefresh(file, null)
            marked.add(document)
        } catch (failure: Exception) {
            logger<XtcClientFeatures>().warn("Could not mark the diagnostics of ${file.path} in its editor (UP86)", failure)
        } catch (failure: LinkageError) {
            logger<XtcClientFeatures>().warn("Could not mark the diagnostics of ${file.path} in its editor (UP86)", failure)
        }
    }
}
