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
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
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
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
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
import org.eclipse.lsp4j.InitializedParams
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
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.fmt
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

/**
 * XTC Language Server implementation using LSP4J.
 *
 * ## Implementation Status
 *
 * All LSP methods are wired up to call the adapter and log their invocations.
 * The actual implementation depends on the adapter:
 *
 * - **MockAdapter**: Basic regex-based parsing, most features log "not implemented"
 * - **TreeSitterAdapter**: Syntax-aware features (hover, completion, definition, references, symbols, folding, highlights)
 * - **XdkAdapter**: (future) Full semantic features via XDK compiler
 *
 * ## Backend Selection
 *
 * Select backend at build time: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`
 *
 * @see org.xvm.lsp.adapter.Adapter
 * @see org.xvm.lsp.adapter.TreeSitterAdapter
 */
@Suppress("LoggingSimilarMessage")
class XtcLanguageServer(
    private val adapter: Adapter,
) : LanguageServer,
    LanguageClientAware {
    private var client: LanguageClient? = null

    @Suppress("unused")
    private var initialized = false

    private val textDocumentService = XtcTextDocumentService(this, adapter)
    private val workspaceService = XtcWorkspaceService(this, adapter)

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
    private val semanticTokensEnabled = buildInfo.getProperty("lsp.semanticTokens", "true").toBoolean()

    /**
     * Editor-provided formatting configuration, received via `workspace/configuration`.
     * This is populated after initialization by [requestFormattingConfig] and updated
     * when the client sends `workspace/didChangeConfiguration`.
     *
     * @see FormattingConfig.resolve
     */
    @Volatile
    var editorFormattingConfig: FormattingConfig? = null
        private set

    /**
     * Helper to handle LSP requests with consistent logging and async execution.
     *
     * @param method    The name of the LSP method (e.g., "textDocument/hover")
     * @param logParams A string describing the input parameters for logging
     * @param logResult A function that returns a string describing the result for logging
     * @param block     The actual implementation to execute
     */
    fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        block: () -> R,
    ): CompletableFuture<R> {
        logger.info("{}: {}", method, logParams)
        return CompletableFuture.supplyAsync {
            val (result, elapsed) = measureTimedValue { block() }
            logger.info("{}: {} in {}", method, logResult(result), elapsed)
            result
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("connect: connected to language client")
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logServerBanner()
        logWorkspaceFolders(params)
        logClientCapabilities(params)

        val capabilities = buildServerCapabilities()

        initialized = true
        logger.info("initialize: XTC Language Server initialized")

        // Health check before workspace indexing
        val healthy = adapter.healthCheck()
        if (!healthy) {
            logger.warn("initialize: adapter health check failed, skipping workspace indexing")
        } else {
            // Extract workspace folder paths and initialize workspace index
            val folders =
                params.workspaceFolders
                    ?.mapNotNull { folder ->
                        runCatching { Path.of(URI(folder.uri)).toString() }
                            .onFailure { logger.warn("initialize: invalid workspace folder URI: {}", folder.uri) }
                            .getOrNull()
                    }
                    ?: emptyList()

            if (folders.isNotEmpty()) {
                adapter.initializeWorkspace(folders) { message, percent ->
                    logger.info("initialize: workspace indexing: {} ({}%)", message, percent)
                }
            }

            // Register file watcher for *.x files (dynamic registration)
            registerFileWatcher()
        }

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    /**
     * LSP: initialized notification.
     *
     * Called after the client sends the `initialized` notification, signaling that the
     * handshake is complete and the server can send requests to the client.
     * We use this to pull formatting configuration from the client via `workspace/configuration`.
     */
    override fun initialized(params: InitializedParams?) {
        logger.info("initialized: handshake complete, requesting editor configuration")
        requestFormattingConfig()
    }

    /**
     * Request formatting configuration from the client via `workspace/configuration`.
     *
     * Sends a request for section `"xtc.formatting"`. The client (e.g., [XtcLanguageClient]
     * in IntelliJ) responds with IntelliJ Code Style settings. The response is parsed into
     * an [FormattingConfig] and stored as [editorFormattingConfig].
     */
    fun requestFormattingConfig() {
        val c = client ?: return
        val item = ConfigurationItem().apply { section = "xtc.formatting" }
        c
            .configuration(ConfigurationParams(listOf(item)))
            .thenAccept { results ->
                val config = results?.firstOrNull()
                if (config is Map<*, *>) {
                    val formattingConfig =
                        FormattingConfig(
                            indentSize = (config["indentSize"] as? Number)?.toInt() ?: 4,
                            continuationIndentSize = (config["continuationIndentSize"] as? Number)?.toInt() ?: 8,
                            insertSpaces = config["insertSpaces"] as? Boolean ?: true,
                            maxLineWidth = (config["maxLineWidth"] as? Number)?.toInt() ?: 120,
                        )
                    editorFormattingConfig = formattingConfig
                    adapter.editorFormattingConfig = formattingConfig
                    logger.info("workspace/configuration: editor formatting config: {}", formattingConfig)
                } else {
                    logger.info("workspace/configuration: no formatting config from client (using defaults)")
                }
            }.exceptionally { ex ->
                logger.warn("initialized: failed to get formatting config: {}", ex.message)
                null
            }
    }

    private fun logServerBanner() {
        val pid = ProcessHandle.current().pid()
        logger.info("initialize: ========================================")
        logger.info("initialize: XTC Language Server v{} (pid={})", version, pid)
        logger.info("initialize: Backend: {}", adapter.displayName)
        logger.info("initialize: Built: {}", buildTime)
        logger.info("initialize: ========================================")
    }

    private fun logWorkspaceFolders(params: InitializeParams) {
        val folders = params.workspaceFolders
        if (!folders.isNullOrEmpty()) {
            logger.info("initialize: workspace folders: {}", folders.map { it.uri })
        } else {
            logger.info("initialize: no workspace folders provided")
        }
    }

    /**
     * Log which LSP capabilities the client advertises.
     *
     * NOTE: The chained ?. calls look verbose but are necessary -- LSP4J is a Java library
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
                td?.onTypeFormatting?.let { "onTypeFormatting" }, // treesitter: auto-indent
                // Not yet implemented (uncomment as we add support)
                // td?.synchronization?.let { "synchronization" }, // built-in: doc sync events
                // td?.rangeFormatting?.let { "rangeFormatting" }, // treesitter: format selection
                // td?.declaration?.let { "declaration" }, // compiler: go-to-declaration
                // td?.typeDefinition?.let { "typeDefinition" }, // compiler(types): jump to type
                // td?.implementation?.let { "implementation" }, // compiler(types): find impls
                td?.codeLens?.let { "codeLens" }, // treesitter: run/compile actions on modules
                // td?.colorProvider?.let { "colorProvider" }, // mock: color swatches
                // td?.publishDiagnostics?.let { "publishDiagnostics" }, // compiler: error reporting
                // td?.typeHierarchy?.let { "typeHierarchy" }, // compiler(full): type tree
                // td?.callHierarchy?.let { "callHierarchy" }, // compiler(full): call tree
                // td?.moniker?.let { "moniker" }, // compiler(full): cross-project IDs
                td?.linkedEditingRange?.let { "linkedEditingRange" }, // treesitter: linked edits
                // td?.inlineValue?.let { "inlineValue" }, // compiler(full): debug values
                // td?.diagnostic?.let { "diagnostic" }, // compiler: pull diagnostics
            )
        if (supportedFeatures.isNotEmpty()) {
            logger.info("initialize: client capabilities: {}", supportedFeatures.joinToString(", "))
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
            documentOnTypeFormattingProvider =
                DocumentOnTypeFormattingOptions("\n").apply {
                    moreTriggerCharacter = listOf("}", ";", ")")
                }
            // inlayHintProvider = Either.forLeft(true) // not implemented in TreeSitterAdapter yet

            documentLinkProvider = DocumentLinkOptions()

            signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))

            // Semantic tokens: enabled by default. Disable with -Plsp.semanticTokens=false if needed.
            if (semanticTokensEnabled) {
                logger.info(
                    "semantic tokens ENABLED ({} types, {} modifiers)",
                    SemanticTokenLegend.tokenTypes.size,
                    SemanticTokenLegend.tokenModifiers.size,
                )
                semanticTokensProvider =
                    SemanticTokensWithRegistrationOptions().apply {
                        legend =
                            SemanticTokensLegend(
                                SemanticTokenLegend.tokenTypes,
                                SemanticTokenLegend.tokenModifiers,
                            )
                        full = Either.forLeft(true)
                    }
            } else {
                logger.warn("semantic tokens DISABLED (set lsp.semanticTokens=true to enable)")
            }

            // --- Workspace features ---
            workspaceSymbolProvider = Either.forLeft(true)

            // Code lenses: Run action on module declarations (TreeSitterAdapter)
            codeLensProvider = CodeLensOptions(false)

            // Linked editing: rename-on-type for same-name identifiers (same-file, TreeSitterAdapter)
            linkedEditingRangeProvider = Either.forLeft(true)

            // Not yet advertised (enable when implemented)
            // declarationProvider = Either.forLeft(true) // compiler: go-to-declaration
            // typeDefinitionProvider = Either.forLeft(true) // compiler(types): jump to type
            // implementationProvider = Either.forLeft(true) // compiler(types): find implementations
            // typeHierarchyProvider = Either.forLeft(true) // compiler(full): type tree
            // callHierarchyProvider = Either.forLeft(true) // compiler(full): call tree
        }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("shutdown: shutting down XTC Language Server")
        initialized = false
        adapter.close()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("exit: exiting XTC Language Server")
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
        supplyAsync(
            "xtc/healthCheck",
            "",
            { result -> result.toString() },
        ) {
            val healthy = adapter.healthCheck()
            mapOf(
                "healthy" to healthy,
                "version" to version,
                "adapter" to adapter.displayName,
                "buildTime" to buildTime,
                "message" to if (healthy) "XTC Language Server is healthy" else "Health check failed",
            )
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
        logger.info("initialize: registered file watcher for **/*.x")
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    fun publishDiagnostics(
        uri: String,
        diagnostics: List<Diagnostic>,
    ) {
        val currentClient = client ?: return
        val lspDiagnostics = diagnostics.map { it.toLsp() }
        currentClient.publishDiagnostics(PublishDiagnosticsParams(uri, lspDiagnostics))
    }
}
