package org.xvm.lsp.server

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CodeActionOptions
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.DocumentLinkOptions
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
import org.eclipse.lsp4j.DocumentRangeFormattingOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationOptions
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationsServerCapabilities
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.FileSystemWatcher
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceOptions
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensServerFull
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WatchKind
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressParams
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFoldersOptions
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolOptions
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkSources
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.treesitter.SemanticTokenLegend
import org.xvm.lsp.util.ExecutionTrace
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.URI
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.measureTimedValue

/**
 * Ecstasy Language Server implementation using LSP4J.
 *
 * ## Implementation Status
 *
 * All LSP methods are wired up to call the adapter and log their invocations. The actual
 * implementation depends on the adapter:
 *
 * - **MockAdapter**: Basic regex-based parsing, most features log "not implemented"
 * - **TreeSitterAdapter**: Syntax-aware features (hover, completion, definition, references,
 *   symbols, folding, highlights)
 * - **XdkAdapter**: Compiler diagnostics and semantic features via the embedding API
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
    private val onExit: (Int) -> Unit = {},
) : LanguageServer,
    LanguageClientAware,
    AutoCloseable {
    private val connectedClient = AtomicReference<LanguageClient?>()
    private val client: LanguageClient?
        get() = connectedClient.get()

    private val refresh = ClientRefresh { client }
    internal val clientTrace = ClientTrace { client }
    internal val partialResults = PartialResults(client = { client })

    override fun setTrace(params: SetTraceParams) = clientTrace.configure(params.value)

    private data class EditCapabilities(
        val versioned: Boolean = false,
        val renameFiles: Boolean = false,
        val fileWatchers: Boolean = false,
        val relativeWatchPatterns: Boolean = false,
        val resourceWatchers: Boolean = false,
    )

    private val clientPresentation = AtomicReference(ClientPresentation())
    internal val presentation: ClientPresentation
        get() = clientPresentation.get()

    private val editCapabilities = AtomicReference(EditCapabilities())
    private val resourceFileWatchers = ResourceFileWatchers()
    private val progress = ConnectionProgress(client = { client })
    private val supportsProgress = AtomicBoolean()
    private val clientReady = CompletableFuture<Void>()
    private val indexing = CompletableFuture<Unit>()

    internal fun <T> observeQuery(
        method: String,
        params: WorkDoneProgressParams?,
        result: CompletableFuture<T>,
    ): CompletableFuture<T> {
        val title =
            when (method) {
                "textDocument/references" -> "Ecstasy: finding references"

                "textDocument/rename",
                "xtc/renameProposal",
                "workspace/willRenameFiles",
                -> "Ecstasy: checking rename"

                "textDocument/codeAction" -> "Ecstasy: checking code actions"

                "workspace/diagnostic" -> "Ecstasy: checking workspace"

                else -> null
            }
        return if (title != null || params?.workDoneToken != null) {
            progress.track(title ?: "Ecstasy: $method", params?.workDoneToken, result)
        } else {
            result
        }
    }

    override fun cancelProgress(params: WorkDoneProgressCancelParams) = progress.cancel(params.token)

    internal val supportsVersionedEdits: Boolean
        get() = editCapabilities.get().versioned

    internal val supportsFileRenames: Boolean
        get() = editCapabilities.get().renameFiles

    private data class DiagnosticCapabilities(
        val pull: Boolean = false,
        val related: Boolean = false,
        val refresh: Boolean = false,
    )

    private val diagnosticCapabilities = AtomicReference(DiagnosticCapabilities())
    internal val usesPullDiagnostics: Boolean
        get() = diagnosticCapabilities.get().pull

    internal val supportsRelatedDiagnostics: Boolean
        get() = diagnosticCapabilities.get().related

    internal fun refreshDiagnostics() = refresh.request(ClientRefresh.Feature.DIAGNOSTICS)

    internal fun workspaceDiagnostics(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> =
        textDocumentService.workspaceDiagnostics(params)

    private val textDocumentService = XtcTextDocumentService(this, adapter)
    private val workspaceService = XtcWorkspaceService(this, adapter)
    private val shutdownRequested = AtomicBoolean()

    /**
     * One immutable context per configuration request; late replies cannot replace newer settings.
     */
    private data class CompilerSettings(
        val folders: List<String> = emptyList(),
        val canRequest: Boolean = false,
        val revision: Long = 0,
        val closed: Boolean = false,
    )

    private data class ResolveCapabilities(
        val completionDocumentation: Boolean = false,
        val actionEdit: Boolean = false,
        val lensCommand: Boolean = false,
        val linkTarget: Boolean = false,
        val hintTooltip: Boolean = false,
        val symbolRange: Boolean = false,
    )

    private val documentSynchronization = AtomicReference(DocumentSynchronization())
    internal val synchronization: DocumentSynchronization
        get() = documentSynchronization.get()

    private val resolveCapabilities = AtomicReference(ResolveCapabilities())
    internal val resolvesCompletionDocumentation: Boolean
        get() = resolveCapabilities.get().completionDocumentation

    internal val resolvesCodeActionEdit: Boolean
        get() = resolveCapabilities.get().actionEdit

    internal val resolvesCodeLensCommand: Boolean
        get() = resolveCapabilities.get().lensCommand

    internal val resolvesDocumentLinkTarget: Boolean
        get() = resolveCapabilities.get().linkTarget

    internal val resolvesInlayHintTooltip: Boolean
        get() = resolveCapabilities.get().hintTooltip

    internal val resolvesWorkspaceSymbolRange: Boolean
        get() = resolveCapabilities.get().symbolRange

    internal fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
        val current =
            client?.takeUnless { compilerSettings.get().closed }
                ?: return CompletableFuture.failedFuture(
                    IllegalStateException("Language client disconnected"),
                )
        return current.applyEdit(params)
    }

    internal fun executeCodeAction(params: ExecuteCommandParams): CompletableFuture<Any> = textDocumentService.executeCodeAction(params)

    internal fun workspaceSymbols(
        params: WorkspaceSymbolParams,
    ): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> = textDocumentService.workspaceSymbols(params)

    internal fun resolveWorkspaceSymbol(symbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol> =
        textDocumentService.resolveWorkspaceSymbol(symbol)

    private data class TokenCapabilities(
        val range: Boolean = false,
        val delta: Boolean = false,
        val refresh: Boolean = false,
    )

    private val tokenCapabilities = AtomicReference(TokenCapabilities())

    internal fun requireSemanticTokenRequest(delta: Boolean) {
        val options = tokenCapabilities.get()
        if (!(if (delta) options.delta else options.range)) {
            throw ResponseErrorException(
                ResponseError(
                    ResponseErrorCode.MethodNotFound,
                    "Semantic token operation was not negotiated",
                    null,
                ),
            )
        }
    }

    internal fun refreshSemanticFeatures() =
        refresh.request(
            ClientRefresh.Feature.TOKENS,
            ClientRefresh.Feature.INLAYS,
            ClientRefresh.Feature.LENSES,
            ClientRefresh.Feature.FOLDING,
        )

    private val fileOperationCapabilities =
        AtomicReference<FileOperationsWorkspaceCapabilities?>(null)

    internal fun willRenameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit?> =
        if (fileOperationCapabilities.get()?.willRename == true) {
            textDocumentService.renameFiles(params)
        } else {
            CompletableFuture.failedFuture(
                ResponseErrorException(
                    ResponseError(
                        ResponseErrorCode.MethodNotFound,
                        "File rename participation was not negotiated",
                        null,
                    ),
                ),
            )
        }

    private val compilerSettings = AtomicReference(CompilerSettings())

    companion object {
        private val logger = LoggerFactory.getLogger(XtcLanguageServer::class.java)
        private const val SEMANTIC_TOKENS_SYSTEM_PROPERTY = "xtc.lsp.semanticTokens"
        private const val SEMANTIC_TOKENS_ENV = "XTC_LSP_SEMANTIC_TOKENS"

        private fun loadBuildInfo(): Properties =
            Properties().apply {
                XtcLanguageServer::class.java.getResourceAsStream("/lsp-version.properties")?.use {
                    load(it)
                }
            }
    }

    private val buildInfo = loadBuildInfo()
    private val version = buildInfo.getProperty("lsp.version", "?")
    private val buildTime = buildInfo.getProperty("lsp.build.time", "?")
    private val semanticTokensEnabled =
        (
            System.getProperty(SEMANTIC_TOKENS_SYSTEM_PROPERTY)
                ?: System.getenv(SEMANTIC_TOKENS_ENV)
                ?: buildInfo.getProperty("lsp.semanticTokens", "true")
        ).toBoolean()

    /**
     * Editor-provided formatting configuration, received via `workspace/configuration`. This is
     * populated after initialization by [requestFormattingConfig] and updated when the client sends
     * `workspace/didChangeConfiguration`.
     *
     * @see FormattingConfig.resolve
     */
    private val formattingState = EditorFormattingState()
    val editorFormattingConfig: FormattingConfig?
        get() = formattingState.config

    fun refreshPresentation() = refresh.request(ClientRefresh.Feature.INLAYS)

    /**
     * Helper to handle LSP requests with consistent logging and async execution.
     *
     * @param method The name of the LSP method (e.g., "textDocument/hover")
     * @param logParams A string describing the input parameters for logging
     * @param logResult A function that returns a string describing the result for logging
     * @param block The actual implementation to execute
     */
    fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        block: () -> R,
    ): CompletableFuture<R> {
        logger.info("{}: {}", method, logParams)
        val trace = ExecutionTrace.current()
        return CompletableFuture.supplyAsync {
            val (result, elapsed) = measureTimedValue { ExecutionTrace.within(trace, block) }
            logger.info("{}: {} in {}", method, logResult(result), elapsed)
            result
        }
    }

    override fun connect(client: LanguageClient) {
        connectedClient.set(client)
        logger.info("connect: connected to language client")
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        supportsProgress.set(params.capabilities?.window?.workDoneProgress == true)
        clientPresentation.set(ClientPresentation.read(params))
        clientTrace.configure(params.trace)
        logServerBanner()
        logWorkspaceFolders(params)
        logClientCapabilities(params)

        if (adapter is XdkAdapter) {
            val folders = ClientPresentation.workspaceUris(params)
            val settings =
                CompilerSettings(folders, params.capabilities?.workspace?.configuration == true)
            synchronized(compilerSettings) {
                compilerSettings.set(settings)
                try {
                    val raw = CompilerConfiguration.initial(params.initializationOptions)
                    val model = CompilerConfiguration.buildModel(raw)
                    if (model != null) {
                        textDocumentService.refreshDependencies {
                            adapter.replaceBuildInputs(model.resolve())
                        }
                    } else {
                        CompilerConfiguration
                            .modules(raw, folders)
                            ?.let(::replaceCompilerSourceModules)
                    }
                } catch (e: IllegalArgumentException) {
                    return CompletableFuture.failedFuture(
                        ResponseErrorException(
                            ResponseError(
                                ResponseErrorCode.InvalidParams,
                                "Invalid ${CompilerConfiguration.SECTION}: ${e.message}",
                                null,
                            ),
                        ),
                    )
                }
            }
        }

        val pull = adapter is XdkAdapter && params.capabilities?.textDocument?.diagnostic != null
        diagnosticCapabilities.set(
            DiagnosticCapabilities(
                pull,
                pull &&
                    params.capabilities
                        ?.textDocument
                        ?.diagnostic
                        ?.relatedDocumentSupport == true,
                pull && params.capabilities
                    ?.workspace
                    ?.diagnostics
                    ?.refreshSupport == true,
            ),
        )
        val workspaceEdits = params.capabilities?.workspace?.workspaceEdit
        editCapabilities.set(
            EditCapabilities(
                workspaceEdits?.documentChanges == true,
                workspaceEdits?.resourceOperations?.contains("rename") == true,
                params.capabilities
                    ?.workspace
                    ?.didChangeWatchedFiles
                    ?.dynamicRegistration == true,
                params.capabilities
                    ?.workspace
                    ?.didChangeWatchedFiles
                    ?.relativePatternSupport ==
                    true,
            ),
        )

        try {
            documentSynchronization.set(DocumentSynchronization.read(params))
        } catch (e: IllegalArgumentException) {
            return CompletableFuture.failedFuture(
                ResponseErrorException(
                    ResponseError(ResponseErrorCode.InvalidParams, e.message, null),
                ),
            )
        }
        val textCapabilities = params.capabilities?.textDocument
        resolveCapabilities.set(
            ResolveCapabilities(
                textCapabilities
                    ?.completion
                    ?.completionItem
                    ?.resolveSupport
                    ?.properties
                    ?.contains("documentation") == true,
                presentation.actionLiterals &&
                    textCapabilities?.codeAction?.dataSupport == true &&
                    textCapabilities.codeAction.resolveSupport
                        ?.properties
                        ?.contains("edit") ==
                    true,
                textCapabilities?.codeLens?.let {
                    it.resolveSupport?.properties?.contains("command") != false
                } == true,
                textCapabilities?.documentLink != null,
                textCapabilities
                    ?.inlayHint
                    ?.resolveSupport
                    ?.properties
                    ?.contains("tooltip") ==
                    true,
                params.capabilities
                    ?.workspace
                    ?.symbol
                    ?.resolveSupport
                    ?.properties
                    ?.contains("location.range") == true,
            ),
        )
        fileOperationCapabilities.set(params.capabilities?.workspace?.fileOperations)
        val tokenRequests =
            params.capabilities
                ?.textDocument
                ?.semanticTokens
                ?.requests
        val tokens =
            semanticTokensEnabled && AdapterCapability.SEMANTIC_TOKENS in adapter.capabilities
        tokenCapabilities.set(
            TokenCapabilities(
                tokens && tokenRequests?.range?.let { it.isRight || it.left == true } == true,
                tokens && tokenRequests?.full?.right?.delta == true,
                tokens && params.capabilities
                    ?.workspace
                    ?.semanticTokens
                    ?.refreshSupport == true,
            ),
        )

        val capabilities = buildServerCapabilities()

        val workspace = params.capabilities?.workspace
        refresh.configure(
            buildSet {
                if (diagnosticCapabilities.get().refresh) add(ClientRefresh.Feature.DIAGNOSTICS)
                if (tokenCapabilities.get().refresh) add(ClientRefresh.Feature.TOKENS)
                if (
                    workspace?.inlayHint?.refreshSupport == true &&
                    AdapterCapability.INLAY_HINT in adapter.capabilities
                ) {
                    add(ClientRefresh.Feature.INLAYS)
                }
                if (
                    workspace?.codeLens?.refreshSupport == true &&
                    AdapterCapability.CODE_LENS in adapter.capabilities
                ) {
                    add(ClientRefresh.Feature.LENSES)
                }
                if (
                    workspace?.foldingRange?.refreshSupport == true &&
                    AdapterCapability.FOLDING_RANGE in adapter.capabilities
                ) {
                    add(ClientRefresh.Feature.FOLDING)
                }
            },
        )
        logger.info("initialize: Ecstasy Language Server initialized")

        // Health check before workspace indexing
        val healthy = adapter.healthCheck()
        if (!healthy) {
            logger.warn("initialize: adapter health check failed, skipping workspace indexing")
        } else {
            // Extract workspace folder paths and initialize workspace index
            val workspaceFolders =
                ClientPresentation.workspaceUris(params).mapNotNull { uri ->
                    runCatching { Path.of(URI(uri)).toString() }
                        .onFailure {
                            logger.warn("initialize: invalid workspace folder URI: {}", uri)
                        }.getOrNull()
                }

            // Extra source roots (XDK source trees, etc.) from init options, sysprop, or env.
            // Lets the indexer find modules whose sources live outside the user's open project.
            val extraRoots = SourceRootResolver.resolve(params.initializationOptions)
            val folders = (workspaceFolders + extraRoots).distinct()

            if (folders.isNotEmpty()) {
                if (params.workDoneToken != null) {
                    progress.track("Ecstasy: indexing workspace", params.workDoneToken, indexing)
                } else {
                    clientReady.thenRun {
                        progress.track("Ecstasy: indexing workspace", null, indexing)
                    }
                }
                try {
                    val scan =
                        adapter.initializeWorkspaceAsync(folders) { message, percent ->
                            logger.info(
                                "initialize: workspace indexing: {} ({}%)",
                                message,
                                percent,
                            )
                            progress.report(indexing, message, percent)
                        }
                    indexing.whenComplete { _, failure -> if (failure != null) scan.cancel(false) }
                    scan.whenComplete { _, failure ->
                        if (failure == null) {
                            indexing.complete(Unit)
                        } else {
                            indexing.completeExceptionally(failure)
                        }
                    }
                } catch (failure: Throwable) {
                    indexing.completeExceptionally(failure)
                    throw failure
                }
            }
        }

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    /**
     * LSP: initialized notification.
     *
     * Called after the client sends the `initialized` notification, signaling that the handshake is
     * complete and the server can send requests to the client. We use this to pull formatting
     * configuration from the client via `workspace/configuration`.
     */
    override fun initialized(params: InitializedParams?) {
        progress.initialized(supportsProgress.get())
        clientReady.complete(null)
        refresh.initialized()
        logger.info("initialized: handshake complete, requesting editor configuration")
        if (
            editCapabilities
                .getAndUpdate {
                    it.copy(
                        fileWatchers = false,
                        resourceWatchers = it.resourceWatchers || it.fileWatchers,
                    )
                }.fileWatchers
        ) {
            registerFileWatcher()
            updateResourceWatchers()
        }
        requestFormattingConfig()
        requestCompilerConfig()
    }

    /** Apply explicit notification settings, or pull them from configuration-capable clients. */
    fun changeCompilerConfig(raw: Any?) {
        if (adapter !is XdkAdapter) return
        val value =
            try {
                if (CompilerConfiguration.presentationOnly(raw)) return
                CompilerConfiguration.changed(raw)
            } catch (e: IllegalArgumentException) {
                nextCompilerSettings()
                reportCompilerConfigError(e)
                return
            }
        val settings = nextCompilerSettings()
        if (value == null) requestCompilerConfig(settings) else applyCompilerConfig(value, settings)
    }

    private fun nextCompilerSettings(): CompilerSettings =
        synchronized(compilerSettings) {
            compilerSettings.updateAndGet { it.copy(revision = it.revision + 1) }
        }

    private fun requestCompilerConfig(settings: CompilerSettings = nextCompilerSettings()) {
        if (adapter !is XdkAdapter) return
        val currentClient = client ?: return
        if (settings.closed || !settings.canRequest) return
        currentClient
            .configuration(
                ConfigurationParams(
                    listOf(ConfigurationItem().apply { section = CompilerConfiguration.SECTION }),
                ),
            ).thenAccept { values -> applyCompilerConfig(values?.firstOrNull(), settings) }
            .exceptionally { failure ->
                logger.warn(
                    "workspace/configuration: compiler settings request failed: {}",
                    failure.message,
                )
                null
            }
    }

    private fun applyCompilerConfig(
        raw: Any?,
        settings: CompilerSettings,
    ) {
        synchronized(compilerSettings) {
            if (settings.closed || compilerSettings.get() !== settings) return
            try {
                val model = CompilerConfiguration.buildModel(raw)
                if (model != null) {
                    val inputs = model.resolve()
                    textDocumentService.refreshDependencies {
                        (adapter as XdkAdapter).replaceBuildInputs(inputs)
                    }
                    updateResourceWatchers()
                } else if (CompilerConfiguration.automatic(raw)) {
                    textDocumentService.refreshDependencies {
                        (adapter as XdkAdapter).discoverSourceModules()
                    }
                    updateResourceWatchers()
                } else {
                    CompilerConfiguration
                        .modules(raw, settings.folders)
                        ?.let(::replaceCompilerSourceModules)
                }
            } catch (e: IllegalArgumentException) {
                reportCompilerConfigError(e)
            }
        }
    }

    private fun reportCompilerConfigError(failure: IllegalArgumentException) {
        val message =
            "Invalid ${CompilerConfiguration.SECTION}; previous source configuration retained: ${failure.message}"
        logger.warn(message)
        client?.showMessage(MessageParams(MessageType.Error, message))
    }

    /**
     * Request formatting configuration from the client via `workspace/configuration`.
     *
     * Sends a request for section `"xtc.formatting"`. The client (e.g., [XtcLanguageClient] in
     * IntelliJ) responds with IntelliJ Code Style settings. The response is parsed into an
     * [FormattingConfig] and stored as [editorFormattingConfig].
     */
    fun requestFormattingConfig() {
        if (!presentation.workspaceConfiguration || compilerSettings.get().closed) return
        val c = client ?: return
        val revision = formattingState.request()
        val item = ConfigurationItem().apply { section = "xtc.formatting" }
        c
            .configuration(ConfigurationParams(listOf(item)))
            .thenAccept { results ->
                if (
                    formattingState.accept(revision, results?.firstOrNull()) {
                        adapter.editorFormattingConfig = it
                    }
                ) {
                    logger.info(
                        "workspace/configuration: effective formatting config={}",
                        editorFormattingConfig,
                    )
                }
            }.exceptionally { failure ->
                logger.warn(
                    "Invalid or unavailable formatting configuration; previous values retained: {}",
                    failure.message,
                )
                null
            }
    }

    private fun logServerBanner() {
        val pid = ProcessHandle.current().pid()
        logger.info("initialize: ========================================")
        logger.info("initialize: Ecstasy Language Server v{} (pid={})", version, pid)
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
     * Log client feature declarations, independently of the selected adapter. Provider availability
     * is defined by [AdapterCapability] and [buildServerCapabilities]; detailed implementation
     * limits live in lang/doc/plans/plan-ide-integration.md.
     */
    private fun logClientCapabilities(params: InitializeParams) {
        val td = params.capabilities?.textDocument
        val supportedFeatures =
            listOfNotNull(
                td?.hover?.let { "hover" },
                td?.completion?.let { "completion" },
                td?.definition?.let { "definition" },
                td?.declaration?.let { "declaration" },
                td?.typeDefinition?.let { "typeDefinition" },
                td?.implementation?.let { "implementation" },
                td?.references?.let { "references" },
                td?.documentSymbol?.let { "documentSymbol" },
                td?.formatting?.let { "formatting" },
                td?.rangeFormatting?.let { "rangeFormatting" },
                td?.onTypeFormatting?.let { "onTypeFormatting" },
                td?.rename?.let { "rename" },
                td?.codeAction?.let { "codeAction" },
                td?.semanticTokens?.let { "semanticTokens" },
                td?.documentHighlight?.let { "documentHighlight" },
                td?.selectionRange?.let { "selectionRange" },
                td?.foldingRange?.let { "foldingRange" },
                td?.signatureHelp?.let { "signatureHelp" },
                td?.inlayHint?.let { "inlayHint" },
                td?.documentLink?.let { "documentLink" },
                td?.codeLens?.let { "codeLens" },
                td?.typeHierarchy?.let { "typeHierarchy" },
                td?.callHierarchy?.let { "callHierarchy" },
                td?.linkedEditingRange?.let { "linkedEditingRange" },
                td?.synchronization?.let { "synchronization" },
                td?.publishDiagnostics?.let { "publishDiagnostics" },
                td?.diagnostic?.let { "diagnostic" },
            )
        if (supportedFeatures.isNotEmpty()) {
            logger.info("initialize: client capabilities: {}", supportedFeatures.joinToString(", "))
        }
    }

    /**
     * Build the server capabilities that we advertise to the client.
     *
     * Each provider corresponds to a method handled by the selected adapter. Optional response
     * fields are negotiated separately through [ClientPresentation] and the resolve options.
     */
    private fun buildServerCapabilities(): ServerCapabilities =
        ServerCapabilities().apply {
            positionEncoding = "utf-16"
            if (adapter is XdkAdapter) experimental = mapOf("xtcRenameProposal" to 1)
            if (usesPullDiagnostics) {
                diagnosticProvider =
                    DiagnosticRegistrationOptions(true, true).apply {
                        identifier = "xtc"
                        workDoneProgress = true
                    }
            }
            textDocumentSync = Either.forRight(synchronization.capabilities())

            // --- Core navigation (treesitter) ---
            hoverProvider = Either.forLeft(true)
            completionProvider =
                CompletionOptions().apply {
                    triggerCharacters = listOf(".", ":", "<")
                    resolveProvider = resolvesCompletionDocumentation
                }
            definitionProvider = Either.forLeft(true)
            referencesProvider =
                if (adapter is XdkAdapter) {
                    Either.forRight(ReferenceOptions().apply { workDoneProgress = true })
                } else {
                    Either.forLeft(true)
                }
            documentSymbolProvider = Either.forLeft(true)

            // --- Structural features (treesitter) ---
            documentHighlightProvider = Either.forLeft(true)
            selectionRangeProvider = Either.forLeft(true)
            foldingRangeProvider = Either.forLeft(true)

            // --- Editing features (treesitter) ---
            renameProvider =
                Either.forRight(
                    RenameOptions().apply {
                        prepareProvider = true
                        workDoneProgress = true
                    },
                )
            codeActionProvider =
                if (!presentation.codeActions) {
                    Either.forLeft(false)
                } else if (resolvesCodeActionEdit) {
                    Either.forRight(CodeActionOptions().apply { resolveProvider = true })
                } else {
                    Either.forLeft(true)
                }
            if (
                !presentation.actionLiterals &&
                presentation.applyEdit &&
                AdapterCapability.CODE_ACTION in adapter.capabilities
            ) {
                executeCommandProvider =
                    ExecuteCommandOptions(listOf(ClientPresentation.APPLY_CODE_ACTION))
            }
            documentFormattingProvider = Either.forLeft(true)
            documentRangeFormattingProvider =
                Either.forRight(DocumentRangeFormattingOptions().apply { rangesSupport = true })
            documentOnTypeFormattingProvider =
                DocumentOnTypeFormattingOptions("\n").apply {
                    moreTriggerCharacter = listOf("}", ";", ")")
                }
            if (AdapterCapability.INLAY_HINT in adapter.capabilities) {
                inlayHintProvider =
                    if (resolvesInlayHintTooltip) {
                        Either.forRight(
                            InlayHintRegistrationOptions().apply { resolveProvider = true },
                        )
                    } else {
                        Either.forLeft(true)
                    }
            }

            // documentLinkProvider: URLs in comments / string literals.
            // See TreeSitterAdapter.getDocumentLinks for the matcher.
            documentLinkProvider = DocumentLinkOptions(resolvesDocumentLinkTarget)

            signatureHelpProvider =
                SignatureHelpOptions(
                    if (adapter is XdkAdapter) listOf("(", ",", "[") else listOf("(", ","),
                )

            // Semantic tokens: enabled by default. Disable with -Plsp.semanticTokens=false if
            // needed.
            if (
                semanticTokensEnabled && AdapterCapability.SEMANTIC_TOKENS in adapter.capabilities
            ) {
                logger.info(
                    "semantic tokens ENABLED via {} ({} types, {} modifiers)",
                    System.getProperty(SEMANTIC_TOKENS_SYSTEM_PROPERTY)?.let {
                        "system property $SEMANTIC_TOKENS_SYSTEM_PROPERTY=$it"
                    }
                        ?: System.getenv(SEMANTIC_TOKENS_ENV)?.let {
                            "environment $SEMANTIC_TOKENS_ENV=$it"
                        }
                        ?: "build property lsp.semanticTokens=${buildInfo.getProperty("lsp.semanticTokens", "true")}",
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
                        full =
                            if (tokenCapabilities.get().delta) {
                                Either.forRight(SemanticTokensServerFull(true))
                            } else {
                                Either.forLeft(true)
                            }
                        if (tokenCapabilities.get().range) range = Either.forLeft(true)
                    }
            } else {
                logger.warn(
                    "semantic tokens DISABLED via {}",
                    System.getProperty(SEMANTIC_TOKENS_SYSTEM_PROPERTY)?.let {
                        "system property $SEMANTIC_TOKENS_SYSTEM_PROPERTY=$it"
                    }
                        ?: System.getenv(SEMANTIC_TOKENS_ENV)?.let {
                            "environment $SEMANTIC_TOKENS_ENV=$it"
                        }
                        ?: "build property lsp.semanticTokens=${buildInfo.getProperty("lsp.semanticTokens", "true")}",
                )
            }

            // --- Workspace features ---
            workspaceSymbolProvider =
                if (resolvesWorkspaceSymbolRange) {
                    Either.forRight(WorkspaceSymbolOptions(true))
                } else {
                    Either.forLeft(true)
                }
            if (adapter is XdkAdapter) {
                workspace =
                    WorkspaceServerCapabilities().apply {
                        fileOperations =
                            FileOperationsServerCapabilities().apply {
                                val filters =
                                    FileOperationOptions(
                                        listOf(
                                            FileOperationFilter(
                                                FileOperationPattern("**/*.x"),
                                                "file",
                                            ),
                                            FileOperationFilter(
                                                FileOperationPattern("**").apply {
                                                    matches = "folder"
                                                },
                                                "file",
                                            ),
                                        ),
                                    )
                                val client = fileOperationCapabilities.get()
                                if (client?.willRename == true && supportsVersionedEdits) {
                                    willRename = filters
                                }
                                if (client?.willCreate == true) willCreate = filters
                                if (client?.willDelete == true) willDelete = filters
                                if (client?.didRename == true) didRename = filters
                                if (client?.didCreate == true) didCreate = filters
                                if (client?.didDelete == true) didDelete = filters
                            }
                        workspaceFolders =
                            WorkspaceFoldersOptions().apply {
                                supported = true
                                changeNotifications = Either.forRight(true)
                            }
                    }
            }

            // Code lenses: Run action on module declarations (TreeSitterAdapter)
            codeLensProvider = CodeLensOptions(resolvesCodeLensCommand)

            // Linked editing: rename-on-type for same-name identifiers (same-file,
            // TreeSitterAdapter)
            linkedEditingRangeProvider = Either.forLeft(true)

            // Advertise only operations implemented by the selected backend.
            if (AdapterCapability.HOVER !in adapter.capabilities) hoverProvider = null
            if (AdapterCapability.COMPLETION !in adapter.capabilities) completionProvider = null
            if (AdapterCapability.DEFINITION !in adapter.capabilities) definitionProvider = null
            if (AdapterCapability.REFERENCES !in adapter.capabilities) referencesProvider = null
            if (AdapterCapability.DOCUMENT_SYMBOL !in adapter.capabilities) {
                documentSymbolProvider = null
            }
            if (AdapterCapability.DOCUMENT_HIGHLIGHT !in adapter.capabilities) {
                documentHighlightProvider = null
            }
            if (AdapterCapability.SELECTION_RANGE !in adapter.capabilities) {
                selectionRangeProvider = null
            }
            if (AdapterCapability.FOLDING_RANGE !in adapter.capabilities) {
                foldingRangeProvider = null
            }
            if (
                AdapterCapability.RENAME !in adapter.capabilities ||
                (adapter is XdkAdapter && !supportsVersionedEdits)
            ) {
                renameProvider = null
            }
            if (AdapterCapability.CODE_ACTION !in adapter.capabilities) codeActionProvider = null
            if (AdapterCapability.FORMATTING !in adapter.capabilities) {
                documentFormattingProvider = null
            }
            if (AdapterCapability.RANGE_FORMATTING !in adapter.capabilities) {
                documentRangeFormattingProvider = null
            }
            if (AdapterCapability.ON_TYPE_FORMATTING !in adapter.capabilities) {
                documentOnTypeFormattingProvider = null
            }
            if (AdapterCapability.DOCUMENT_LINK !in adapter.capabilities) {
                documentLinkProvider = null
            }
            if (AdapterCapability.SIGNATURE_HELP !in adapter.capabilities) {
                signatureHelpProvider = null
            }
            if (AdapterCapability.WORKSPACE_SYMBOL !in adapter.capabilities) {
                workspaceSymbolProvider = null
            }
            if (AdapterCapability.CODE_LENS !in adapter.capabilities) codeLensProvider = null
            if (AdapterCapability.LINKED_EDITING !in adapter.capabilities) {
                linkedEditingRangeProvider = null
            }

            // Compiler semantic navigation.
            if (AdapterCapability.TYPE_DEFINITION in adapter.capabilities) {
                typeDefinitionProvider = Either.forLeft(true)
            }
            if (AdapterCapability.DECLARATION in adapter.capabilities) {
                declarationProvider = Either.forLeft(true)
            }
            if (AdapterCapability.IMPLEMENTATION in adapter.capabilities) {
                implementationProvider = Either.forLeft(true)
            }
            if (AdapterCapability.TYPE_HIERARCHY in adapter.capabilities) {
                typeHierarchyProvider = Either.forLeft(true)
            }
            if (AdapterCapability.CALL_HIERARCHY in adapter.capabilities) {
                callHierarchyProvider = Either.forLeft(true)
            }
        }

    override fun shutdown(): CompletableFuture<Any> {
        logger.info("shutdown: shutting down Ecstasy Language Server")
        shutdownRequested.set(true)
        close()
        return CompletableFuture.completedFuture(null)
    }

    /** Release resources on both a protocol shutdown and an abrupt transport disconnect. */
    override fun close() {
        val alreadyClosed =
            synchronized(compilerSettings) {
                compilerSettings.getAndUpdate { it.copy(closed = true) }.closed
            }
        if (alreadyClosed) return
        clientReady.cancel(false)
        indexing.cancel(false)
        formattingState.close()
        resourceFileWatchers.close()
        refresh.close()
        clientTrace.close()
        progress.close()
        partialResults.close()
        editCapabilities.set(EditCapabilities())
        try {
            textDocumentService.close()
        } finally {
            adapter.close()
        }
    }

    override fun exit() {
        logger.info("exit: exiting Ecstasy Language Server")
        try {
            close()
        } finally {
            onExit(if (shutdownRequested.get()) 0 else 1)
        }
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    // =========================================================================
    // Custom Ecstasy LSP Methods
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

    /** Effective immutable inputs for host configuration views and explicit override creation. */
    @JsonRequest("xtc/compilerSourceModules")
    fun compilerSourceModules(): CompletableFuture<List<SourceModuleConfiguration>> =
        supplyAsync(
            "xtc/compilerSourceModules",
            "effective inputs",
        ) {
            (adapter as? XdkAdapter)
                ?.effectiveSourceModules()
                ?.map(::SourceModuleConfiguration)
                .orEmpty()
        }

    /** Hosts opting into this extension own persistence and undo of explicit graph replacements. */
    @JsonRequest("xtc/rename")
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?> = textDocumentService.renameProposal(params)

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
     * NOTE: Called at runtime via JSON-RPC by LSP clients (e.g., IntelliJ plugin, VS Code
     * extension) sending a request with method "xtc/health check". LSP4J dispatches via reflection.
     */
    @Suppress("unused")
    @JsonRequest("xtc/languageServiceStatus")
    fun languageServiceStatus(): CompletableFuture<Map<String, Any?>> =
        CompletableFuture.completedFuture(
            mapOf(
                "adapter" to adapter.displayName,
                "version" to version,
                "pid" to ProcessHandle.current().pid(),
                "runtime" to System.getProperty("java.runtime.version"),
                "textSynchronization" to if (synchronization.incremental) "incremental" else "full",
                "serverSaveFormatting" to
                    (synchronization.formatOnSave && synchronization.waitUntil),
                "saveHookSupported" to synchronization.waitUntil,
                "formatting" to editorFormattingConfig,
                "semanticTokens" to
                    (
                        semanticTokensEnabled &&
                            AdapterCapability.SEMANTIC_TOKENS in adapter.capabilities
                    ),
                "capabilities" to buildServerCapabilities(),
                "bundledXdk" to
                    if (adapter is XdkAdapter) {
                        mapOf("readOnly" to true, "modules" to XdkLibraries.packagedResources)
                    } else {
                        null
                    },
                "compilerQueue" to (adapter as? XdkAdapter)?.compilerQueueSnapshot(),
                "heap" to
                    ManagementFactory.getMemoryMXBean().heapMemoryUsage.let {
                        mapOf(
                            "usedBytes" to it.used,
                            "committedBytes" to it.committed,
                            "maxBytes" to it.max,
                        )
                    },
            ),
        )

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
                "message" to
                    if (healthy) "Ecstasy Language Server is healthy" else "Health check failed",
            )
        }

    /**
     * Register source file watchers (and workspace resource changes in compiler mode). This enables
     * the client to notify us when XTC files are created, changed, or deleted on disk (outside of
     * the editor), which we use to keep the workspace index up to date.
     */
    private fun registerFileWatcher() {
        val currentClient = client ?: return
        val watcherOptions =
            DidChangeWatchedFilesRegistrationOptions(
                listOf(
                    FileSystemWatcher(
                        Either.forLeft(if (adapter is XdkAdapter) "**/*" else "**/*.x"),
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
        currentClient.registerCapability(RegistrationParams(listOf(registration))).whenComplete {
            _,
            failure,
            ->
            if (failure == null) {
                logger.info("initialized: registered file watcher for **/*.x")
            } else {
                logger.warn("initialized: client rejected file watcher registration", failure)
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    fun changeCompilerWorkspaceFolders(
        added: List<String>,
        removed: List<String>,
    ) {
        val compiler = adapter as? XdkAdapter ?: return
        synchronized(compilerSettings) {
            compilerSettings.updateAndGet { settings ->
                settings.copy(
                    folders = (settings.folders.filterNot { it in removed } + added).distinct(),
                    revision = settings.revision + 1,
                )
            }
        }
        try {
            textDocumentService.refreshDependencies {
                compiler.changeWorkspaceFolders(added, removed)
            }
            updateResourceWatchers()
        } catch (failure: IllegalArgumentException) {
            reportCompilerConfigError(failure)
        } catch (failure: IOException) {
            logger.warn("Workspace discovery failed; keeping previous graph: {}", failure.message)
        }
    }

    fun refreshCompilerDiscovery() {
        val compiler = adapter as? XdkAdapter ?: return
        try {
            textDocumentService.refreshDependencies { compiler.refreshDiscoveredSources() }
            updateResourceWatchers()
        } catch (failure: IllegalArgumentException) {
            reportCompilerConfigError(failure)
        } catch (failure: IOException) {
            logger.warn("Source discovery failed; keeping previous graph: {}", failure.message)
        }
    }

    private fun updateResourceWatchers() {
        val compiler = adapter as? XdkAdapter ?: return
        val currentClient = client ?: return
        if (!editCapabilities.get().resourceWatchers) return
        val folders = compilerSettings.get().folders.mapNotNull { XdkSources.file(it)?.toPath() }
        val external =
            compiler
                .inputWatchRoots()
                .filter { root ->
                    folders.none { root.toPath().startsWith(it) }
                }.mapTo(linkedSetOf()) { it.toURI().toString() }
        resourceFileWatchers.update(
            currentClient,
            external,
            editCapabilities.get().relativeWatchPatterns,
        )
    }

    fun refreshForFile(uri: String) = textDocumentService.refreshForFile(uri)

    /** Host API; project discovery/configuration is separate from installing matching artifacts. */
    fun replaceCompilerDependencies(dependencies: List<XdkDependency>) {
        val compiler = adapter as? XdkAdapter ?: error("Compiler dependencies require XdkAdapter")
        textDocumentService.refreshDependencies { compiler.replaceDependencies(dependencies) }
    }

    /**
     * Host-supplied source roots/edges enable automatic dependency builds on editor/file events.
     */
    fun replaceCompilerSourceModules(modules: List<XdkSourceModule>) {
        val compiler = adapter as? XdkAdapter ?: error("Compiler source modules require XdkAdapter")
        textDocumentService.refreshDependencies { compiler.replaceSourceModules(modules) }
        updateResourceWatchers()
    }

    fun publishDiagnostics(
        uri: String,
        diagnostics: List<Diagnostic>,
        version: Int? = null,
    ) {
        if (usesPullDiagnostics) return
        val currentClient = client ?: return
        val options = presentation
        val lspDiagnostics =
            diagnostics.map {
                it.toLsp(uri).apply {
                    if (!options.diagnosticRelatedInformation) relatedInformation = null
                }
            }
        currentClient.publishDiagnostics(
            PublishDiagnosticsParams(
                uri,
                lspDiagnostics,
                version.takeIf { options.diagnosticVersions },
            ),
        )
    }
}
