package org.xtclang.idea.lsp

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer

/** Standard Rename remains available to clients that cannot persist compiler graph changes. */
interface XtcLanguageServer : LanguageServer {
    @JsonRequest("xtc/rename")
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?>
}

data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
)

data class SourceGraphReplacement(
    val before: List<SourceModuleConfiguration>,
    val after: List<SourceModuleConfiguration>,
)

data class SourceModuleConfiguration(
    val name: String,
    val uri: String,
    val dependencies: List<String> = emptyList(),
)
