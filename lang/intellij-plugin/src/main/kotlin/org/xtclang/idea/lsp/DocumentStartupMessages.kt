package org.xtclang.idea.lsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

/**
 * LSP4IJ 0.21 dispatches didOpen independently of its ordered change/close executor. Keep
 * changes behind that open, and bind folding responses to the requesting editor incarnation.
 * State belongs to one transport and uses the client's existing versions and document identities.
 */
internal class DocumentStartupMessages(
    private val snapshot: (String) -> Snapshot?,
) {
    data class Snapshot(
        val owner: Any,
        val stamp: Long,
        val text: String,
    )

    private data class Opening(
        val owner: Any,
        val change: NotificationMessage,
    )

    private data class Fold(
        val uri: String,
        val snapshot: Snapshot?,
    )

    private val lock = Any()
    private val opening = mutableMapOf<String, Opening>()
    private val opened = mutableMapOf<String, Any>()
    private val folds = mutableMapOf<String, Fold>()

    fun outgoing(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val params = (message as? NotificationMessage)?.params
            val uri =
                when (params) {
                    is DidOpenTextDocumentParams -> params.textDocument.uri
                    is DidChangeTextDocumentParams -> params.textDocument.uri
                    is DidCloseTextDocumentParams -> params.textDocument.uri
                    else -> ((message as? RequestMessage)?.params as? FoldingRangeRequestParams)?.textDocument?.uri
                }
            // Read actions precede the transport lock: EDT edits must never wait behind a
            // response thread holding this lock while waiting for a write action to finish.
            val current = uri?.let(snapshot)
            synchronized(lock) {
                when (params) {
                    is DidOpenTextDocumentParams -> {
                        open(message, params, current, next)
                    }

                    is DidChangeTextDocumentParams -> {
                        change(message, params, current, next)
                    }

                    is DidCloseTextDocumentParams -> {
                        close(params.textDocument.uri, current, next)
                    }

                    else -> {
                        if (message is RequestMessage && message.params is FoldingRangeRequestParams) {
                            val uri = (message.params as FoldingRangeRequestParams).textDocument.uri
                            folds[message.id] = Fold(uri, current)
                        }
                        next.consume(message)
                    }
                }
            }
        }

    fun incoming(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val fold = synchronized(lock) { (message as? ResponseMessage)?.let { folds.remove(it.id) } }
            val accepted =
                if (fold != null && snapshot(fold.uri) != fold.snapshot) {
                    ResponseMessage().apply {
                        id = (message as ResponseMessage).id
                        error = ResponseError(ResponseErrorCode.ContentModified, "Document changed during folding", null)
                    }
                } else {
                    message
                }
            // Completing a client request can re-enter the transport. Never do so under lock.
            next.consume(accepted)
        }

    private fun open(
        message: NotificationMessage,
        params: DidOpenTextDocumentParams,
        current: Snapshot?,
        next: MessageConsumer,
    ) {
        val uri = params.textDocument.uri
        if (current == null) {
            close(uri, current, next)
            return
        }
        if (opened[uri] === current.owner) return
        retire(uri, next)
        val pending = opening.remove(uri)?.takeIf { it.owner === current.owner }?.change
        // Edits before the synchronizer was attached have no didChange. Read the actual buffer
        // when no later version is queued; otherwise preserve the client's version/text pairing.
        val item = params.textDocument
        val latest = pending?.params as? DidChangeTextDocumentParams
        val content = latest?.contentChanges?.lastOrNull()?.text ?: current.text
        val version = latest?.textDocument?.version ?: item.version
        next.consume(notification(message.method, DidOpenTextDocumentParams(TextDocumentItem(uri, item.languageId, version, content))))
        opened[uri] = current.owner
    }

    private fun change(
        message: NotificationMessage,
        params: DidChangeTextDocumentParams,
        current: Snapshot?,
        next: MessageConsumer,
    ) {
        val uri = params.textDocument.uri
        if (current == null) return
        if (opened[uri] === current.owner) {
            next.consume(message)
        } else {
            retire(uri, next)
            val previous = opening[uri]?.takeIf { it.owner === current.owner }?.change
            // Full sync needs only the latest version, not every intermediate buffer copy.
            val latest = listOfNotNull(previous, message).maxBy { (it.params as DidChangeTextDocumentParams).textDocument.version }
            opening[uri] = Opening(current.owner, latest)
        }
    }

    private fun close(
        uri: String,
        current: Snapshot?,
        next: MessageConsumer,
    ) {
        // A delayed close cannot discard the reopened incarnation, even before its open arrives.
        if (current != null && (opened[uri] === current.owner || opening[uri]?.owner === current.owner)) return
        retire(uri, next)
        opening.remove(uri)
    }

    private fun retire(
        uri: String,
        next: MessageConsumer,
    ) {
        if (opened.remove(uri) != null) {
            next.consume(notification("textDocument/didClose", DidCloseTextDocumentParams(TextDocumentIdentifier(uri))))
        }
    }

    private fun notification(
        method: String,
        params: Any,
    ): NotificationMessage =
        NotificationMessage().apply {
            this.method = method
            this.params = params
        }
}
