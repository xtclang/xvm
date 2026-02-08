package org.xvm.lsp.server

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
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
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
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
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.measureTime
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

        /**
         * Execute a block and return its result along with elapsed duration.
         */
        private inline fun <T> timed(block: () -> T): Pair<T, Duration> {
            var result: T
            val duration = measureTime { result = block() }
            return result to duration
        }
    }

    private val buildInfo = loadBuildInfo()
    private val version = buildInfo.getProperty("lsp.version", "?")
    private val buildTime = buildInfo.getProperty("lsp.build.time", "?")

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("connect: Connected to language client")
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logServerBanner()
        logWorkspaceFolders(params)
        logClientCapabilities(params)

        val capabilities = buildServerCapabilities()

        initialized = true
        logger.info("initialize: XTC Language Server initialized")

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
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
     * | semanticTokens     | Token-level semantic highlighting (types vs vars)      | compiler (sym)  |
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
            renameProvider = Either.forLeft(true)
            codeActionProvider = Either.forLeft(true)
            documentFormattingProvider = Either.forLeft(true)
            documentRangeFormattingProvider = Either.forLeft(true)
            inlayHintProvider = Either.forLeft(true)

            // Not yet advertised (enable when implemented)
            // signatureHelpProvider = SignatureHelpOptions(listOf("(", ",")) // treesitter
            // documentLinkProvider = DocumentLinkOptions() // treesitter
            // semanticTokensProvider = SemanticTokensWithRegistrationOptions(...) // compiler(sym)
            // declarationProvider = Either.forLeft(true) // compiler: go-to-declaration
            // typeDefinitionProvider = Either.forLeft(true) // compiler(types): jump to type
            // implementationProvider = Either.forLeft(true) // compiler(types): find implementations
            // codeLensProvider = CodeLensOptions() // compiler: inline actions
            // typeHierarchyProvider = Either.forLeft(true) // compiler(full): type tree
            // callHierarchyProvider = Either.forLeft(true) // compiler(full): call tree
            // workspaceSymbolProvider = Either.forLeft(true) // compiler(sym): cross-file search
        }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("shutdown: Shutting down XTC Language Server")
        initialized = false
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("exit: Exiting XTC Language Server")
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
    // to avoid collisions (e.g., "xtc/healthCheck", "xtc/getModuleInfo").
    //
    // How it works:
    // 1. Client sends JSON-RPC request: {"jsonrpc":"2.0","id":1,"method":"xtc/healthCheck"}
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
     * Usage from client: Send JSON-RPC request with method "xtc/healthCheck"
     */
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
            logger.info("xtc/healthCheck: {}", status)
            status
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
        private val openDocuments = ConcurrentHashMap<String, String>()

        /**
         * LSP: textDocument/didOpen
         * @see org.eclipse.lsp4j.services.TextDocumentService.didOpen
         */
        override fun didOpen(params: DidOpenTextDocumentParams) {
            val uri = params.textDocument.uri
            val content = params.textDocument.text

            logger.info("{}: {} ({} bytes)", "textDocument/didOpen", uri, content.length)
            openDocuments[uri] = content

            val (result, elapsed) = timed { adapter.compile(uri, content) }
            logger.info("textDocument/didOpen: compiled in {}, {} diagnostics", elapsed, result.diagnostics.size)
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
                logger.warn("textDocument/didChange: no content changes for: {}", uri)
                return
            }
            val content = changes.first().text

            logger.info("{}: {} ({} bytes)", "textDocument/didChange", uri, content.length)
            openDocuments[uri] = content

            val (result, elapsed) = timed { adapter.compile(uri, content) }
            logger.info("textDocument/didChange: compiled in {}, {} diagnostics", elapsed, result.diagnostics.size)
            publishDiagnostics(uri, result.diagnostics)
        }

        /**
         * LSP: textDocument/didClose
         * @see org.eclipse.lsp4j.services.TextDocumentService.didClose
         */
        override fun didClose(params: DidCloseTextDocumentParams) {
            val uri = params.textDocument.uri
            logger.info("textDocument/didClose: {}", uri)
            openDocuments.remove(uri)
            publishDiagnostics(uri, emptyList())
        }

        /**
         * LSP: textDocument/didSave
         * @see org.eclipse.lsp4j.services.TextDocumentService.didSave
         */
        override fun didSave(params: DidSaveTextDocumentParams) {
            logger.info("textDocument/didSave: {}", params.textDocument.uri)
        }

        /**
         * LSP: textDocument/hover
         * @see org.eclipse.lsp4j.services.TextDocumentService.hover
         */
        override fun hover(params: HoverParams): CompletableFuture<Hover?> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("{}: {} at {}:{}", "textDocument/hover", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (hoverInfo, elapsed) = timed { adapter.getHoverInfo(uri, line, column) }

                if (hoverInfo == null) {
                    logger.info("textDocument/hover: no result in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/hover: found symbol in {}", elapsed)
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

            logger.info("{}: {} at {}:{}", "textDocument/completion", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (completions, elapsed) = timed { adapter.getCompletions(uri, line, column) }

                val items =
                    completions.map { c ->
                        CompletionItem(c.label).apply {
                            kind = toCompletionItemKind(c.kind)
                            detail = c.detail
                            insertText = c.insertText
                        }
                    }

                logger.info("textDocument/completion: {} items in {}", items.size, elapsed)
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

            logger.info("textDocument/definition: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (definition, elapsed) = timed { adapter.findDefinition(uri, line, column) }

                if (definition == null) {
                    logger.info("textDocument/definition: no result in {}", elapsed)
                    return@supplyAsync Either.forLeft(emptyList())
                }

                logger.info("textDocument/definition: found in {}", elapsed)
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

            logger.info("textDocument/references: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (refs, elapsed) = timed { adapter.findReferences(uri, line, column, includeDeclaration) }
                logger.info("textDocument/references: {} references in {}", refs.size, elapsed)
                refs.map { it.toLsp() }
            }
        }

        /**
         * LSP: textDocument/documentSymbol
         * @see org.eclipse.lsp4j.services.TextDocumentService.documentSymbol
         */
        override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]

            logger.info("textDocument/documentSymbol: {}", uri)
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("textDocument/documentSymbol: no content cached")
                    return@supplyAsync emptyList()
                }

                val (result, elapsed) = timed { adapter.compile(uri, content) }
                logger.info("textDocument/documentSymbol: {} symbols in {}", result.symbols.size, elapsed)
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

            logger.info("{}: {} pos={}", "textDocument/documentHighlight", uri, pos.fmt())
            return CompletableFuture.supplyAsync {
                val (highlights, elapsed) = timed { adapter.getDocumentHighlights(uri, pos.line, pos.character) }
                logger.info("textDocument/documentHighlight: {} highlights in {}", highlights.size, elapsed)
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

            logger.info("textDocument/selectionRange: {} positions={}", uri, positions.map { it.fmt() })
            return CompletableFuture.supplyAsync {
                val adapterPositions = positions.map { toAdapterPosition(it) }
                val (ranges, elapsed) = timed { adapter.getSelectionRanges(uri, adapterPositions) }
                logger.info("textDocument/selectionRange: {} ranges in {}", ranges.size, elapsed)
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

            logger.info("textDocument/foldingRange: {}", uri)
            return CompletableFuture.supplyAsync {
                val (ranges, elapsed) = timed { adapter.getFoldingRanges(uri) }
                logger.info("textDocument/foldingRange: {} ranges in {}", ranges.size, elapsed)
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

            logger.info("textDocument/documentLink: {}", uri)
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("textDocument/documentLink: no content cached")
                    return@supplyAsync emptyList()
                }

                val (links, elapsed) = timed { adapter.getDocumentLinks(uri, content) }
                logger.info("textDocument/documentLink: {} links in {}", links.size, elapsed)
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

            logger.info("textDocument/signatureHelp: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (help, elapsed) = timed { adapter.getSignatureHelp(uri, line, column) }

                if (help == null) {
                    logger.info("textDocument/signatureHelp: no result in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/signatureHelp: {} signatures in {}", help.signatures.size, elapsed)
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

            logger.info("textDocument/prepareRename: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (result, elapsed) = timed { adapter.prepareRename(uri, line, column) }

                if (result == null) {
                    logger.info("textDocument/prepareRename: rename not allowed in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/prepareRename: '{}' in {}", result.placeholder, elapsed)
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

            logger.info("textDocument/rename: {} at {}:{} -> '{}'", uri, line, column, newName)
            return CompletableFuture.supplyAsync {
                val (edit, elapsed) = timed { adapter.rename(uri, line, column, newName) }

                if (edit == null) {
                    logger.info("textDocument/rename: no edit in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/rename: {} files changed in {}", edit.changes.size, elapsed)
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
                "{}: {} range={} diagnostics={} only={} triggerKind={}",
                "textDocument/codeAction",
                uri,
                range.fmt(),
                context.diagnostics?.size ?: 0,
                context.only?.joinToString(",") ?: "null",
                context.triggerKind,
            )
            return CompletableFuture.supplyAsync {
                val adapterDiagnostics = params.context.diagnostics.map { Diagnostic.fromLsp(uri, it) }

                val (actions, elapsed) =
                    timed {
                        adapter.getCodeActions(uri, toAdapterRange(range), adapterDiagnostics)
                    }
                logger.info("textDocument/codeAction: {} actions in {}", actions.size, elapsed)

                actions.map { a ->
                    Either.forRight<Command, CodeAction>(
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

            logger.info("textDocument/semanticTokens/full: {}", uri)
            return CompletableFuture.supplyAsync {
                val (tokens, elapsed) = timed { adapter.getSemanticTokens(uri) }

                if (tokens == null) {
                    logger.info("textDocument/semanticTokens/full: no tokens in {}", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/semanticTokens/full: {} token data items in {}", tokens.data.size, elapsed)
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

            logger.info("{}: {} range={}", "textDocument/inlayHint", uri, range.fmt())
            return CompletableFuture.supplyAsync {
                val (hints, elapsed) = timed { adapter.getInlayHints(uri, toAdapterRange(range)) }
                logger.info("textDocument/inlayHint: {} hints in {}", hints.size, elapsed)
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

            logger.info("textDocument/formatting: {}", uri)
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("textDocument/formatting: no content cached")
                    return@supplyAsync emptyList()
                }

                val options =
                    AdapterFormattingOptions(
                        tabSize = params.options.tabSize,
                        insertSpaces = params.options.isInsertSpaces,
                    )
                val (edits, elapsed) = timed { adapter.formatDocument(uri, content, options) }
                logger.info("textDocument/formatting: {} edits in {}", edits.size, elapsed)
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

            logger.info("{}: {} range={}", "textDocument/rangeFormatting", uri, range.fmt())
            return CompletableFuture.supplyAsync {
                if (content == null) {
                    logger.info("textDocument/rangeFormatting: no content cached")
                    return@supplyAsync emptyList()
                }

                val options =
                    AdapterFormattingOptions(
                        tabSize = params.options.tabSize,
                        insertSpaces = params.options.isInsertSpaces,
                    )
                val (edits, elapsed) = timed { adapter.formatRange(uri, content, toAdapterRange(range), options) }
                logger.info("textDocument/rangeFormatting: {} edits in {}", edits.size, elapsed)
                edits.map { e ->
                    TextEdit().apply {
                        this.range = e.range.toLsp()
                        newText = e.newText
                    }
                }
            }
        }
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
            logger.info("workspace/didChangeConfiguration")
        }

        /**
         * LSP: workspace/didChangeWatchedFiles
         * @see org.eclipse.lsp4j.services.WorkspaceService.didChangeWatchedFiles
         */
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
            logger.info("workspace/didChangeWatchedFiles: {} changes", params.changes.size)
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

            logger.info("workspace/symbol: query='{}'", query)
            return CompletableFuture.supplyAsync {
                val (symbols, elapsed) = timed { adapter.findWorkspaceSymbols(query) }
                logger.info("workspace/symbol: {} symbols in {}", symbols.size, elapsed)
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
