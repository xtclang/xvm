package org.xtclang.idea.lsp

import com.intellij.openapi.diagnostic.logger
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

// TODO LSP4IJ: UP69 — LSP4IJ 0.21.0 never sends workspace/diagnostic and pulls the diagnostics of open documents only,
// so an error in a file that isn't open never reaches IntelliJ. This asks for the server's workspace report itself and
// hands the reports of closed documents to LSP4IJ's push path, which keeps them as the closed document's and reports
// the file to WolfTheProblemSolver. Remove it with an LSP4IJ release whose own workspace pull updates closed documents,
// as main's WorkspaceDiagnosticManager does.

/**
 * The diagnostics of the documents that are not open, from the server's workspace report, for LSP4IJ, which keeps them
 * as the closed document's and reports a file with errors to IntelliJ's problem files: the files that the Project
 * view, the editor tabs and the commit view mark red, and that LSP4IJ's batch inspection reads. Open documents stay on
 * LSP4IJ's pull path. Each document's full report, open or closed, also goes to [reported][Connection.reported], for
 * the Problems window's Project Errors, which LSP4IJ does not fill.
 *
 * Each [refresh] asks for a full report, with no previous result IDs, so each closed document is published as it is
 * now, as a server that pushes its diagnostics does after each analysis; a document that LSP4IJ has dropped, as when it
 * was opened and closed again, is published again. At most one report is asked for at a time, and a refresh while one
 * is outstanding asks for one more once it has been applied. [publish][Connection.publish],
 * [opened][Connection.opened] and [reported][Connection.reported] run on [executor], one report at a time.
 */
internal class ClosedDocumentDiagnostics(
    private val connection: Connection,
    private val executor: Executor,
) {
    private val lock = Any()

    /** A report has been asked for and not yet applied; guarded by [lock]. */
    private var pulling = false

    /** A refresh came while [pulling]; guarded by [lock]. */
    private var again = false

    /** The closed documents whose latest publication had diagnostics; read and written only while applying a report. */
    private val published = mutableSetOf<String>()

    /** What the diagnostics of closed documents need of the server and of LSP4IJ. */
    interface Connection {
        /** Asks for the server's workspace report; null when there is no server, or it has no workspace report. */
        fun report(): CompletableFuture<WorkspaceDiagnosticReport>?

        /** Where LSP4IJ has the document at [uri]. */
        fun document(uri: String): Document

        /** Gives LSP4IJ the diagnostics of the closed document at [uri], as a server's publication would. */
        fun publish(
            uri: String,
            diagnostics: List<Diagnostic>,
        )

        /**
         * The document at [uri], whose diagnostics were published while it was closed, is open now: LSP4IJ pulls its
         * diagnostics, and dropped the published ones when it opened.
         */
        fun opened(uri: String)

        /** The report of the document at [uri], open or closed, for what lists the workspace's errors. */
        fun reported(
            uri: String,
            diagnostics: List<Diagnostic>,
        )
    }

    /** Where LSP4IJ has a document. */
    enum class Document {
        /** No editor has it open: its diagnostics are those published. */
        CLOSED,

        /** An editor has it open, and LSP4IJ has not connected it yet. */
        OPENING,

        /** Open and connected: LSP4IJ pulls its diagnostics. */
        OPEN,

        /** No file that LSP4IJ can find. */
        MISSING,
    }

    /** Asks for the server's workspace report, after the one already asked for, if any, has been applied. */
    fun refresh() {
        synchronized(lock) {
            if (pulling) {
                again = true
                return
            }
            pulling = true
        }
        pull()
    }

    private fun pull() {
        val report =
            try {
                connection.report()
            } catch (failure: RuntimeException) {
                logger.warn("Could not ask the Ecstasy server for its workspace diagnostics", failure)
                null
            }
        if (report == null) {
            pulled()
            return
        }
        report.whenCompleteAsync(
            { result, failure ->
                try {
                    if (failure == null) {
                        result?.let(::apply)
                    } else if (!outdated(failure)) {
                        logger.warn("The Ecstasy server's workspace diagnostics failed", failure)
                    }
                } catch (problem: RuntimeException) {
                    logger.warn("Could not apply the Ecstasy server's workspace diagnostics", problem)
                } finally {
                    pulled()
                }
            },
            executor,
        )
    }

    private fun pulled() {
        synchronized(lock) {
            if (!again) {
                pulling = false
                return
            }
            again = false
        }
        pull()
    }

    private fun apply(report: WorkspaceDiagnosticReport) {
        val reported = mutableSetOf<String>()
        report.items.orEmpty().forEach { item ->
            if (item.isWorkspaceFullDocumentDiagnosticReport) {
                item.workspaceFullDocumentDiagnosticReport.let {
                    reported += it.uri
                    apply(it.uri, it.items.orEmpty())
                }
            } else {
                // only a previous result ID has an unchanged report, and none is sent
                reported += item.workspaceUnchangedDocumentDiagnosticReport.uri
            }
        }
        // a document the report no longer has, as when its module is no longer analyzed, has no diagnostics now
        (published - reported).forEach { apply(it, emptyList()) }
    }

    private fun apply(
        uri: String,
        diagnostics: List<Diagnostic>,
    ) {
        connection.reported(uri, diagnostics)
        when (connection.document(uri)) {
            Document.CLOSED -> {
                connection.publish(uri, diagnostics)
                if (diagnostics.isEmpty()) published -= uri else published += uri
            }

            Document.OPEN -> {
                if (published.remove(uri)) connection.opened(uri)
            }

            // the next report has it connected, or closed again
            Document.OPENING -> Unit

            Document.MISSING -> published -= uri
        }
    }

    /**
     * A request the server refused because a newer analysis is coming, whose refresh asks again, or one that was
     * cancelled, as when the connection ends.
     */
    private fun outdated(failure: Throwable): Boolean {
        val cause = (failure as? CompletionException)?.cause ?: failure
        return cause is CancellationException || (cause as? ResponseErrorException)?.responseError?.code in OUTDATED
    }

    companion object {
        private val logger = logger<ClosedDocumentDiagnostics>()

        private val OUTDATED =
            setOf(
                ResponseErrorCode.ContentModified.value,
                ResponseErrorCode.ServerCancelled.value,
                ResponseErrorCode.RequestCancelled.value,
            )
    }
}
