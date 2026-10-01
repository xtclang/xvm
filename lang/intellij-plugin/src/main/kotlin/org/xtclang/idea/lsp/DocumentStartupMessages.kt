package org.xtclang.idea.lsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.net.URI

// TODO LSP4IJ: serialize didOpen/change/close and reject folding responses for retired editors.
// Remove this transport bridge once upstream passes startup typing and close/reopen regressions.

/**
 * LSP4IJ 0.21 dispatches didOpen independently of its ordered change/close executor. Keep changes
 * behind that open, and bind folding responses to the requesting editor incarnation. State belongs
 * to one transport and uses the client's existing versions and document identities.
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

    // The actual transmitted version, never a second counter. A closed entry retains only the
    // version ceiling: LSP4IJ can reuse versions on reopen, making old server edits ambiguous.
    private data class Sent(
        val owner: Any?,
        val version: Int,
        val text: String,
        val retiredVersion: Int = -1,
    )

    private val lock = Any()
    private val opening = mutableMapOf<String, Opening>()
    private val opened = mutableMapOf<URI, Sent>()
    private val folds = mutableMapOf<String, Fold>()

    /** Prove that an incoming edit's version describes this live editor incarnation and text. */
    fun editSnapshot(
        uri: String,
        version: Int?,
    ): Snapshot? {
        val current = snapshot(uri) ?: return null
        return synchronized(lock) {
            val sent = opened[URI(uri)] ?: return@synchronized null
            current.takeIf {
                sent.owner === it.owner &&
                    sent.text == it.text &&
                    (version == null || (version == sent.version && version > sent.retiredVersion))
            }
        }
    }

    fun isCurrent(
        uri: String,
        version: Int?,
        expected: Snapshot,
    ): Boolean = editSnapshot(uri, version) == expected

    fun outgoing(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val params = (message as? NotificationMessage)?.params
            val uri =
                when (params) {
                    is DidOpenTextDocumentParams -> {
                        params.textDocument.uri
                    }

                    is DidChangeTextDocumentParams -> {
                        params.textDocument.uri
                    }

                    is DidCloseTextDocumentParams -> {
                        params.textDocument.uri
                    }

                    else -> {
                        ((message as? RequestMessage)?.params as? FoldingRangeRequestParams)
                            ?.textDocument
                            ?.uri
                    }
                }
            // The snapshot provider must not acquire an IDE read action: VFS rename can hold
            // the write lock while waiting for this transport to finish didOpen/didChange.
            // Capture before our lock so document lookup never runs under the transport lock.
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
                        error =
                            ResponseError(
                                ResponseErrorCode.ContentModified,
                                "Document changed during folding",
                                null,
                            )
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
        if (opened[URI(uri)]?.owner === current.owner) return
        retire(uri, next)
        val pending = opening.remove(uri)?.takeIf { it.owner === current.owner }?.change
        // Edits before the synchronizer was attached have no didChange. Read the actual buffer
        // when no later version is queued; otherwise preserve the client's version/text pairing.
        val item = params.textDocument
        val latest = pending?.params as? DidChangeTextDocumentParams
        val content = latest?.contentChanges?.lastOrNull()?.text ?: current.text
        val version = latest?.textDocument?.version ?: item.version
        next.consume(
            notification(
                message.method,
                DidOpenTextDocumentParams(TextDocumentItem(uri, item.languageId, version, content)),
            ),
        )
        opened[URI(uri)] =
            Sent(current.owner, version, content, opened[URI(uri)]?.retiredVersion ?: -1)
    }

    private fun change(
        message: NotificationMessage,
        params: DidChangeTextDocumentParams,
        current: Snapshot?,
        next: MessageConsumer,
    ) {
        val uri = params.textDocument.uri
        if (current == null) return
        if (opened[URI(uri)]?.owner === current.owner) {
            val before = opened.getValue(URI(uri))
            if (params.textDocument.version <= before.version) return
            val text = applyChanges(before.text, params.contentChanges)
            next.consume(message)
            opened[URI(uri)] = before.copy(version = params.textDocument.version, text = text)
        } else {
            retire(uri, next)
            val previous = opening[uri]?.takeIf { it.owner === current.owner }?.change
            // Full sync needs only the latest version, not every intermediate buffer copy.
            val latest =
                listOfNotNull(previous, message).maxBy {
                    (it.params as DidChangeTextDocumentParams).textDocument.version
                }
            opening[uri] = Opening(current.owner, latest)
        }
    }

    private fun close(
        uri: String,
        current: Snapshot?,
        next: MessageConsumer,
    ) {
        // A delayed close cannot discard the reopened incarnation, even before its open arrives.
        if (
            current != null &&
            (opened[URI(uri)]?.owner === current.owner || opening[uri]?.owner === current.owner)
        ) {
            return
        }
        retire(uri, next)
        opening.remove(uri)
    }

    private fun retire(
        uri: String,
        next: MessageConsumer,
    ) {
        val before = opened[URI(uri)]
        if (before?.owner != null) {
            opened[URI(uri)] =
                Sent(null, before.version, "", maxOf(before.version, before.retiredVersion))
            next.consume(
                notification(
                    "textDocument/didClose",
                    DidCloseTextDocumentParams(TextDocumentIdentifier(uri)),
                ),
            )
        }
    }

    /** Reconstruct only the wire text, including sequential incremental patches and CRLF. */
    private fun applyChanges(
        text: String,
        changes: List<TextDocumentContentChangeEvent>,
    ): String =
        changes.fold(text) { content, change ->
            val range = change.range
            if (range == null) {
                change.text
            } else {
                val breaks = Regex("\r\n|\r|\n").findAll(content).toList()
                val starts = listOf(0) + breaks.map { it.range.last + 1 }
                val ends = breaks.map { it.range.first } + content.length

                fun offset(at: Position): Int {
                    require(at.line in starts.indices && at.character >= 0)
                    return starts[at.line] +
                        at.character.coerceAtMost(ends[at.line] - starts[at.line])
                }
                content.replaceRange(offset(range.start), offset(range.end), change.text)
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
