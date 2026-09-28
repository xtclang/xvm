package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.OpenedDocument
import com.redhat.devtools.lsp4ij.internal.CancellationSupport
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/**
 * A rename response bound to the client documents that produced it. LSP4IJ 0.21 applies
 * WorkspaceEdit without checking document versions. Keep the request's immutable snapshot
 * until application and check it under the same write action as all of the returned edits.
 * No second version counter or long-lived document listener is needed.
 */
class XtcRenameEdit private constructor(
    private val snapshot: Snapshot,
    val edit: WorkspaceEdit,
    private val graph: SourceGraphEdit? = null,
) {
    /** Return false without changing any file when the request's documents have been retired. */
    fun apply(): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val project = snapshot.wrapper.project
        if (project.isDisposed) return false
        return WriteCommandAction.writeCommandAction(project).withName("Rename").withGlobalUndo().compute<Boolean, RuntimeException> {
            if (!snapshot.isCurrent() || graph?.isCurrent() == false) {
                false
            } else {
                graph?.beforeApply()
                LSPIJUtils.applyWorkspaceEdit(edit)
                graph?.apply()
                true
            }
        }
    }

    private data class Buffer(
        val opened: OpenedDocument,
        val synchronizer: DocumentContentSynchronizer,
        val stamp: Long,
        val url: String,
    ) {
        fun isCurrent(): Boolean =
            opened.file.isValid && opened.file.url == url && opened.synchronizer === synchronizer &&
                synchronizer.document.modificationStamp == stamp
    }

    private data class Snapshot(
        val wrapper: LanguageServerWrapper,
        val server: LanguageServer,
        val buffers: List<Buffer>,
    ) {
        fun isCurrent(): Boolean =
            !wrapper.isDisposed && wrapper.languageServer === server &&
                wrapper.openedDocuments.toSet() == buffers.map { it.opened }.toSet() &&
                buffers.all(Buffer::isCurrent)

        fun flush(): CompletableFuture<Void> =
            CompletableFuture.allOf(
                *buffers
                    .map { it.synchronizer }
                    .map { sync ->
                        sync.didOpenFuture.thenCompose { sync.flushPendingChanges() }
                    }.toTypedArray(),
            )
    }

    companion object {
        /**
         * Capture before flushing changes or sending the request. Comparing only after the
         * response arrives misses edits made while the compiler was calculating the rename.
         * Open/close epochs are represented by the existing OpenedDocument identities.
         */
        @JvmStatic
        fun request(
            wrapper: LanguageServerWrapper,
            file: VirtualFile,
            offset: Int,
            name: String,
        ): CompletableFuture<XtcRenameEdit?> =
            ReadAction.computeBlocking<CompletableFuture<XtcRenameEdit?>, RuntimeException> {
                val document = requireNotNull(LSPIJUtils.getDocument(file))
                requireNotNull(wrapper.getOpenedDocument(wrapper.toUri(file))) { "Rename source is no longer open" }
                val snapshot =
                    Snapshot(
                        wrapper,
                        requireNotNull(wrapper.languageServer),
                        wrapper.openedDocuments.map { opened ->
                            val synchronizer = requireNotNull(opened.synchronizer) { "Rename source is no longer open" }
                            Buffer(opened, synchronizer, synchronizer.document.modificationStamp, opened.file.url)
                        },
                    )
                val params = RenameParams(TextDocumentIdentifier(wrapper.toUriString(file)), LSPIJUtils.toPosition(offset, document), name)
                val graph = SourceGraphEdit.capture(wrapper.project, wrapper.serverDefinition.id)
                val cancellation = CancellationSupport()
                val result =
                    snapshot
                        .flush()
                        .thenCompose {
                            cancellation.checkCanceled()
                            val server = snapshot.server
                            cancellation.execute(
                                if (server is XtcLanguageServer) {
                                    server.renameProposal(params)
                                } else {
                                    server.textDocumentService.rename(params).thenApply { it?.let(::RenameProposal) }
                                },
                            )
                        }.thenApply { proposal ->
                            val edit = proposal?.edit
                            val hasEdits = edit != null && (!edit.documentChanges.isNullOrEmpty() || !edit.changes.isNullOrEmpty())
                            if (hasEdits) XtcRenameEdit(snapshot, edit, proposal.graph?.let(graph::replacement)) else null
                        }
                result.whenComplete { _, _ -> if (result.isCancelled) cancellation.cancel() }
                result
            }
    }
}
