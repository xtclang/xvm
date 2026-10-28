package org.xvm.lsp.server

import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentContentResult
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.ReadOnlyDocument
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-connection ownership of revision-addressed library documents. Mapping a navigation target
 * registers it; this is deliberately not a file reader or a client-controlled URI decoder.
 * A new library revision has a new URI, so a URI's text never changes, and the server never asks a
 * client to fetch a library document again.
 */
internal class ReadOnlyDocuments(
    private val adapter: Adapter,
) : AutoCloseable {
    private val state = AtomicReference(State())

    /** Whether the connection negotiated library content, and the published library documents. */
    private data class State(
        val enabled: Boolean = false,
        val closed: Boolean = false,
        val documents: Map<String, ReadOnlyDocument> = emptyMap(),
    )

    fun configure(supported: Boolean) {
        state.updateAndGet { if (it.closed) it else it.copy(enabled = supported && adapter.readOnlyDocumentSchemes.isNotEmpty()) }
    }

    fun options(): TextDocumentContentRegistrationOptions? =
        if (state.get().enabled) TextDocumentContentRegistrationOptions(adapter.readOnlyDocumentSchemes.sorted()) else null

    fun present(uri: String): String {
        val current = state.get()
        if (!current.enabled || current.closed) return uri
        val document = adapter.readOnlyDocument(uri) ?: return uri
        val installed =
            state.updateAndGet {
                if (it.closed) it else it.copy(documents = it.documents + (document.uri to document))
            }
        return if (installed.closed) uri else document.uri
    }

    fun content(uri: String): CompletableFuture<TextDocumentContentResult> {
        val current = state.get()
        val document = current.documents[uri]
        return if (!current.closed && current.enabled && document != null) {
            CompletableFuture.completedFuture(TextDocumentContentResult(document.text))
        } else {
            CompletableFuture.failedFuture(
                ResponseErrorException(ResponseError(ResponseErrorCode.InvalidParams, "Unknown or retired library document", null)),
            )
        }
    }

    override fun close() {
        state.set(State(closed = true))
    }
}
