package org.xtclang.idea.lsp

import org.eclipse.lsp4j.Diagnostic
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
 * waiting for the diagnostics of an edit needs to know, and [XtcClientFeatures]' bridge of UP86, which [recorded] tells
 * of each report recorded for a document's text as it is. A full report's items are kept by its result ID, also when
 * the text changed while it came, as LSP4IJ can apply it then: an unchanged report names the report it repeats, and
 * counts its items. State belongs to one connection; a close forgets the document's reports.
 */
internal class DiagnosticResultMessages(
    private val snapshot: (String) -> Snapshot?,
    private val recorded: (String) -> Unit = {},
) {
    private val lock = Any()
    private val requests = mutableMapOf<String, Pending>()

    /** The latest report of each document, by URI, and the snapshot it was asked for. */
    private val reports = mutableMapOf<String, Report>()

    /** The latest full reports of each document, by URI, each by its result ID, the newest last. */
    private val fullReports = mutableMapOf<String, LinkedHashMap<String, Full>>()

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

    /** The items of a document's latest report, the full report's that it is or names, and the snapshot it was asked for. */
    private data class Report(
        val snapshot: Snapshot,
        val items: List<Diagnostic>,
    )

    /** A full report's items, and the owner of the snapshot it was asked for: a document's connection, which a reopen replaces. */
    private data class Full(
        val owner: Any,
        val items: List<Diagnostic>,
    )

    /** The URIs of the documents with a report. */
    fun reported(): Set<String> = synchronized(lock) { reports.keys.toSet() }

    /**
     * The number of items of the latest report of the document at [uri], if that report was asked for the document as
     * it is now; null if there is none, or the document has changed since, as a test waiting for the diagnostics of an
     * edit needs to know.
     */
    fun currentItems(uri: String): Int? = currentReport(uri)?.size

    /**
     * The items of the latest report of the document at [uri], if that report was asked for the document as it is now:
     * its own pull's report, full or unchanged, which LSP4IJ applies, or has; null otherwise.
     */
    fun currentReport(uri: String): List<Diagnostic>? {
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
                    fullReports.remove(uri)
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
            var now = false
            if (pending != null && response != null) {
                synchronized(lock) {
                    // Snapshot lookup runs outside this lock. Close/cancel may retire the request
                    // while it runs; only the still-owned request can record its report.
                    val owned = requests.remove(response.id, pending)
                    if (owned && response.error == null && report != null) {
                        val items = items(pending, report)
                        // a late reply for an earlier state never replaces the record of the current one
                        if (items != null && current == pending.snapshot) {
                            reports[pending.uri] = Report(pending.snapshot, items)
                            now = true
                        }
                    }
                }
            }
            // Client callbacks may re-enter the transport; never invoke them under our lock.
            next.consume(message)
            if (now) recorded(checkNotNull(pending).uri)
        }

    /**
     * Called under the lock. The items of [report], the reply to [pending]: a full report's, which are kept by its
     * result ID, also for a text that has changed since, or those of the full report that an unchanged one names, if it
     * was the same connection's; null when it names none known.
     */
    private fun items(
        pending: Pending,
        report: DocumentDiagnosticReport,
    ): List<Diagnostic>? {
        val full =
            report.left ?: return report.right?.resultId?.let { id ->
                fullReports[pending.uri]?.get(id)?.takeIf { it.owner == pending.snapshot.owner }?.items
            }
        val items = full.items.orEmpty()
        full.resultId?.let { id ->
            val kept = fullReports.getOrPut(pending.uri) { LinkedHashMap() }
            kept.remove(id)
            kept[id] = Full(pending.snapshot.owner, items)
            while (kept.size > KEPT) kept.remove(kept.keys.first())
        }
        return items
    }

    private companion object {
        /**
         * How many full reports of a document are kept: an unchanged report names the latest one that LSP4IJ applied. Were
         * more than this many full reports that LSP4IJ discarded to follow that one, it would be evicted, and the next
         * unchanged report, naming it, would record nothing. Our server does not answer so: it gives a document's report a
         * new result ID whenever the document's items change, and answers a pull as unchanged only when it names the
         * current one (DiagnosticReports.document), so once a full report with another ID has come, no unchanged report
         * names the one before it. The others kept are for replies that cross.
         */
        const val KEPT = 8
    }
}
