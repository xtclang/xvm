package org.xvm.lsp.server

import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkOptions
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.LinkedEditingRangeParams
import org.eclipse.lsp4j.LinkedEditingRanges
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WatchKind
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.fromLsp
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.model.toRange
import org.xvm.lsp.treesitter.SemanticTokenLegend
import java.net.URI
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem.CompletionKind as AdapterCompletionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.FormattingOptions as AdapterFormattingOptions
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position as AdapterPosition
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range as AdapterRange
import org.xvm.lsp.adapter.XtcCompilerAdapter.SelectionRange as AdapterSelectionRange

// =============================================================================
// Extension functions for clean logging of LSP4J types
// =============================================================================

/** Format Position as "line:character" (0-based) */
private fun Position.fmt(): String = "$line:$character"

/** Format Range as "startLine:startChar-endLine:endChar" */
private fun Range.fmt(): String = "${start.fmt()}-${end.fmt()}"

/**
 * XTC Language Server implementation using LSP4J.
 *
 * ## Implementation Status
 *
 * All LSP methods are wired up to call the adapter and log their invocations.
 * The actual implementation depends on the adapter:
 *
 * - **MockXtcCompilerAdapter**: Basic regex-based parsing, most features log "not implemented"
 * - **TreeSitterAdapter**: Syntax-aware features (hover, completion, definition, references, symbols, folding, highlights)
 * - **XtcCompilerAdapterFull**: (future) Full semantic features
 *
 * ## Backend Selection
 *
 * Select backend at build time: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`
 *
 * @see org.xvm.lsp.adapter.XtcCompilerAdapter
 * @see org.xvm.lsp.adapter.TreeSitterAdapter
 */
@Suppress("LoggingSimilarMessage")
class XtcLanguageServer(
    private val adapter: XtcCompilerAdapter,
) : LanguageServer,
    LanguageClientAware {
    private var client: LanguageClient? = null

    @Suppress("unused")
    private var initialized = false

    private val textDocumentService = XtcTextDocumentService()
    private val workspaceService = XtcWorkspaceService()

    companion object {
        private val logger = LoggerFactory.getLogger(XtcLanguageServer::class.java)

        private fun loadBuildInfo(): Properties =
            Properties().apply {
                XtcLanguageServer::class.java.getResourceAsStream("/lsp-version.properties")?.use { load(it) }
            }
    }

    private val buildInfo = loadBuildInfo()
    private val version = buildInfo.getProperty("lsp.version", "?")
    private val buildTime = buildInfo.getProperty("lsp.build.time", "?")
    private val semanticTokensEnabled = buildInfo.getProperty("lsp.semanticTokens", "false").toBoolean()

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("[Server] connect: connected to language client")
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logServerBanner()
        logWorkspaceFolders(params)
        logClientCapabilities(params)

        val capabilities = buildServerCapabilities()

        initialized = true
        logger.info("[Server] initialize: XTC Language Server initialized")

        // Health check before workspace indexing
        val healthy = adapter.healthCheck()
        if (!healthy) {
            logger.warn("[Server] initialize: adapter health check failed, skipping workspace indexing")
        } else {
            // Extract workspace folder paths and initialize workspace index
            val folders =
                params.workspaceFolders
                    ?.mapNotNull { folder ->
                        runCatching { Path.of(URI(folder.uri)).toString() }
                            .onFailure { logger.warn("[Server] initialize: invalid workspace folder URI: {}", folder.uri) }
                            .getOrNull()
                    }
                    ?: emptyList()

            if (folders.isNotEmpty()) {
                adapter.initializeWorkspace(folders) { message, percent ->
                    logger.info("[Server] initialize: workspace indexing: {} ({}%)", message, percent)
                }
            }

            // Register file watcher for *.x files (dynamic registration)
            registerFileWatcher()
        }

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    private fun logServerBanner() {
        val pid = ProcessHandle.current().pid()
        logger.info("[Server] initialize: ========================================")
        logger.info("[Server] initialize: XTC Language Server v{} (pid={})", version, pid)
        logger.info("[Server] initialize: Backend: {}", adapter.displayName)
        logger.info("[Server] initialize: Built: {}", buildTime)
        logger.info("[Server] initialize: ========================================")
    }

    private fun logWorkspaceFolders(params: InitializeParams) {
        val folders = params.workspaceFolders
        if (!folders.isNullOrEmpty()) {
            logger.info("[Server] initialize: workspace folders: {}", folders.map { it.uri })
        } else {
            logger.info("[Server] initialize: no workspace folders provided")
        }
    }

    /**
     * Log which LSP capabilities the client advertises.
     *
     * NOTE: The chained ?. calls look verbose but are necessary — LSP4J is a Java library
     * where all these capability fields are nullable. This is idiomatic for Java interop.
     *
     * ## LSP Capabilities Reference
     *
     * Each capability is annotated with:
     * - What it does for the end user
     * - What adapter level is needed to implement it properly:
     *   - **mock**: regex-based, no parse tree needed
     *   - **treesitter**: requires syntax tree (structural parsing)
     *   - **compiler**: requires XTC compiler integration (type resolution, semantic analysis)
     *
     * ### Currently implemented (server advertises these):
     *
     * | Capability         | Description                                            | Adapter    |
     * |--------------------|--------------------------------------------------------|------------|
     * | hover              | Tooltip with type/doc info on mouse-over               | treesitter |
     * | completion         | Code completion suggestions (., :, < triggers)         | treesitter |
     * | definition         | Go-to-definition (jump to where a symbol is declared)  | treesitter |
     * | references         | Find all references to a symbol in the current file    | treesitter |
     * | documentSymbol     | Outline view / breadcrumbs (classes, methods, fields)  | treesitter |
     * | formatting         | Whole-document code formatting                         | treesitter |
     * | rangeFormatting    | Format a selected range of code                        | treesitter |
     * | rename             | Rename a symbol across the file                        | treesitter |
     * | codeAction         | Quick fixes and refactorings (lightbulb menu)          | treesitter |
     * | documentHighlight  | Highlight other occurrences of symbol under cursor      | treesitter |
     * | selectionRange     | Smart expand/shrink selection based on syntax           | treesitter |
     * | foldingRange       | Code folding regions (classes, methods, blocks)        | treesitter |
     * | inlayHint          | Inline hints (parameter names, inferred types)         | treesitter |
     *
     * ### Not yet implemented:
     *
     * | Capability         | Description                                            | Adapter needed  |
     * |--------------------|--------------------------------------------------------|-----------------|
     * | signatureHelp      | Parameter hints while typing a method call             | treesitter      |
     * | documentLink       | Clickable links in code (import paths, URLs)           | treesitter      |
     * | declaration        | Go-to-declaration (vs definition, for interfaces)      | compiler        |
     * | typeDefinition     | Jump to the type definition of a variable              | compiler (types)|
     * | implementation     | Find implementations of an interface/abstract method   | compiler (types)|
     * | codeLens           | Inline actionable info above functions (run, #refs)    | compiler        |
     * | colorProvider      | Color swatches in editor for color literals             | mock            |
     * | onTypeFormatting   | Auto-format as you type (e.g., indent after {)         | treesitter      |
     * | typeHierarchy      | Show super/subtypes of a class (hierarchy tree)        | compiler (full) |
     * | callHierarchy      | Show callers/callees of a function (call tree)         | compiler (full) |
     * | semanticTokens     | Token-level semantic highlighting (types vs vars)      | treesitter      |
     * | moniker            | Cross-project symbol identity for indexing              | compiler (full) |
     * | linkedEditingRange | Edit matching tags/names simultaneously                 | treesitter      |
     * | inlineValue        | Show variable values inline during debugging            | compiler (full) |
     * | diagnostic         | Pull-based diagnostics (vs push via publishDiagnostics)| compiler        |
     * | workspaceSymbol    | Search symbols across all files in workspace            | compiler (sym)  |
     */
    private fun logClientCapabilities(params: InitializeParams) {
        val td = params.capabilities?.textDocument
        val supportedFeatures =
            listOfNotNull(
                // Implemented (server advertises these)
                td?.hover?.let { "hover" }, // treesitter: tooltip info
                td?.completion?.let { "completion" }, // treesitter: code completions
                td?.definition?.let { "definition" }, // treesitter: go-to-definition
                td?.references?.let { "references" }, // treesitter: find references
                td?.documentSymbol?.let { "documentSymbol" }, // treesitter: outline/breadcrumbs
                td?.formatting?.let { "formatting" }, // treesitter: format document
                td?.rename?.let { "rename" }, // treesitter: rename symbol
                td?.codeAction?.let { "codeAction" }, // treesitter: quick fixes
                td?.semanticTokens?.let { "semanticTokens" }, // compiler(sym): semantic highlighting
                td?.documentHighlight?.let { "documentHighlight" }, // treesitter: highlight occurrences
                td?.selectionRange?.let { "selectionRange" }, // treesitter: smart selection
                td?.foldingRange?.let { "foldingRange" }, // treesitter: code folding
                td?.signatureHelp?.let { "signatureHelp" }, // treesitter: parameter hints
                td?.inlayHint?.let { "inlayHint" }, // treesitter: inline hints
                td?.documentLink?.let { "documentLink" }, // treesitter: clickable links
                // Not yet implemented (uncomment as we add support)
                // td?.synchronization?.let { "synchronization" }, // built-in: doc sync events
                // td?.rangeFormatting?.let { "rangeFormatting" }, // treesitter: format selection
                // td?.onTypeFormatting?.let { "onTypeFormatting" }, // treesitter: auto-indent
                // td?.declaration?.let { "declaration" }, // compiler: go-to-declaration
                // td?.typeDefinition?.let { "typeDefinition" }, // compiler(types): jump to type
                // td?.implementation?.let { "implementation" }, // compiler(types): find impls
                // td?.codeLens?.let { "codeLens" }, // compiler: inline actions
                // td?.colorProvider?.let { "colorProvider" }, // mock: color swatches
                // td?.publishDiagnostics?.let { "publishDiagnostics" }, // compiler: error reporting
                // td?.typeHierarchy?.let { "typeHierarchy" }, // compiler(full): type tree
                // td?.callHierarchy?.let { "callHierarchy" }, // compiler(full): call tree
                // td?.moniker?.let { "moniker" }, // compiler(full): cross-project IDs
                // td?.linkedEditingRange?.let { "linkedEditingRange" }, // treesitter: linked edits
                // td?.inlineValue?.let { "inlineValue" }, // compiler(full): debug values
                // td?.diagnostic?.let { "diagnostic" }, // compiler: pull diagnostics
            )
        if (supportedFeatures.isNotEmpty()) {
            logger.info("[Server] initialize: client capabilities: {}", supportedFeatures.joinToString(", "))
        }
    }

    /**
     * Build the server capabilities that we advertise to the client.
     *
     * Each capability here corresponds to an LSP method that the server handles.
     * See [logClientCapabilities] for a full reference table of all LSP capabilities,
     * what they do, and what adapter level is required.
     */
    private fun buildServerCapabilities(): ServerCapabilities =
        ServerCapabilities().apply {
            // Text document sync - Full means the client sends the entire document on each change.
            // Incremental sync (sending only deltas) is more efficient but requires diffing logic.
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

            // --- Core navigation (treesitter) ---
            hoverProvider = Either.forLeft(true)
            completionProvider =
                CompletionOptions().apply {
                    triggerCharacters = listOf(".", ":", "<")
                    resolveProvider = false
                }
            definitionProvider = Either.forLeft(true)
            referencesProvider = Either.forLeft(true)
            documentSymbolProvider = Either.forLeft(true)

            // --- Structural features (treesitter) ---
            documentHighlightProvider = Either.forLeft(true)
            selectionRangeProvider = Either.forLeft(true)
            foldingRangeProvider = Either.forLeft(true)

            // --- Editing features (treesitter) ---
            renameProvider = Either.forRight(RenameOptions().apply { prepareProvider = true })
            codeActionProvider = Either.forLeft(true)
            documentFormattingProvider = Either.forLeft(true)
            documentRangeFormattingProvider = Either.forLeft(true)
            inlayHintProvider = Either.forLeft(true)

            documentLinkProvider = DocumentLinkOptions()

            signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))

            // Semantic tokens: opt-in via -Plsp.semanticTokens=true in gradle.properties (default: disabled)
            if (semanticTokensEnabled) {
                semanticTokensProvider =
                    SemanticTokensWithRegistrationOptions().apply {
                        legend =
                            SemanticTokensLegend(
                                SemanticTokenLegend.tokenTypes,
                                SemanticTokenLegend.tokenModifiers,
                            )
                        full = Either.forLeft(true)
                    }
            }

            // --- Workspace features ---
            workspaceSymbolProvider = Either.forLeft(true)

            // Not yet advertised (enable when implemented)
            // declarationProvider = Either.forLeft(true) // compiler: go-to-declaration
            // typeDefinitionProvider = Either.forLeft(true) // compiler(types): jump to type
            // implementationProvider = Either.forLeft(true) // compiler(types): find implementations
            // codeLensProvider = CodeLensOptions() // compiler: inline actions
            // typeHierarchyProvider = Either.forLeft(true) // compiler(full): type tree
            // callHierarchyProvider = Either.forLeft(true) // compiler(full): call tree
        }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("[Server] shutdown: shutting down XTC Language Server")
        initialized = false
        adapter.close()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("[Server] exit: exiting XTC Language Server")
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    // =========================================================================
    // Custom XTC LSP Methods
    // =========================================================================
    //
    // LSP allows servers to define custom methods beyond the standard protocol.
    // Custom methods use the @JsonRequest annotation with a method name.
    //
    // Convention: Custom methods should be prefixed with the language/server name
    // to avoid collisions (e.g., "xtc/health check", "xtc/getModuleInfo").
    //
    // How it works:
    // 1. Client sends JSON-RPC request: {"jsonrpc":"2.0","id":1,"method":"xtc/health check"}
    // 2. LSP4J routes to the annotated method via reflection
    // 3. Method returns CompletableFuture with the response
    // 4. Response sent back: {"jsonrpc":"2.0","id":1,"result":{...}}
    //
    // IntelliJ/LSP4IJ: Use LanguageServerManager to send custom requests:
    //   languageServer.sendRequest("xtc/healthCheck", null)
    //
    // VS Code: Use sendRequest on the LanguageClient:
    //   client.sendRequest("xtc/healthCheck")
    //
    // =========================================================================

    /**
     * Custom health check method that clients can call to verify the server is working.
     *
     * Returns a map with:
     * - healthy: boolean - overall health status
     * - version: string - server version
     * - adapter: string - active adapter name
     * - backend: string - backend type (mock, treesitter, compiler)
     * - message: string - human-readable status message
     *
     * Usage from client: Send JSON-RPC request with method "xtc/health check"
     *
     * NOTE: Called at runtime via JSON-RPC by LSP clients (e.g., IntelliJ plugin, VS Code extension)
     * sending a request with method "xtc/health check". LSP4J dispatches via reflection.
     */
    @Suppress("unused")
    @JsonRequest("xtc/healthCheck")
    fun healthCheck(): CompletableFuture<Map<String, Any>> =
        CompletableFuture.supplyAsync {
            val healthy = adapter.healthCheck()
            val status =
                mapOf(
                    "healthy" to healthy,
                    "version" to version,
                    "adapter" to adapter.displayName,
                    "buildTime" to buildTime,
                    "message" to if (healthy) "XTC Language Server is healthy" else "Health check failed",
                )
            logger.info("[Server] xtc/healthCheck: {}", status)
            status
        }

    /**
     * Register a file watcher for `**&#47;*.x` files via dynamic capability registration.
     * This enables the client to notify us when XTC files are created, changed, or deleted
     * on disk (outside of the editor), which we use to keep the workspace index up to date.
     */
    private fun registerFileWatcher() {
        val currentClient = client ?: return
        val watcherOptions =
            DidChangeWatchedFilesRegistrationOptions(
                listOf(
                    FileSystemWatcher(
                        Either.forLeft("**/*.x"),
                        WatchKind.Create + WatchKind.Change + WatchKind.Delete,
                    ),
                ),
            )
        val registration =
            Registration(
                "xtc-file-watcher",
                "workspace/didChangeWatchedFiles",
                watcherOptions,
            )
        currentClient.registerCapability(RegistrationParams(listOf(registration)))
        logger.info("[Server] initialize: registered file watcher for **/*.x")
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun publishDiagnostics(
        uri: String,
        diagnostics: List<Diagnostic>,
    ) {
        val currentClient = client ?: return
        val lspDiagnostics = diagnostics.map { it.toLsp() }
        currentClient.publishDiagnostics(PublishDiagnosticsParams(uri, lspDiagnostics))
    }

    private fun toAdapterPosition(pos: Position) = AdapterPosition(pos.line, pos.character)

    private fun toAdapterRange(range: Range) = AdapterRange(toAdapterPosition(range.start), toAdapterPosition(range.end))

    // ========================================================================
    // Text Document Service
    // ========================================================================

    private inner class XtcTextDocumentService : TextDocumentService {
        // ConcurrentHashMap is required because didOpen/didChange/didClose write on the LSP
        // message thread, while documentSymbol, formatting, documentLink, etc. read from
        // CompletableFuture.supplyAsync handlers on the ForkJoinPool.
        private val openDocuments = ConcurrentHashMap<String, String>()

        /**
         * LSP: textDocument/didOpen
         * @see org.eclipse.lsp4j.services.TextDocumentService.didOpen
         */
        override fun didOpen(params: DidOpenTextDocumentParams) {
            val uri = params.textDocument.uri
            val content = params.textDocument.text

            logger.info("[Server] textDocument/didOpen: {} ({} bytes)", uri, content.length)
            openDocuments[uri] = content

            val (result, elapsed) = measureTimedValue { adapter.compile(uri, content) }
            logger.info("[Server] textDocument/didOpen: compiled in {}, {} diagnostics", elapsed, result.diagnostics.size)
            publishDiagnostics(uri, result.diagnostics)
        }

        /**
         * LSP: textDocument/didChange
         * @see org.eclipse.lsp4j.services.TextDocumentService.didChange
         */
        override fun didChange(params: DidChangeTextDocumentParams) {
            val uri = params.textDocument.uri
            val changes = params.contentChanges
            if (changes.isNullOrEmpty()) {
                logger.warn("[Server] textDocument/didChange: no content changes for: {}", uri)
                return
            }
            val content = changes.first().text

            logger.info("[Server] textDocument/didChange: {} ({} bytes)", uri, content.length)
            openDocuments[uri] = content

            val (result, elapsed) = measureTimedValue { adapter.compile(uri, content) }
            logger.info("[Server] textDocument/didChange: compiled in {}, {} diagnostics", elapsed, result.diagnostics.size)
            publishDiagnostics(uri, result.diagnostics)
        }

        /**
         * LSP: textDocument/didClose
         * @see org.eclipse.lsp4j.services.TextDocumentService.didClose
         */
        override fun didClose(params: DidCloseTextDocumentParams) {
            val uri = params.textDocument.uri
            logger.info("[Server] textDocument/didClose: {}", uri)
            openDocuments.remove(uri)
            adapter.closeDocument(uri)
            publishDiagnostics(uri, emptyList())
        }

        /**
         * LSP: textDocument/didSave
         * @see org.eclipse.lsp4j.services.TextDocumentService.didSave
         */
        override fun didSave(params: DidSaveTextDocumentParams) {
            logger.info("[Server] textDocument/didSave: {}", params.textDocument.uri)
        }

        /**
         * LSP: textDocument/hover
         * @see org.eclipse.lsp4j.services.TextDocumentService.hover
         */
        override fun hover(params: HoverParams): CompletableFuture<Hover?> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/hover: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (hoverInfo, elapsed) = measureTimedValue { adapter.getHoverInfo(uri, line, column) }

                if (hoverInfo == null) {
                    logger.info("[Server] textDocument/hover: no result in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/hover: found symbol in {}", elapsed)
                Hover().apply {
                    contents =
                        Either.forRight(
                            MarkupContent().apply {
                                kind = MarkupKind.MARKDOWN
                                value = hoverInfo
                            },
                        )
                }
            }
        }

        /**
         * LSP: textDocument/completion
         * @see org.eclipse.lsp4j.services.TextDocumentService.completion
         */
        override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/completion: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (completions, elapsed) = measureTimedValue { adapter.getCompletions(uri, line, column) }

                val items =
                    completions.map { c ->
                        CompletionItem(c.label).apply {
                            kind = toCompletionItemKind(c.kind)
                            detail = c.detail
                            insertText = c.insertText
                        }
                    }

                logger.info("[Server] textDocument/completion: {} items in {}", items.size, elapsed)
                Either.forLeft(items)
            }
        }

        private fun toCompletionItemKind(kind: AdapterCompletionKind): CompletionItemKind =
            when (kind) {
                AdapterCompletionKind.CLASS -> CompletionItemKind.Class
                AdapterCompletionKind.INTERFACE -> CompletionItemKind.Interface
                AdapterCompletionKind.METHOD -> CompletionItemKind.Method
                AdapterCompletionKind.PROPERTY -> CompletionItemKind.Property
                AdapterCompletionKind.VARIABLE -> CompletionItemKind.Variable
                AdapterCompletionKind.KEYWORD -> CompletionItemKind.Keyword
                AdapterCompletionKind.MODULE -> CompletionItemKind.Module
            }

        /**
         * LSP: textDocument/definition
         * @see org.eclipse.lsp4j.services.TextDocumentService.definition
         */
        override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/definition: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (definition, elapsed) = measureTimedValue { adapter.findDefinition(uri, line, column) }

                if (definition == null) {
                    logger.info("[Server] textDocument/definition: no result in {}", elapsed)
                    return@supplyAsync Either.forLeft(emptyList())
                }

                logger.info("[Server] textDocument/definition: found in {}", elapsed)
                Either.forLeft(listOf(definition.toLsp()))
            }
        }

        /**
         * LSP: textDocument/references
         * @see org.eclipse.lsp4j.services.TextDocumentService.references
         */
        override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character
            val includeDeclaration = params.context.isIncludeDeclaration

            logger.info("[Server] textDocument/references: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (refs, elapsed) = measureTimedValue { adapter.findReferences(uri, line, column, includeDeclaration) }
                logger.info("[Server] textDocument/references: {} references in {}", refs.size, elapsed)
                refs.map { it.toLsp() }
            }
        }

        /**
         * LSP: textDocument/documentSymbol
         * @see org.eclipse.lsp4j.services.TextDocumentService.documentSymbol
         */
        override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
            val uri = params.textDocument.uri

            logger.info("[Server] textDocument/documentSymbol: {}", uri)
            return CompletableFuture.supplyAsync {
                // Use cached compilation result if available; only recompile if not cached
                val result =
                    adapter.getCachedResult(uri) ?: run {
                        val content = openDocuments[uri]
                        if (content == null) {
                            logger.info("[Server] textDocument/documentSymbol: no content for {}", uri)
                            return@supplyAsync emptyList()
                        }
                        val (compiled, elapsed) = measureTimedValue { adapter.compile(uri, content) }
                        logger.info("[Server] textDocument/documentSymbol: recompiled in {}", elapsed)
                        compiled
                    }

                logger.info("[Server] textDocument/documentSymbol: {} symbols", result.symbols.size)
                result.symbols.map { symbol ->
                    Either.forRight(toDocumentSymbol(symbol))
                }
            }
        }

        private fun toDocumentSymbol(symbol: SymbolInfo): DocumentSymbol =
            DocumentSymbol().apply {
                name = symbol.name
                kind = symbol.kind.toLsp()
                range = symbol.location.toRange()
                selectionRange = symbol.location.toRange()
                if (symbol.typeSignature != null) {
                    detail = symbol.typeSignature
                }
                if (symbol.children.isNotEmpty()) {
                    children = symbol.children.map { toDocumentSymbol(it) }
                }
            }

        // ====================================================================
        // Tree-sitter capable features
        // ====================================================================

        /**
         * LSP: textDocument/documentHighlight
         * @see org.eclipse.lsp4j.services.TextDocumentService.documentHighlight
         */
        override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
            val uri = params.textDocument.uri
            val pos = params.position

            logger.info("[Server] textDocument/documentHighlight: {} pos={}", uri, pos.fmt())
            return CompletableFuture.supplyAsync {
                val (highlights, elapsed) = measureTimedValue { adapter.getDocumentHighlights(uri, pos.line, pos.character) }
                logger.info("[Server] textDocument/documentHighlight: {} highlights in {}", highlights.size, elapsed)
                highlights.map { h ->
                    DocumentHighlight().apply {
                        range = h.range.toLsp()
                        kind = h.kind.toLsp()
                    }
                }
            }
        }

        /**
         * LSP: textDocument/selectionRange
         * @see org.eclipse.lsp4j.services.TextDocumentService.selectionRange
         */
        override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> {
            val uri = params.textDocument.uri
            val positions = params.positions

            logger.info("[Server] textDocument/selectionRange: {} positions={}", uri, positions.map { it.fmt() })
            return CompletableFuture.supplyAsync {
                val adapterPositions = positions.map { toAdapterPosition(it) }
                val (ranges, elapsed) = measureTimedValue { adapter.getSelectionRanges(uri, adapterPositions) }
                logger.info("[Server] textDocument/selectionRange: {} ranges in {}", ranges.size, elapsed)
                ranges.map { toLspSelectionRange(it) }
            }
        }

        private fun toLspSelectionRange(range: AdapterSelectionRange): SelectionRange =
            SelectionRange().apply {
                this.range = range.range.toLsp()
                this.parent = range.parent?.let { toLspSelectionRange(it) }
            }

        /**
         * LSP: textDocument/foldingRange
         * @see org.eclipse.lsp4j.services.TextDocumentService.foldingRange
         */
        override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
            val uri = params.textDocument.uri

            logger.info("[Server] textDocument/foldingRange: {}", uri)
            return CompletableFuture.supplyAsync {
                val (ranges, elapsed) = measureTimedValue { adapter.getFoldingRanges(uri) }
                logger.info("[Server] textDocument/foldingRange: {} ranges in {}", ranges.size, elapsed)
                ranges.map { r ->
                    FoldingRange(r.startLine, r.endLine).apply {
                        kind = r.kind?.toLsp()
                    }
                }
            }
        }

        /**
         * LSP: textDocument/documentLink
         * @see org.eclipse.lsp4j.services.TextDocumentService.documentLink
         */
        override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>> {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]

            logger.info("[Server] textDocument/documentLink: {}", uri)
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("[Server] textDocument/documentLink: no content cached")
                    return@supplyAsync emptyList()
                }

                val (links, elapsed) = measureTimedValue { adapter.getDocumentLinks(uri, content) }
                logger.info("[Server] textDocument/documentLink: {} links in {}", links.size, elapsed)
                links.map { l ->
                    DocumentLink().apply {
                        range = l.range.toLsp()
                        target = l.target
                        tooltip = l.tooltip
                    }
                }
            }
        }

        // ====================================================================
        // Semantic features (require compiler - stubs log "not implemented")
        // ====================================================================

        /**
         * LSP: textDocument/signatureHelp
         * @see org.eclipse.lsp4j.services.TextDocumentService.signatureHelp
         */
        override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/signatureHelp: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (help, elapsed) = measureTimedValue { adapter.getSignatureHelp(uri, line, column) }

                if (help == null) {
                    logger.info("[Server] textDocument/signatureHelp: no result in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/signatureHelp: {} signatures in {}", help.signatures.size, elapsed)
                SignatureHelp().apply {
                    signatures =
                        help.signatures.map { s ->
                            SignatureInformation().apply {
                                label = s.label
                                documentation = s.documentation?.let { Either.forLeft(it) }
                                parameters =
                                    s.parameters.map { p ->
                                        ParameterInformation().apply {
                                            label = Either.forLeft(p.label)
                                            documentation = p.documentation?.let { Either.forLeft(it) }
                                        }
                                    }
                            }
                        }
                    activeSignature = help.activeSignature
                    activeParameter = help.activeParameter
                }
            }
        }

        /**
         * LSP: textDocument/prepareRename
         * @see org.eclipse.lsp4j.services.TextDocumentService.prepareRename
         */
        override fun prepareRename(
            params: PrepareRenameParams,
        ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/prepareRename: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (result, elapsed) = measureTimedValue { adapter.prepareRename(uri, line, column) }

                if (result == null) {
                    logger.info("[Server] textDocument/prepareRename: rename not allowed in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/prepareRename: '{}' in {}", result.placeholder, elapsed)
                Either3.forSecond(
                    PrepareRenameResult().apply {
                        range = result.range.toLsp()
                        placeholder = result.placeholder
                    },
                )
            }
        }

        /**
         * LSP: textDocument/rename
         * @see org.eclipse.lsp4j.services.TextDocumentService.rename
         */
        override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character
            val newName = params.newName

            logger.info("[Server] textDocument/rename: {} at {}:{} -> '{}'", uri, line, column, newName)
            return CompletableFuture.supplyAsync {
                val (edit, elapsed) = measureTimedValue { adapter.rename(uri, line, column, newName) }

                if (edit == null) {
                    logger.info("[Server] textDocument/rename: no edit in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/rename: {} files changed in {}", edit.changes.size, elapsed)
                WorkspaceEdit().apply {
                    changes =
                        edit.changes.mapValues { (_, edits) ->
                            edits.map { e ->
                                TextEdit().apply {
                                    range = e.range.toLsp()
                                    newText = e.newText
                                }
                            }
                        }
                }
            }
        }

        /**
         * LSP: textDocument/codeAction
         * @see org.eclipse.lsp4j.services.TextDocumentService.codeAction
         */
        override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
            val uri = params.textDocument.uri
            val range = params.range
            val context = params.context

            logger.info(
                "[Server] textDocument/codeAction: {} range={} diagnostics={} only={} triggerKind={}",
                uri,
                range.fmt(),
                context.diagnostics?.size ?: 0,
                context.only?.joinToString(",") ?: "null",
                context.triggerKind,
            )
            return CompletableFuture.supplyAsync {
                val adapterDiagnostics = params.context.diagnostics.map { Diagnostic.fromLsp(uri, it) }

                val (actions, elapsed) =
                    measureTimedValue {
                        adapter.getCodeActions(uri, toAdapterRange(range), adapterDiagnostics)
                    }
                logger.info("[Server] textDocument/codeAction: {} actions in {}", actions.size, elapsed)

                actions.map { a ->
                    Either.forRight(
                        CodeAction().apply {
                            title = a.title
                            kind = a.kind.toLsp()
                            isPreferred = a.isPreferred
                            a.edit?.let { e ->
                                edit =
                                    WorkspaceEdit().apply {
                                        changes =
                                            e.changes.mapValues { (_, edits) ->
                                                edits.map { te ->
                                                    TextEdit().apply {
                                                        this.range = te.range.toLsp()
                                                        newText = te.newText
                                                    }
                                                }
                                            }
                                    }
                            }
                        },
                    )
                }
            }
        }

        /**
         * LSP: textDocument/semanticTokens/full
         * @see org.eclipse.lsp4j.services.TextDocumentService.semanticTokensFull
         */
        override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens?> {
            val uri = params.textDocument.uri

            logger.info("[Server] textDocument/semanticTokens/full: {}", uri)
            return CompletableFuture.supplyAsync {
                val (tokens, elapsed) = measureTimedValue { adapter.getSemanticTokens(uri) }

                if (tokens == null) {
                    logger.info("[Server] textDocument/semanticTokens/full: no tokens in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/semanticTokens/full: {} token data items in {}", tokens.data.size, elapsed)
                SemanticTokens().apply {
                    data = tokens.data
                }
            }
        }

        /**
         * LSP: textDocument/inlayHint
         * @see org.eclipse.lsp4j.services.TextDocumentService.inlayHint
         */
        override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
            val uri = params.textDocument.uri
            val range = params.range

            logger.info("[Server] textDocument/inlayHint: {} range={}", uri, range.fmt())
            return CompletableFuture.supplyAsync {
                val (hints, elapsed) = measureTimedValue { adapter.getInlayHints(uri, toAdapterRange(range)) }
                logger.info("[Server] textDocument/inlayHint: {} hints in {}", hints.size, elapsed)
                hints.map { h ->
                    InlayHint().apply {
                        position = Position(h.position.line, h.position.column)
                        label = Either.forLeft(h.label)
                        kind = h.kind.toLsp()
                        paddingLeft = h.paddingLeft
                        paddingRight = h.paddingRight
                    }
                }
            }
        }

        /**
         * LSP: textDocument/formatting
         * @see org.eclipse.lsp4j.services.TextDocumentService.formatting
         */
        override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]

            logger.info("[Server] textDocument/formatting: {}", uri)
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("[Server] textDocument/formatting: no content cached")
                    return@supplyAsync emptyList()
                }

                val options =
                    AdapterFormattingOptions(
                        tabSize = params.options.tabSize,
                        insertSpaces = params.options.isInsertSpaces,
                    )
                val (edits, elapsed) = measureTimedValue { adapter.formatDocument(uri, content, options) }
                logger.info("[Server] textDocument/formatting: {} edits in {}", edits.size, elapsed)
                edits.map { e ->
                    TextEdit().apply {
                        range = e.range.toLsp()
                        newText = e.newText
                    }
                }
            }
        }

        /**
         * LSP: textDocument/rangeFormatting
         * @see org.eclipse.lsp4j.services.TextDocumentService.rangeFormatting
         */
        override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]
            val range = params.range

            logger.info("[Server] textDocument/rangeFormatting: {} range={}", uri, range.fmt())
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("[Server] textDocument/rangeFormatting: no content cached")
                    return@supplyAsync emptyList()
                }

                val options =
                    AdapterFormattingOptions(
                        tabSize = params.options.tabSize,
                        insertSpaces = params.options.isInsertSpaces,
                    )
                val (edits, elapsed) = measureTimedValue { adapter.formatRange(uri, content, toAdapterRange(range), options) }
                logger.info("[Server] textDocument/rangeFormatting: {} edits in {}", edits.size, elapsed)
                edits.map { e ->
                    TextEdit().apply {
                        this.range = e.range.toLsp()
                        newText = e.newText
                    }
                }
            }
        }

        // ====================================================================
        // Planned features (stubs with logging — not yet advertised)
        // ====================================================================
        //
        // These handlers are wired up but the server does NOT advertise the
        // capabilities yet (see buildServerCapabilities). They exist so that:
        // 1. The code structure is ready to plug in when adapters implement them
        // 2. If a client sends the request anyway, we respond gracefully
        // 3. Log traces show the exact request parameters for debugging
        //
        // ====================================================================

        /**
         * LSP: textDocument/declaration
         * @see org.eclipse.lsp4j.services.TextDocumentService.declaration
         */
        override fun declaration(params: DeclarationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/declaration: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (declaration, elapsed) = measureTimedValue { adapter.findDeclaration(uri, line, column) }

                if (declaration == null) {
                    logger.info("[Server] textDocument/declaration: no result in {}", elapsed)
                    return@supplyAsync Either.forLeft(emptyList())
                }

                logger.info("[Server] textDocument/declaration: found in {}", elapsed)
                Either.forLeft(listOf(declaration.toLsp()))
            }
        }

        /**
         * LSP: textDocument/typeDefinition
         * @see org.eclipse.lsp4j.services.TextDocumentService.typeDefinition
         */
        override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/typeDefinition: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (typeDefLocation, elapsed) = measureTimedValue { adapter.findTypeDefinition(uri, line, column) }

                if (typeDefLocation == null) {
                    logger.info("[Server] textDocument/typeDefinition: no result in {}", elapsed)
                    return@supplyAsync Either.forLeft(emptyList())
                }

                logger.info("[Server] textDocument/typeDefinition: found in {}", elapsed)
                Either.forLeft(listOf(typeDefLocation.toLsp()))
            }
        }

        /**
         * LSP: textDocument/implementation
         * @see org.eclipse.lsp4j.services.TextDocumentService.implementation
         */
        override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/implementation: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (impls, elapsed) = measureTimedValue { adapter.findImplementation(uri, line, column) }
                logger.info("[Server] textDocument/implementation: {} locations in {}", impls.size, elapsed)
                Either.forLeft(impls.map { it.toLsp() })
            }
        }

        /**
         * LSP: typeHierarchy/prepareTypeHierarchy
         * @see org.eclipse.lsp4j.services.TextDocumentService.prepareTypeHierarchy
         */
        override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] typeHierarchy/prepare: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (items, elapsed) = measureTimedValue { adapter.prepareTypeHierarchy(uri, line, column) }
                logger.info("[Server] typeHierarchy/prepare: {} items in {}", items.size, elapsed)
                items.map { it.toLsp(uri) }
            }
        }

        /**
         * LSP: typeHierarchy/supertypes
         * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySupertypes
         */
        override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> {
            val item = params.item

            logger.info("[Server] typeHierarchy/supertypes: {}", item.name)
            return CompletableFuture.supplyAsync {
                val adapterItem = item.toAdapter()
                val (supertypes, elapsed) = measureTimedValue { adapter.getSupertypes(adapterItem) }
                logger.info("[Server] typeHierarchy/supertypes: {} items in {}", supertypes.size, elapsed)
                supertypes.map { it.toLsp(item.uri) }
            }
        }

        /**
         * LSP: typeHierarchy/subtypes
         * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySubtypes
         */
        override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> {
            val item = params.item

            logger.info("[Server] typeHierarchy/subtypes: {}", item.name)
            return CompletableFuture.supplyAsync {
                val adapterItem = item.toAdapter()
                val (subtypes, elapsed) = measureTimedValue { adapter.getSubtypes(adapterItem) }
                logger.info("[Server] typeHierarchy/subtypes: {} items in {}", subtypes.size, elapsed)
                subtypes.map { it.toLsp(item.uri) }
            }
        }

        /**
         * LSP: callHierarchy/prepare
         * @see org.eclipse.lsp4j.services.TextDocumentService.prepareCallHierarchy
         */
        override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] callHierarchy/prepare: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (items, elapsed) = measureTimedValue { adapter.prepareCallHierarchy(uri, line, column) }
                logger.info("[Server] callHierarchy/prepare: {} items in {}", items.size, elapsed)
                items.map { it.toLspCallItem() }
            }
        }

        /**
         * LSP: callHierarchy/incomingCalls
         * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyIncomingCalls
         */
        override fun callHierarchyIncomingCalls(
            params: CallHierarchyIncomingCallsParams,
        ): CompletableFuture<List<CallHierarchyIncomingCall>> {
            val item = params.item

            logger.info("[Server] callHierarchy/incomingCalls: {}", item.name)
            return CompletableFuture.supplyAsync {
                val adapterItem = item.toAdapterCallItem()
                val (calls, elapsed) = measureTimedValue { adapter.getIncomingCalls(adapterItem) }
                logger.info("[Server] callHierarchy/incomingCalls: {} calls in {}", calls.size, elapsed)
                calls.map { c ->
                    CallHierarchyIncomingCall().apply {
                        from = c.from.toLspCallItem()
                        fromRanges = c.fromRanges.map { it.toLsp() }
                    }
                }
            }
        }

        /**
         * LSP: callHierarchy/outgoingCalls
         * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyOutgoingCalls
         */
        override fun callHierarchyOutgoingCalls(
            params: CallHierarchyOutgoingCallsParams,
        ): CompletableFuture<List<CallHierarchyOutgoingCall>> {
            val item = params.item

            logger.info("[Server] callHierarchy/outgoingCalls: {}", item.name)
            return CompletableFuture.supplyAsync {
                val adapterItem = item.toAdapterCallItem()
                val (calls, elapsed) = measureTimedValue { adapter.getOutgoingCalls(adapterItem) }
                logger.info("[Server] callHierarchy/outgoingCalls: {} calls in {}", calls.size, elapsed)
                calls.map { c ->
                    CallHierarchyOutgoingCall().apply {
                        to = c.to.toLspCallItem()
                        fromRanges = c.fromRanges.map { it.toLsp() }
                    }
                }
            }
        }

        /**
         * LSP: textDocument/codeLens
         * @see org.eclipse.lsp4j.services.TextDocumentService.codeLens
         */
        override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
            val uri = params.textDocument.uri

            logger.info("[Server] textDocument/codeLens: {}", uri)
            return CompletableFuture.supplyAsync {
                val (lenses, elapsed) = measureTimedValue { adapter.getCodeLenses(uri) }
                logger.info("[Server] textDocument/codeLens: {} lenses in {}", lenses.size, elapsed)
                lenses.map { l ->
                    CodeLens().apply {
                        range = l.range.toLsp()
                        l.command?.let { cmd ->
                            command = Command(cmd.title, cmd.command)
                        }
                    }
                }
            }
        }

        /**
         * LSP: textDocument/onTypeFormatting
         * @see org.eclipse.lsp4j.services.TextDocumentService.onTypeFormatting
         */
        override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character
            val ch = params.ch

            logger.info("[Server] textDocument/onTypeFormatting: {} at {}:{} ch='{}'", uri, line, column, ch)
            return CompletableFuture.supplyAsync {
                val options =
                    AdapterFormattingOptions(
                        tabSize = params.options.tabSize,
                        insertSpaces = params.options.isInsertSpaces,
                    )
                val (edits, elapsed) = measureTimedValue { adapter.onTypeFormatting(uri, line, column, ch, options) }
                logger.info("[Server] textDocument/onTypeFormatting: {} edits in {}", edits.size, elapsed)
                edits.map { e ->
                    TextEdit().apply {
                        range = e.range.toLsp()
                        newText = e.newText
                    }
                }
            }
        }

        /**
         * LSP: textDocument/linkedEditingRange
         * @see org.eclipse.lsp4j.services.TextDocumentService.linkedEditingRange
         */
        override fun linkedEditingRange(params: LinkedEditingRangeParams): CompletableFuture<LinkedEditingRanges> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("[Server] textDocument/linkedEditingRange: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (result, elapsed) = measureTimedValue { adapter.getLinkedEditingRanges(uri, line, column) }

                if (result == null) {
                    logger.info("[Server] textDocument/linkedEditingRange: no result in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("[Server] textDocument/linkedEditingRange: {} ranges in {}", result.ranges.size, elapsed)
                LinkedEditingRanges().apply {
                    ranges = result.ranges.map { it.toLsp() }
                    wordPattern = result.wordPattern
                }
            }
        }

        // ====================================================================
        // Conversion helpers for hierarchy types
        // ====================================================================

        private fun XtcCompilerAdapter.TypeHierarchyItem.toLsp(defaultUri: String): TypeHierarchyItem {
            val resolvedUri = this.uri.ifEmpty { defaultUri }
            return TypeHierarchyItem(
                this.name,
                this.kind.toLsp(),
                resolvedUri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
                this.detail,
            )
        }

        private fun TypeHierarchyItem.toAdapter(): XtcCompilerAdapter.TypeHierarchyItem =
            XtcCompilerAdapter.TypeHierarchyItem(
                name = name,
                kind = SymbolInfo.SymbolKind.CLASS,
                uri = uri,
                range = toAdapterRange(range),
                selectionRange = toAdapterRange(selectionRange),
                detail = detail,
            )

        private fun XtcCompilerAdapter.CallHierarchyItem.toLspCallItem(): CallHierarchyItem {
            val result = CallHierarchyItem(this.name, this.kind.toLsp(), this.uri, this.range.toLsp(), this.selectionRange.toLsp())
            result.detail = this.detail
            return result
        }

        private fun CallHierarchyItem.toAdapterCallItem(): XtcCompilerAdapter.CallHierarchyItem =
            XtcCompilerAdapter.CallHierarchyItem(
                name = name,
                kind = SymbolInfo.SymbolKind.METHOD,
                uri = uri,
                range = toAdapterRange(range),
                selectionRange = toAdapterRange(selectionRange),
                detail = detail,
            )
    }

    // ========================================================================
    // Workspace Service
    // ========================================================================

    private inner class XtcWorkspaceService : WorkspaceService {
        /**
         * LSP: workspace/didChangeConfiguration
         * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeConfiguration
         */
        override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
            logger.info("[Server] workspace/didChangeConfiguration")
        }

        /**
         * LSP: workspace/didChangeWatchedFiles
         * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeWatchedFiles
         */
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
            logger.info("[Server] workspace/didChangeWatchedFiles: {} changes", params.changes.size)
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
        ): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
            val query = params.query

            logger.info("[Server] workspace/symbol: query='{}'", query)
            return CompletableFuture.supplyAsync {
                val (symbols, elapsed) = measureTimedValue { adapter.findWorkspaceSymbols(query) }
                logger.info("[Server] workspace/symbol: {} symbols in {}", symbols.size, elapsed)
                Either.forRight(
                    symbols.map { s ->
                        WorkspaceSymbol().apply {
                            name = s.name
                            kind = s.kind.toLsp()
                            location = Either.forLeft(s.location.toLsp())
                        }
                    },
                )
            }
        }
    }
}
