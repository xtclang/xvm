package org.xtclang.idea.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends the language server a document's current text when the didOpen that LSP4IJ sent for it carried older text.
 *
 * TODO LSP4IJ: UP83 — LSP4IJ 0.30.1 opens a document with the text it took when it asked for the connection, and
 *  listens to the document only once the server has initialized, so an edit made in between never reaches the server
 *  (redhat-developer/lsp4ij#2126, fixed by our #2127 for 0.30.2). With full synchronization, a change event on the
 *  document's synchronizer sends its whole current text, with LSP4IJ's next version. Incremental synchronization sends
 *  ranges, which can't repair text that the server doesn't have.
 */
@Service(Service.Level.PROJECT)
internal class OpenedTextRepair(
    private val project: Project,
) : LanguageServerLifecycleListener,
    Disposable {
    private val listening = AtomicBoolean()

    /** Starts watching the server's didOpen notifications: before the server starts, so that its first opens count. */
    fun listen() {
        if (listening.compareAndSet(false, true)) {
            LanguageServerLifecycleManager.getInstance(project).addLanguageServerLifecycleListener(this)
        }
    }

    override fun handleLSPMessage(
        message: Message,
        messageConsumer: MessageConsumer,
        languageServer: LanguageServerWrapper,
    ) {
        // a didOpen as it goes out: its parameters are still objects, not yet written
        if (messageConsumer !is StreamMessageConsumer || languageServer.serverDefinition.id != CompilerSettings.SERVER_ID) return
        val opened =
            ((message as? NotificationMessage)?.takeIf { it.method == DID_OPEN }?.params as? DidOpenTextDocumentParams)
                ?.textDocument ?: return
        ApplicationManager
            .getApplication()
            .invokeLater({ repair(languageServer, opened.uri, opened.text) }, ModalityState.any(), project.disposed)
    }

    /**
     * Sends the document's text again when it is no longer the text that its didOpen sent. An edit made after LSP4IJ
     * began listening has gone, or goes, as a change of its own, so the text sent again is then the server's already.
     */
    private fun repair(
        languageServer: LanguageServerWrapper,
        uri: String,
        sent: String,
    ) {
        val synchronizer = runCatching { languageServer.getOpenedDocument(URI(uri))?.synchronizer }.getOrNull() ?: return
        val document = synchronizer.document
        if (document.charsSequence.contentEquals(sent)) return
        if (languageServer.syncOptions.change != TextDocumentSyncKind.Full) {
            logger<OpenedTextRepair>().warn(
                "The language server opened $uri without an edit made while it connected; incremental text " +
                    "synchronization cannot send it again (UP83)",
            )
            return
        }
        logger<OpenedTextRepair>().info("Sending the language server the text of $uri again: its didOpen was older (UP83)")
        synchronizer.documentChanged(WholeText(document))
    }

    override fun handleStatusChanged(languageServer: LanguageServerWrapper) = Unit

    override fun handleError(
        languageServer: LanguageServerWrapper,
        exception: Throwable,
    ) = Unit

    override fun dispose() {
        // the project's services may be going away with it
        if (listening.get() && !project.isDisposed) {
            runCatching { LanguageServerLifecycleManager.getInstance(project).removeLanguageServerLifecycleListener(this) }
        }
    }

    /** A change that leaves [document] as it is: with full synchronization, the synchronizer sends its whole text. */
    private class WholeText(
        document: Document,
    ) : DocumentEvent(document) {
        private val stamp = document.modificationStamp

        override fun getOffset() = 0

        override fun getOldLength() = 0

        override fun getNewLength() = 0

        override fun getOldFragment(): CharSequence = ""

        override fun getNewFragment(): CharSequence = ""

        override fun getOldTimeStamp() = stamp
    }

    private companion object {
        const val DID_OPEN = "textDocument/didOpen"
    }
}
