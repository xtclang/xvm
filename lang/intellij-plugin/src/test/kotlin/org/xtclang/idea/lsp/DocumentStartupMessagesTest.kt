package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test

class DocumentStartupMessagesTest {
    private val uri = "file:///Startup.x"
    private val buffers = mutableMapOf(uri to DocumentStartupMessages.Snapshot(Any(), 1, "module Startup {}"))
    private val sent = mutableListOf<Message>()
    private val received = mutableListOf<Message>()
    private val guard = DocumentStartupMessages(buffers::get)
    private val outgoing = guard.outgoing(MessageConsumer { sent.add(it) })
    private val incoming = guard.incoming(MessageConsumer { received.add(it) })

    @Test
    fun `typing and replacement before asynchronous open preserve latest client version`() {
        change(2, "module Startup { void run() {} }")
        change(3, "module Startup {}")
        assertThat(sent).isEmpty()
        open("module Startup { stale opening snapshot }")
        val params = (sent.single() as NotificationMessage).params as DidOpenTextDocumentParams
        assertThat(params.textDocument.version).isEqualTo(3)
        assertThat(params.textDocument.text).isEqualTo("module Startup {}")
        change(4, "module Startup { Int value = 1; }")
        assertThat((sent.last() as NotificationMessage).params).isInstanceOf(DidChangeTextDocumentParams::class.java)
    }

    @Test
    fun `edits before listener attachment replace the old initial snapshot`() {
        open("old text captured before server initialized")
        assertThat(((sent.single() as NotificationMessage).params as DidOpenTextDocumentParams).textDocument.text)
            .isEqualTo(buffers.getValue(uri).text)
    }

    @Test
    fun `close before open cannot resurrect a closed document`() {
        change(2, "intermediate text")
        buffers.clear()
        outgoing.consume(notification("textDocument/didClose", DidCloseTextDocumentParams(TextDocumentIdentifier(uri))))
        open("late initial text")
        assertThat(sent).isEmpty()
    }

    @Test
    fun `reopen retires old state and a delayed close cannot close the new incarnation`() {
        open("initial text")
        buffers[uri] = DocumentStartupMessages.Snapshot(Any(), 1, "module Reopened {}")
        open("reopened")
        outgoing.consume(notification("textDocument/didClose", DidCloseTextDocumentParams(TextDocumentIdentifier(uri))))
        assertThat(sent.map { (it as NotificationMessage).method })
            .containsExactly("textDocument/didOpen", "textDocument/didClose", "textDocument/didOpen")
        assertThat(((sent.last() as NotificationMessage).params as DidOpenTextDocumentParams).textDocument.text)
            .isEqualTo("module Reopened {}")
    }

    @Test
    fun `late close preserves changes queued for the reopened incarnation`() {
        open("initial text")
        buffers[uri] = DocumentStartupMessages.Snapshot(Any(), 2, "module Reopened { Int value = 1; }")
        change(2, buffers.getValue(uri).text)
        outgoing.consume(notification("textDocument/didClose", DidCloseTextDocumentParams(TextDocumentIdentifier(uri))))
        open("reopened snapshot before edit")
        val params = (sent.last() as NotificationMessage).params as DidOpenTextDocumentParams
        assertThat(params.textDocument.version).isEqualTo(2)
        assertThat(params.textDocument.text).isEqualTo(buffers.getValue(uri).text)
        assertThat(sent.map { (it as NotificationMessage).method })
            .containsExactly("textDocument/didOpen", "textDocument/didClose", "textDocument/didOpen")
    }

    @Test
    fun `fold response for a shortened document is rejected before the editor sees old lines`() {
        requestFolds()
        buffers[uri] = buffers.getValue(uri).copy(stamp = 2, text = "")
        respondFolds()
        assertThat((received.single() as ResponseMessage).error.code).isEqualTo(ResponseErrorCode.ContentModified.value)
        assertThat((received.single() as ResponseMessage).result).isNull()
    }

    @Test
    fun `reopened document with identical text and stamp rejects old folding response`() {
        requestFolds()
        buffers[uri] = buffers.getValue(uri).copy(owner = Any())
        respondFolds()
        assertThat((received.single() as ResponseMessage).error.code).isEqualTo(ResponseErrorCode.ContentModified.value)
    }

    @Test
    fun `current folding response and ordinary notifications pass unchanged`() {
        requestFolds()
        val response = respondFolds()
        assertThat(received.single()).isSameAs(response)
        val trace = notification("$/setTrace", "verbose")
        outgoing.consume(trace)
        assertThat(sent.last()).isSameAs(trace)
    }

    private fun requestFolds() =
        outgoing.consume(
            RequestMessage().apply {
                id = "fold"
                method = "textDocument/foldingRange"
                params = FoldingRangeRequestParams(TextDocumentIdentifier(uri))
            },
        )

    private fun respondFolds(): ResponseMessage =
        ResponseMessage()
            .apply {
                id = "fold"
                result = listOf(FoldingRange(0, 10))
            }.also(incoming::consume)

    private fun open(text: String) =
        outgoing.consume(notification("textDocument/didOpen", DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text))))

    private fun change(
        version: Int,
        text: String,
    ) = outgoing.consume(
        notification(
            "textDocument/didChange",
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(text)),
            ),
        ),
    )

    private fun notification(
        method: String,
        params: Any,
    ) = NotificationMessage().apply {
        this.method = method
        this.params = params
    }
}
