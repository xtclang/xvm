package org.xtclang.idea.lsp

import com.redhat.devtools.lsp4ij.JSONUtils
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import java.util.UUID

// TODO LSP4IJ: UP07 — every full report rebuilds/cancels lazy fixes, even when diagnostic equality
// suppresses annotation refresh. Give each delivered full report a client-only data revision so
// annotations acquire the new fixes. Restore the server's exact data before a code-action request.
// Remove when upstream retains equivalent lazy fixes or refreshes their owning annotations.
internal object DiagnosticQuickFixes {
    private data class Presentation(
        val original: Any?,
        val revision: UUID,
    )

    fun incoming(message: Message): Message {
        val response = message as? ResponseMessage ?: return message
        val report = response.result as? DocumentDiagnosticReport ?: return message
        val full = report.left ?: return message
        if (full.items.isEmpty()) return message
        val gson = JSONUtils.getLsp4jGson()
        val copy = gson.fromJson(gson.toJsonTree(full), full.javaClass)
        val revision = UUID.randomUUID()
        copy.items = copy.items.map { it.apply { data = Presentation(data, revision) } }
        return ResponseMessage().apply {
            jsonrpc = response.jsonrpc
            rawId = response.rawId
            error = response.error
            result = DocumentDiagnosticReport(copy)
        }
    }

    fun outgoing(message: Message): Message {
        val request = message as? RequestMessage ?: return message
        val params = request.params as? CodeActionParams ?: return message
        if (params.context.diagnostics.none { it.data is Presentation }) return message
        val gson = JSONUtils.getLsp4jGson()
        val copy = gson.fromJson(gson.toJsonTree(params), CodeActionParams::class.java)
        copy.context.diagnostics =
            params.context.diagnostics.map { diagnostic ->
                gson.fromJson(gson.toJsonTree(diagnostic), Diagnostic::class.java).apply {
                    data =
                        when (val payload = diagnostic.data) {
                            is Presentation -> payload.original
                            else -> payload
                        }
                }
            }
        return RequestMessage().apply {
            jsonrpc = request.jsonrpc
            rawId = request.rawId
            method = request.method
            this.params = copy
        }
    }
}
