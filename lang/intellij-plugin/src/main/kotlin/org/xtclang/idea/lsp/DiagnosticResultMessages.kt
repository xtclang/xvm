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
 * Records the latest pull-diagnostic report of each open document and the editor snapshot it was asked for, as a test
 * waiting for the diagnostics of an edit needs to know. State belongs to one connection; a close forgets the
 * document's report.
 */
internal class DiagnosticResultMessages(
    private val snapshot: (String) -> Snapshot?,
) {
    private val lock = Any()
    private val requests = mutableMapOf<String, Pending>()

    /** The latest report of each document, by URI, and the snapshot it was asked for. */
    private val reports = mutableMapOf<String, Report>()

    /** An open document's owner, stamp and text, which tell whether a report is still the document's as it is now. */
    data class Snapshot(
        val owner: Any,
        val stamp: Long,
        val text: String,
    )

    /** The document of a pull-diagnostic request, and its snapshot when the request is sent. */
    private data class Pending(
        val uri: String,
        val snapshot: Snapshot,
    )

    /** The number of items of a document's latest report, and the snapshot that report was asked for. */
    private data class Report(
        val snapshot: Snapshot,
        val items: Int,
    )

    /** The URIs of the documents with a report. */
    fun reported(): Set<String> = synchronized(lock) { reports.keys.toSet() }

    /**
     * The number of items of the latest report of the document at [uri], if that report was asked for the document as
     * it is now; null if there is none, or the document has changed since, as a test waiting for the diagnostics of an
     * edit needs to know.
     */
    fun currentItems(uri: String): Int? {
        val now = snapshot(uri) ?: return null
        return synchronized(lock) { reports[uri]?.takeIf { it.snapshot == now }?.items }
    }

    fun outgoing(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val request = message as? RequestMessage
            val params = request?.params as? DocumentDiagnosticParams
            val current = params?.textDocument?.uri?.let(snapshot)
            synchronized(lock) {
                val close = (message as? NotificationMessage)?.params as? DidCloseTextDocumentParams
                val cancelled = (message as? NotificationMessage)?.params as? CancelParams
                cancelled?.let { requests.remove(it.id) }
                if (close != null) {
                    val uri = close.textDocument.uri
                    requests.values.removeIf { it.uri == uri }
                    reports.remove(uri)
                }
                // every pull of an open document, with or without a previousResultId, which LSP4IJ's carry
                if (params != null && current != null) {
                    requests[request.id] = Pending(params.textDocument.uri, current)
                }
            }
            next.consume(message)
        }

    fun incoming(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val response = message as? ResponseMessage
            val pending = synchronized(lock) { response?.let { requests[it.id] } }
            val current = pending?.uri?.let(snapshot)
            val report = response?.result as? DocumentDiagnosticReport
            if (pending != null && response != null) {
                synchronized(lock) {
                    // Snapshot lookup runs outside this lock. Close/cancel may retire the request
                    // while it runs; only the still-owned request can record its report.
                    val owned = requests.remove(response.id, pending)
                    // a late reply for an earlier state never replaces the record of the current one
                    if (owned && current == pending.snapshot && response.error == null && report != null) {
                        val items = report.left?.items?.size ?: reports[pending.uri]?.items ?: 0
                        reports[pending.uri] = Report(pending.snapshot, items)
                    }
                }
            }
            // Client callbacks may re-enter the transport; never invoke them under our lock.
            next.consume(message)
        }
}
