package org.xvm.lsp.server

import java.util.UUID
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.FullDocumentDiagnosticReport
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.RelatedUnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceUnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.toLsp

/** Called under the document lifecycle lock. IDs belong to this server and one document's items. */
internal class DiagnosticReports {
    private data class Entry(val id: String, val diagnostics: List<Diagnostic>)

    private val entries = mutableMapOf<String, Entry>()

    fun record(uri: String, diagnostics: List<Diagnostic>) {
        if (entries[uri]?.diagnostics != diagnostics) {
            entries[uri] = Entry(UUID.randomUUID().toString(), diagnostics.toList())
        }
    }

    fun record(results: List<CompilationResult>): Set<String> {
        val uris = results.flatMap { it.documentUris }.toSet()
        uris.forEach { uri ->
            record(
                uri,
                results
                    .flatMap { result ->
                        result.diagnostics.filter {
                            it.location.uri == uri ||
                                (uri == result.uri && it.location.uri !in result.documentUris)
                        }
                    }
                    .distinct(),
            )
        }
        return uris
    }

    fun document(uri: String, previous: String?, related: Set<String>): DocumentDiagnosticReport {
        recordIfAbsent(uri)
        val entry = entries.getValue(uri)
        val others =
            related
                .filter { it != uri }
                .associateWith { other ->
                    Either.forLeft<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>(
                        FullDocumentDiagnosticReport(
                                entries.getValue(other).diagnostics.map { it.toLsp(other) }
                            )
                            .apply {
                                resultId = entries.getValue(other).id
                            }
                    )
                }
                .takeIf { it.isNotEmpty() }
        return if (previous == entry.id) {
            DocumentDiagnosticReport(
                RelatedUnchangedDocumentDiagnosticReport(entry.id).apply {
                    relatedDocuments = others
                }
            )
        } else {
            DocumentDiagnosticReport(
                RelatedFullDocumentDiagnosticReport(entry.diagnostics.map { it.toLsp(uri) }).apply {
                    resultId = entry.id
                    relatedDocuments = others
                }
            )
        }
    }

    fun workspace(
        current: Set<String>,
        previous: Map<String, String>,
        versions: Map<String, Int>,
    ): WorkspaceDiagnosticReport {
        // A removed root/file must explicitly clear the client's previous report. Do not retain
        // tombstones indefinitely: an unknown old ID simply receives another empty full report.
        (previous.keys - current).forEach { record(it, emptyList()) }
        val result =
            WorkspaceDiagnosticReport(
                (current + previous.keys).sorted().map { uri ->
                    recordIfAbsent(uri)
                    val entry = entries.getValue(uri)
                    if (previous[uri] == entry.id) {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceUnchangedDocumentDiagnosticReport(entry.id, uri, versions[uri])
                        )
                    } else {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceFullDocumentDiagnosticReport(
                                    entry.diagnostics.map { it.toLsp(uri) },
                                    uri,
                                    versions[uri],
                                )
                                .apply { resultId = entry.id }
                        )
                    }
                }
            )
        entries.keys.retainAll(current)
        return result
    }

    private fun recordIfAbsent(uri: String) {
        if (uri !in entries) record(uri, emptyList())
    }

    fun clear() = entries.clear()
}
