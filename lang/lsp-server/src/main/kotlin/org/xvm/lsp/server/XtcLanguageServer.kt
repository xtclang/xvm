package org.xvm.lsp.server

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
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureNanoTime

/**
 * XTC Language Server implementation using LSP4J.
 *
 * ## Implementation Status
 *
 * - **IMPLEMENTED**: hover, completion, definition, references, documentSymbol, diagnostics
 * - **NOT IMPLEMENTED**: rename, codeAction, formatting, semanticTokens, signatureHelp,
 *   foldingRange, inlayHints, callHierarchy, typeHierarchy
 * - **MISSING**: workspace/symbol, workspace/configuration, textDocument/linkedEditingRange
 *
 * ## Backend Selection
 *
 * The server uses a pluggable [XtcCompilerAdapter] for parsing and analysis. Available backends:
 *
 * - **MockXtcCompilerAdapter**: Regex-based, for testing only (default)
 * - **TreeSitterAdapter**: Syntax-aware parsing with incremental updates (~70% LSP features)
 * - **XtcCompilerAdapterFull**: (future) Full compiler integration for semantic features
 *
 * Select backend at build time: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`
 *
 * @see org.xvm.lsp.adapter.XtcCompilerAdapter
 * @see org.xvm.lsp.adapter.TreeSitterAdapter
 * @see PLAN_TREE_SITTER.md for Tree-sitter integration roadmap
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
         * Execute a block and return its result along with elapsed time in milliseconds (with sub-ms precision).
         */
        private inline fun <T> timed(block: () -> T): Pair<T, Double> {
            var result: T
            val nanos = measureNanoTime { result = block() }
            return result to (nanos / 1_000_000.0)
        }
    }

    private val buildInfo = loadBuildInfo()
    private val version = buildInfo.getProperty("lsp.version", "?")
    private val buildTime = buildInfo.getProperty("lsp.build.time", "?")

    override fun connect(client: LanguageClient) {
        this.client = client
        logger.info("Connected to language client")
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logger.info("========================================")
        logger.info("XTC Language Server v{}", version)
        logger.info("Backend: {}", adapter.displayName)
        logger.info("Built: {}", buildTime)
        logger.info("========================================")

        val folders = params.workspaceFolders
        if (!folders.isNullOrEmpty()) {
            logger.info("Initializing for workspace folders: {}", folders.map { it.uri })
        } else {
            logger.info("Initializing (no workspace folders provided)")
        }

        // Log client capabilities
        val clientCapabilities = params.capabilities
        val supportedFeatures =
            buildList {
                clientCapabilities?.textDocument?.let { td ->
                    if (td.hover != null) add("hover")
                    if (td.completion != null) add("completion")
                    if (td.definition != null) add("definition")
                    if (td.references != null) add("references")
                    if (td.documentSymbol != null) add("documentSymbol")
                    if (td.formatting != null) add("formatting")
                    if (td.rename != null) add("rename")
                    if (td.codeAction != null) add("codeAction")
                    if (td.semanticTokens != null) add("semanticTokens")
                }
            }
        if (supportedFeatures.isNotEmpty()) {
            logger.info("Client capabilities: {}", supportedFeatures.joinToString(", "))
        }

        val capabilities =
            ServerCapabilities().apply {
                // Text document sync
                // TODO LSP: Using Full sync is simpler but less efficient. For large files,
                // incremental sync (TextDocumentSyncKind.Incremental) would be better.
                // Requires the parallel lexer/parser to support incremental updates.
                textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

                // Hover support
                hoverProvider = Either.forLeft(true)

                // Completion support
                completionProvider =
                    CompletionOptions().apply {
                        triggerCharacters = listOf(".", ":", "<")
                        resolveProvider = false
                    }

                // Definition support
                definitionProvider = Either.forLeft(true)

                // References support
                referencesProvider = Either.forLeft(true)

                // Document symbol support
                documentSymbolProvider = Either.forLeft(true)

                // TODO LSP: Missing capability registrations for advanced features:
                // renameProvider = RenameOptions(true) // with prepareSupport
                // codeActionProvider = CodeActionOptions(listOf(...))
                // documentFormattingProvider = Either.forLeft(true)
                // documentRangeFormattingProvider = Either.forLeft(true)
                // semanticTokensProvider = semanticTokensOptions // Replace TextMate
                // signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
                // foldingRangeProvider = Either.forLeft(true)
                // inlayHintProvider = Either.forLeft(true)
                // callHierarchyProvider = Either.forLeft(true)
                // typeHierarchyProvider = Either.forLeft(true)
                // workspaceSymbolProvider = Either.forLeft(true)
            }

        initialized = true
        logger.info("XTC Language Server initialized")

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("Shutting down XTC Language Server")
        initialized = false
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        logger.info("Exiting XTC Language Server")
        // Do NOT call System.exit() - we may be running in-process in IntelliJ
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    /**
     * Publish diagnostics to the client.
     */
    private fun publishDiagnostics(
        uri: String,
        diagnostics: List<Diagnostic>,
    ) {
        val currentClient = client ?: return

        val lspDiagnostics = diagnostics.map { toLspDiagnostic(it) }
        currentClient.publishDiagnostics(PublishDiagnosticsParams(uri, lspDiagnostics))
    }

    private fun toLspDiagnostic(diag: Diagnostic): org.eclipse.lsp4j.Diagnostic =
        org.eclipse.lsp4j.Diagnostic().apply {
            range = toRange(diag.location)
            severity = toLspSeverity(diag.severity)
            message = diag.message
            source = diag.source
            if (diag.code != null) {
                code = Either.forLeft(diag.code)
            }
        }

    private fun toLspSeverity(severity: Diagnostic.Severity): org.eclipse.lsp4j.DiagnosticSeverity =
        when (severity) {
            Diagnostic.Severity.ERROR -> org.eclipse.lsp4j.DiagnosticSeverity.Error
            Diagnostic.Severity.WARNING -> org.eclipse.lsp4j.DiagnosticSeverity.Warning
            Diagnostic.Severity.INFORMATION -> org.eclipse.lsp4j.DiagnosticSeverity.Information
            Diagnostic.Severity.HINT -> org.eclipse.lsp4j.DiagnosticSeverity.Hint
        }

    private fun toRange(loc: org.xvm.lsp.model.Location): Range =
        Range(
            Position(loc.startLine, loc.startColumn),
            Position(loc.endLine, loc.endColumn),
        )

    private fun toLspLocation(loc: org.xvm.lsp.model.Location): Location = Location(loc.uri, toRange(loc))

    // ========================================================================
    // Text Document Service
    // ========================================================================

    private inner class XtcTextDocumentService : TextDocumentService {
        private val openDocuments = ConcurrentHashMap<String, String>()

        override fun didOpen(params: DidOpenTextDocumentParams) {
            val uri = params.textDocument.uri
            val content = params.textDocument.text

            logger.info("textDocument/didOpen: {} ({} bytes)", uri, content.length)
            openDocuments[uri] = content

            // Compile and publish diagnostics
            val (result, elapsed) = timed { adapter.compile(uri, content) }
            logger.info("textDocument/didOpen: compiled in {:.1f}ms, {} diagnostics", elapsed, result.diagnostics.size)
            publishDiagnostics(uri, result.diagnostics)
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            val uri = params.textDocument.uri
            val changes = params.contentChanges
            if (changes.isNullOrEmpty()) {
                logger.warn("textDocument/didChange: no content changes for: {}", uri)
                return
            }
            // We use full sync, so there's only one change with the full content
            val content = changes.first().text

            logger.info("textDocument/didChange: {} ({} bytes)", uri, content.length)
            openDocuments[uri] = content

            // Recompile and publish diagnostics
            val (result, elapsed) = timed { adapter.compile(uri, content) }
            logger.info("textDocument/didChange: compiled in {:.1f}ms, {} diagnostics", elapsed, result.diagnostics.size)
            publishDiagnostics(uri, result.diagnostics)
        }

        override fun didClose(params: DidCloseTextDocumentParams) {
            val uri = params.textDocument.uri
            logger.info("textDocument/didClose: {}", uri)
            openDocuments.remove(uri)

            // Clear diagnostics
            publishDiagnostics(uri, emptyList())
        }

        override fun didSave(params: DidSaveTextDocumentParams) {
            logger.info("textDocument/didSave: {}", params.textDocument.uri)
        }

        override fun hover(params: HoverParams): CompletableFuture<Hover?> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("textDocument/hover: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (hoverInfo, elapsed) = timed { adapter.getHoverInfo(uri, line, column) }

                if (hoverInfo == null) {
                    logger.info("textDocument/hover: no result in {:.1f}ms", elapsed)
                    return@supplyAsync null
                }

                logger.info("textDocument/hover: found symbol in {:.1f}ms", elapsed)
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

        override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("textDocument/completion: {} at {}:{}", uri, line, column)
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

                logger.info("textDocument/completion: {} items in {:.1f}ms", items.size, elapsed)
                Either.forLeft(items)
            }
        }

        private fun toCompletionItemKind(kind: XtcCompilerAdapter.CompletionItem.CompletionKind): CompletionItemKind =
            when (kind) {
                XtcCompilerAdapter.CompletionItem.CompletionKind.CLASS -> CompletionItemKind.Class
                XtcCompilerAdapter.CompletionItem.CompletionKind.INTERFACE -> CompletionItemKind.Interface
                XtcCompilerAdapter.CompletionItem.CompletionKind.METHOD -> CompletionItemKind.Method
                XtcCompilerAdapter.CompletionItem.CompletionKind.PROPERTY -> CompletionItemKind.Property
                XtcCompilerAdapter.CompletionItem.CompletionKind.VARIABLE -> CompletionItemKind.Variable
                XtcCompilerAdapter.CompletionItem.CompletionKind.KEYWORD -> CompletionItemKind.Keyword
                XtcCompilerAdapter.CompletionItem.CompletionKind.MODULE -> CompletionItemKind.Module
            }

        override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character

            logger.info("textDocument/definition: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (definition, elapsed) = timed { adapter.findDefinition(uri, line, column) }

                if (definition == null) {
                    logger.info("textDocument/definition: no result in {:.1f}ms", elapsed)
                    return@supplyAsync Either.forLeft(emptyList())
                }

                logger.info("textDocument/definition: found in {:.1f}ms", elapsed)
                Either.forLeft(listOf(toLspLocation(definition)))
            }
        }

        override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
            val uri = params.textDocument.uri
            val line = params.position.line
            val column = params.position.character
            val includeDeclaration = params.context.isIncludeDeclaration

            logger.info("textDocument/references: {} at {}:{}", uri, line, column)
            return CompletableFuture.supplyAsync {
                val (refs, elapsed) = timed { adapter.findReferences(uri, line, column, includeDeclaration) }
                logger.info("textDocument/references: {} references in {:.1f}ms", refs.size, elapsed)
                refs.map { toLspLocation(it) }
            }
        }

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
                logger.info("textDocument/documentSymbol: {} symbols in {:.1f}ms", result.symbols.size, elapsed)
                result.symbols.map { symbol ->
                    Either.forRight(toDocumentSymbol(symbol))
                }
            }
        }

        private fun toDocumentSymbol(symbol: SymbolInfo): DocumentSymbol =
            DocumentSymbol().apply {
                name = symbol.name
                kind = toSymbolKind(symbol.kind)
                range = toRange(symbol.location)
                selectionRange = toRange(symbol.location)
                if (symbol.typeSignature != null) {
                    detail = symbol.typeSignature
                }
                if (symbol.children.isNotEmpty()) {
                    children = symbol.children.map { toDocumentSymbol(it) }
                }
            }

        // TODO LSP: MIXIN and SERVICE map to Class because LSP has no dedicated symbol kinds.
        //  Consider using custom symbol kind IDs (allowed in LSP 3.17+) for XTC-specific types.
        private fun toSymbolKind(kind: SymbolInfo.SymbolKind): SymbolKind =
            when (kind) {
                SymbolInfo.SymbolKind.MODULE -> SymbolKind.Module
                SymbolInfo.SymbolKind.PACKAGE -> SymbolKind.Package
                SymbolInfo.SymbolKind.CLASS, SymbolInfo.SymbolKind.MIXIN, SymbolInfo.SymbolKind.SERVICE -> SymbolKind.Class
                SymbolInfo.SymbolKind.INTERFACE -> SymbolKind.Interface
                SymbolInfo.SymbolKind.ENUM -> SymbolKind.Enum
                SymbolInfo.SymbolKind.CONST -> SymbolKind.Constant
                SymbolInfo.SymbolKind.METHOD -> SymbolKind.Method
                SymbolInfo.SymbolKind.PROPERTY -> SymbolKind.Property
                SymbolInfo.SymbolKind.PARAMETER -> SymbolKind.Variable
                SymbolInfo.SymbolKind.TYPE_PARAMETER -> SymbolKind.TypeParameter
                SymbolInfo.SymbolKind.CONSTRUCTOR -> SymbolKind.Constructor
            }
    }

    // ========================================================================
    // Workspace Service
    // ========================================================================

    private class XtcWorkspaceService : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
            logger.info("workspace/didChangeConfiguration")
        }

        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
            logger.info("workspace/didChangeWatchedFiles: {} changes", params.changes.size)
        }

        companion object {
            private val logger = LoggerFactory.getLogger(XtcWorkspaceService::class.java)
        }
    }
}
