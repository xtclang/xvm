package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPInlayHintFeature
import com.redhat.devtools.lsp4ij.client.features.LSPRenameFeature
import com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.services.LanguageServer
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Client features and the document ownership of the current transport. */
class XtcClientFeatures : LSPClientFeatures() {
    internal val documents = AtomicReference<DocumentStartupMessages?>()
    private val edits = ConcurrentHashMap.newKeySet<CompletableFuture<ApplyWorkspaceEditResponse>>()

    // TODO LSP4IJ: UP04 — expose transmitted document versions and validate workspace/applyEdit inside
    // its write command. Reuse our existing transport ownership until upstream provides this.
    fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        val result = CompletableFuture<ApplyWorkspaceEditResponse>()
        edits.add(result)
        result.whenComplete { _, _ -> edits.remove(result) }
        CompletableFuture.runAsync {
            try {
                val edit =
                    ReadAction.computeBlocking<ServerWorkspaceEdit, RuntimeException> {
                        ServerWorkspaceEdit.prepare(this, params)
                    }
                ApplicationManager.getApplication().invokeLater {
                    if (!result.isDone) {
                        try {
                            result.complete(edit.apply())
                        } catch (failure: RuntimeException) {
                            result.complete(
                                ServerWorkspaceEdit.refused(failure.message ?: "Edit failed"),
                            )
                        }
                    }
                }
            } catch (failure: RuntimeException) {
                result.complete(
                    ServerWorkspaceEdit.refused(failure.message ?: "Edit cannot be verified"),
                )
            }
        }
        return result
    }

    override fun dispose() {
        documents.set(null)
        edits.forEach { it.cancel(false) }
        super.dispose()
    }

    override fun initializeParams(params: InitializeParams) {
        super.initializeParams(params)
        // TODO LSP4IJ: UP02 — advertise save hooks only when DocumentContentSynchronizer
        // actually dispatches them. Native Actions on Save owns formatting here.
        params.capabilities?.textDocument?.synchronization?.apply {
            willSave = false
            willSaveWaitUntil = false
        }
        // TODO LSP4IJ: UP18 — snippet templates indent even when an item requests AsIs.
        // Advertise the implemented mode until native X150 passes without this constraint.
        params.capabilities?.textDocument?.completion?.apply {
            insertTextMode = InsertTextMode.AdjustIndentation
            completionItem?.insertTextModeSupport?.valueSet = listOf(InsertTextMode.AdjustIndentation)
        }
    }

    init {
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
            private fun snapshot(uri: String): DocumentStartupMessages.Snapshot? {
                if (project.isDisposed || serverWrapper.isDisposed) return null
                val opened = serverWrapper.getOpenedDocument(URI(uri)) ?: return null
                val document = opened.synchronizer?.document ?: return null
                // File rename waits for didOpen while holding the IDE write lock.
                // Transport hooks must use the document's lock-free immutable text;
                // acquiring a read action here deadlocks that rename on the EDT.
                return DocumentStartupMessages.Snapshot(
                    opened,
                    document.modificationStamp,
                    document.immutableCharSequence.toString(),
                )
            }

            private val documents =
                DocumentStartupMessages(::snapshot).also {
                    this@XtcClientFeatures.documents.set(it)
                }
            private val diagnostics = DiagnosticResultMessages(::snapshot)

            override fun wrapMessageConsumer(consumer: MessageConsumer): MessageConsumer {
                val wrapped = super.wrapMessageConsumer(consumer)
                return if (consumer is RemoteEndpoint) {
                    documents.incoming(
                        diagnostics.incoming(CodeActionMessages.incoming(wrapped)),
                    )
                } else {
                    documents.outgoing(diagnostics.outgoing(wrapped))
                }
            }
        }.configureGson {
            // configureGson replaces the base callback; retain LSP4IJ's compatibility
            // adapters.
            JSONUtils.configureCompatibilityAdapters(it)
            it.registerTypeAdapterFactory(ConfigurationJson)
            it.registerTypeAdapterFactory(DiagnosticReportJson)
        }
}
