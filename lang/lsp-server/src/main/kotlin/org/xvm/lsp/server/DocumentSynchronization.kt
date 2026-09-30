package org.xvm.lsp.server

import com.google.gson.Gson
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions

/** Connection options. Full synchronization and no server save edits remain the defaults. */
internal data class DocumentSynchronization(
    val incremental: Boolean = false,
    val formatOnSave: Boolean = false,
    val willSave: Boolean = false,
    val waitUntil: Boolean = false,
) {
    fun capabilities() =
        TextDocumentSyncOptions().apply {
            openClose = true
            change =
                if (incremental) TextDocumentSyncKind.Incremental else TextDocumentSyncKind.Full
            willSave = this@DocumentSynchronization.willSave
            willSaveWaitUntil = waitUntil
            setSave(true)
        }

    companion object {
        fun read(params: InitializeParams): DocumentSynchronization {
            val options =
                Gson()
                    .toJsonTree(params.initializationOptions)
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("xtcDocumentSync")
                    ?.takeUnless { it.isJsonNull }
            require(options == null || options.isJsonObject) { "xtcDocumentSync must be an object" }
            fun flag(name: String): Boolean {
                val value = options?.asJsonObject?.get(name) ?: return false
                require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
                    "xtcDocumentSync.$name must be a boolean"
                }
                return value.asBoolean
            }
            val synchronization = params.capabilities?.textDocument?.synchronization
            return DocumentSynchronization(
                flag("incremental"),
                flag("formatOnSave"),
                synchronization?.willSave == true,
                synchronization?.willSaveWaitUntil == true,
            )
        }
    }
}
