package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.model.Diagnostic
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS

/**
 * An editor change the server cannot apply leaves the two texts different. The server must say so and
 * stop answering for the wrong text, then recover from a full-text change or a reopen.
 */
class OutOfSyncDocumentTest {
    private class Client : LanguageClient {
        val diagnostics = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val messages = CopyOnWriteArrayList<MessageParams>()

        override fun telemetryEvent(`object`: Any?) {}

        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
            this.diagnostics.add(diagnostics)
        }

        override fun showMessage(messageParams: MessageParams) {
            messages.add(messageParams)
        }

        override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
            CompletableFuture.completedFuture(null)

        override fun logMessage(message: MessageParams) {}

        fun codes(): List<String?> =
            diagnostics
                .lastOrNull { it.uri == URI }
                ?.diagnostics
                .orEmpty()
                .map { it.code?.left }

        /** Analysis publishes asynchronously; wait for the report the test expects. */
        fun awaitCodes(accept: (List<String?>) -> Boolean): List<String?> {
            val deadline = System.nanoTime() + SECONDS.toNanos(10)
            while (!accept(codes())) {
                check(System.nanoTime() < deadline) { "unexpected diagnostics ${codes()}" }
                Thread.sleep(20)
            }
            return codes()
        }
    }

    private class Session : AutoCloseable {
        val client = Client()
        val server = XtcLanguageServer(MockAdapter())
        val documents = server.textDocumentService
        private var version = 1

        init {
            server.connect(client)
            server
                .initialize(
                    InitializeParams().apply {
                        initializationOptions = mapOf("xtcDocumentSync" to mapOf("incremental" to true))
                        capabilities =
                            ClientCapabilities().apply {
                                textDocument =
                                    TextDocumentClientCapabilities().apply {
                                        synchronization = SynchronizationCapabilities(true, true, true)
                                    }
                            }
                    },
                ).get(10, SECONDS)
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", version, TEXT)))
        }

        fun change(vararg changes: TextDocumentContentChangeEvent) =
            documents.didChange(DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(URI, ++version), changes.toList()))

        fun reopen() {
            documents.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", ++version, TEXT)))
        }

        fun hover(): CompletableFuture<*> = documents.hover(HoverParams(TextDocumentIdentifier(URI), Position(0, 7)))

        override fun close() {
            server.shutdown().get(10, SECONDS)
        }
    }

    private fun patch(
        line: Int,
        start: Int,
        end: Int,
        text: String,
    ) = TextDocumentContentChangeEvent(Range(Position(line, start), Position(line, end)), text)

    private fun assertRefused(request: CompletableFuture<*>) {
        assertThatThrownBy { request.get(10, SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .cause()
            .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                assertThat(it.responseError.code).isEqualTo(ResponseErrorCode.RequestFailed.value)
                assertThat(it.responseError.message).contains("out of sync")
            }
    }

    @Test
    fun `an unappliable change reports the file, warns once and refuses queries`() {
        Session().use { session ->
            session.change(patch(5, 0, 0, "x"))
            assertThat(session.client.codes()).containsExactly(Diagnostic.Codes.DOCUMENT_OUT_OF_SYNC)
            assertThat(session.client.messages.map { it.type }).containsExactly(MessageType.Warning)
            assertThat(
                session.client.messages
                    .single()
                    .message,
            ).contains("Editing.x", "Close and reopen")
            assertRefused(session.hover())

            // later patches would apply to the wrong text, so they are ignored without another warning
            session.change(patch(0, 0, 0, "y"))
            session.change(patch(9, 0, 0, "z"))
            assertThat(session.client.messages).hasSize(1)
            assertRefused(session.hover())
        }
    }

    @Test
    fun `a full-text change resynchronizes`() {
        Session().use { session ->
            session.change(patch(5, 0, 0, "x"))
            assertRefused(session.hover())
            session.change(TextDocumentContentChangeEvent(TEXT), patch(0, 0, 0, ""))
            session.hover().get(10, SECONDS)
            session.client.awaitCodes { Diagnostic.Codes.DOCUMENT_OUT_OF_SYNC !in it }
        }
    }

    @Test
    fun `close and reopen resynchronizes`() {
        Session().use { session ->
            session.change(patch(5, 0, 0, "x"))
            session.reopen()
            session.hover().get(10, SECONDS)
            session.client.awaitCodes { Diagnostic.Codes.DOCUMENT_OUT_OF_SYNC !in it }
        }
    }

    private companion object {
        const val URI = "file:///workspace/Editing.x"
        const val TEXT = "module Editing {\n    Int value = 1;\n}\n"
    }
}
