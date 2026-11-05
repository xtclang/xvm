package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceUnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.ClosedDocumentDiagnostics.Document
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP69 — ClosedDocumentDiagnostics bridges LSP4IJ 0.21.0's missing workspace pull; these tests go with it.

/** How the diagnostics of closed documents follow the server's workspace reports, against a connection of the test's. */
class ClosedDocumentDiagnosticsTest {
    private val connection = Connection()
    private val diagnostics = ClosedDocumentDiagnostics(connection) { it.run() }

    @Test
    fun `a closed document's report is published`() {
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        assertThat(connection.events).containsExactly("publish a.x [one]")
    }

    @Test
    fun `a document that is open or opening is not published`() {
        connection.documents["open.x"] = Document.OPEN
        connection.documents["opening.x"] = Document.OPENING
        connection.documents["missing.x"] = Document.MISSING
        diagnostics.refresh()
        connection.answer(
            report("open.x" to listOf(diagnostic("one")), "opening.x" to listOf(diagnostic("two")), "missing.x" to emptyList()),
        )
        assertThat(connection.events).isEmpty()
    }

    @Test
    fun `a document published with diagnostics that opens is retired once`() {
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        connection.documents["a.x"] = Document.OPENING
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        connection.documents["a.x"] = Document.OPEN
        repeat(2) {
            diagnostics.refresh()
            connection.answer(report("a.x" to emptyList()))
        }
        assertThat(connection.events).containsExactly("publish a.x [one]", "opened a.x")
    }

    @Test
    fun `a document published without diagnostics that opens is left to LSP4IJ`() {
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to emptyList()))
        connection.documents["a.x"] = Document.OPEN
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        assertThat(connection.events).containsExactly("publish a.x []")
    }

    @Test
    fun `a closed document opened and closed again is published again`() {
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        connection.documents["a.x"] = Document.OPEN
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        assertThat(connection.events).containsExactly("publish a.x [one]", "opened a.x", "publish a.x [one]")
    }

    @Test
    fun `a published document the report no longer has is cleared`() {
        connection.documents["a.x"] = Document.CLOSED
        connection.documents["b.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one")), "b.x" to emptyList()))
        diagnostics.refresh()
        connection.answer(report())
        // b.x was published without diagnostics, which is what it has
        assertThat(connection.events).containsExactly("publish a.x [one]", "publish b.x []", "publish a.x []")
    }

    @Test
    fun `an unchanged report keeps what was published`() {
        connection.documents["a.x"] = Document.CLOSED
        diagnostics.refresh()
        connection.answer(report("a.x" to listOf(diagnostic("one"))))
        diagnostics.refresh()
        connection.answer(
            WorkspaceDiagnosticReport(
                listOf(WorkspaceDocumentDiagnosticReport(WorkspaceUnchangedDocumentDiagnosticReport("id", "a.x", null))),
            ),
        )
        assertThat(connection.events).containsExactly("publish a.x [one]")
    }

    @Test
    fun `refreshes while a report is outstanding ask for one more after it`() {
        diagnostics.refresh()
        diagnostics.refresh()
        diagnostics.refresh()
        assertThat(connection.asked).isEqualTo(1)
        connection.answer(report())
        assertThat(connection.asked).isEqualTo(2)
        connection.answer(report())
        assertThat(connection.asked).isEqualTo(2)
        diagnostics.refresh()
        assertThat(connection.asked).isEqualTo(3)
    }

    @Test
    fun `a refused report is asked for again at the next refresh`() {
        diagnostics.refresh()
        connection.refuse(ResponseErrorCode.ContentModified)
        diagnostics.refresh()
        assertThat(connection.asked).isEqualTo(2)
    }

    @Test
    fun `a report that cannot be asked for is asked for again at the next refresh`() {
        connection.unavailable = true
        diagnostics.refresh()
        assertThat(connection.asked).isEqualTo(1)
        connection.unavailable = false
        diagnostics.refresh()
        assertThat(connection.asked).isEqualTo(2)
    }

    private fun diagnostic(code: String) = Diagnostic(Range(Position(0, 0), Position(0, 1)), code, null, null, code)

    private fun report(vararg documents: Pair<String, List<Diagnostic>>) =
        WorkspaceDiagnosticReport(
            documents.map { (uri, items) -> WorkspaceDocumentDiagnosticReport(WorkspaceFullDocumentDiagnosticReport(items, uri, null)) },
        )

    /** The test's server and LSP4IJ: the documents where the test says they are, and a report the test answers. */
    private class Connection : ClosedDocumentDiagnostics.Connection {
        val documents = mutableMapOf<String, Document>()
        val events = mutableListOf<String>()
        var asked = 0
        var unavailable = false
        private var outstanding: CompletableFuture<WorkspaceDiagnosticReport>? = null

        override fun report(): CompletableFuture<WorkspaceDiagnosticReport>? {
            asked++
            check(outstanding == null) { "A report was asked for while one was outstanding" }
            if (unavailable) throw IllegalStateException("no server")
            return CompletableFuture<WorkspaceDiagnosticReport>().also { outstanding = it }
        }

        override fun document(uri: String): Document = documents[uri] ?: Document.MISSING

        override fun publish(
            uri: String,
            diagnostics: List<Diagnostic>,
        ) {
            events += "publish $uri ${diagnostics.joinToString(",", "[", "]") { it.code.left }}"
        }

        override fun opened(uri: String) {
            events += "opened $uri"
        }

        fun answer(report: WorkspaceDiagnosticReport) = checkNotNull(outstanding).also { outstanding = null }.complete(report)

        fun refuse(code: ResponseErrorCode) =
            checkNotNull(outstanding)
                .also { outstanding = null }
                .completeExceptionally(ResponseErrorException(ResponseError(code, "refused", null)))
    }
}
