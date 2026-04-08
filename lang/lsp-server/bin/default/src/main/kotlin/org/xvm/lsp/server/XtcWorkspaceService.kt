package org.xvm.lsp.server

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.model.toLsp
import java.util.concurrent.CompletableFuture

/**
 * Workspace service for XTC Language Server.
 * Handles workspace-wide features like symbol search and configuration changes.
 */
class XtcWorkspaceService(
    private val server: XtcLanguageServer,
    private val adapter: Adapter,
) : WorkspaceService {
    companion object {
        private val logger = LoggerFactory.getLogger(XtcWorkspaceService::class.java)
    }

    private fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        block: () -> R,
    ): CompletableFuture<R> = server.supplyAsync(method, logParams, logResult, block)

    /**
     * LSP: workspace/didChangeConfiguration
     * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeConfiguration
     */
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        logger.info("workspace/didChangeConfiguration: re-requesting formatting config")
        server.requestFormattingConfig()
    }

    /**
     * LSP: workspace/didChangeWatchedFiles
     * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeWatchedFiles
     */
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        logger.info("workspace/didChangeWatchedFiles: {} changes", params.changes.size)
        for (change in params.changes) {
            adapter.didChangeWatchedFile(change.uri, change.type.value)
        }
    }

    /**
     * LSP: workspace/symbol
     * @see org.eclipse.lsp4j.services.WorkspaceService.symbol
     */
    @Suppress("ktlint:standard:function-signature")
    override fun symbol(
        params: WorkspaceSymbolParams,
    ): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> =
        supplyAsync(
            "workspace/symbol",
            "query='${params.query}'",
            { result -> "${result.right.size} symbols" },
        ) {
            Either.forRight(
                adapter.findWorkspaceSymbols(params.query).map { s ->
                    WorkspaceSymbol().apply {
                        name = s.name
                        kind = s.kind.toLsp()
                        location = Either.forLeft(s.location.toLsp())
                    }
                },
            )
        }
}
