package org.xvm.lsp.server

import com.google.gson.JsonPrimitive
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
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
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
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.RenameFileOptions
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.TextDocumentService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.fmt
import org.xvm.lsp.model.fromLsp
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.model.toRange
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.nanoseconds
import org.xvm.lsp.adapter.CallHierarchyIncomingCall as AdapterCallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem as AdapterCallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall as AdapterCallHierarchyOutgoingCall
import org.xvm.lsp.adapter.CompletionItem as AdapterCompletionItem
import org.xvm.lsp.adapter.FormattingOptions as AdapterFormattingOptions
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange
import org.xvm.lsp.adapter.SelectionRange as AdapterSelectionRange
import org.xvm.lsp.adapter.TypeHierarchyItem as AdapterTypeHierarchyItem
import org.xvm.lsp.adapter.WorkspaceEdit as AdapterWorkspaceEdit
import org.xvm.lsp.model.Location as DiagnosticLocation

/**
 * Text document service for Ecstasy Language Server.
 * Handles document synchronization and language features.
 */
class XtcTextDocumentService(
    private val server: XtcLanguageServer,
    private val adapter: Adapter,
) : TextDocumentService {
    companion object {
        private val logger = LoggerFactory.getLogger(XtcTextDocumentService::class.java)
    }

    private class Document(
        val content: String,
        val version: Int,
        val scope: String,
        val analysis: CompletableFuture<CompilationResult>,
    )

    // Completion callbacks and notifications must agree on which document may publish.
    private val lifecycle = Any()
    private val openDocuments = ConcurrentHashMap<String, Document>()
    private var closed = false
    private val publishedByScope = mutableMapOf<String, Set<String>>()
    private val pendingQueries = mutableMapOf<CompletableFuture<*>, String>()

    private fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        uri: String? = null,
        block: () -> R,
    ): CompletableFuture<R> {
        val document = uri?.let { openDocuments[it] }
        val ready = document?.analysis ?: CompletableFuture.completedFuture(null)
        return ready.handle { _, _ -> null }.thenCompose {
            server.supplyAsync(method, logParams, logResult) {
                synchronized(lifecycle) {
                    if (closed || (uri != null && openDocuments[uri] !== document)) {
                        throw ResponseErrorException(
                            ResponseError(ResponseErrorCode.ContentModified, "Document changed during analysis", null),
                        )
                    }
                    block()
                }
            }
        }
    }

    /** A semantic query owns its backend future, while the module analysis remains shared. */
    private fun <T, R> queryAsync(
        method: String,
        uri: String,
        request: () -> CompletableFuture<T>,
        workspace: Boolean = false,
        convert: (T) -> R,
    ): CompletableFuture<R> {
        val result = CompletableFuture<R>()
        val (document, documents) =
            synchronized(lifecycle) {
                if (closed) return CompletableFuture.failedFuture(contentModified())
                pendingQueries[result] = uri
                openDocuments[uri] to if (workspace) openDocuments.toMap() else null
            }

        fun stale(): Boolean = closed || openDocuments[uri] !== document || (documents != null && documents != openDocuments)
        val started = System.nanoTime()
        logger.info("{}: {}", method, uri)
        result.whenComplete { _, failure ->
            synchronized(lifecycle) { pendingQueries.remove(result) }
            val outcome = if (failure == null) "completed" else "canceled or failed"
            logger.info(
                "{}: {} in {}",
                method,
                outcome,
                (System.nanoTime() - started).nanoseconds,
            )
        }
        val ready = document?.analysis ?: CompletableFuture.completedFuture(null)
        ready
            .handle { _, _ -> Unit }
            .thenRunAsync {
                val work =
                    synchronized(lifecycle) {
                        if (result.isDone) return@thenRunAsync
                        if (stale()) throw contentModified()
                        request()
                    }
                // Register after starting work: if cancellation won the race, this runs immediately.
                result.whenComplete { _, failure -> if (failure != null) work.cancel(false) }
                work.whenComplete { value, failure ->
                    synchronized(lifecycle) {
                        if (!result.isDone) {
                            when {
                                stale() -> {
                                    result.completeExceptionally(contentModified())
                                }

                                failure != null -> {
                                    val cause = generateSequence(failure) { (it as? CompletionException)?.cause }.last()
                                    if (cause is CancellationException) result.cancel(false) else result.completeExceptionally(cause)
                                }

                                else -> {
                                    try {
                                        result.complete(convert(value))
                                    } catch (e: Exception) {
                                        result.completeExceptionally(e)
                                    } catch (e: Error) {
                                        result.completeExceptionally(e)
                                        throw e
                                    }
                                }
                            }
                        }
                    }
                }
            }.whenComplete { _, failure -> if (failure != null) result.completeExceptionally(failure) }
        return result
    }

    private fun contentModified() =
        ResponseErrorException(ResponseError(ResponseErrorCode.ContentModified, "Document changed during analysis", null))

    /** Called under lifecycle; retire the public result even if a backend ignores cancellation. */
    private fun invalidateQueries(uris: Set<String>) {
        pendingQueries
            .filterValues { it in uris }
            .keys
            .toList()
            .forEach { it.completeExceptionally(contentModified()) }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        synchronized(lifecycle) {
            if (closed) return
            analyse(params.textDocument.uri, params.textDocument.text, params.textDocument.version)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        synchronized(lifecycle) {
            if (closed) return
            val uri = params.textDocument.uri
            val previous = openDocuments[uri] ?: return
            val version = params.textDocument.version
            if (version <= previous.version) return
            val changes = params.contentChanges
            if (changes.isNullOrEmpty()) return
            // The server advertises full synchronization. Do not mistake an incremental patch
            // for a complete file if a client violates that contract.
            if (changes.any { it.range != null }) {
                logger.warn("textDocument/didChange: ignoring incremental changes for full-sync document {}", uri)
                return
            }
            analyse(uri, changes.last().text, version)
        }
    }

    /** Refresh the changed module, then open source consumers in dependency order. */
    private fun analyse(
        uri: String,
        content: String,
        version: Int,
    ) {
        val changedGraph = (adapter as? XdkAdapter)?.updateDocument(uri, content).orEmpty()
        analyseOne(uri, content, version)
        refreshScopes((changedGraph + adapter.affectedAnalysisScopes(uri)) - adapter.analysisScope(uri))
    }

    private fun refreshScopes(scopes: Set<String>) {
        scopes
            .mapNotNull { scope ->
                openDocuments.entries.firstOrNull { it.value.scope == scope || adapter.analysisScope(it.key) == scope }
            }.distinctBy { adapter.analysisScope(it.key) }
            .forEach { (uri, document) ->
                analyseOne(uri, document.content, document.version)
            }
    }

    /** A member edit replaces the analysis future for every open document in that module. */
    private fun analyseOne(
        uri: String,
        content: String,
        version: Int,
    ) {
        val analysis = adapter.compileAsync(uri, content)
        val scope = adapter.analysisScope(uri)
        val affected =
            openDocuments.filter { (otherUri, document) ->
                otherUri == uri || document.scope == scope || adapter.analysisScope(otherUri) == scope
            }
        val current = affected.mapValues { (_, document) -> Document(document.content, document.version, scope, analysis) }.toMutableMap()
        current[uri] = Document(content, version, scope, analysis)
        openDocuments.putAll(current)
        invalidateQueries(current.keys)
        affected.values
            .map { it.analysis }
            .distinct()
            .filter { it !== analysis }
            .forEach { it.cancel(false) }
        analysis.whenComplete { result, failure ->
            synchronized(lifecycle) {
                if (!closed && current.all { (documentUri, document) -> openDocuments[documentUri] === document }) {
                    if (failure == null) {
                        publish(scope, result)
                    } else if (failure !is CancellationException) {
                        logger.error("analysis failed: uri={}, version={}", uri, version, failure)
                        publish(
                            scope,
                            CompilationResult.failure(
                                uri,
                                listOf(
                                    Diagnostic(
                                        location = DiagnosticLocation(uri, 0, 0, 0, 0),
                                        severity = Diagnostic.Severity.ERROR,
                                        message = "XTC analysis failed; see the language server log for details",
                                        code = "ANALYSIS-FAILED",
                                        source = "xtc",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** A source closure publishes together; artifact-only foreign sources remain related info. */
    private fun publish(
        scope: String,
        result: CompilationResult,
    ) {
        val previous = publishedByScope.put(scope, result.documentUris).orEmpty()
        clearUnowned(previous - result.documentUris)
        result.documentUris.forEach { uri ->
            val diagnostics =
                result.diagnostics.filter {
                    it.location.uri == uri || (uri == result.uri && it.location.uri !in result.documentUris)
                }
            server.publishDiagnostics(uri, diagnostics, openDocuments[uri]?.version)
        }
        // Root discovery can change after file creation/removal; release publications of old scopes.
        val inactive = publishedByScope.keys.filter { key -> openDocuments.values.none { it.scope == key } }
        inactive.forEach { key ->
            clearUnowned(publishedByScope.remove(key).orEmpty() - result.documentUris)
        }
    }

    private fun clearUnowned(uris: Set<String>) {
        uris.filter { uri -> publishedByScope.values.none { uri in it } }.forEach {
            server.publishDiagnostics(it, emptyList(), openDocuments[it]?.version)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        synchronized(lifecycle) {
            val uri = params.textDocument.uri
            val affected = adapter.affectedAnalysisScopes(uri)
            val document = openDocuments.remove(uri)
            invalidateQueries(setOf(uri))
            document?.analysis?.cancel(false)
            val retired =
                if (adapter is XdkAdapter) {
                    adapter.closeDocumentAndRefresh(uri)
                } else {
                    adapter.closeDocument(uri)
                    emptySet()
                }
            if (closed) return
            server.publishDiagnostics(uri, emptyList(), document?.version)
            refreshScopes(affected + retired)
            if (openDocuments.values.none { it.scope == document?.scope }) {
                clearUnowned(publishedByScope.remove(document?.scope).orEmpty() - uri)
            }
        }
    }

    /** Re-read closed files and module membership after filesystem notifications. */
    fun refreshForFile(uri: String) {
        synchronized(lifecycle) {
            // An open buffer is authoritative, including when its disk file is created or
            // deleted. Recompiling identical overlays here cancels otherwise current queries.
            // didChange already propagates edits; didClose re-reads disk and membership.
            if (closed || openDocuments.containsKey(uri)) return
            refreshScopes(adapter.affectedAnalysisScopes(uri) + publishedByScope.filterValues { uri in it }.keys)
        }
    }

    /** Dependency replacement and versioned reanalysis share the diagnostic publication lock. */
    internal fun refreshDependencies(replace: () -> Set<String>) {
        synchronized(lifecycle) {
            if (closed) return
            refreshScopes(replace())
        }
    }

    fun close() {
        synchronized(lifecycle) {
            closed = true
            val documents = openDocuments.toMap()
            openDocuments.clear()
            invalidateQueries(pendingQueries.values.toSet())
            publishedByScope.clear()
            documents.forEach { (uri, document) ->
                document.analysis.cancel(false)
                adapter.closeDocument(uri)
            }
        }
    }

    /**
     * LSP: textDocument/didSave
     * @see org.eclipse.lsp4j.services.TextDocumentService.didSave
     */
    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("textDocument/didSave: {}", params.textDocument.uri)
        refreshForFile(params.textDocument.uri)
    }

    /**
     * LSP: textDocument/hover
     * @see org.eclipse.lsp4j.services.TextDocumentService.hover
     */
    override fun hover(params: HoverParams): CompletableFuture<Hover?> =
        supplyAsync(
            "textDocument/hover",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result == null) "no result" else "found symbol" },
            uri = params.textDocument.uri,
        ) {
            adapter.getHoverInfo(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Hover().apply {
                    contents =
                        Either.forRight(
                            MarkupContent().apply {
                                kind = MarkupKind.MARKDOWN
                                value = it
                            },
                        )
                }
            }
        }

    /**
     * LSP: textDocument/completion
     * @see org.eclipse.lsp4j.services.TextDocumentService.completion
     */
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        queryAsync(
            "textDocument/completion",
            params.textDocument.uri,
            {
                adapter.getCompletionsAsync(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.context?.triggerCharacter,
                )
            },
        ) { completions ->
            val items =
                completions.map { c ->
                    CompletionItem(c.label).apply {
                        kind = toCompletionItemKind(c.kind)
                        detail = c.detail
                        insertText = c.insertText
                        textEdit = c.textEdit?.let { Either.forLeft(TextEdit(it.range.toLsp(), it.newText)) }
                    }
                }
            Either.forLeft(items)
        }

    private fun toCompletionItemKind(kind: AdapterCompletionItem.CompletionKind): CompletionItemKind =
        when (kind) {
            AdapterCompletionItem.CompletionKind.CLASS -> CompletionItemKind.Class
            AdapterCompletionItem.CompletionKind.INTERFACE -> CompletionItemKind.Interface
            AdapterCompletionItem.CompletionKind.METHOD -> CompletionItemKind.Method
            AdapterCompletionItem.CompletionKind.PROPERTY -> CompletionItemKind.Property
            AdapterCompletionItem.CompletionKind.VARIABLE -> CompletionItemKind.Variable
            AdapterCompletionItem.CompletionKind.KEYWORD -> CompletionItemKind.Keyword
            AdapterCompletionItem.CompletionKind.MODULE -> CompletionItemKind.Module
        }

    /**
     * LSP: textDocument/definition
     * @see org.eclipse.lsp4j.services.TextDocumentService.definition
     */
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/definition",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result ->
                if (result.left.isEmpty()) {
                    "no result"
                } else {
                    val loc = result.left.first()
                    "found ${loc.uri.substringAfterLast('/')}@${loc.range.start.fmt()}"
                }
            },
            uri = params.textDocument.uri,
        ) {
            adapter.findDefinition(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
            } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/references
     * @see org.eclipse.lsp4j.services.TextDocumentService.references
     */
    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> =
        queryAsync(
            "textDocument/references",
            params.textDocument.uri,
            {
                adapter.findReferencesAsync(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.context.isIncludeDeclaration,
                )
            },
            workspace = true,
        ) { references -> references.map { it.toLsp() } }

    /**
     * LSP: textDocument/documentSymbol
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentSymbol
     */
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        supplyAsync(
            "textDocument/documentSymbol",
            params.textDocument.uri,
            { result -> "${result.size} symbols" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val result = adapter.getCachedResult(uri) ?: return@supplyAsync emptyList()

            result.symbols.map { symbol ->
                Either.forRight(toDocumentSymbol(symbol))
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

    /**
     * LSP: textDocument/documentHighlight
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentHighlight
     */
    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> =
        supplyAsync(
            "textDocument/documentHighlight",
            "${params.textDocument.uri} pos=${params.position.fmt()}",
            { result -> "${result.size} highlights" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .getDocumentHighlights(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                ).map { h ->
                    DocumentHighlight().apply {
                        range = h.range.toLsp()
                        kind = h.kind.toLsp()
                    }
                }
        }

    /**
     * LSP: textDocument/selectionRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.selectionRange
     */
    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> =
        supplyAsync(
            "textDocument/selectionRange",
            "${params.textDocument.uri} positions=${params.positions.map { it.fmt() }}",
            { result -> "${result.size} ranges" },
            uri = params.textDocument.uri,
        ) {
            val adapterPositions = params.positions.map { AdapterPosition(it.line, it.character) }
            adapter.getSelectionRanges(params.textDocument.uri, adapterPositions).map { toLspSelectionRange(it) }
        }

    private fun toLspSelectionRange(range: AdapterSelectionRange): org.eclipse.lsp4j.SelectionRange =
        org.eclipse.lsp4j.SelectionRange().apply {
            this.range = range.range.toLsp()
            this.parent = range.parent?.let { toLspSelectionRange(it) }
        }

    /**
     * LSP: textDocument/foldingRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.foldingRange
     */
    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> =
        supplyAsync(
            "textDocument/foldingRange",
            params.textDocument.uri,
            { result -> "${result.size} ranges" },
            uri = params.textDocument.uri,
        ) {
            adapter.getFoldingRanges(params.textDocument.uri).map { r ->
                FoldingRange(r.startLine, r.endLine).apply {
                    kind = r.kind?.toLsp()
                    startCharacter = r.startCharacter
                    endCharacter = r.endCharacter
                }
            }
        }

    /**
     * LSP: textDocument/documentLink
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentLink
     */
    override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>> =
        supplyAsync(
            "textDocument/documentLink",
            params.textDocument.uri,
            { result -> "${result.size} links" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]?.content ?: return@supplyAsync emptyList()
            adapter.getDocumentLinks(uri, content).map { l ->
                DocumentLink().apply {
                    range = l.range.toLsp()
                    target = l.target
                    tooltip = l.tooltip
                }
            }
        }

    /**
     * LSP: textDocument/signatureHelp
     * @see org.eclipse.lsp4j.services.TextDocumentService.signatureHelp
     */
    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        queryAsync(
            "textDocument/signatureHelp",
            params.textDocument.uri,
            { adapter.getSignatureHelpAsync(params.textDocument.uri, params.position.line, params.position.character) },
        ) { result ->
            result?.let { help ->
                SignatureHelp().apply {
                    signatures =
                        help.signatures.map { s ->
                            SignatureInformation().apply {
                                label = s.label
                                documentation = s.documentation?.let { Either.forLeft(it) }
                                activeParameter = s.activeParameter
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
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> =
        supplyAsync(
            "textDocument/prepareRename",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { _ -> "valid" },
            uri = params.textDocument.uri,
        ) {
            adapter.prepareRename(params.textDocument.uri, params.position.line, params.position.character)?.let { result ->
                Either3.forSecond<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>(
                    PrepareRenameResult().apply {
                        range = result.range.toLsp()
                        placeholder = result.placeholder
                    },
                )
            } ?: throw ResponseErrorException(
                ResponseError(
                    ResponseErrorCode.InvalidParams,
                    "Rename not allowed at this position",
                    null,
                ),
            )
        }

    /**
     * LSP: textDocument/rename
     * @see org.eclipse.lsp4j.services.TextDocumentService.rename
     */
    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> =
        queryAsync(
            "textDocument/rename",
            params.textDocument.uri,
            {
                adapter.renameAsync(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.newName,
                )
            },
            workspace = true,
        ) { edit -> edit?.let(::protocolEdit) }

    /** Called while the document lifecycle is locked, after the query's version checks. */
    private fun protocolEdit(edit: AdapterWorkspaceEdit): WorkspaceEdit? {
        if ((edit.versioned && !server.supportsVersionedEdits) || (edit.renames.isNotEmpty() && !server.supportsFileRenames)) return null
        val changes = edit.changes.mapValues { (_, edits) -> edits.map { TextEdit(it.range.toLsp(), it.newText) } }
        return WorkspaceEdit().apply {
            if (edit.versioned) {
                this.changes = null
                documentChanges =
                    changes.map { (uri, edits) ->
                        Either.forLeft<TextDocumentEdit, ResourceOperation>(
                            TextDocumentEdit(
                                VersionedTextDocumentIdentifier(uri, openDocuments[uri]?.version),
                                edits.map { Either.forLeft(it) },
                            ),
                        )
                    } +
                    edit.renames.map { (from, to) ->
                        Either.forRight<TextDocumentEdit, ResourceOperation>(RenameFile(from, to, RenameFileOptions(false, false)))
                    }
            } else {
                this.changes = changes
            }
        }
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> =
        queryAsync(
            "textDocument/codeAction",
            params.textDocument.uri,
            {
                adapter.getCodeActionsAsync(
                    params.textDocument.uri,
                    toAdapterRange(params.range),
                    params.context.diagnostics
                        .orEmpty()
                        .map { Diagnostic.fromLsp(params.textDocument.uri, it) },
                )
            },
            workspace = true,
        ) { actions ->
            actions.mapNotNull { action ->
                val proposed = action.edit?.let { protocolEdit(it) ?: return@mapNotNull null }
                Either.forRight<Command, CodeAction>(
                    CodeAction().apply {
                        title = action.title
                        kind = action.kind.toLsp()
                        isPreferred = action.isPreferred
                        edit = proposed
                    },
                )
            }
        }

    /**
     * LSP: textDocument/semanticTokens/full
     * @see org.eclipse.lsp4j.services.TextDocumentService.semanticTokensFull
     */
    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens?> =
        supplyAsync(
            "textDocument/semanticTokens/full",
            params.textDocument.uri,
            { result ->
                if (result == null) "no tokens" else "${result.data.size} items (${result.data.size / 5} tokens)"
            },
            uri = params.textDocument.uri,
        ) {
            adapter.getSemanticTokens(params.textDocument.uri)?.let { tokens ->
                SemanticTokens().apply {
                    data = tokens.data
                }
            }
        }

    /**
     * LSP: textDocument/inlayHint
     * @see org.eclipse.lsp4j.services.TextDocumentService.inlayHint
     */
    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> =
        supplyAsync(
            "textDocument/inlayHint",
            "${params.textDocument.uri} range=${params.range.fmt()}",
            { result -> "${result.size} hints" },
            uri = params.textDocument.uri,
        ) {
            adapter.getInlayHints(params.textDocument.uri, toAdapterRange(params.range)).map { h ->
                InlayHint().apply {
                    position = Position(h.position.line, h.position.column)
                    label = Either.forLeft(h.label)
                    kind = h.kind.toLsp()
                    paddingLeft = h.paddingLeft
                    paddingRight = h.paddingRight
                }
            }
        }

    /**
     * LSP: textDocument/formatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.formatting
     */
    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/formatting",
            params.textDocument.uri,
            { result -> "${result.size} edits" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]?.content ?: return@supplyAsync emptyList()
            val options = toAdapterFormattingOptions(params.options)
            adapter.formatDocument(uri, content, options).map { e ->
                TextEdit().apply {
                    range = e.range.toLsp()
                    newText = e.newText
                }
            }
        }

    /**
     * LSP: textDocument/rangeFormatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.rangeFormatting
     */
    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/rangeFormatting",
            "${params.textDocument.uri} range=${params.range.fmt()}",
            { result -> "${result.size} edits" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]?.content ?: return@supplyAsync emptyList()
            val options = toAdapterFormattingOptions(params.options)
            adapter.formatRange(uri, content, toAdapterRange(params.range), options).map { e ->
                TextEdit().apply {
                    this.range = e.range.toLsp()
                    newText = e.newText
                }
            }
        }

    /**
     * LSP: textDocument/declaration
     * @see org.eclipse.lsp4j.services.TextDocumentService.declaration
     */
    override fun declaration(params: DeclarationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/declaration",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
            uri = params.textDocument.uri,
        ) {
            adapter.findDeclaration(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
            } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/typeDefinition
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeDefinition
     */
    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/typeDefinition",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
            uri = params.textDocument.uri,
        ) {
            Either.forLeft(
                adapter.findTypeDefinitions(params.textDocument.uri, params.position.line, params.position.character).map { it.toLsp() },
            )
        }

    /**
     * LSP: textDocument/implementation
     * @see org.eclipse.lsp4j.services.TextDocumentService.implementation
     */
    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/implementation",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.left.size} locations" },
            uri = params.textDocument.uri,
        ) {
            Either.forLeft(
                adapter.findImplementation(params.textDocument.uri, params.position.line, params.position.character).map { it.toLsp() },
            )
        }

    /**
     * LSP: typeHierarchy/prepareTypeHierarchy
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareTypeHierarchy
     */
    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/prepare",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} items" },
            uri = params.textDocument.uri,
        ) {
            adapter.prepareTypeHierarchy(params.textDocument.uri, params.position.line, params.position.character).map {
                it.toLsp(params.textDocument.uri)
            }
        }

    /**
     * LSP: typeHierarchy/supertypes
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySupertypes
     */
    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/supertypes",
            params.item.name,
            { result -> "${result.size} items" },
            uri = params.item.uri,
        ) {
            adapter.getSupertypes(params.item.toAdapter()).map { it.toLsp(params.item.uri) }
        }

    /**
     * LSP: typeHierarchy/subtypes
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySubtypes
     */
    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/subtypes",
            params.item.name,
            { result -> "${result.size} items" },
            uri = params.item.uri,
        ) {
            adapter.getSubtypes(params.item.toAdapter()).map { it.toLsp(params.item.uri) }
        }

    /**
     * LSP: callHierarchy/prepare
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareCallHierarchy
     */
    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> =
        supplyAsync(
            "callHierarchy/prepare",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} items" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .prepareCallHierarchy(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                ).map { it.toLspCallItem() }
        }

    /**
     * LSP: callHierarchy/incomingCalls
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyIncomingCalls
     */
    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> =
        supplyAsync(
            "callHierarchy/incomingCalls",
            params.item.name,
            { result -> "${result.size} calls" },
            uri = params.item.uri,
        ) {
            adapter.getIncomingCalls(params.item.toAdapterCallItem()).map { c ->
                CallHierarchyIncomingCall().apply {
                    from = c.from.toLspCallItem()
                    fromRanges = c.fromRanges.map { it.toLsp() }
                }
            }
        }

    /**
     * LSP: callHierarchy/outgoingCalls
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyOutgoingCalls
     */
    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> =
        supplyAsync(
            "callHierarchy/outgoingCalls",
            params.item.name,
            { result -> "${result.size} calls" },
            uri = params.item.uri,
        ) {
            adapter.getOutgoingCalls(params.item.toAdapterCallItem()).map { c ->
                CallHierarchyOutgoingCall().apply {
                    to = c.to.toLspCallItem()
                    fromRanges = c.fromRanges.map { it.toLsp() }
                }
            }
        }

    /**
     * LSP: textDocument/codeLens
     * @see org.eclipse.lsp4j.services.TextDocumentService.codeLens
     */
    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> =
        supplyAsync(
            "textDocument/codeLens",
            params.textDocument.uri,
            { result -> "${result.size} lenses" },
            uri = params.textDocument.uri,
        ) {
            adapter.getCodeLenses(params.textDocument.uri).map { l ->
                CodeLens().apply {
                    range = l.range.toLsp()
                    l.command?.let { cmd ->
                        command = Command(cmd.title, cmd.command)
                    }
                }
            }
        }

    /**
     * LSP: textDocument/onTypeFormatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.onTypeFormatting
     */
    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/onTypeFormatting",
            "${params.textDocument.uri} at ${params.position.fmt()} ch='${params.ch}'",
            { result ->
                if (result.isEmpty()) {
                    "0 edits"
                } else {
                    val preview =
                        result.take(2).joinToString("; ") { edit ->
                            val text =
                                edit.newText
                                    .replace("\n", "\\n")
                                    .replace("\t", "\\t")
                            "${edit.range.start.line}:${edit.range.start.character}-${edit.range.end.line}:${edit.range.end.character}='$text'"
                        }
                    "${result.size} edits [$preview]"
                }
            },
            uri = params.textDocument.uri,
        ) {
            val options =
                AdapterFormattingOptions(
                    tabSize = params.options.tabSize,
                    insertSpaces = params.options.isInsertSpaces,
                )
            adapter
                .onTypeFormatting(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.ch,
                    options,
                ).map { e ->
                    TextEdit().apply {
                        range = e.range.toLsp()
                        newText = e.newText
                    }
                }
        }

    /**
     * LSP: textDocument/linkedEditingRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.linkedEditingRange
     */
    override fun linkedEditingRange(params: LinkedEditingRangeParams): CompletableFuture<LinkedEditingRanges> =
        supplyAsync(
            "textDocument/linkedEditingRange",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.ranges?.size ?: 0} ranges" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .getLinkedEditingRanges(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )?.let { result ->
                    LinkedEditingRanges().apply {
                        ranges = result.ranges.map { it.toLsp() }
                        wordPattern = result.wordPattern
                    }
                } ?: LinkedEditingRanges()
        }

    // ====================================================================
    // Conversion helpers for hierarchy types
    // ====================================================================

    private fun AdapterTypeHierarchyItem.toLsp(defaultUri: String): org.eclipse.lsp4j.TypeHierarchyItem {
        val resolvedUri = this.uri.ifEmpty { defaultUri }
        return org.eclipse.lsp4j
            .TypeHierarchyItem(
                this.name,
                this.kind.toLsp(),
                resolvedUri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
            ).apply {
                this.detail = this@toLsp.detail
                this.data = this@toLsp.data
            }
    }

    private fun org.eclipse.lsp4j.TypeHierarchyItem.toAdapter(): AdapterTypeHierarchyItem =
        AdapterTypeHierarchyItem(
            name = name,
            kind = SymbolInfo.SymbolKind.CLASS,
            uri = uri,
            range = toAdapterRange(range),
            selectionRange = toAdapterRange(selectionRange),
            detail = detail,
            data =
                when (val value = data) {
                    is String -> value
                    is JsonPrimitive -> if (value.isString) value.asString else null
                    else -> null
                },
        )

    private fun AdapterCallHierarchyItem.toLspCallItem(): org.eclipse.lsp4j.CallHierarchyItem {
        val result =
            org.eclipse.lsp4j.CallHierarchyItem(
                this.name,
                this.kind.toLsp(),
                this.uri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
            )
        result.detail = this.detail
        result.data = this.data
        return result
    }

    private fun org.eclipse.lsp4j.CallHierarchyItem.toAdapterCallItem(): AdapterCallHierarchyItem =
        AdapterCallHierarchyItem(
            name = name,
            kind = SymbolInfo.SymbolKind.METHOD,
            uri = uri,
            range = toAdapterRange(range),
            selectionRange = toAdapterRange(selectionRange),
            detail = detail,
            data =
                when (val value = data) {
                    is String -> value
                    is JsonPrimitive -> value.takeIf { it.isString }?.asString
                    else -> null
                },
        )

    private fun toAdapterRange(range: org.eclipse.lsp4j.Range) =
        AdapterRange(
            AdapterPosition(range.start.line, range.start.character),
            AdapterPosition(range.end.line, range.end.character),
        )

    private fun toAdapterFormattingOptions(lsp: org.eclipse.lsp4j.FormattingOptions) =
        AdapterFormattingOptions(
            tabSize = lsp.tabSize,
            insertSpaces = lsp.isInsertSpaces,
            trimTrailingWhitespace = lsp.isTrimTrailingWhitespace,
            insertFinalNewline = lsp.isInsertFinalNewline,
        )
}
