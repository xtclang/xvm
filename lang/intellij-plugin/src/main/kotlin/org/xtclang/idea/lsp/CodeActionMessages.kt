package org.xtclang.idea.lsp

import com.redhat.devtools.lsp4ij.JSONUtils
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

// TODO LSP4IJ: UP11 — resolved action edits need a version-checked write command, not an undo-transparent
// action. Route selection through the supported client-command extension until upstream preserves
// import/member-generation Undo/Redo (X105/X122). Listing or resolving alone never applies edits.
internal object CodeActionMessages {
    const val COMMAND = "xtc.resolveCodeAction"

    fun incoming(next: MessageConsumer): MessageConsumer =
        MessageConsumer { message ->
            val results = (message as? ResponseMessage)?.result as? List<*>
            results.orEmpty().forEach { item ->
                val action = (item as? Either<*, *>)?.right as? CodeAction
                if (action?.data != null && action.edit == null && action.command == null) {
                    val original = JSONUtils.getLsp4jGson().toJsonTree(action)
                    action.command = Command(action.title, COMMAND, listOf(original))
                }
            }
            next.consume(message)
        }
}
