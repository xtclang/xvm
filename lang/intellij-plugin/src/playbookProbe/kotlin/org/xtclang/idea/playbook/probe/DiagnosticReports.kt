package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.xtclang.idea.lsp.XtcClientFeatures

/**
 * Whether the diagnostics LSP4IJ holds for a document are the server's report for the document as it is now, which a
 * test waiting for the diagnostics of an edit needs to know: those of the document before the edit may still be held.
 */
object DiagnosticReports {
    /** The server pushes its diagnostics, so no report is asked for a known state of the document. */
    private const val PUSHED = -2

    /** No report has been asked for the document as it is now, or the client has not received it yet. */
    private const val NONE = -1

    /**
     * The number of items of the latest diagnostics report of the open document at [path], if that report was asked
     * for the document as it is now; [NONE] if not, and [PUSHED] if the server does not pull diagnostics.
     */
    @JvmStatic
    fun currentItems(
        project: Project,
        path: String,
    ): Int {
        val server = LanguageServiceAccessor.getInstance(project).startedServers.singleOrNull() ?: return NONE
        if (server.serverCapabilitiesSync?.diagnosticProvider == null) return PUSHED
        return (server.clientFeatures as? XtcClientFeatures)?.currentDiagnosticItems(path) ?: NONE
    }
}
