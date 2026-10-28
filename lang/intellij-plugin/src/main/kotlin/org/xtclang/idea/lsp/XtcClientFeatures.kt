package org.xtclang.idea.lsp

import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPInlayHintFeature
import com.redhat.devtools.lsp4ij.client.features.LSPRenameFeature
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
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
import java.util.concurrent.atomic.AtomicReference

/** Client features, and the rename and request ownership and the diagnostic reports of the current transport. */
class XtcClientFeatures : LSPClientFeatures() {
    internal val preflightedRenames = PreflightedRenames()

    /** The current connection's pull-diagnostic results, which say what each document's latest report was asked for. */
    internal val diagnosticResults = AtomicReference<DiagnosticResultMessages?>()

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
        // TODO LSP4IJ: UP78 — Ctrl+Q on an item that has its documentation only on resolve shows nothing
        setCompletionFeature(CompletionDocumentation())
        setInlayHintFeature(
            object : LSPInlayHintFeature() {
                override fun isInlayHintSupported(file: PsiFile): Boolean =
                    LanguageServiceSettings.validated(file.project).inlayHints &&
                        super.isInlayHintSupported(file)
            },
        )
        // TODO LSP4IJ: UP04 — remove this override when native symbol rename checks document
        // epochs.
        // XtcRenameHandler supplies the guarded native entry point until then.
        // Keep LSP4IJ's independent file-operation support enabled.
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
}
