package org.xvm.lsp.server

import org.eclipse.lsp4j.TextDocumentContentRefreshParams
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentContentResult
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.ReadOnlyDocument
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-connection ownership of revision-addressed library documents. Mapping a navigation target
 * registers it; this is deliberately not a file reader or a client-controlled URI decoder.
 * A new library revision has a new URI. Refresh revalidates an already fetched immutable view.
 */
internal class ReadOnlyDocuments(
    private val adapter: Adapter,
    private val client: () -> LanguageClient?,
) : AutoCloseable {
    private data class State(
        val enabled: Boolean = false,
        val ready: Boolean = false,
        val closed: Boolean = false,
        val documents: Map<String, ReadOnlyDocument> = emptyMap(),
        val fetched: Set<String> = emptySet(),
    )

    private val state = AtomicReference(State())
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    fun configure(supported: Boolean) {
        state.updateAndGet { if (it.closed) it else it.copy(enabled = supported && adapter.readOnlyDocumentSchemes.isNotEmpty()) }
    }

    fun options(): TextDocumentContentRegistrationOptions? =
        if (state.get().enabled) TextDocumentContentRegistrationOptions(adapter.readOnlyDocumentSchemes.sorted()) else null

    fun initialized() {
        state.updateAndGet { it.copy(ready = !it.closed) }
    }

    fun present(uri: String): String {
        val current = state.get()
        if (!current.enabled || current.closed) return uri
        val document = adapter.readOnlyDocument(uri) ?: return uri
        val installed = state.updateAndGet {
            if (it.closed) it else it.copy(documents = it.documents + (document.uri to document))
        }
        return if (installed.closed) uri else document.uri
    }

    fun content(uri: String): CompletableFuture<TextDocumentContentResult> {
        val current = state.updateAndGet {
            if (it.closed || uri !in it.documents) it else it.copy(fetched = it.fetched + uri)
        }
        val document = current.documents[uri]
        return if (!current.closed && current.enabled && document != null) {
            CompletableFuture.completedFuture(TextDocumentContentResult(document.text))
        } else {
            CompletableFuture.failedFuture(
                ResponseErrorException(ResponseError(ResponseErrorCode.InvalidParams, "Unknown or retired library document", null)),
            )
        }
    }

    /** Called after compiler input replacement. One outstanding refresh per immutable document. */
    fun refresh() {
        val current = state.get()
        if (!current.ready || current.closed) return
        current.fetched.filter { refreshing.add(it) }.forEach { uri ->
            // Never enter the client transport while holding compiler/document publication locks.
            CompletableFuture.runAsync {
                val reply = runCatching {
                    if (state.get().closed) null else client()?.refreshTextDocumentContent(TextDocumentContentRefreshParams(uri))
                }.getOrElse { CompletableFuture.failedFuture(it) } ?: CompletableFuture.completedFuture(null)
                reply.orTimeout(10, SECONDS).whenComplete { _, failure ->
                    refreshing.remove(uri)
                    if (failure != null && !state.get().closed) logger.debug("Library content refresh failed for {}", uri, failure)
                }
            }
        }
    }

    override fun close() {
        state.set(State(closed = true))
        refreshing.clear()
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ReadOnlyDocuments::class.java)
    }
}
