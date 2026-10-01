package org.xvm.lsp.server

import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import java.util.concurrent.CompletableFuture

/**
 * Workspace service for Ecstasy Language Server. Handles workspace-wide features like symbol search
 * and configuration changes.
 */
class XtcWorkspaceService(
    private val server: XtcLanguageServer,
    private val adapter: Adapter,
) : WorkspaceService {
    companion object {
        private val logger = LoggerFactory.getLogger(XtcWorkspaceService::class.java)
    }

    private val fileChanges = FileChangeSnapshots()

    override fun diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> =
        server.workspaceDiagnostics(params)

    /**
     * LSP: workspace/didChangeConfiguration
     *
     * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeConfiguration
     */
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        logger.info("workspace/didChangeConfiguration: updating editor configuration")
        server.requestFormattingConfig()
        server.refreshPresentation()
        server.changeCompilerConfig(params.settings)
    }

    /**
     * LSP: workspace/didChangeWatchedFiles
     *
     * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeWatchedFiles
     */
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        logger.info("workspace/didChangeWatchedFiles: {} changes", params.changes.size)
        refreshFiles(params.changes)
    }

    override fun willRenameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit?> = server.willRenameFiles(params)

    // Creation/deletion do not justify rewriting references. Post-operation diagnostics reflect
    // the new membership; a pre-operation hook must never modify files speculatively.
    override fun willCreateFiles(params: CreateFilesParams): CompletableFuture<WorkspaceEdit?> = CompletableFuture.completedFuture(null)

    override fun willDeleteFiles(params: DeleteFilesParams): CompletableFuture<WorkspaceEdit?> = CompletableFuture.completedFuture(null)

    override fun didCreateFiles(params: CreateFilesParams) = refreshFiles(params.files.map { FileEvent(it.uri, FileChangeType.Created) })

    override fun didDeleteFiles(params: DeleteFilesParams) = refreshFiles(params.files.map { FileEvent(it.uri, FileChangeType.Deleted) })

    /** File operations can arrive without watcher notifications, especially after a client edit. */
    override fun didRenameFiles(params: RenameFilesParams) {
        logger.info("workspace/didRenameFiles: {} renames", params.files.size)
        refreshFiles(
            params.files.flatMap {
                listOf(
                    FileEvent(it.oldUri, FileChangeType.Deleted),
                    FileEvent(it.newUri, FileChangeType.Created),
                )
            },
        )
    }

    private fun refreshFiles(events: List<FileEvent>) {
        val changes = fileChanges.changed(events)
        if (changes.isEmpty()) return
        // Resource content changes cannot add/remove source modules. Membership events still
        // rediscover, including directory events that can contain several source files.
        if (changes.any { it.type != FileChangeType.Changed || it.uri.endsWith(".x") }) {
            server.refreshCompilerDiscovery()
        }
        changes
            .distinctBy { it.uri to it.type }
            .forEach { change ->
                adapter.didChangeWatchedFile(change.uri, change.type.value)
                server.refreshForFile(change.uri)
            }
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        server.changeCompilerWorkspaceFolders(
            params.event.added.map { it.uri },
            params.event.removed.map { it.uri },
        )
    }

    /**
     * LSP: workspace/symbol
     *
     * @see org.eclipse.lsp4j.services.WorkspaceService.symbol
     */
    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> =
        server.workspaceSymbols(params)

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> = server.executeCodeAction(params)

    override fun resolveWorkspaceSymbol(symbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol> = server.resolveWorkspaceSymbol(symbol)
}
