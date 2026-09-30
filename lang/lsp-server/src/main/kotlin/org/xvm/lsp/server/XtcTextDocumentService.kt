package org.xvm.lsp.server

import com.google.gson.JsonPrimitive
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.nanoseconds
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
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentRangesFormattingParams
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
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ResourceOperation
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
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
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkDoneProgressParams
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolLocation
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.TextDocumentService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CallHierarchyItem as AdapterCallHierarchyItem
import org.xvm.lsp.adapter.CodeLensCommand
import org.xvm.lsp.adapter.CompletionItem as AdapterCompletionItem
import org.xvm.lsp.adapter.DocumentLink as AdapterDocumentLink
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions as AdapterFormattingOptions
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange
import org.xvm.lsp.adapter.SelectionRange as AdapterSelectionRange
import org.xvm.lsp.adapter.TypeHierarchyItem as AdapterTypeHierarchyItem
import org.xvm.lsp.adapter.WorkspaceEdit as AdapterWorkspaceEdit
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location as AdapterLocation
import org.xvm.lsp.model.Location as DiagnosticLocation
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.fmt
import org.xvm.lsp.model.fromLsp
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.model.toRange
import org.xvm.lsp.util.ExecutionTrace

/**
 * Text document service for Ecstasy Language Server. Handles document synchronization and language
 * features.
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
    private val diagnosticReports = DiagnosticReports()
    private val semanticTokenReports = SemanticTokenReports()
    private val completionReports = ResolveReports<String>()
    private val actionReports = ResolveReports<AdapterWorkspaceEdit>()
    private val lensReports = ResolveReports<CodeLensCommand>()
    private val linkReports = ResolveReports<AdapterDocumentLink>()
    private val hintReports = ResolveReports<String>()
    private val symbolReports = ResolveReports<AdapterLocation>()

    private fun clearResolveReports() =
        listOf(
                completionReports,
                actionReports,
                lensReports,
                linkReports,
                hintReports,
                symbolReports,
            )
            .forEach { it.clear() }

    private fun requireResolve(enabled: Boolean, feature: String) {
        if (!enabled)
            throw ResponseErrorException(
                ResponseError(
                    ResponseErrorCode.MethodNotFound,
                    "$feature resolution was not negotiated",
                    null,
                )
            )
    }

    private var diagnosticRevision = 0L
    private val publishedByScope = mutableMapOf<String, Set<String>>()
    private val pendingQueries = mutableMapOf<CompletableFuture<*>, String>()

    private fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        uri: String? = null,
        sourceOnly: Boolean = false,
        block: () -> R,
    ): CompletableFuture<R> {
        val document = uri?.let { openDocuments[it] }
        return queryAsync(
            method,
            uri.orEmpty(),
            request = {
                server.supplyAsync(method, logParams, logResult) {
                    synchronized(lifecycle) {
                        if (closed || (uri != null && openDocuments[uri] !== document))
                            throw contentModified()
                        block()
                    }
                }
            },
            workspace = uri == null,
            sourceOnly = sourceOnly,
        ) {
            it
        }
    }

    /** A semantic query owns its backend future, while the module analysis remains shared. */
    private fun <T, R> queryAsync(
        method: String,
        uri: String,
        request: () -> CompletableFuture<T>,
        workspace: Boolean = false,
        sourceOnly: Boolean = false,
        progress: WorkDoneProgressParams? = null,
        convert: (T) -> R,
    ): CompletableFuture<R> {
        val result = CompletableFuture<R>()
        val trace = ExecutionTrace.current()
        val (document, documents) =
            synchronized(lifecycle) {
                if (closed) return CompletableFuture.failedFuture(contentModified())
                pendingQueries[result] = uri
                openDocuments[uri] to if (workspace) openDocuments.toMap() else null
            }

        fun stale(): Boolean =
            closed ||
                openDocuments[uri] !== document ||
                (documents != null && documents != openDocuments)
        val started = System.nanoTime()
        logger.info("{}: {}", method, uri)
        // A JSON-RPC cancellation can complete this future under the transport's request lock.
        // Release that thread before entering either the document or compiler lifecycle.
        result.whenCompleteAsync { _, failure ->
            synchronized(lifecycle) { pendingQueries.remove(result) }
            val outcome = if (failure == null) "completed" else "canceled or failed"
            logger.info(
                "{}: {} in {}",
                method,
                outcome,
                (System.nanoTime() - started).nanoseconds,
            )
        }
        val ready =
            if (sourceOnly) CompletableFuture.completedFuture(null)
            else if (documents != null)
                CompletableFuture.allOf(
                    *documents.values.map { it.analysis }.distinct().toTypedArray()
                )
            else document?.analysis ?: CompletableFuture.completedFuture(null)
        ready
            .handle { _, _ -> Unit }
            .thenRunAsync {
                val work =
                    synchronized(lifecycle) {
                        if (result.isDone) return@thenRunAsync
                        if (stale()) throw contentModified()
                        ExecutionTrace.within(trace, request)
                    }
                // Register after starting work: prior cancellation still schedules backend cleanup.
                result.whenCompleteAsync { _, failure -> if (failure != null) work.cancel(false) }
                // Do not occupy the compiler worker while acquiring the publication lock: a
                // synchronous navigation request can hold it while awaiting that same worker.
                work.whenCompleteAsync { value, failure ->
                    synchronized(lifecycle) {
                        if (!result.isDone) {
                            when {
                                stale() -> {
                                    result.completeExceptionally(contentModified())
                                }

                                failure != null -> {
                                    val cause =
                                        generateSequence(failure) {
                                                (it as? CompletionException)?.cause
                                            }
                                            .last()
                                    if (cause is CancellationException) result.cancel(false)
                                    else result.completeExceptionally(cause)
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
            }
            .whenComplete { _, failure ->
                if (failure != null) result.completeExceptionally(failure)
            }
        return server.observeQuery(method, progress, result)
    }

    private fun contentModified() =
        ResponseErrorException(
            ResponseError(
                ResponseErrorCode.ContentModified,
                "Document changed during analysis",
                null,
            )
        )

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
            logger.info(
                "textDocument/didOpen: {} version={}",
                params.textDocument.uri,
                params.textDocument.version,
            )
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
            val content =
                try {
                    DocumentText(previous.content)
                        .change(changes, server.synchronization.incremental)
                } catch (e: IllegalArgumentException) {
                    logger.warn(
                        "textDocument/didChange: ignoring invalid changes for {}: {}",
                        uri,
                        e.message,
                    )
                    return
                }
            analyse(uri, content, version)
        }
    }

    /** Refresh the changed module, then open source consumers in dependency order. */
    private fun analyse(
        uri: String,
        content: String,
        version: Int,
    ) {
        diagnosticRevision++
        clearResolveReports()
        val compiler = adapter as? XdkAdapter
        val changedGraph = compiler?.updateDocument(uri, content).orEmpty()
        analyseOne(uri, content, version)
        refreshScopes(
            (changedGraph +
                (compiler?.affectedSourceScopes(uri) ?: adapter.affectedAnalysisScopes(uri))) -
                adapter.analysisScope(uri)
        )
    }

    private fun refreshScopes(scopes: Set<String>) {
        scopes
            .mapNotNull { scope ->
                openDocuments.entries.firstOrNull {
                    it.value.scope == scope || adapter.analysisScope(it.key) == scope
                }
            }
            .distinctBy { adapter.analysisScope(it.key) }
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
        val affected = openDocuments.filter { (otherUri, document) ->
            otherUri == uri || document.scope == scope || adapter.analysisScope(otherUri) == scope
        }
        val current =
            affected
                .mapValues { (_, document) ->
                    Document(document.content, document.version, scope, analysis)
                }
                .toMutableMap()
        current[uri] = Document(content, version, scope, analysis)
        openDocuments.putAll(current)
        invalidateQueries(current.keys)
        affected.values
            .map { it.analysis }
            .distinct()
            .filter { it !== analysis }
            .forEach { it.cancel(false) }
        // Publication must not block the compiler worker behind a queued navigation request.
        analysis.whenCompleteAsync { result, failure ->
            synchronized(lifecycle) {
                if (
                    !closed &&
                        current.all { (documentUri, document) ->
                            openDocuments[documentUri] === document
                        }
                ) {
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
                                        message =
                                            "Ecstasy analysis failed; see the language server log for details",
                                        code = "ANALYSIS-FAILED",
                                        source = "xtc",
                                    )
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
                    it.location.uri == uri ||
                        (uri == result.uri && it.location.uri !in result.documentUris)
                }
            diagnosticReports.record(uri, diagnostics)
            server.publishDiagnostics(uri, diagnostics, openDocuments[uri]?.version)
        }
        server.refreshDiagnostics()
        server.refreshSemanticTokens()
        // Root discovery can change after file creation/removal; release publications of old
        // scopes.
        val inactive =
            publishedByScope.keys.filter { key -> openDocuments.values.none { it.scope == key } }
        inactive.forEach { key ->
            clearUnowned(publishedByScope.remove(key).orEmpty() - result.documentUris)
        }
    }

    private fun clearUnowned(uris: Set<String>) {
        uris
            .filter { uri -> publishedByScope.values.none { uri in it } }
            .forEach {
                diagnosticReports.record(it, emptyList())
                server.publishDiagnostics(it, emptyList(), openDocuments[it]?.version)
            }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        synchronized(lifecycle) {
            val uri = params.textDocument.uri
            logger.info("textDocument/didClose: {}", uri)
            diagnosticRevision++
            clearResolveReports()
            val affected = adapter.affectedAnalysisScopes(uri)
            semanticTokenReports.retire(uri)
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
            diagnosticReports.record(uri, emptyList())
            server.publishDiagnostics(uri, emptyList(), document?.version)
            server.refreshDiagnostics()
            server.refreshSemanticTokens()
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
            val affected =
                (adapter as? XdkAdapter)?.changedFileScopes(uri)
                    ?: adapter.affectedAnalysisScopes(uri)
            if (affected.isEmpty()) return
            diagnosticRevision++
            clearResolveReports()
            server.refreshDiagnostics()
            server.refreshSemanticTokens()
            refreshScopes(affected + publishedByScope.filterValues { uri in it }.keys)
        }
    }

    /** Dependency replacement and versioned reanalysis share the diagnostic publication lock. */
    internal fun refreshDependencies(replace: () -> Set<String>) {
        synchronized(lifecycle) {
            if (closed) return
            val affected = replace()
            if (affected.isEmpty()) return
            diagnosticRevision++
            clearResolveReports()
            refreshScopes(affected)
            server.refreshDiagnostics()
            server.refreshSemanticTokens()
        }
    }

    fun close() {
        synchronized(lifecycle) {
            closed = true
            val documents = openDocuments.toMap()
            openDocuments.clear()
            invalidateQueries(pendingQueries.values.toSet())
            publishedByScope.clear()
            diagnosticReports.clear()
            semanticTokenReports.clear()
            clearResolveReports()
            documents.forEach { (uri, document) ->
                document.analysis.cancel(false)
                adapter.closeDocument(uri)
            }
        }
    }

    /** The pre-save notification is observational; only didSave refreshes filesystem inputs. */
    override fun willSave(params: WillSaveTextDocumentParams) {
        logger.info("textDocument/willSave: {} reason={}", params.textDocument.uri, params.reason)
    }

    // Saving does not wait for compilation. The formatter uses the captured source and the same
    // version guard as formatting requests; returned edits are applied only by the client.
    override fun willSaveWaitUntil(
        params: WillSaveTextDocumentParams
    ): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/willSaveWaitUntil",
            params.textDocument.uri,
            { "${it.size} edits" },
            uri = params.textDocument.uri,
            sourceOnly = true,
        ) {
            requireResolve(server.synchronization.waitUntil, "Save edits")
            if (!server.synchronization.formatOnSave) return@supplyAsync emptyList()
            val uri = params.textDocument.uri
            val content = openDocuments[uri]?.content ?: return@supplyAsync emptyList()
            val config = server.editorFormattingConfig ?: FormattingConfig.DEFAULT
            adapter
                .formatDocument(
                    uri,
                    content,
                    AdapterFormattingOptions(
                        tabSize = config.indentSize,
                        insertSpaces = config.insertSpaces,
                    ),
                )
                .map { TextEdit(it.range.toLsp(), it.newText) }
        }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("textDocument/didSave: {}", params.textDocument.uri)
        refreshForFile(params.textDocument.uri)
    }

    override fun diagnostic(
        params: DocumentDiagnosticParams
    ): CompletableFuture<DocumentDiagnosticReport> =
        pullDiagnostics(
            "textDocument/diagnostic",
            params.textDocument.uri,
            params.identifier,
            workspace = false,
            progress = params,
        ) { results ->
            val current = diagnosticReports.record(results)
            // Unknown or removed documents have an empty report, never an old cached error.
            if (!diagnosticReports.includes(current, params.textDocument.uri))
                diagnosticReports.record(params.textDocument.uri, emptyList())
            diagnosticReports.document(
                params.textDocument.uri,
                params.previousResultId,
                if (server.supportsRelatedDiagnostics) current else emptySet(),
            )
        }

    internal fun workspaceDiagnostics(
        params: WorkspaceDiagnosticParams
    ): CompletableFuture<WorkspaceDiagnosticReport> =
        pullDiagnostics(
            "workspace/diagnostic",
            "",
            params.identifier,
            workspace = true,
            progress = params,
        ) { results ->
            val current = diagnosticReports.record(results)
            diagnosticReports.workspace(
                current,
                params.previousResultIds.orEmpty().associate { it.uri to it.value },
                openDocuments.mapValues { it.value.version },
            )
        }

    private fun <T> pullDiagnostics(
        method: String,
        uri: String,
        identifier: String?,
        workspace: Boolean,
        progress: WorkDoneProgressParams,
        convert: (List<CompilationResult>) -> T,
    ): CompletableFuture<T> {
        if (!server.usesPullDiagnostics || (identifier != null && identifier != "xtc")) {
            return CompletableFuture.failedFuture(
                ResponseErrorException(
                    ResponseError(
                        ResponseErrorCode.InvalidParams,
                        "Compiler pull diagnostics were not negotiated for this provider",
                        null,
                    )
                )
            )
        }
        val revision = synchronized(lifecycle) { diagnosticRevision }
        return queryAsync(
            method,
            uri,
            request = {
                val open = openDocuments[uri]
                if (!workspace && open != null)
                    CompletableFuture.completedFuture(listOf(open.analysis.join()))
                else (adapter as XdkAdapter).workspaceDiagnosticsAsync()
            },
            progress = progress,
            convert = { results ->
                if (revision != diagnosticRevision) throw contentModified()
                val owned = results.flatMap { it.documentUris }.toSet()
                // A closed member can belong to an open standalone module, outside the
                // configured graph. Preserve that module's current analysis for document pulls
                // too, without including unrelated standalone modules in the report.
                val all =
                    results +
                        openDocuments
                            .filterKeys { !diagnosticReports.includes(owned, it) }
                            .values
                            .map { it.analysis.join() }
                            .filter {
                                workspace || diagnosticReports.includes(it.documentUris, uri)
                            }
                convert(all)
            },
            workspace = true,
        )
    }

    /**
     * LSP: textDocument/hover
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.hover
     */
    override fun hover(params: HoverParams): CompletableFuture<Hover?> =
        supplyAsync(
            "textDocument/hover",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result == null) "no result" else "found symbol" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .getHoverInfo(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )
                ?.let {
                    Hover().apply {
                        contents = Either.forRight(server.presentation.hover(it))
                    }
                }
        }

    /**
     * LSP: textDocument/completion
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.completion
     */
    override fun completion(
        params: CompletionParams
    ): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
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
            progress = params,
        ) { completions ->
            val items = completions.map { c ->
                CompletionItem(c.label).apply {
                    kind = toCompletionItemKind(c.kind)
                    detail = c.detail
                    insertText = c.insertText
                    val handle =
                        c.documentation
                            ?.takeIf { server.resolvesCompletionDocumentation }
                            ?.let {
                                completionReports.remember(
                                    diagnosticRevision,
                                    c.label,
                                    it,
                                    it.length,
                                )
                            }
                    if (handle == null) documentation = c.documentation?.let { Either.forLeft(it) }
                    else data = handle
                    sortText = c.sortText
                    textEdit =
                        c.textEdit?.let { Either.forLeft(TextEdit(it.range.toLsp(), it.newText)) }
                }
            }
            Either.forLeft(items)
        }

    override fun resolveCompletionItem(item: CompletionItem): CompletableFuture<CompletionItem> =
        supplyAsync("completionItem/resolve", item.label) {
            if (!server.resolvesCompletionDocumentation)
                throw ResponseErrorException(
                    ResponseError(
                        ResponseErrorCode.MethodNotFound,
                        "Completion resolution was not negotiated",
                        null,
                    )
                )
            if (item.data != null) {
                val documentation =
                    completionReports.resolve(item.data, diagnosticRevision, item.label)
                if (item.documentation == null) item.documentation = Either.forLeft(documentation)
            }
            item
        }

    private fun toCompletionItemKind(
        kind: AdapterCompletionItem.CompletionKind
    ): CompletionItemKind =
        when (kind) {
            AdapterCompletionItem.CompletionKind.CLASS -> CompletionItemKind.Class
            AdapterCompletionItem.CompletionKind.INTERFACE -> CompletionItemKind.Interface
            AdapterCompletionItem.CompletionKind.METHOD -> CompletionItemKind.Method
            AdapterCompletionItem.CompletionKind.PROPERTY -> CompletionItemKind.Property
            AdapterCompletionItem.CompletionKind.VARIABLE -> CompletionItemKind.Variable
            AdapterCompletionItem.CompletionKind.KEYWORD -> CompletionItemKind.Keyword
            AdapterCompletionItem.CompletionKind.MODULE -> CompletionItemKind.Module
            AdapterCompletionItem.CompletionKind.VALUE -> CompletionItemKind.Value
        }

    /**
     * LSP: textDocument/definition
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.definition
     */
    override fun definition(
        params: DefinitionParams
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
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
            adapter
                .findDefinition(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )
                ?.let {
                    Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
                } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/references
     *
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
            progress = params,
        ) { references ->
            references.map { it.toLsp() }
        }

    /**
     * LSP: textDocument/documentSymbol
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentSymbol
     */
    override fun documentSymbol(
        params: DocumentSymbolParams
    ): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        supplyAsync(
            "textDocument/documentSymbol",
            params.textDocument.uri,
            { result -> "${result.size} symbols" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val result = adapter.getCachedResult(uri) ?: return@supplyAsync emptyList()

            if (server.presentation.hierarchicalSymbols)
                result.symbols.map { Either.forRight(toDocumentSymbol(it)) }
            else flatSymbols(result.symbols).map { Either.forLeft(it) }
        }

    private fun flatSymbols(
        symbols: List<SymbolInfo>,
        container: String? = null,
    ): List<SymbolInformation> = symbols.flatMap { symbol ->
        listOf(
            SymbolInformation(
                symbol.name,
                server.presentation.symbolKind(symbol.kind.toLsp()),
                symbol.location.toLsp(),
                container,
            )
        ) + flatSymbols(symbol.children, symbol.name)
    }

    private fun toDocumentSymbol(symbol: SymbolInfo): DocumentSymbol =
        DocumentSymbol().apply {
            name = symbol.name
            kind = server.presentation.symbolKind(symbol.kind.toLsp())
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentHighlight
     */
    override fun documentHighlight(
        params: DocumentHighlightParams
    ): CompletableFuture<List<DocumentHighlight>> =
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
                )
                .map { h ->
                    DocumentHighlight().apply {
                        range = h.range.toLsp()
                        kind = h.kind.toLsp()
                    }
                }
        }

    /**
     * LSP: textDocument/selectionRange
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.selectionRange
     */
    override fun selectionRange(
        params: SelectionRangeParams
    ): CompletableFuture<List<SelectionRange>> =
        supplyAsync(
            "textDocument/selectionRange",
            "${params.textDocument.uri} positions=${params.positions.map { it.fmt() }}",
            { result -> "${result.size} ranges" },
            uri = params.textDocument.uri,
        ) {
            val adapterPositions = params.positions.map { AdapterPosition(it.line, it.character) }
            adapter.getSelectionRanges(params.textDocument.uri, adapterPositions).map {
                toLspSelectionRange(it)
            }
        }

    private fun toLspSelectionRange(
        range: AdapterSelectionRange
    ): org.eclipse.lsp4j.SelectionRange =
        org.eclipse.lsp4j.SelectionRange().apply {
            this.range = range.range.toLsp()
            this.parent = range.parent?.let { toLspSelectionRange(it) }
        }

    /**
     * LSP: textDocument/foldingRange
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.foldingRange
     */
    override fun foldingRange(
        params: FoldingRangeRequestParams
    ): CompletableFuture<List<FoldingRange>> =
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
     *
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
                    val handle =
                        if (server.resolvesDocumentLinkTarget)
                            linkReports.remember(
                                diagnosticRevision,
                                range.fmt(),
                                l,
                                (l.target?.length ?: 0) + (l.tooltip?.length ?: 0),
                            )
                        else null
                    if (handle == null) {
                        target = l.target
                        tooltip = l.tooltip
                    } else data = handle
                }
            }
        }

    /**
     * LSP: textDocument/signatureHelp
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.signatureHelp
     */
    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        queryAsync(
            "textDocument/signatureHelp",
            params.textDocument.uri,
            {
                adapter.getSignatureHelpAsync(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )
            },
            progress = params,
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
                                            documentation =
                                                p.documentation?.let { Either.forLeft(it) }
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareRename
     */
    override fun prepareRename(
        params: PrepareRenameParams
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> =
        supplyAsync(
            "textDocument/prepareRename",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { _ -> "valid" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .prepareRename(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )
                ?.let { result ->
                    Either3.forSecond<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>(
                        PrepareRenameResult().apply {
                            range = result.range.toLsp()
                            placeholder = result.placeholder
                        }
                    )
                }
                ?: throw ResponseErrorException(
                    ResponseError(
                        ResponseErrorCode.InvalidParams,
                        "Rename not allowed at this position",
                        null,
                    )
                )
        }

    /**
     * LSP: textDocument/rename
     *
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
            progress = params,
        ) { edit ->
            edit?.let(::protocolEdit)
        }

    /**
     * Same lifecycle/version checks as standard Rename; settings are never installed by a proposal.
     */
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?> {
        val compiler =
            adapter as? XdkAdapter ?: return rename(params).thenApply { it?.let(::RenameProposal) }
        return queryAsync(
            "xtc/rename",
            params.textDocument.uri,
            {
                compiler.renameProposalAsync(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.newName,
                )
            },
            workspace = true,
        ) { proposal ->
            proposal?.let {
                protocolEdit(it.edit)?.let { edit ->
                    RenameProposal(
                        edit,
                        it.sourceModules?.let { modules ->
                            SourceGraphReplacement(
                                requireNotNull(it.previousSourceModules)
                                    .map(::SourceModuleConfiguration),
                                modules.map(::SourceModuleConfiguration),
                            )
                        },
                        it.scope?.let { scope ->
                            RenameScope(
                                scope.boundary.name,
                                scope.modules.map(::SourceModuleConfiguration),
                                scope.sourceUris,
                                scope.revision,
                            )
                        },
                    )
                }
            }
        }
    }

    internal fun renameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit?> {
        val compiler = adapter as? XdkAdapter ?: return CompletableFuture.completedFuture(null)
        val first = params.files.firstOrNull() ?: return CompletableFuture.completedFuture(null)
        if (
            !server.supportsVersionedEdits ||
                params.files.map { it.oldUri }.distinct().size != params.files.size
        )
            return CompletableFuture.completedFuture(null)
        return queryAsync(
            "workspace/willRenameFiles",
            first.oldUri,
            { compiler.renameFilesAsync(params.files.associate { it.oldUri to it.newUri }) },
            workspace = true,
        ) {
            it?.let(::protocolEdit)
        }
    }

    private fun canConvertEdit(edit: AdapterWorkspaceEdit): Boolean =
        (!edit.versioned || server.supportsVersionedEdits) &&
            (edit.renames.isEmpty() || server.supportsFileRenames)

    /** Called while the document lifecycle is locked, after the query's version checks. */
    private fun protocolEdit(edit: AdapterWorkspaceEdit): WorkspaceEdit? {
        if (!canConvertEdit(edit)) return null
        val changes =
            edit.changes.mapValues { (_, edits) ->
                edits.map { TextEdit(it.range.toLsp(), it.newText) }
            }
        return WorkspaceEdit().apply {
            if (edit.versioned) {
                this.changes = null
                documentChanges =
                    changes.map { (uri, edits) ->
                        Either.forLeft<TextDocumentEdit, ResourceOperation>(
                            TextDocumentEdit(
                                VersionedTextDocumentIdentifier(uri, openDocuments[uri]?.version),
                                edits.map { Either.forLeft(it) },
                            )
                        )
                    } +
                        edit.renames.map { (from, to) ->
                            Either.forRight<TextDocumentEdit, ResourceOperation>(
                                RenameFile(from, to, RenameFileOptions(false, false))
                            )
                        }
            } else {
                this.changes = changes
            }
        }
    }

    override fun codeAction(
        params: CodeActionParams
    ): CompletableFuture<List<Either<Command, CodeAction>>> =
        queryAsync(
            "textDocument/codeAction",
            params.textDocument.uri,
            {
                adapter.getCodeActionsAsync(
                    params.textDocument.uri,
                    toAdapterRange(params.range),
                    params.context.diagnostics.orEmpty().map {
                        Diagnostic.fromLsp(params.textDocument.uri, it)
                    },
                )
            },
            workspace = true,
            progress = params,
        ) { actions ->
            actions.mapNotNull { action ->
                val kind = action.kind.toLsp()
                if (params.context.only?.none { kind == it || kind.startsWith("$it.") } == true)
                    return@mapNotNull null
                val edit = action.edit
                if (edit != null && !canConvertEdit(edit)) return@mapNotNull null
                val handle =
                    edit
                        ?.takeIf { server.resolvesCodeActionEdit }
                        ?.let {
                            actionReports.remember(
                                diagnosticRevision,
                                action.title,
                                it,
                                it.changes.values.sumOf { edits ->
                                    edits.sumOf { change -> change.newText.length + 64 }
                                },
                            )
                        }
                val proposed = if (handle == null) edit?.let(::protocolEdit) else null
                Either.forRight<Command, CodeAction>(
                    CodeAction().apply {
                        title = action.title
                        this.kind = action.kind.toLsp()
                        isPreferred = action.isPreferred
                        this.edit = proposed
                        data = handle
                    }
                )
            }
        }

    override fun resolveCodeAction(action: CodeAction): CompletableFuture<CodeAction> =
        supplyAsync("codeAction/resolve", action.title) {
            if (!server.resolvesCodeActionEdit)
                throw ResponseErrorException(
                    ResponseError(
                        ResponseErrorCode.MethodNotFound,
                        "Code action resolution was not negotiated",
                        null,
                    )
                )
            if (action.data != null) {
                val edit = actionReports.resolve(action.data, diagnosticRevision, action.title)
                if (action.edit == null) action.edit = protocolEdit(edit) ?: throw contentModified()
            }
            action
        }

    /**
     * LSP: textDocument/semanticTokens/full
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.semanticTokensFull
     */
    override fun semanticTokensFull(
        params: SemanticTokensParams
    ): CompletableFuture<SemanticTokens?> =
        supplyAsync(
            "textDocument/semanticTokens/full",
            params.textDocument.uri,
            { result ->
                if (result == null) "no tokens"
                else "${result.data.size} items (${result.data.size / 5} tokens)"
            },
            uri = params.textDocument.uri,
        ) {
            adapter.getSemanticTokens(params.textDocument.uri)?.let { tokens ->
                semanticTokenReports.full(params.textDocument.uri, tokens.data)
            }
        }

    override fun semanticTokensFullDelta(
        params: SemanticTokensDeltaParams
    ): CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>?> =
        supplyAsync(
            "textDocument/semanticTokens/full/delta",
            params.textDocument.uri,
            uri = params.textDocument.uri,
        ) {
            server.requireSemanticTokenRequest(delta = true)
            semanticTokenReports.delta(
                params.textDocument.uri,
                params.previousResultId,
                adapter.getSemanticTokens(params.textDocument.uri)?.data.orEmpty(),
            )
        }

    override fun semanticTokensRange(
        params: SemanticTokensRangeParams
    ): CompletableFuture<SemanticTokens?> =
        supplyAsync(
            "textDocument/semanticTokens/range",
            params.textDocument.uri,
            uri = params.textDocument.uri,
        ) {
            server.requireSemanticTokenRequest(delta = false)
            SemanticTokenReports.range(
                adapter.getSemanticTokens(params.textDocument.uri)?.data.orEmpty(),
                params.range,
            )
        }

    /**
     * LSP: textDocument/inlayHint
     *
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
                    val handle =
                        h.tooltip
                            ?.takeIf { server.resolvesInlayHintTooltip }
                            ?.let {
                                hintReports.remember(
                                    diagnosticRevision,
                                    hintKey(this),
                                    it,
                                    it.length,
                                )
                            }
                    if (handle == null)
                        tooltip =
                            h.tooltip?.let {
                                Either.forRight(MarkupContent(MarkupKind.MARKDOWN, it))
                            }
                    else data = handle
                }
            }
        }

    /**
     * LSP: textDocument/formatting
     *
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.rangeFormatting
     */
    override fun rangeFormatting(
        params: DocumentRangeFormattingParams
    ): CompletableFuture<List<TextEdit>> =
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

    override fun rangesFormatting(
        params: DocumentRangesFormattingParams
    ): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/rangesFormatting",
            params.textDocument.uri,
            { "${it.size} edits" },
            uri = params.textDocument.uri,
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri]?.content ?: return@supplyAsync emptyList()
            val text = DocumentText(content)
            try {
                params.ranges.forEach { text.bounds(it) }
                val options = toAdapterFormattingOptions(params.options)
                text.nonOverlapping(
                    params.ranges.flatMap { range ->
                        adapter.formatRange(uri, content, toAdapterRange(range), options).map {
                            TextEdit(it.range.toLsp(), it.newText)
                        }
                    }
                )
            } catch (e: IllegalArgumentException) {
                throw ResponseErrorException(
                    ResponseError(ResponseErrorCode.InvalidParams, e.message, null)
                )
            }
        }

    /**
     * LSP: textDocument/declaration
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.declaration
     */
    override fun declaration(
        params: DeclarationParams
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/declaration",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
            uri = params.textDocument.uri,
        ) {
            Either.forLeft(
                adapter
                    .findDeclarations(
                        params.textDocument.uri,
                        params.position.line,
                        params.position.character,
                    )
                    .map { it.toLsp() }
            )
        }

    /**
     * LSP: textDocument/typeDefinition
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeDefinition
     */
    override fun typeDefinition(
        params: TypeDefinitionParams
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/typeDefinition",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
            uri = params.textDocument.uri,
        ) {
            Either.forLeft(
                adapter
                    .findTypeDefinitions(
                        params.textDocument.uri,
                        params.position.line,
                        params.position.character,
                    )
                    .map { it.toLsp() }
            )
        }

    /**
     * LSP: textDocument/implementation
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.implementation
     */
    override fun implementation(
        params: ImplementationParams
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/implementation",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.left.size} locations" },
            uri = params.textDocument.uri,
        ) {
            Either.forLeft(
                adapter
                    .findImplementation(
                        params.textDocument.uri,
                        params.position.line,
                        params.position.character,
                    )
                    .map { it.toLsp() }
            )
        }

    /**
     * LSP: typeHierarchy/prepareTypeHierarchy
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareTypeHierarchy
     */
    override fun prepareTypeHierarchy(
        params: TypeHierarchyPrepareParams
    ): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/prepare",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} items" },
            uri = params.textDocument.uri,
        ) {
            adapter
                .prepareTypeHierarchy(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )
                .map {
                    it.toLsp(params.textDocument.uri)
                }
        }

    /**
     * LSP: typeHierarchy/supertypes
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySupertypes
     */
    override fun typeHierarchySupertypes(
        params: TypeHierarchySupertypesParams
    ): CompletableFuture<List<TypeHierarchyItem>> =
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySubtypes
     */
    override fun typeHierarchySubtypes(
        params: TypeHierarchySubtypesParams
    ): CompletableFuture<List<TypeHierarchyItem>> =
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareCallHierarchy
     */
    override fun prepareCallHierarchy(
        params: CallHierarchyPrepareParams
    ): CompletableFuture<List<CallHierarchyItem>> =
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
                )
                .map { it.toLspCallItem() }
        }

    /**
     * LSP: callHierarchy/incomingCalls
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyIncomingCalls
     */
    override fun callHierarchyIncomingCalls(
        params: CallHierarchyIncomingCallsParams
    ): CompletableFuture<List<CallHierarchyIncomingCall>> =
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
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyOutgoingCalls
     */
    override fun callHierarchyOutgoingCalls(
        params: CallHierarchyOutgoingCallsParams
    ): CompletableFuture<List<CallHierarchyOutgoingCall>> =
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
     *
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
                        val handle =
                            if (server.resolvesCodeLensCommand)
                                lensReports.remember(
                                    diagnosticRevision,
                                    range.fmt(),
                                    cmd.copy(arguments = cmd.arguments.toList()),
                                    cmd.title.length +
                                        cmd.command.length +
                                        cmd.arguments.sumOf { it.toString().length },
                                )
                            else null
                        if (handle == null)
                            command = Command(cmd.title, cmd.command, cmd.arguments.toList())
                        else data = handle
                    }
                }
            }
        }

    override fun resolveCodeLens(lens: CodeLens): CompletableFuture<CodeLens> =
        supplyAsync("codeLens/resolve", lens.range.fmt()) {
            requireResolve(server.resolvesCodeLensCommand, "Code lens")
            if (lens.data != null) {
                val command = lensReports.resolve(lens.data, diagnosticRevision, lens.range.fmt())
                lens.command = Command(command.title, command.command, command.arguments.toList())
            }
            lens
        }

    override fun documentLinkResolve(link: DocumentLink): CompletableFuture<DocumentLink> =
        supplyAsync("documentLink/resolve", link.range.fmt()) {
            requireResolve(server.resolvesDocumentLinkTarget, "Document link")
            if (link.data != null) {
                val resolved = linkReports.resolve(link.data, diagnosticRevision, link.range.fmt())
                link.target = resolved.target
                link.tooltip = resolved.tooltip
            }
            link
        }

    private fun hintKey(hint: InlayHint) = "${hint.position.fmt()}:${hint.label}"

    override fun resolveInlayHint(hint: InlayHint): CompletableFuture<InlayHint> =
        supplyAsync("inlayHint/resolve", hint.position.fmt()) {
            requireResolve(server.resolvesInlayHintTooltip, "Inlay hint")
            if (hint.data != null)
                hint.tooltip =
                    Either.forRight(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            hintReports.resolve(hint.data, diagnosticRevision, hintKey(hint)),
                        )
                    )
            hint
        }

    internal fun workspaceSymbols(
        params: WorkspaceSymbolParams
    ): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> =
        supplyAsync("workspace/symbol", params.query) {
            val symbols = adapter.findWorkspaceSymbols(params.query)
            if (!server.resolvesWorkspaceSymbolRange)
                return@supplyAsync Either.forLeft(
                    symbols.map {
                        SymbolInformation(
                            it.name,
                            server.presentation.symbolKind(it.kind.toLsp(), workspace = true),
                            it.location.toLsp(),
                        )
                    }
                )
            Either.forRight(
                symbols.map { symbol ->
                    WorkspaceSymbol().apply {
                        name = symbol.name
                        kind = server.presentation.symbolKind(symbol.kind.toLsp(), workspace = true)
                        val handle =
                            if (server.resolvesWorkspaceSymbolRange)
                                symbolReports.remember(
                                    diagnosticRevision,
                                    "$name:$kind",
                                    symbol.location,
                                    symbol.location.uri.length + 64,
                                )
                            else null
                        if (handle == null) location = Either.forLeft(symbol.location.toLsp())
                        else {
                            location = Either.forRight(WorkspaceSymbolLocation(symbol.location.uri))
                            data = handle
                        }
                    }
                }
            )
        }

    internal fun resolveWorkspaceSymbol(
        symbol: WorkspaceSymbol
    ): CompletableFuture<WorkspaceSymbol> =
        supplyAsync("workspaceSymbol/resolve", symbol.name) {
            requireResolve(server.resolvesWorkspaceSymbolRange, "Workspace symbol")
            if (symbol.data != null)
                symbol.location =
                    Either.forLeft(
                        symbolReports
                            .resolve(
                                symbol.data,
                                diagnosticRevision,
                                "${symbol.name}:${symbol.kind}",
                            )
                            .toLsp()
                    )
            symbol
        }

    /**
     * LSP: textDocument/onTypeFormatting
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.onTypeFormatting
     */
    override fun onTypeFormatting(
        params: DocumentOnTypeFormattingParams
    ): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/onTypeFormatting",
            "${params.textDocument.uri} at ${params.position.fmt()} ch='${params.ch}'",
            { result ->
                if (result.isEmpty()) {
                    "0 edits"
                } else {
                    val preview =
                        result.take(2).joinToString("; ") { edit ->
                            val text = edit.newText.replace("\n", "\\n").replace("\t", "\\t")
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
                )
                .map { e ->
                    TextEdit().apply {
                        range = e.range.toLsp()
                        newText = e.newText
                    }
                }
        }

    /**
     * LSP: textDocument/linkedEditingRange
     *
     * @see org.eclipse.lsp4j.services.TextDocumentService.linkedEditingRange
     */
    override fun linkedEditingRange(
        params: LinkedEditingRangeParams
    ): CompletableFuture<LinkedEditingRanges> =
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
                )
                ?.let { result ->
                    LinkedEditingRanges().apply {
                        ranges = result.ranges.map { it.toLsp() }
                        wordPattern = result.wordPattern
                    }
                } ?: LinkedEditingRanges()
        }

    // ====================================================================
    // Conversion helpers for hierarchy types
    // ====================================================================

    private fun AdapterTypeHierarchyItem.toLsp(
        defaultUri: String
    ): org.eclipse.lsp4j.TypeHierarchyItem {
        val resolvedUri = this.uri.ifEmpty { defaultUri }
        return org.eclipse.lsp4j
            .TypeHierarchyItem(
                this.name,
                this.kind.toLsp(),
                resolvedUri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
            )
            .apply {
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
