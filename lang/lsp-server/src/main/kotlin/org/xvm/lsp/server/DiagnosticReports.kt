package org.xvm.lsp.server

import java.io.File
import java.net.URI
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
        val key = identity(uri)
        val items = diagnostics.map {
            it.copy(location = it.location.copy(uri = identity(it.location.uri)))
        }
        if (entries[key]?.diagnostics != items)
            entries[key] = Entry(UUID.randomUUID().toString(), items)
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

    fun document(
        uri: String,
        previous: String?,
        related: Set<String>,
        relatedInformation: Boolean,
    ): DocumentDiagnosticReport {
        recordIfAbsent(uri)
        val entry = entries.getValue(identity(uri))
        val others =
            related
                .filter { it != uri }
                .associateWith { other ->
                    Either.forLeft<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>(
                        FullDocumentDiagnosticReport(
                                entries.getValue(identity(other)).diagnostics.map {
                                    it.present(other, relatedInformation)
                                }
                            )
                            .apply {
                                resultId = entries.getValue(identity(other)).id
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
                RelatedFullDocumentDiagnosticReport(
                        entry.diagnostics.map { it.present(uri, relatedInformation) }
                    )
                    .apply {
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
        relatedInformation: Boolean,
    ): WorkspaceDiagnosticReport {
        // A removed root/file must explicitly clear the client's previous report. Do not retain
        // tombstones indefinitely: an unknown old ID simply receives another empty full report.
        val currentUris = current.associateBy(::identity)
        val oldUris = previous.keys.associateBy(::identity)
        val previousIds = previous.mapKeys { identity(it.key) }
        val documentVersions = versions.mapKeys { identity(it.key) }
        (oldUris - currentUris.keys).values.forEach { record(it, emptyList()) }
        val result =
            WorkspaceDiagnosticReport(
                (oldUris + currentUris).values.sorted().map { uri ->
                    recordIfAbsent(uri)
                    val entry = entries.getValue(identity(uri))
                    if (previousIds[identity(uri)] == entry.id) {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceUnchangedDocumentDiagnosticReport(
                                entry.id,
                                uri,
                                documentVersions[identity(uri)],
                            )
                        )
                    } else {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceFullDocumentDiagnosticReport(
                                    entry.diagnostics.map { it.present(uri, relatedInformation) },
                                    uri,
                                    documentVersions[identity(uri)],
                                )
                                .apply { resultId = entry.id }
                        )
                    }
                }
            )
        entries.keys.retainAll(currentUris.keys)
        return result
    }

    private fun recordIfAbsent(uri: String) {
        if (identity(uri) !in entries) record(uri, emptyList())
    }

    fun includes(uris: Set<String>, uri: String): Boolean = uris.any {
        identity(it) == identity(uri)
    }

    private fun Diagnostic.present(uri: String, relatedInformation: Boolean) =
        (if (location.uri == identity(uri)) copy(location = location.copy(uri = uri)) else this)
            .toLsp(uri)
            .apply { if (!relatedInformation) this.relatedInformation = null }

    private fun identity(uri: String): String = runCatching {
        val parsed = URI(uri).normalize()
        if (parsed.scheme == "file") File(parsed).canonicalFile.toURI().toString()
        else parsed.toString()
    }
        .getOrDefault(uri)

    fun clear() = entries.clear()
}
