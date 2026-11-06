package org.xvm.lsp.server

import org.eclipse.lsp4j.DiagnosticTag
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
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.toLsp
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Called under the document lifecycle lock. IDs belong to this server and one document's items. */
internal class DiagnosticReports {
    private val entries = mutableMapOf<String, Entry>()

    /**
     * The documents whose current items no report has given the client yet, by their identities: a report of theirs
     * that the client holds may still change, as it pulls again. Read without the lock, by the server's status.
     */
    private val unreported = ConcurrentHashMap.newKeySet<String>()

    /**
     * Diagnostic items and their result ID, retained for unchanged pull-diagnostic responses, with the tests of the
     * module whose root the document is, if a workspace report has said so: a change to either is a new report.
     */
    private data class Entry(
        val id: String,
        val diagnostics: List<Diagnostic>,
        val tests: XdkTest? = null,
    )

    /** Records [diagnostics] as the document [uri]'s, with the tests its entry has. */
    fun record(
        uri: String,
        diagnostics: List<Diagnostic>,
    ) = record(uri, diagnostics, entries[identity(uri)]?.tests)

    private fun record(
        uri: String,
        diagnostics: List<Diagnostic>,
        tests: XdkTest?,
    ) {
        val key = identity(uri)
        val items =
            diagnostics.map {
                it.copy(location = it.location.copy(uri = identity(it.location.uri)))
            }
        val previous = entries[key]
        if (previous?.diagnostics != items || previous.tests != tests) {
            entries[key] = Entry(UUID.randomUUID().toString(), items, tests)
            unreported += key
        }
    }

    /** The documents whose current items no report has given the client yet, as their canonical URIs. */
    fun unreported(): List<String> = unreported.sorted()

    /**
     * Records the documents' diagnostics of [results], and the tests of the modules whose roots they are, by those
     * roots' URIs, as [testsByRoot] has them.
     */
    fun record(
        results: List<CompilationResult>,
        testsByRoot: Map<String, XdkTest?> = emptyMap(),
    ): Set<String> {
        val uris = results.flatMap { it.documentUris }.toSet()
        val roots = testsByRoot.mapKeys { identity(it.key) }
        uris.forEach { uri ->
            val key = identity(uri)
            record(
                uri,
                results
                    .flatMap { result ->
                        result.diagnostics.filter {
                            it.location.uri == uri ||
                                (uri == result.uri && it.location.uri !in result.documentUris)
                        }
                    }.distinct(),
                if (key in roots) roots[key] else entries[key]?.tests,
            )
        }
        return uris
    }

    fun document(
        uri: String,
        previous: String?,
        related: Set<String>,
        relatedInformation: Boolean,
        tags: Set<DiagnosticTag> = emptySet(),
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
                                it.present(other, relatedInformation, tags)
                            },
                        ).apply {
                            resultId = entries.getValue(identity(other)).id
                        },
                    )
                }.takeIf { it.isNotEmpty() }
        unreported -= identity(uri)
        others?.keys?.forEach { unreported -= identity(it) }
        return if (previous == entry.id) {
            DocumentDiagnosticReport(
                RelatedUnchangedDocumentDiagnosticReport(entry.id).apply {
                    relatedDocuments = others
                },
            )
        } else {
            DocumentDiagnosticReport(
                RelatedFullDocumentDiagnosticReport(
                    entry.diagnostics.map { it.present(uri, relatedInformation, tags) },
                ).apply {
                    resultId = entry.id
                    relatedDocuments = others
                },
            )
        }
    }

    fun workspace(
        current: Set<String>,
        previous: Map<String, String>,
        versions: Map<String, Int>,
        relatedInformation: Boolean,
        tags: Set<DiagnosticTag> = emptySet(),
    ): WorkspaceDiagnosticReport {
        // A removed root/file must explicitly clear the client's previous report, with an empty report whose result ID
        // is REMOVED's. That needs no tombstone: a client that names it again, as VS Code's does in each pull after,
        // is told nothing changed, and an unknown old ID receives another empty full report.
        val currentUris = current.associateBy(::identity)
        // A document's own pulls give it no result ID that the client names in a workspace pull: VS Code's client
        // forgets them as it closes the document. Once the document is gone, as a deleted file is, its items stay on
        // the client unless this report clears them, once, as the entry it drops below.
        val gone =
            entries
                .filter { (key, entry) -> key !in currentUris && entry.diagnostics.isNotEmpty() }
                .keys
                .associateWith { it }
        val oldUris = gone + previous.keys.associateBy(::identity)
        val previousIds = previous.mapKeys { identity(it.key) }
        val documentVersions = versions.mapKeys { identity(it.key) }
        val result =
            WorkspaceDiagnosticReport(
                (oldUris + currentUris).values.sorted().map { uri ->
                    val entry =
                        if (identity(uri) in currentUris) {
                            recordIfAbsent(uri)
                            entries.getValue(identity(uri))
                        } else {
                            REMOVED
                        }
                    unreported -= identity(uri)
                    if (previousIds[identity(uri)] == entry.id) {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceUnchangedDocumentDiagnosticReport(
                                entry.id,
                                uri,
                                documentVersions[identity(uri)],
                            ),
                        )
                    } else {
                        WorkspaceDocumentDiagnosticReport(
                            WorkspaceFullDocumentDiagnosticReport(
                                entry.diagnostics.map { it.present(uri, relatedInformation, tags) },
                                uri,
                                documentVersions[identity(uri)],
                            ).apply { resultId = entry.id },
                        )
                    }
                },
            )
        entries.keys.retainAll(currentUris.keys)
        unreported.retainAll(entries.keys)
        return result
    }

    private fun recordIfAbsent(uri: String) {
        if (identity(uri) !in entries) record(uri, emptyList())
    }

    fun includes(
        uris: Set<String>,
        uri: String,
    ): Boolean =
        uris.any {
            identity(it) == identity(uri)
        }

    private fun Diagnostic.present(
        uri: String,
        relatedInformation: Boolean,
        tags: Set<DiagnosticTag>,
    ) = (if (location.uri == identity(uri)) copy(location = location.copy(uri = uri)) else this)
        .toLsp(uri)
        .apply {
            if (!relatedInformation) this.relatedInformation = null
            this.tags = this.tags?.filter(tags::contains)?.ifEmpty { null }
        }

    private fun identity(uri: String): String =
        runCatching {
            val parsed = URI(uri).normalize()
            if (parsed.scheme == "file") {
                File(parsed).canonicalFile.toURI().toString()
            } else {
                parsed.toString()
            }
        }.getOrDefault(uri)

    fun clear() {
        entries.clear()
        unreported.clear()
    }

    private companion object {
        /** The report of a document that is no longer current: no items, under a result ID that no recorded one has. */
        val REMOVED = Entry("removed", emptyList())
    }
}
