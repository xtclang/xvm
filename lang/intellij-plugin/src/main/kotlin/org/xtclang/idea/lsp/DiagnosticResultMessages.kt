package org.xtclang.idea.lsp

import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

/**
 * LSP4IJ 0.21 omits previousResultId from automatic pulls. Repeating an identical full report
 * replaces and cancels its lazy quick fixes without refreshing unchanged annotations. Use the
 * protocol's unchanged report for repeated pulls of the same editor snapshot. State belongs to one
 * connection; edits and editor replacement require a full report again.
 */
// TODO LSP4IJ: send previousResultId on automatic pulls and preserve unchanged quick fixes.
// Remove this bridge once upstream owns the result lifecycle (diagnostic and quick-fix tests).
internal class DiagnosticResultMessages(
    private val snapshot: (String) -> DocumentStartupMessages.Snapshot?
) {
    private data class Key(val uri: String, val identifier: String?)

    private data class Pending(val key: Key, val snapshot: DocumentStartupMessages.Snapshot)

    private data class Result(val snapshot: DocumentStartupMessages.Snapshot, val id: String)

    private val lock = Any()
    private val requests = mutableMapOf<String, Pending>()
    private val results = mutableMapOf<Key, Result>()

    fun outgoing(next: MessageConsumer): MessageConsumer = MessageConsumer { message ->
        val request = message as? RequestMessage
        val params = request?.params as? DocumentDiagnosticParams
        val current = params?.textDocument?.uri?.let(snapshot)
        val forwarded =
            synchronized(lock) {
                val close = (message as? NotificationMessage)?.params as? DidCloseTextDocumentParams
                val cancelled = (message as? NotificationMessage)?.params as? CancelParams
                cancelled?.let { requests.remove(it.id) }
                if (close != null) {
                    val uri = close.textDocument.uri
                    results.keys.removeIf { it.uri == uri }
                    requests.values.removeIf { it.key.uri == uri }
                }
                // Explicit callers own their previous-result policy. Only the installed
                // synchronizer's typed, automatic request is enriched here.
                if (params == null || current == null || params.previousResultId != null) {
                    message
                } else {
                    val key = Key(params.textDocument.uri, params.identifier)
                    requests[request.id] = Pending(key, current)
                    val previous = results[key]?.takeIf { it.snapshot == current }?.id
                    if (previous == null) message
                    else
                        RequestMessage().apply {
                            id = request.id
                            method = request.method
                            this.params =
                                DocumentDiagnosticParams(params.textDocument).apply {
                                    identifier = params.identifier
                                    previousResultId = previous
                                    workDoneToken = params.workDoneToken
                                    partialResultToken = params.partialResultToken
                                }
                        }
                }
            }
        next.consume(forwarded)
    }

    fun incoming(next: MessageConsumer): MessageConsumer = MessageConsumer { message ->
        val response = message as? ResponseMessage
        val pending = synchronized(lock) { response?.let { requests.remove(it.id) } }
        val current = pending?.key?.uri?.let(snapshot)
        val report = response?.result as? DocumentDiagnosticReport
        val id = report?.left?.resultId ?: report?.right?.resultId
        if (
            pending != null && current == pending.snapshot && response?.error == null && id != null
        ) {
            synchronized(lock) { results[pending.key] = Result(pending.snapshot, id) }
        }
        // Client callbacks may re-enter the transport; never invoke them under our lock.
        next.consume(message)
    }
}
