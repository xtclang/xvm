package org.xtclang.idea.lsp

import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
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

class DiagnosticResultMessagesTest {
    @Test
    fun `close or cancellation during reply snapshot lookup cannot reinsert a retired result`() {
        listOf(false, true).forEach { cancel ->
            val duringSnapshot = AtomicReference<(() -> Unit)?>(null)
            val guard = DiagnosticResultMessages {
                duringSnapshot.getAndSet(null)?.invoke()
                initial
            }
            val sent = mutableListOf<Message>()
            val outgoing = guard.outgoing(MessageConsumer { sent.add(it) })
            val incoming = guard.incoming(MessageConsumer {})
            fun request(id: String) =
                RequestMessage().apply {
                    this.id = id
                    method = "textDocument/diagnostic"
                    params = DocumentDiagnosticParams(TextDocumentIdentifier(uri))
                }
            outgoing.consume(request("pending"))
            duringSnapshot.set {
                outgoing.consume(
                    NotificationMessage().apply {
                        method = if (cancel) "$/cancelRequest" else "textDocument/didClose"
                        params =
                            if (cancel) CancelParams().apply { id = "pending" }
                            else DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
                    }
                )
            }
            incoming.consume(
                ResponseMessage().apply {
                    id = "pending"
                    result =
                        DocumentDiagnosticReport(
                            RelatedFullDocumentDiagnosticReport(emptyList()).apply {
                                resultId = "retired"
                            }
                        )
                }
            )
            outgoing.consume(request("next"))
            assertThat(
                    ((sent.last() as RequestMessage).params as DocumentDiagnosticParams)
                        .previousResultId
                )
                .isNull()
        }
    }

    private val uri = "file:///Diagnostics.x"
    private val initial = DocumentStartupMessages.Snapshot(Any(), 1, "module Diagnostics {}")
    private val buffers = mutableMapOf(uri to initial)
    private val sent = mutableListOf<Message>()
    private val received = mutableListOf<Message>()
    private val guard = DiagnosticResultMessages(buffers::get)
    private val outgoing = guard.outgoing(MessageConsumer { sent.add(it) })
    private val incoming = guard.incoming(MessageConsumer { received.add(it) })

    @Test
    fun `repeated automatic pulls reuse the current report without changing its response`() {
        assertThat(request("1").previousResultId).isNull()
        val response = reply("1", "first")
        assertThat(received.single()).isSameAs(response)
        assertThat(request("2").previousResultId).isEqualTo("first")
        reply("2", "second")
        assertThat(request("3").previousResultId).isEqualTo("second")
    }

    @Test
    fun `an edit requires a fresh full report even if diagnostic contents would be identical`() {
        request("1")
        reply("1", "old")
        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        assertThat(request("2").previousResultId).isNull()
        reply("2", "new")
        assertThat(request("3").previousResultId).isEqualTo("new")
    }

    @Test
    fun `late results from an earlier edit cannot replace the current result ID`() {
        request("old")
        buffers[uri] = initial.copy(stamp = 2, text = "module Diagnostics { }")
        request("new")
        reply("new", "current")
        reply("old", "obsolete")
        assertThat(request("next").previousResultId).isEqualTo("current")
    }

    @Test
    fun `close and reopen clear pending and cached reports even with the same document stamp`() {
        request("1")
        reply("1", "old")
        request("late")
        outgoing.consume(
            NotificationMessage().apply {
                method = "textDocument/didClose"
                params = DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
            }
        )
        buffers[uri] = initial.copy(owner = Any())
        reply("late", "obsolete")
        assertThat(request("next").previousResultId).isNull()
    }

    @Test
    fun `cancellation and errors do not seed a report the client never consumed`() {
        request("cancelled")
        outgoing.consume(
            NotificationMessage().apply {
                method = "$/cancelRequest"
                params = CancelParams().apply { id = "cancelled" }
            }
        )
        reply("cancelled", "ignored")
        assertThat(request("failed").previousResultId).isNull()
        incoming.consume(
            ResponseMessage().apply {
                id = "failed"
                error = ResponseError(ResponseErrorCode.ContentModified, "changed", null)
            }
        )
        assertThat(request("next").previousResultId).isNull()
    }

    @Test
    fun `explicit previous IDs and different providers keep their own request policy`() {
        request("1")
        reply("1", "cached")
        assertThat(request("2", previous = "caller-owned").previousResultId)
            .isEqualTo("caller-owned")
        reply("2", "caller-result")
        assertThat(request("3").previousResultId).isEqualTo("cached")
        assertThat(request("4", identifier = "other").previousResultId).isNull()
    }

    @Test
    fun `a new connection and requests without an open document start without cached results`() {
        request("1")
        reply("1", "cached")
        buffers.clear()
        assertThat(request("closed").previousResultId).isNull()
        buffers[uri] = initial
        val reconnect = DiagnosticResultMessages(buffers::get)
        val request =
            RequestMessage().apply {
                id = "new-connection"
                method = "textDocument/diagnostic"
                params = DocumentDiagnosticParams(TextDocumentIdentifier(uri))
            }
        reconnect.outgoing(MessageConsumer { sent.add(it) }).consume(request)
        assertThat(sent.last()).isSameAs(request)
    }

    private fun request(
        id: String,
        previous: String? = null,
        identifier: String = "xtc",
    ): DocumentDiagnosticParams {
        outgoing.consume(
            RequestMessage().apply {
                this.id = id
                method = "textDocument/diagnostic"
                params =
                    DocumentDiagnosticParams(TextDocumentIdentifier(uri)).apply {
                        previousResultId = previous
                        this.identifier = identifier
                    }
            }
        )
        return (sent.last() as RequestMessage).params as DocumentDiagnosticParams
    }

    private fun reply(id: String, resultId: String): ResponseMessage =
        ResponseMessage().apply {
            this.id = id
            result =
                DocumentDiagnosticReport(
                    RelatedFullDocumentDiagnosticReport(emptyList()).apply {
                        this.resultId = resultId
                    }
                )
            incoming.consume(this)
        }
}
