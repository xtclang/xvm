package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.RelatedUnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Forces diagnostic reply, edit and close races around snapshot lookup. Ensures a retired or late report cannot become
 * the record of a document as it is now.
 */
class DiagnosticResultMessagesTest {
    private val uri = "file:///Diagnostics.x"
    private val initial = DiagnosticResultMessages.Snapshot(Any(), 1, "module Diagnostics {}")
    private val buffers = mutableMapOf(uri to initial)
    private val received = mutableListOf<Message>()
    private val recorded = mutableListOf<String>()
    private val guard = DiagnosticResultMessages(buffers::get) { recorded += it }
    private val outgoing = guard.outgoing(MessageConsumer {})
    private val incoming = guard.incoming(MessageConsumer { received.add(it) })

    @Test
    fun `close or cancellation during reply snapshot lookup cannot record a retired report`() {
        listOf(false, true).forEach { cancel ->
            val duringSnapshot = AtomicReference<(() -> Unit)?>(null)
            val guard =
                DiagnosticResultMessages(
                    snapshot = {
                        duringSnapshot.getAndSet(null)?.invoke()
                        initial
                    },
                )
            val outgoing = guard.outgoing(MessageConsumer {})
            val incoming = guard.incoming(MessageConsumer {})
            outgoing.consume(
                RequestMessage().apply {
                    id = "pending"
                    method = "textDocument/diagnostic"
                    params = DocumentDiagnosticParams(TextDocumentIdentifier(uri))
                },
            )
            duringSnapshot.set {
                outgoing.consume(
                    NotificationMessage().apply {
                        method = if (cancel) "$/cancelRequest" else "textDocument/didClose"
                        params =
                            if (cancel) {
                                CancelParams().apply { id = "pending" }
                            } else {
                                DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
                            }
                    },
                )
            }
            incoming.consume(
                ResponseMessage().apply {
                    id = "pending"
                    result = DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()).apply { resultId = "retired" })
                },
            )
            assertThat(guard.reported()).isEmpty()
        }
    }

    @Test
    fun `a report reaches the client as it came`() {
        request("1")
        val response = reply("1", "first")
        assertThat(received.single()).isSameAs(response)
    }

    @Test
    fun `cancellation and errors record no report the client never consumed`() {
        request("cancelled")
        outgoing.consume(
            NotificationMessage().apply {
                method = "$/cancelRequest"
                params = CancelParams().apply { id = "cancelled" }
            },
        )
        reply("cancelled", "ignored", items = 1)
        request("failed")
        incoming.consume(
            ResponseMessage().apply {
                id = "failed"
                error = ResponseError(ResponseErrorCode.ContentModified, "changed", null)
            },
        )
        assertThat(guard.currentItems(uri)).isNull()
        assertThat(guard.reported()).isEmpty()
    }

    @Test
    fun `the latest report counts for the document as it is, and not once it has changed`() {
        assertThat(guard.currentItems(uri)).isNull()
        request("1")
        reply("1", "first", items = 2)
        assertThat(guard.currentItems(uri)).isEqualTo(2)
        assertThat(guard.reported()).containsExactly(uri)

        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        assertThat(guard.currentItems(uri)).isNull()
        request("2", previous = "first")
        reply("2", "second", items = 0)
        assertThat(guard.currentItems(uri)).isEqualTo(0)
    }

    @Test
    fun `an unchanged report keeps the item count of the report before it`() {
        request("1")
        reply("1", "first", items = 1)
        // LSP4IJ asks with the result ID of the report it applied
        request("2", previous = "first")
        incoming.consume(
            ResponseMessage().apply {
                id = "2"
                result = DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport("first"))
            },
        )
        assertThat(guard.currentItems(uri)).isEqualTo(1)
    }

    @Test
    fun `an unchanged report counts the items of the report it names, also one for a text that changed while it came`() {
        request("1")
        reply("1", "first", items = 1)
        assertThat(guard.currentItems(uri)).isEqualTo(1)
        // the text changes while the next report comes, which LSP4IJ can apply all the same, before it sends the change
        request("2", previous = "first")
        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        reply("2", "second", items = 0)
        assertThat(guard.currentItems(uri)).isNull()
        // and its next pull names that report, as X71's did (run-4165373714699057152)
        request("3", previous = "second")
        unchanged("3", "second")
        assertThat(guard.currentItems(uri)).isEqualTo(0)
        assertThat(recorded).containsExactly(uri, uri)
    }

    @Test
    fun `an unchanged report that names no report it knows records nothing`() {
        request("1")
        reply("1", "first", items = 1)
        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        request("2", previous = "unknown")
        unchanged("2", "unknown")
        assertThat(guard.currentItems(uri)).isNull()
    }

    @Test
    fun `a late report for an earlier state does not replace the current one, and a close forgets it`() {
        request("old")
        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        request("new")
        reply("new", "current", items = 0)
        reply("old", "obsolete", items = 3)
        assertThat(guard.currentItems(uri)).isEqualTo(0)

        outgoing.consume(
            NotificationMessage().apply {
                method = "textDocument/didClose"
                params = DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
            },
        )
        assertThat(guard.currentItems(uri)).isNull()
        assertThat(guard.reported()).isEmpty()
    }

    private fun request(
        id: String,
        previous: String? = null,
    ) {
        outgoing.consume(
            RequestMessage().apply {
                this.id = id
                method = "textDocument/diagnostic"
                params =
                    DocumentDiagnosticParams(TextDocumentIdentifier(uri)).apply {
                        previousResultId = previous
                        identifier = "xtc"
                    }
            },
        )
    }

    private fun unchanged(
        id: String,
        resultId: String,
    ) = incoming.consume(
        ResponseMessage().apply {
            this.id = id
            result = DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport(resultId))
        },
    )

    private fun reply(
        id: String,
        resultId: String,
        items: Int = 0,
    ): ResponseMessage =
        ResponseMessage().apply {
            this.id = id
            result =
                DocumentDiagnosticReport(
                    RelatedFullDocumentDiagnosticReport(
                        List(items) { Diagnostic(Range(Position(0, 0), Position(0, 1)), "problem $it") },
                    ).apply {
                        this.resultId = resultId
                    },
                )
            incoming.consume(this)
        }
}
