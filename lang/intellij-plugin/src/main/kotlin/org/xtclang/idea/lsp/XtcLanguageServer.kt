package org.xtclang.idea.lsp

import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/** Standard Rename remains available to clients that cannot persist compiler graph changes. */
interface XtcLanguageServer : LanguageServer {
    @JsonRequest("xtc/languageServiceStatus")
    fun languageServiceStatus(): CompletableFuture<Map<String, Any?>>

    @JsonRequest("xtc/exportLogs")
    fun exportLogs(): CompletableFuture<Map<String, String>>

    @JsonRequest("xtc/compilerSourceModules")
    fun compilerSourceModules(): CompletableFuture<List<SourceModuleConfiguration>>

    @JsonRequest("xtc/rename")
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?>

    @JsonRequest("xtc/renameFiles")
    fun renameFilesProposal(params: RenameFilesParams): CompletableFuture<RenameProposal?>
}

/** Server rename edit paired with any source-graph change that must be applied in the same transaction. */
data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
)

/** Expected and proposed source graphs for a guarded configuration replacement. */
data class SourceGraphReplacement(
    val before: List<SourceModuleConfiguration>,
    val after: List<SourceModuleConfiguration>,
)

/** Wire representation of a source module, excluding server-only compiler and filesystem state. */
data class SourceModuleConfiguration(
    val name: String,
    val uri: String,
    val dependencies: List<String> = emptyList(),
    val resourceRoots: List<String>? = null,
)
