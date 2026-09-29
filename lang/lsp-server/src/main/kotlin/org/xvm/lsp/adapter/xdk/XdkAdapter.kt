package org.xvm.lsp.adapter.xdk

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.nanoseconds
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.CallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeLens
import org.xvm.lsp.adapter.CodeLensCommand
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.DocumentLink
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions
import org.xvm.lsp.adapter.InlayHint
import org.xvm.lsp.adapter.LinkedEditingRanges
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.PrepareRenameResult
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.adapter.mapCancellable
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.tool.ModuleInfo

/**
 * Compiler diagnostics and semantic navigation for module source trees.
 *
 * A single worker serializes compilations sharing the embedding repository. Each module request
 * captures source membership and text, with open editor buffers taking precedence over disk. A
 * member edit replaces queued module work and cooperatively cancels the previous attempt. Only the
 * current attempt can install its document views, together as one module snapshot.
 *
 * Copied semantic facts support cross-file navigation and declared type hierarchy within a module.
 * Per-source AST roots supply outlines, folding and selection. The server supplies document
 * versions and publishes diagnostics after checking that the whole module request is still current.
 * The complete matching XDK library set is bundled; compilation does not start an interpreter.
 */
class XdkAdapter
internal constructor(
    compileSource: (Source, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    compileTree: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    analyzeCursor:
        (
            Source,
            ModuleInfo?,
            Long,
            ModuleRepository?,
            ErrorListener,
        ) -> EmbeddingSupport.PartialAnalysis,
) : AbstractAdapter() {
    private val compiler = CompilerCalls(compileSource, compileTree, analyzeCursor)

    internal constructor(
        compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation,
        compileTree: (ModuleInfo, ErrorListener) -> EmbeddingSupport.Compilation,
        analyzeCursor:
            (Source, ModuleInfo?, Long, ErrorListener) -> EmbeddingSupport.PartialAnalysis =
            { source, sources, cursor, errors ->
                analyzeIncomplete(source, sources, cursor, null, errors)
            },
    ) : this(
        { source, _, errors -> compileSource(source, errors) },
        { sources, _, errors -> compileTree(sources, errors) },
        { source, sources, cursor, _, errors -> analyzeCursor(source, sources, cursor, errors) },
    )

    internal constructor(
        compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation
    ) : this(
        compileSource,
        { sources, errs ->
            XdkLibraries.configure()
            EmbeddingSupport.instance().compileModule(sources, null, errs)
        },
    )

    constructor() :
        this(
            { source, repository, errors ->
                XdkLibraries.configure()
                EmbeddingSupport.instance().compileModule(source, repository, errors)
            },
            { sources, repository, errors ->
                XdkLibraries.configure()
                EmbeddingSupport.instance().compileModule(sources, repository, errors)
            },
            ::analyzeIncomplete,
        )

    override val displayName: String = "XDK"

    override val capabilities: Set<AdapterCapability> =
        setOf(
            AdapterCapability.COMPLETION,
            AdapterCapability.HOVER,
            AdapterCapability.DEFINITION,
            AdapterCapability.DECLARATION,
            AdapterCapability.REFERENCES,
            AdapterCapability.DOCUMENT_SYMBOL,
            AdapterCapability.DOCUMENT_HIGHLIGHT,
            AdapterCapability.SELECTION_RANGE,
            AdapterCapability.FOLDING_RANGE,
            AdapterCapability.WORKSPACE_SYMBOL,
            AdapterCapability.TYPE_HIERARCHY,
            AdapterCapability.TYPE_DEFINITION,
            AdapterCapability.IMPLEMENTATION,
            AdapterCapability.CALL_HIERARCHY,
            AdapterCapability.SIGNATURE_HELP,
            AdapterCapability.SEMANTIC_TOKENS,
            AdapterCapability.INLAY_HINT,
            AdapterCapability.RENAME,
            AdapterCapability.CODE_ACTION,
            AdapterCapability.FORMATTING,
            AdapterCapability.RANGE_FORMATTING,
            AdapterCapability.ON_TYPE_FORMATTING,
            AdapterCapability.DOCUMENT_LINK,
            AdapterCapability.CODE_LENS,
            AdapterCapability.LINKED_EDITING,
        )

    private data class Discovery(
        val folders: List<File> = emptyList(),
        val explicit: Boolean = false,
        val buildModel: Boolean = false,
        val problem: String? = null,
        val catalog: XdkWorkspaceDiscovery.Catalog = XdkWorkspaceDiscovery.Catalog(),
    )

    private val discovery = AtomicReference(Discovery())

    override fun initializeWorkspace(
        workspaceFolders: List<String>,
        progressReporter: ((String, Int) -> Unit)?,
    ) {
        discovery.updateAndGet {
            it.copy(folders = workspaceFolders.map { path -> File(path).canonicalFile }.distinct())
        }
        refreshDiscoveredSources()
        progressReporter?.invoke("Compiler source graph discovered", 100)
    }

    /** Returns retired scopes so the server can rebuild current document versions. */
    fun refreshDiscoveredSources(): Set<String> {
        val settings = discovery.get()
        if (settings.explicit) return emptySet()
        val (buffers, previous) = synchronized(lifecycle) { overlays.toMap() to project }
        val catalog =
            settings.catalog.rescan(settings.folders, buffers) {
                closed || discovery.get() !== settings
            }
        return installDiscoveredCatalog(catalog, settings, previous) { overlays == buffers }
    }

    /**
     * Incremental header update; callers refresh these scopes at their current document versions.
     */
    fun updateDocument(
        uri: String,
        content: String,
    ): Set<String> {
        val snapshot =
            synchronized(lifecycle) {
                if (closed || XdkLibrarySources.owns(uri) || overlays[uri] == content)
                    return emptySet()
                overlays[uri] = content
                discovery.get() to project
            }
        return refreshDocumentHeader(uri, content, snapshot.first, snapshot.second)
    }

    private fun refreshDocumentHeader(
        uri: String,
        content: String?,
        settings: Discovery,
        previous: XdkProject,
        closing: Boolean = false,
    ): Set<String> {
        val file = XdkSources.file(uri) ?: return emptySet()
        if (settings.explicit || !XdkWorkspaceDiscovery.includes(settings.folders, file))
            return emptySet()
        val catalog =
            settings.catalog.withSource(file, content) { closed || discovery.get() !== settings }
        return installDiscoveredCatalog(catalog, settings, previous) {
            if (closing) uri !in overlays else overlays[uri] == content
        }
    }

    private fun installDiscoveredCatalog(
        catalog: XdkWorkspaceDiscovery.Catalog,
        settings: Discovery,
        previous: XdkProject,
        current: () -> Boolean,
    ): Set<String> {
        val candidate = runCatching { XdkProject(catalog.modules(previous.modules.values)) }
        val failure = candidate.exceptionOrNull()
        if (failure != null && failure !is IllegalArgumentException) throw failure
        val problem = failure?.message
        return installSourceModules(
            (candidate.getOrNull() ?: previous).modules.values.toList(),
            explicit = false,
            force = settings.problem != problem,
        ) {
            !closed &&
                project === previous &&
                current() &&
                discovery.compareAndSet(
                    settings,
                    settings.copy(catalog = catalog, problem = problem),
                )
        }
    }

    fun changeWorkspaceFolders(
        added: List<String>,
        removed: List<String>,
    ): Set<String> {
        val deleted = removed.mapNotNull(XdkSources::file).toSet()
        discovery.updateAndGet { settings ->
            settings.copy(
                folders =
                    (settings.folders.filterNot { it in deleted } +
                            added.mapNotNull(XdkSources::file))
                        .distinct()
            )
        }
        return refreshDiscoveredSources()
    }

    override fun healthCheck(): Boolean = runCatching { XdkLibraries.configure() }.isSuccess

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult = compileAsync(uri, content).join()

    override fun analysisScope(uri: String): String =
        synchronized(lifecycle) {
            project.scope(uri)?.let {
                return it
            }
            val root = XdkSources.moduleRoot(uri, overlays)
            if (root == XdkSources.file(uri)) {
                scopes[uri] ?: root?.toURI()?.toString() ?: uri
            } else {
                root?.toURI()?.toString() ?: uri
            }
        }

    /** Unsaved source buffers do not change the resource files read from disk. */
    internal fun affectedSourceScopes(uri: String): Set<String> =
        synchronized(lifecycle) { project.affected(analysisScope(uri)) }

    override fun affectedAnalysisScopes(uri: String): Set<String> =
        synchronized(lifecycle) {
            val sources = project.affected(analysisScope(uri))
            val resources =
                project
                    .resourceScopes(uri)
                    .filter { scope ->
                        val root = project.modules.values.single { it.uri == scope }
                        val inputs =
                            completed[scope]?.sourceInputs
                                ?: builds[root.name]?.key?.sources
                                ?: diagnosticCache.snapshot().builds[scope]?.key?.sources
                        requests[scope]?.result?.isDone == false ||
                            inputs == null ||
                            inputs.resources.entries.isNotEmpty()
                    }
                    .flatMapTo(linkedSetOf(), project::affected)
            project.orderedScopes(sources + resources)
        }

    fun resourceWatchRoots(): Set<File> = synchronized(lifecycle) { project.resourceWatchRoots() }

    override fun compileAsync(
        uri: String,
        content: String,
    ): CompletableFuture<CompilationResult> {
        if (XdkLibrarySources.owns(uri))
            return CompletableFuture.completedFuture(CompilationResult.success(uri, emptyList()))
        updateDocument(uri, content)
        val submission =
            synchronized(lifecycle) {
                if (closed)
                    return CompletableFuture.failedFuture(
                        IllegalStateException("XDK adapter is closed")
                    )
                overlays[uri] = content
                val scope = analysisScope(uri)
                scopes[uri] = scope
                val sourceScopes = project.buildOrder(scope).mapTo(mutableSetOf(scope)) { it.uri }
                val request =
                    Request(
                        scope,
                        uri,
                        overlays.filterKeys {
                            analysisScope(it) in sourceScopes
                        },
                        dependencies,
                        project,
                        discovery.get().problem,
                        queueTrace,
                        ::runCompilation,
                    )
                request.result.whenComplete { _, _ ->
                    if (request.result.isCancelled) {
                        val obsolete =
                            synchronized(lifecycle) {
                                removeQueued(request)
                                if (requests.remove(scope, request)) {
                                    completed.remove(scope)
                                    retireQueries(scope)
                                } else {
                                    emptyList()
                                }
                            }
                        obsolete.forEach { it.result.cancel(false) }
                    }
                }
                val (retired, obsoleteQueries) = retireRequests(project.affected(scope))
                requests[scope] = request
                scheduled[request] =
                    debouncer.schedule(
                        {
                            synchronized(lifecycle) {
                                if (scheduled.remove(request) != null && !isStale(request))
                                    compiles.execute(request.task.ready())
                            }
                        },
                        DEBOUNCE_MILLIS,
                        TimeUnit.MILLISECONDS,
                    )
                Submission(request = request, retired = retired, obsoleteQueries = obsoleteQueries)
            }
        submission.retired.forEach { it.result.cancel(false) }
        submission.obsoleteQueries.forEach { it.result.cancel(false) }
        return submission.request.result
    }

    /**
     * Compiler-worker probe for a current open document. Results contain copied facts only; they
     * neither replace normal diagnostics nor install another module analysis. A newer cursor of the
     * same query kind in this document, or any edit in its module, invalidates the request.
     * Protocol consumers must also check their captured document version before publishing facts.
     */
    internal fun analyzeAtAsync(
        uri: String,
        position: Position,
    ): CompletableFuture<PartialSemanticModel?> =
        analyzeAtAsync(CursorKey(uri, CursorKind.PROBE), position)

    private fun analyzeAtAsync(
        key: CursorKey,
        position: Position,
    ): CompletableFuture<PartialSemanticModel?> {
        val uri = key.uri
        val (request, previous) =
            synchronized(lifecycle) {
                if (closed)
                    return CompletableFuture.failedFuture(
                        IllegalStateException("XDK adapter is closed")
                    )
                if (discovery.get().problem != null) return CompletableFuture.completedFuture(null)
                val compilation =
                    requests[analysisScope(uri)] ?: return CompletableFuture.completedFuture(null)
                if (uri !in compilation.overlays) return CompletableFuture.completedFuture(null)
                val request =
                    CursorRequest(compilation, key, position, queueTrace, ::runCursorAnalysis)
                request.result.whenComplete { _, _ ->
                    if (request.result.isCancelled) {
                        synchronized(lifecycle) {
                            cursors.remove(key, request)
                            compiles.remove(request.task)
                        }
                    }
                }
                val previous = cursors.put(key, request)
                previous?.let { compiles.remove(it.task) }
                compiles.execute(request.task.ready())
                request to previous
            }
        previous?.result?.cancel(false)
        return request.result
    }

    private enum class CursorKind {
        PROBE,
        COMPLETION,
        SIGNATURE,
    }

    private data class CursorKey(
        val uri: String,
        val kind: CursorKind,
    )

    private sealed interface QueryWork {
        val result: CompletableFuture<*>
        val task: CompilerQueueTrace.Work
    }

    private sealed interface QueryRequest : QueryWork {
        val compilation: Request
    }

    private enum class ProjectQueryKind {
        REFERENCES,
        RENAME,
        RENAME_PROPOSAL,
        FILE_RENAME,
        SYMBOLS,
        NAVIGATION,
        CODE_ACTIONS,
        DIAGNOSTICS,
    }

    private data class ProjectQueryKey(
        val uri: String,
        val kind: ProjectQueryKind,
        val range: Range? = null,
        val requestId: UUID? = null,
    )

    private class ProjectRequest<T>(
        val key: ProjectQueryKey,
        val project: XdkProject,
        val dependencies: XdkDependencies,
        val overlays: Map<String, String>,
        trace: CompilerQueueTrace,
        work: (ProjectRequest<T>) -> Unit,
    ) : QueryWork {
        override val result = CompletableFuture<T>()
        override val task = trace.task("project-${key.kind}", key.uri, result) { work(this) }
    }

    private class CursorRequest(
        override val compilation: Request,
        val key: CursorKey,
        val position: Position,
        trace: CompilerQueueTrace,
        work: (CursorRequest) -> Unit,
    ) : QueryRequest {
        val uri: String
            get() = key.uri

        override val result = CompletableFuture<PartialSemanticModel?>()
        override val task = trace.task("cursor-${key.kind}", uri, result) { work(this) }
    }

    private class RenameRequest(
        override val compilation: Request,
        val module: ModuleAnalysis,
        val uri: String,
        val position: Position,
        val name: String,
        trace: CompilerQueueTrace,
        work: (RenameRequest) -> Unit,
    ) : QueryRequest {
        override val result = CompletableFuture<WorkspaceEdit?>()
        override val task = trace.task("rename-proof", uri, result) { work(this) }
    }

    /** Called under lifecycle; future callbacks must run after releasing it. */
    private fun retireQueries(scope: String): List<QueryWork> =
        (cursors.values + renames.values)
            .filter { it.compilation.scope == scope }
            .onEach {
                when (it) {
                    is CursorRequest -> cursors.remove(it.key, it)
                    is RenameRequest -> renames.remove(it.uri, it)
                }
                compiles.remove(it.task)
            } + retireProjectQueries()

    /** Every graph query owns a complete configured snapshot, so any source change retires it. */
    private fun retireProjectQueries(): List<QueryWork> =
        projectQueries.values.toList().also { retired ->
            projectQueries.clear()
            navigationCache.set(emptyMap())
            retired.forEach { compiles.remove(it.task) }
        }

    private fun <T> projectQuery(
        key: ProjectQueryKey,
        unavailable: T,
        query: (XdkProjectQueries) -> T,
    ): CompletableFuture<T> {
        val (request, previous) =
            synchronized(lifecycle) {
                if (closed)
                    return CompletableFuture.failedFuture(
                        IllegalStateException("XDK adapter is closed")
                    )
                if (discovery.get().problem != null || project.scope(key.uri) == null)
                    return CompletableFuture.completedFuture(unavailable)
                val request =
                    ProjectRequest(key, project, dependencies, overlays.toMap(), queueTrace) {
                        work: ProjectRequest<T> ->
                        fun stale(): Boolean =
                            projectQueries[key] !== work || work.result.isCancelled
                        try {
                            if (stale()) throw CancellationException()
                            val result =
                                try {
                                    query(
                                        XdkProjectQueries(
                                            work.project,
                                            work.overlays,
                                            work.dependencies,
                                            compiler::compileTree,
                                            ::stale,
                                            navigationCache,
                                            discoverImports = !discovery.get().explicit,
                                            diagnosticCache = diagnosticCache,
                                        )
                                    )
                                } catch (_: IOException) {
                                    unavailable
                                }
                            synchronized(lifecycle) { if (stale()) throw CancellationException() }
                            work.result.complete(result)
                        } catch (_: CancellationException) {
                            work.result.cancel(false)
                        } catch (failure: Exception) {
                            work.result.completeExceptionally(failure)
                        } catch (failure: Error) {
                            work.result.completeExceptionally(failure)
                            throw failure
                        } finally {
                            projectQueries.remove(key, work)
                        }
                    }
                request.result.whenComplete { _, _ ->
                    if (request.result.isCancelled) {
                        synchronized(lifecycle) {
                            projectQueries.remove(key, request)
                            compiles.remove(request.task)
                        }
                    }
                }
                val previous = projectQueries.put(key, request)
                previous?.let { compiles.remove(it.task) }
                compiles.execute(request.task.ready())
                request to previous
            }
        previous?.result?.cancel(false)
        return request.result
    }

    private fun isStale(request: CursorRequest): Boolean =
        cursors[request.key] !== request ||
            request.result.isCancelled ||
            isStale(request.compilation)

    private fun runCursorAnalysis(request: CursorRequest) {
        try {
            if (isStale(request)) throw CancellationException()
            val source = Source(request.compilation.overlays.getValue(request.uri), request.uri)
            val errors = ErrorListener.cancellable(ErrorList()) { isStale(request) }
            val cursor = cursorPosition(source, request.position, errors)
            val facts = cursor?.let {
                val sources = captureSources(request.compilation) { isStale(request) }
                val dependencies =
                    (completed[request.compilation.scope]?.inputs
                            ?: request.compilation.dependencies)
                        .open()
                compiler
                    .analyzeCursor(source, sources, it, dependencies.repository, errors)
                    .semanticSnapshot(errors)
            }
            synchronized(lifecycle) {
                if (isStale(request)) throw CancellationException()
            }
            request.result.complete(facts)
        } catch (_: CancellationException) {
            request.result.cancel(false)
        } catch (e: Exception) {
            request.result.completeExceptionally(e)
        } catch (e: Error) {
            request.result.completeExceptionally(e)
            throw e
        } finally {
            cursors.remove(request.key, request)
        }
    }

    private fun runCompilation(request: Request) {
        try {
            if (isStale(request)) throw CancellationException()
            val started = System.nanoTime()
            val result = compileNow(request)
            synchronized(lifecycle) {
                if (isStale(request)) throw CancellationException()
                completed[request.scope] = result
            }
            logger.info(
                "compile: scope={}, {} document(s), {} diagnostic(s), compiled in {}",
                request.scope,
                result.documents.size,
                result.diagnostics.size,
                (System.nanoTime() - started).nanoseconds,
            )
            // Future callbacks may publish diagnostics; never invoke them under the adapter lock.
            request.result.complete(
                CompilationResult.withDiagnostics(
                    request.uri,
                    result.diagnostics,
                    result.document(request.uri)?.symbols.orEmpty(),
                    result.documentUris,
                )
            )
        } catch (_: CancellationException) {
            request.result.cancel(false)
        } catch (e: Exception) {
            request.result.completeExceptionally(e)
        } catch (e: Error) {
            request.result.completeExceptionally(e)
            throw e
        }
    }

    override fun getCachedResult(uri: String): CompilationResult? =
        analysis(uri)?.let { CompilationResult.withDiagnostics(uri, it.diagnostics, it.symbols) }

    private class Request(
        val scope: String,
        val uri: String,
        val overlays: Map<String, String>,
        val dependencies: XdkDependencies,
        val project: XdkProject,
        val problem: String?,
        trace: CompilerQueueTrace,
        work: (Request) -> Unit,
    ) {
        val result = CompletableFuture<CompilationResult>()
        val task = trace.task("compile", scope, result) { work(this) }
    }

    private data class Submission(
        val request: Request,
        val retired: List<Request>,
        val obsoleteQueries: List<QueryWork>,
    )

    /** Installed atomically, so all member views always belong to the same compilation. */
    private class ModuleAnalysis(
        val documents: Map<String, Analysis>,
        val diagnostics: List<Diagnostic>,
        val dependencies: Set<String>,
        val succeeded: Boolean,
        val dependencySources: Map<String, String>,
        val inputs: XdkDependencies,
        val artifact: XdkDependency? = null,
        val documentUris: Set<String> = documents.keys,
        val sourceInputs: XdkSources.Inputs? = null,
        val sourceTexts: Map<String, String> = emptyMap(),
    ) {
        private val semanticViews =
            documents
                .mapNotNull { (uri, analysis) -> analysis.semantics?.let { uri to it } }
                .toMap()
        val hierarchy = XdkHierarchy(semanticViews)
        val calls = XdkCalls(semanticViews)

        fun document(uri: String): Analysis? =
            documents[uri]
                ?: documents.entries
                    .firstOrNull {
                        XdkSources.file(it.key) != null &&
                            XdkSources.file(it.key) == XdkSources.file(uri)
                    }
                    ?.value

        fun sourceUri(name: String?): String? =
            documents.entries.firstOrNull { it.value.semantics?.sourceName == name }?.key
                ?: dependencySources[name]
                ?: XdkLibrarySources.sourceUri(name)
    }

    private data class Analysis(
        val diagnostics: List<Diagnostic> = emptyList(),
        val symbols: List<SymbolInfo> = emptyList(),
        val ast: AstNode? = null,
        val semantics: SemanticModel? = null,
    )

    private fun module(uri: String): ModuleAnalysis? = completed[analysisScope(uri)]

    private fun analysis(uri: String): Analysis? = module(uri)?.document(uri)

    /** Explicit source graph; installing a new configuration retires all current attempts. */
    fun replaceSourceModules(modules: List<XdkSourceModule>): Set<String> =
        installSourceModules(modules, explicit = true)

    fun discoverSourceModules(): Set<String> {
        discovery.updateAndGet { it.copy(explicit = false) }
        return refreshDiscoveredSources()
    }

    private fun installSourceModules(
        modules: List<XdkSourceModule>,
        explicit: Boolean,
        force: Boolean = false,
        current: () -> Boolean = { true },
    ): Set<String> {
        val replacement = XdkProject(modules)
        val (retired, probes) =
            synchronized(lifecycle) {
                check(!closed) { "XDK adapter is closed" }
                if (!current()) return emptySet()
                val recovered = explicit && discovery.get().problem != null
                val clearBuildArtifacts = discovery.get().buildModel
                discovery.updateAndGet {
                    it.copy(
                        explicit = explicit,
                        buildModel = false,
                        problem = if (explicit) null else it.problem,
                    )
                }
                if (
                    !force &&
                        !recovered &&
                        !clearBuildArtifacts &&
                        project.sameConfiguration(replacement)
                )
                    return emptySet()
                if (clearBuildArtifacts) dependencies = XdkDependencies(emptyList())
                project = replacement
                builds.clear()
                retireRequests(requests.keys.toSet())
            }
        retired.forEach { it.result.cancel(false) }
        probes.forEach { it.result.cancel(false) }
        return replacement.orderedScopes(
            retired.flatMapTo(linkedSetOf()) { replacement.affected(it.scope) + it.scope }
        )
    }

    /** Called under lifecycle. Compiler-owned objects never enter the artifact cache. */
    private fun retireRequests(scopes: Set<String>): Pair<List<Request>, List<QueryWork>> {
        val retired = scopes.mapNotNull { requests.remove(it) }
        scopes.forEach(completed::remove)
        retired.forEach(::removeQueued)
        return retired to (scopes.flatMap(::retireQueries) + retireProjectQueries())
    }

    private fun removeQueued(request: Request) {
        scheduled.remove(request)?.cancel(false)
        compiles.remove(request.task)
    }

    /**
     * Atomically replace host-supplied artifacts and retire affected analyses/cursor probes. The
     * host must reanalyse the returned scopes to publish diagnostics for current document versions;
     * XtcLanguageServer.replaceCompilerDependencies performs that step under its lock. Failed and
     * pending attempts are conservatively retried because their import set is incomplete.
     */
    fun replaceDependencies(artifacts: List<XdkDependency>): Set<String> {
        val replacement = XdkDependencies(artifacts)
        val (retired, probes) =
            synchronized(lifecycle) {
                check(!closed) { "XDK adapter is closed" }
                discovery.updateAndGet { it.copy(buildModel = false) }
                val changed =
                    (dependencies.modules.keys + replacement.modules.keys).filterTo(linkedSetOf()) {
                        dependencies.modules[it]?.revision != replacement.modules[it]?.revision
                    }
                if (changed.isEmpty()) return emptySet()
                dependencies = replacement
                val retired =
                    requests.values.filter {
                        val analysis = completed[it.scope]
                        analysis == null ||
                            !analysis.succeeded ||
                            analysis.dependencies.any(changed::contains)
                    }
                val probes =
                    retired.flatMap { request ->
                        requests.remove(request.scope, request)
                        completed.remove(request.scope)
                        removeQueued(request)
                        retireQueries(request.scope)
                    } + retireProjectQueries()
                retired to probes
            }
        retired.forEach { it.result.cancel(false) }
        probes.forEach { it.result.cancel(false) }
        return synchronized(lifecycle) {
            project.orderedScopes(retired.mapTo(linkedSetOf()) { it.scope })
        }
    }

    /**
     * Install one evaluated build snapshot; validation completes before either live input changes.
     */
    internal fun replaceBuildInputs(inputs: XdkBuildModel.Inputs): Set<String> {
        val replacement = XdkProject(inputs.modules)
        val artifacts = XdkDependencies(inputs.binaries)
        val (retired, probes) =
            synchronized(lifecycle) {
                check(!closed) { "XDK adapter is closed" }
                discovery.updateAndGet {
                    it.copy(explicit = true, buildModel = true, problem = null)
                }
                if (
                    project.sameConfiguration(replacement) &&
                        dependencies.modules.mapValues { it.value.revision } ==
                            artifacts.modules.mapValues { it.value.revision }
                )
                    return emptySet()
                project = replacement
                dependencies = artifacts
                builds.clear()
                retireRequests(requests.keys.toSet())
            }
        retired.forEach { it.result.cancel(false) }
        probes.forEach { it.result.cancel(false) }
        return replacement.orderedScopes(retired.mapTo(linkedSetOf()) { it.scope })
    }

    override fun closeDocument(uri: String) {
        closeDocumentAndRefresh(uri)
    }

    /** Closing restores disk headers and returns exactly the scopes retired by that change. */
    fun closeDocumentAndRefresh(uri: String): Set<String> {
        if (XdkLibrarySources.owns(uri)) return emptySet()
        val (retired, obsoleteQueries) =
            synchronized(lifecycle) {
                val scope = scopes.remove(uri) ?: analysisScope(uri)
                overlays.remove(uri)
                retireRequests(project.affected(scope)).also {
                    val retained =
                        requests.keys
                            .flatMap { project.buildOrder(it).map(XdkSourceModule::name) }
                            .toSet()
                    builds.keys.retainAll(retained)
                }
            }
        retired.forEach { it.result.cancel(false) }
        obsoleteQueries.forEach { it.result.cancel(false) }
        val (settings, previous) = synchronized(lifecycle) { discovery.get() to project }
        val file = XdkSources.file(uri)
        val disk =
            try {
                file
                    ?.takeIf {
                        !settings.explicit &&
                            XdkWorkspaceDiscovery.includes(settings.folders, it) &&
                            it.isFile
                    }
                    ?.readText()
            } catch (_: IOException) {
                null
            }
        return refreshDocumentHeader(uri, disk, settings, previous, closing = true) +
            retired.map { it.scope }
    }

    override fun close() {
        val pending =
            synchronized(lifecycle) {
                closed = true
                val pending =
                    requests.values.map { it.result } +
                        cursors.values.map { it.result } +
                        renames.values.map { it.result } +
                        projectQueries.values.map { it.result }
                requests.clear()
                cursors.clear()
                renames.clear()
                projectQueries.clear()
                navigationCache.set(emptyMap())
                diagnosticCache.clear()
                overlays.clear()
                scopes.clear()
                completed.clear()
                builds.clear()
                scheduled.values.forEach { it.cancel(false) }
                scheduled.clear()
                debouncer.shutdownNow()
                compiles.queue.clear()
                compiles.shutdown()
                pending
            }
        pending.forEach { it.cancel(false) }
        if (!compiles.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) compiles.shutdownNow()
    }

    private fun isStale(request: Request): Boolean =
        requests[request.scope] !== request || request.result.isCancelled

    private fun captureSources(
        request: Request,
        cancelled: () -> Boolean,
    ): XdkSources? =
        XdkSources.file(request.scope)
            ?.takeIf {
                it.isFile ||
                    it != XdkSources.file(request.uri) ||
                    File(it.parentFile, it.nameWithoutExtension).isDirectory
            }
            ?.let { root ->
                val resources =
                    request.project.modules.values.firstOrNull { it.root == root }?.resourceFiles
                XdkSources.capture(root, request.overlays, resources, cancelled)
            }

    private data class BuildKey(
        val sources: XdkSources.Inputs,
        val dependencies: Map<String, String>,
    )

    private data class CachedBuild(
        val key: BuildKey,
        val artifact: XdkDependency?,
        val diagnostics: List<Diagnostic>,
        val documentUris: Set<String>,
    )

    /**
     * Capture the complete dependency closure before compiling any of it. Only artifacts are
     * cached.
     */
    private fun compileNow(request: Request): ModuleAnalysis {
        request.problem?.let {
            return unavailableProjectModule(request.uri, request.dependencies, "SOURCE-GRAPH", it)
        }
        val order = request.project.buildOrder(request.scope)
        if (order.isEmpty()) {
            return compileOne(
                request,
                request.uri,
                captureSources(request) { isStale(request) },
                request.dependencies,
            )
        }
        val captured = order.associateWith { module ->
            try {
                Result.success(
                    XdkSources.capture(module.root, request.overlays, module.resourceFiles) {
                        isStale(request)
                    }
                )
            } catch (failure: IOException) {
                Result.failure(failure)
            }
        }
        // A source-owned module must never fall back to an older host-supplied binary.
        val artifacts =
            request.dependencies.modules
                .filterKeys { it !in request.project.modules }
                .toMutableMap()
        val diagnostics = mutableListOf<Diagnostic>()
        val documents = linkedSetOf<String>()
        val analyses = order.map { module ->
            if (isStale(request)) throw CancellationException()
            val inputs = XdkDependencies(artifacts.values.toList())
            val sources = captured.getValue(module).getOrNull()
            val uri = sources?.uri(module.root) ?: module.uri
            val missing =
                module.dependencies.filter { it !in artifacts && it !in XdkLibraries.moduleNames }
            val key = sources?.let {
                BuildKey(it.inputs, artifacts.mapValues { (_, artifact) -> artifact.revision })
            }
            val cached =
                builds[module.name]?.takeIf {
                    key != null && it.key == key && module.uri != request.scope
                }
            val analysis =
                when {
                    sources == null -> {
                        unavailableProjectModule(
                            uri,
                            inputs,
                            "SOURCE-UNAVAILABLE",
                            "Cannot read source for ${module.name}: ${captured.getValue(module).exceptionOrNull()?.message}",
                        )
                    }

                    missing.isNotEmpty() -> {
                        unavailableProjectModule(
                            uri,
                            inputs,
                            "DEPENDENCY-FAILED",
                            "Dependencies unavailable: ${missing.joinToString()}",
                            sources.documentUris,
                        )
                    }

                    cached != null -> {
                        ModuleAnalysis(
                            documents = emptyMap(),
                            diagnostics = cached.diagnostics,
                            dependencies = emptySet(),
                            succeeded = cached.artifact != null,
                            dependencySources = emptyMap(),
                            inputs = inputs,
                            artifact = cached.artifact,
                            documentUris = cached.documentUris,
                        )
                    }

                    else -> {
                        compileOne(request, uri, sources, inputs, module.name)
                    }
                }
            synchronized(lifecycle) {
                if (isStale(request)) throw CancellationException()
                if (key != null)
                    builds[module.name] =
                        CachedBuild(
                            key,
                            analysis.artifact,
                            analysis.diagnostics,
                            analysis.documentUris,
                        )
            }
            analysis.artifact?.let { artifacts[module.name] = it }
            diagnostics += analysis.diagnostics
            documents += analysis.documentUris
            analysis
        }
        val target = analyses.last()
        return ModuleAnalysis(
            documents = target.documents,
            diagnostics = diagnostics,
            dependencies = target.dependencies + order.map { it.name },
            succeeded = target.succeeded,
            dependencySources = target.dependencySources,
            inputs = target.inputs,
            artifact = target.artifact,
            documentUris = documents,
            sourceInputs = target.sourceInputs,
            sourceTexts = target.sourceTexts,
        )
    }

    private fun unavailableProjectModule(
        uri: String,
        inputs: XdkDependencies,
        code: String,
        message: String,
        documents: Set<String> = setOf(uri),
    ) =
        ModuleAnalysis(
            documents = emptyMap(),
            diagnostics =
                listOf(
                    Diagnostic(
                        Location(uri, 0, 0, 0, 0),
                        Diagnostic.Severity.ERROR,
                        message,
                        code,
                        SOURCE,
                    )
                ),
            dependencies = emptySet(),
            succeeded = false,
            dependencySources = emptyMap(),
            inputs = inputs,
            documentUris = documents,
        )

    /** Compile and copy all source views on the single worker. */
    private fun compileOne(
        request: Request,
        uri: String,
        sources: XdkSources?,
        inputs: XdkDependencies,
        moduleName: String? = null,
    ): ModuleAnalysis {
        val heard = ErrorList()
        val errs = ErrorListener.cancellable(heard) { isStale(request) }
        val source = Source(request.overlays[uri].orEmpty(), uri)
        val dependencies = inputs.open()
        val compilation =
            if (sources == null) {
                compiler.compileSource(source, dependencies.repository, errs)
            } else {
                compiler.compileTree(sources, dependencies.repository, errs)
            }
        if (isStale(request)) throw CancellationException()
        if (sources != null && !sources.resourcesCurrent { isStale(request) })
            throw CancellationException()
        val footprint =
            ExecutionTrace.api("EmbeddingSupport.footprint", request.uri) {
                EmbeddingSupport.instance().footprint(compilation)
            }
        logger.info("compile: scope={} [{}]", request.scope, footprint)
        val roots = buildMap {
            compilation.sourceTrees().forEach { putAll(XdkAst.rootsBySource(it)) }
        }
        val views = compilation.semanticSnapshots(errs, dependencies)
        val sourceUris = sources?.sourceUris ?: roots.keys.associateWith { it }
        val fallback = if (sources == null) source else Source("", sources.uri(sources.sourceFile))
        val declarations = XdkAst.declarationLocations(compilation.sourceTrees(), sourceUris)
        val diagnostics = heard.errors.map { it.toDiagnostic(fallback, sourceUris, declarations) }
        val documentUris = sources?.documentUris ?: setOf(uri)
        val documents = documentUris.associateWith { uri ->
            val sourceName = sourceUris.entries.firstOrNull { it.value == uri }?.key ?: uri
            val ast = roots[sourceName]
            Analysis(
                diagnostics.filter { it.location.uri == uri },
                XdkSymbols.of(uri, ast),
                ast,
                views.firstOrNull { it.sourceName == sourceName },
            )
        }
        val dependencySources =
            dependencies.declarations.values
                .mapNotNull { declaration ->
                    val name = declaration.location.sourceName ?: return@mapNotNull null
                    val uri =
                        runCatching { URI(name).takeIf { it.isAbsolute }?.toString() }.getOrNull()
                            ?: XdkSources.file(name)?.toURI()?.toString()
                    uri?.let { name to it }
                }
                .toMap()
        val artifact =
            if (moduleName != null && compilation.succeeded() && !heard.hasSeriousErrors())
                compilation.toDependency()
            else null
        if (artifact != null && artifact.module != moduleName) {
            return unavailableProjectModule(
                uri,
                inputs,
                "PROJECT-MODULE",
                "Expected module $moduleName, found ${artifact.module}",
                documentUris,
            )
        }
        return ModuleAnalysis(
            documents = documents,
            diagnostics = diagnostics,
            dependencies =
                compilation.file()?.moduleIds()?.mapTo(linkedSetOf()) { it.name }.orEmpty(),
            succeeded = compilation.succeeded(),
            dependencySources = dependencySources,
            inputs = inputs,
            artifact = artifact,
            sourceInputs = sources?.inputs,
            sourceTexts =
                sources?.inputs?.text?.entries?.associate { (file, text) -> file.path to text }
                    ?: mapOf(uri to request.overlays.getValue(uri)),
        )
    }

    // ----- what the tree can answer --------------------------------------------------------------

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? = XdkSymbols.at(analysis(uri)?.symbols ?: emptyList(), line, column)

    /** The resolved occurrence and its inferred type, including flow-sensitive narrowing. */
    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        val model = analysis(uri)?.semantics
        val hover = model?.let { XdkPresentation.hover(it, line, column) }
        if (hover == null && model?.status != SemanticModel.Status.COMPLETE) {
            val partial = analyzeAtAsync(uri, Position(line, column + 1)).join()
            val site = partial?.sites?.singleOrNull()
            val prefix = site?.memberPrefix
            val text = currentText(uri)?.lineSequence()?.elementAtOrNull(line)
            if (
                partial != null &&
                    prefix != null &&
                    text != null &&
                    prefix.range.end.column <= text.length
            ) {
                val written = text.substring(prefix.range.start.column, prefix.range.end.column)
                site.formals
                    .singleOrNull { it.name == written }
                    ?.let {
                        return "```xtc\n${XdkCursorQueries.formalDetail(partial, it)}\n```"
                    }
            }
        }
        return hover
    }

    /** Highlight only occurrences of the resolved target, including its declaration when known. */
    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        val model = analysis(uri)?.semantics ?: return emptyList()
        val symbol = model.symbolAt(line, column) ?: return emptyList()
        return model.occurrences
            .filter { it.symbol == symbol.id }
            .map {
                val kind =
                    when (it.usage) {
                        SemanticModel.Usage.READ -> DocumentHighlight.HighlightKind.READ
                        SemanticModel.Usage.WRITE,
                        SemanticModel.Usage.READ_WRITE -> DocumentHighlight.HighlightKind.WRITE
                        null -> DocumentHighlight.HighlightKind.TEXT
                    }
                DocumentHighlight(it.range.toRange(), kind)
            }
    }

    override fun getSemanticTokens(uri: String): SemanticTokens? =
        analysis(uri)?.semantics?.let {
            XdkPresentation.tokens(it, XdkLexical.tokens(currentText(uri).orEmpty()))
        }

    private fun currentText(uri: String): String? =
        analysis(uri)?.ast?.source?.toRawString() ?: synchronized(lifecycle) { overlays[uri] }

    override fun formatDocument(
        uri: String,
        content: String,
        options: FormattingOptions,
    ): List<TextEdit> =
        if (XdkLibrarySources.owns(uri)) {
            emptyList()
        } else {
            XdkLexical.format(
                content,
                FormattingConfig.resolve(uri, options, editorFormattingConfig),
                options,
            )
        }

    override fun formatRange(
        uri: String,
        content: String,
        range: Range,
        options: FormattingOptions,
    ): List<TextEdit> =
        if (XdkLibrarySources.owns(uri)) {
            emptyList()
        } else {
            XdkLexical.format(
                content,
                FormattingConfig.resolve(uri, options, editorFormattingConfig),
                options,
                range,
            )
        }

    override fun onTypeFormatting(
        uri: String,
        line: Int,
        column: Int,
        ch: String,
        options: FormattingOptions,
    ): List<TextEdit> =
        currentText(uri)
            ?.let { text ->
                XdkLexical.format(
                    text,
                    FormattingConfig.resolve(uri, options, editorFormattingConfig),
                    options,
                    Range(
                        Position(line, 0),
                        Position(line, text.lines().getOrNull(line)?.length ?: column),
                    ),
                )
            }
            .orEmpty()

    override fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<DocumentLink> = XdkLexical.links(content)

    override fun getCodeLenses(uri: String): List<CodeLens> =
        analysis(uri)
            ?.symbols
            .orEmpty()
            .filter { it.kind == SymbolInfo.SymbolKind.MODULE }
            .map { symbol ->
                val at = symbol.location
                CodeLens(
                    Range(
                        Position(at.startLine, at.startColumn),
                        Position(at.endLine, at.endColumn),
                    ),
                    CodeLensCommand(
                        "▶ Run ${symbol.name}",
                        "xtc.runModule",
                        listOf(uri, symbol.name),
                    ),
                )
            }

    override fun getLinkedEditingRanges(
        uri: String,
        line: Int,
        column: Int,
    ): LinkedEditingRanges? {
        val model =
            analysis(uri)?.semantics?.takeIf { it.status == SemanticModel.Status.COMPLETE }
                ?: return null
        val symbol =
            model.symbolAt(line, column)?.takeIf {
                it.renameable && it.kind == SemanticModel.SymbolKind.VARIABLE
            } ?: return null
        val ranges =
            model.occurrences
                .filter { it.symbol == symbol.id && it.name == symbol.name }
                .map { it.range.toRange() }
                .distinct()
        return ranges.takeIf { it.size > 1 }?.let { LinkedEditingRanges(it) }
    }

    override fun getInlayHints(
        uri: String,
        range: Range,
    ): List<InlayHint> =
        analysis(uri)?.semantics?.let { XdkPresentation.hints(it, range) }.orEmpty()

    /**
     * Blocks and declarations that span more than one line. An editor offers a fold per region, so
     * a region per expression would be noise rather than help.
     */
    override fun getFoldingRanges(uri: String): List<FoldingRange> =
        XdkAst.foldingRegions(analysis(uri)?.ast)

    /**
     * Expanding a selection walks out through the tree, which is exactly what the parent chain of
     * the innermost node containing the cursor is.
     */
    override fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange> {
        val ast = analysis(uri)?.ast
        return positions.map { position ->
            XdkAst.chainAt(ast, position.line, position.column).fold(null as SelectionRange?) {
                parent,
                node ->
                SelectionRange(XdkAst.rangeOf(node), parent)
            } ?: SelectionRange(Range(position, position))
        }
    }

    /** Compile the discovered/configured graph on the worker, including unopened modules. */
    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
        val root = synchronized(lifecycle) { project.buildOrder().firstOrNull()?.uri }
        return if (root == null) {
            completed.values
                .flatMap { it.documents.values }
                .flatMap { flatten(it.symbols) }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        } else {
            projectQuery(ProjectQueryKey(root, ProjectQueryKind.SYMBOLS), emptyList()) {
                    it.symbols(query)
                }
                .join()
        }
    }

    /**
     * Detached current diagnostics for every configured/discovered root, including closed files.
     */
    fun workspaceDiagnosticsAsync(): CompletableFuture<List<CompilationResult>> {
        val root =
            synchronized(lifecycle) { project.buildOrder().firstOrNull()?.uri }
                ?: return CompletableFuture.completedFuture(emptyList())
        return projectQuery(
            ProjectQueryKey(root, ProjectQueryKind.DIAGNOSTICS, requestId = UUID.randomUUID()),
            emptyList(),
        ) {
            it.diagnostics()
        }
    }

    private fun flatten(symbols: List<SymbolInfo>): List<SymbolInfo> = symbols.flatMap {
        listOf(it) + flatten(it.children)
    }

    // ----- what the name resolved to -------------------------------------------------------------

    /**
     * Resolve within the current module snapshot. Compiled dependencies with no source location
     * cannot supply a navigable declaration.
     */
    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        if (module(uri) == null && hasProject(uri))
            return workspaceNavigation(uri)?.definition(uri, line, column)
        val module = module(uri) ?: return null
        val declaration =
            module.document(uri)?.semantics?.definitionLocationAt(line, column) ?: return null
        return module.sourceUri(declaration.sourceName)?.let {
            locationOf(it, declaration.range.toRange())
        }
    }

    override fun findDeclaration(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = findDeclarations(uri, line, column).singleOrNull()

    override fun findDeclarations(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> {
        if (module(uri) == null && hasProject(uri))
            return workspaceNavigation(uri)?.declarations(uri, line, column).orEmpty()
        val module = module(uri) ?: return emptyList()
        return module.locations(
            module.document(uri)?.semantics?.declarationLocationsAt(line, column).orEmpty()
        )
    }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> = findReferencesAsync(uri, line, column, includeDeclaration).join()

    override fun findReferencesAsync(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): CompletableFuture<List<Location>> =
        if (synchronized(lifecycle) { project.scope(uri) != null }) {
            projectQuery(ProjectQueryKey(uri, ProjectQueryKind.REFERENCES), emptyList()) {
                it.references(uri, line, column, includeDeclaration)
            }
        } else {
            CompletableFuture.completedFuture(
                moduleReferences(uri, line, column, includeDeclaration)
            )
        }

    private fun moduleReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val module = module(uri) ?: return emptyList()
        val symbol = module.document(uri)?.semantics?.symbolAt(line, column) ?: return emptyList()
        return module.documents
            .flatMap { (sourceUri, document) ->
                document.semantics
                    ?.occurrences
                    .orEmpty()
                    .filter {
                        it.symbol == symbol.id &&
                            (includeDeclaration || it.role != SemanticModel.Role.DECLARATION)
                    }
                    .map { locationOf(sourceUri, it.range.toRange()) }
            }
            .distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))
    }

    override fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): PrepareRenameResult? {
        val model = module(uri)?.takeIf { it.succeeded }?.document(uri)?.semantics ?: return null
        if (hasProject(uri)) {
            model.importAt(line, column)?.let { alias ->
                val position = SemanticModel.Position(line, column)
                val range = (alias.uses + alias.declaration).single { position in it }
                return PrepareRenameResult(range.toRange(), alias.name)
            }
        }
        val symbol =
            model.symbolAt(line, column)?.takeIf {
                it.renameable || isProjectTarget(uri, model, it)
            } ?: return null
        if (
            symbol.kind == SemanticModel.SymbolKind.MODULE &&
                model.occurrenceAt(line, column)?.name != symbol.name
        )
            return null
        val range =
            model.occurrences
                .firstOrNull {
                    it.symbol == symbol.id &&
                        it.name != "super" &&
                        SemanticModel.Position(line, column) in it.range
                }
                ?.range ?: return null
        return PrepareRenameResult(range.toRange(), symbol.name)
    }

    /**
     * Eligibility is provisional; the worker proves binding/dispatch preservation before editing.
     */
    private fun isProjectTarget(
        uri: String,
        model: SemanticModel,
        symbol: SemanticModel.Symbol,
    ): Boolean =
        (symbol.kind == SemanticModel.SymbolKind.PACKAGE &&
            symbol.declarationSource == null &&
            symbol.dependency == null &&
            hasProject(uri)) ||
            (symbol.name != "construct" &&
                (symbol.kind != SemanticModel.SymbolKind.PARAMETER ||
                    symbol.id in model.parameters) &&
                symbol.kind in
                    setOf(
                        SemanticModel.SymbolKind.METHOD,
                        SemanticModel.SymbolKind.TYPE,
                        SemanticModel.SymbolKind.PROPERTY,
                        SemanticModel.SymbolKind.PACKAGE,
                        SemanticModel.SymbolKind.MODULE,
                        SemanticModel.SymbolKind.PARAMETER,
                    ) &&
                (symbol.declarationSource
                        ?: model.parameters[symbol.id]
                            ?.method
                            ?.let(model::symbol)
                            ?.declarationSource)
                    ?.let {
                        synchronized(lifecycle) {
                            project.scope(uri) != null && project.scope(it) != null
                        }
                    } == true)

    /** Hosts that own persistent source settings can accept the graph replacement with the edit. */
    fun renameProposalAsync(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): CompletableFuture<XdkRenameProposal?> =
        if (isProjectRename(uri, line, column)) {
            projectQuery(ProjectQueryKey(uri, ProjectQueryKind.RENAME_PROPOSAL), null) {
                it.renameProposal(uri, line, column, newName)
            }
        } else {
            renameAsync(uri, line, column, newName).thenApply { it?.let(::XdkRenameProposal) }
        }

    /** Detached effective inputs for host settings views; never compiler-owned objects. */
    internal fun effectiveSourceModules(): List<XdkSourceModule> =
        synchronized(lifecycle) {
            project.buildOrder().map { module ->
                XdkSourceModule(
                    module.name,
                    module.uri,
                    module.dependencies,
                    XdkResources.roots(module.root, module.resourceFiles).map {
                        it.toURI().toString()
                    },
                )
            }
        }

    /**
     * Compiler-proven edits for IDE file-tree operations; no filesystem or configuration writes.
     */
    fun renameFilesAsync(files: Map<String, String>): CompletableFuture<WorkspaceEdit?> {
        // Refuse links before canonicalization loses the requested file identity or captures a
        // source snapshot for an alias. The compiler proof below performs the remaining checks.
        if (
            files.keys.any { uri ->
                runCatching { Files.isSymbolicLink(Path.of(URI(uri))) }.getOrDefault(true)
            }
        )
            return CompletableFuture.completedFuture(null)
        val scope =
            synchronized(lifecycle) {
                files.keys.firstNotNullOfOrNull { uri ->
                    project.scope(uri)
                        ?: XdkSources.file(uri)?.toPath()?.let { path ->
                            project.modules.values
                                .firstOrNull { it.root.toPath().startsWith(path) }
                                ?.uri
                        }
                }
            } ?: return CompletableFuture.completedFuture(null)
        return projectQuery(ProjectQueryKey(scope, ProjectQueryKind.FILE_RENAME), null) {
            it.renameFiles(files)
        }
    }

    override fun getCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
    ): List<CodeAction> = getCodeActionsAsync(uri, range, diagnostics).join()

    override fun getCodeActionsAsync(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
    ): CompletableFuture<List<CodeAction>> =
        if (hasProject(uri)) {
            projectQuery(ProjectQueryKey(uri, ProjectQueryKind.CODE_ACTIONS, range), emptyList()) {
                it.codeActions(uri, range)
            }
        } else {
            CompletableFuture.completedFuture(emptyList())
        }

    override fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): WorkspaceEdit? = renameAsync(uri, line, column, newName).join()

    override fun renameAsync(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): CompletableFuture<WorkspaceEdit?> {
        if (isProjectRename(uri, line, column)) {
            return projectQuery<WorkspaceEdit?>(
                ProjectQueryKey(uri, ProjectQueryKind.RENAME),
                null,
            ) {
                it.rename(uri, line, column, newName)
            }
        }
        val (request, previous) =
            synchronized(lifecycle) {
                if (closed)
                    return CompletableFuture.failedFuture(
                        IllegalStateException("XDK adapter is closed")
                    )
                if (discovery.get().problem != null) return CompletableFuture.completedFuture(null)
                val compilation =
                    requests[analysisScope(uri)] ?: return CompletableFuture.completedFuture(null)
                val module =
                    completed[compilation.scope]?.takeIf { it.succeeded }
                        ?: return CompletableFuture.completedFuture(null)
                if (prepareRename(uri, line, column) == null)
                    return CompletableFuture.completedFuture(null)
                val request =
                    RenameRequest(
                        compilation,
                        module,
                        uri,
                        Position(line, column),
                        newName,
                        queueTrace,
                        ::runRename,
                    )
                request.result.whenComplete { _, _ ->
                    if (request.result.isCancelled) {
                        synchronized(lifecycle) {
                            renames.remove(uri, request)
                            compiles.remove(request.task)
                        }
                    }
                }
                val previous = renames.put(uri, request)
                previous?.let { compiles.remove(it.task) }
                compiles.execute(request.task.ready())
                request to previous
            }
        previous?.result?.cancel(false)
        return request.result
    }

    private fun isProjectRename(
        uri: String,
        line: Int,
        column: Int,
    ): Boolean =
        synchronized(lifecycle) {
            module(uri)?.document(uri)?.semantics?.let { model ->
                model.importAt(line, column) != null ||
                    model.symbolAt(line, column)?.let { isProjectTarget(uri, model, it) } == true
            } == true && project.scope(uri) != null
        }

    private fun isStale(request: RenameRequest): Boolean =
        renames[request.uri] !== request ||
            request.result.isCancelled ||
            isStale(request.compilation)

    /** Both proof attempts stay on the compiler worker and never replace the live analysis. */
    private fun runRename(request: RenameRequest) {
        try {
            val edit = proveRename(request)
            synchronized(lifecycle) {
                if (isStale(request)) throw CancellationException()
            }
            request.result.complete(edit)
        } catch (_: CancellationException) {
            request.result.cancel(false)
        } catch (e: Exception) {
            request.result.completeExceptionally(e)
        } catch (e: Error) {
            request.result.completeExceptionally(e)
            throw e
        } finally {
            renames.remove(request.uri, request)
        }
    }

    private fun proveRename(request: RenameRequest): WorkspaceEdit? {
        val module = request.module

        fun compile(texts: Map<String, String>): CompilerRenameFacts? {
            if (isStale(request)) throw CancellationException()
            val heard = ErrorList()
            val errors = ErrorListener.cancellable(heard) { isStale(request) }
            val dependencies = module.inputs.open()
            val repository = dependencies.repository
            val sources =
                module.sourceInputs?.let {
                    XdkSources.replay(
                        requireNotNull(XdkSources.file(request.compilation.scope)),
                        it,
                        texts,
                    )
                }
            val compilation =
                if (sources == null) {
                    compiler.compileSource(
                        Source(texts.getValue(request.compilation.uri), request.compilation.uri),
                        repository,
                        errors,
                    )
                } else {
                    compiler.compileTree(sources, repository, errors)
                }
            if (isStale(request)) throw CancellationException()
            return if (compilation.succeeded() && !heard.hasSeriousErrors())
                compilation.renameFacts(dependencies)
            else null
        }
        val source = module.document(request.uri)?.semantics?.sourceName ?: return null
        val before = compile(module.sourceTexts) ?: return null
        val plan =
            XdkRename.plan(
                before,
                module.sourceTexts,
                source,
                request.position.line,
                request.position.column,
                request.name,
            ) ?: return null
        val after = compile(plan.proposed) ?: return null
        if (!XdkRename.preservesBindings(before, after, plan)) return null
        // A closed file can change without a watcher event. Refuse edits against that old snapshot.
        if (module.sourceInputs != null) {
            val current =
                try {
                    captureSources(request.compilation) { isStale(request) }?.inputs
                } catch (_: IOException) {
                    return null
                }
            if (current != module.sourceInputs) return null
        }
        return WorkspaceEdit(
            plan.edits.keys.associate { name ->
                (module.sourceUri(name) ?: return null) to plan.textEdits(name)
            },
            versioned = true,
        )
    }

    private fun hasProject(uri: String): Boolean =
        synchronized(lifecycle) { project.scope(uri) != null }

    private fun workspaceNavigation(uri: String): XdkWorkspaceNavigation? =
        projectQuery<XdkWorkspaceNavigation?>(
                ProjectQueryKey(uri, ProjectQueryKind.NAVIGATION),
                null,
            ) {
                it.navigation()
            }
            .join()

    override fun prepareTypeHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<TypeHierarchyItem> =
        if (hasProject(uri)) {
            workspaceNavigation(uri)?.prepareTypes(uri, line, column).orEmpty()
        } else {
            module(uri)?.hierarchy?.prepare(uri, line, column).orEmpty()
        }

    override fun findTypeDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = findTypeDefinitions(uri, line, column).firstOrNull()

    override fun findTypeDefinitions(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> {
        if (module(uri) == null && hasProject(uri))
            return workspaceNavigation(uri)?.typeDefinitions(uri, line, column).orEmpty()
        val module = module(uri) ?: return emptyList()
        return module.locations(
            module.document(uri)?.semantics?.typeDefinitionLocationsAt(line, column).orEmpty()
        )
    }

    override fun findImplementation(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> {
        if (hasProject(uri))
            return workspaceNavigation(uri)?.implementations(uri, line, column).orEmpty()
        val module = module(uri) ?: return emptyList()
        return module.locations(
            module.document(uri)?.semantics?.implementationLocationsAt(line, column).orEmpty()
        )
    }

    private fun ModuleAnalysis.locations(
        locations: List<SemanticModel.SourceLocation>
    ): List<Location> =
        locations
            .mapNotNull { target ->
                sourceUri(target.sourceName)?.let { locationOf(it, target.range.toRange()) }
            }
            .distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))

    override fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        if (hasProject(item.uri)) {
            workspaceNavigation(item.uri)?.parents(item).orEmpty()
        } else {
            module(item.uri)?.hierarchy?.supertypes(item).orEmpty()
        }

    override fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem> =
        if (hasProject(item.uri)) {
            workspaceNavigation(item.uri)?.children(item).orEmpty()
        } else {
            module(item.uri)?.hierarchy?.subtypes(item).orEmpty()
        }

    override fun prepareCallHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<CallHierarchyItem> =
        if (hasProject(uri)) {
            workspaceNavigation(uri)?.prepareCalls(uri, line, column).orEmpty()
        } else {
            module(uri)?.calls?.prepare(uri, line, column).orEmpty()
        }

    override fun getIncomingCalls(item: CallHierarchyItem): List<CallHierarchyIncomingCall> =
        if (hasProject(item.uri)) {
            workspaceNavigation(item.uri)?.incoming(item).orEmpty()
        } else {
            module(item.uri)?.calls?.incoming(item).orEmpty()
        }

    override fun getOutgoingCalls(item: CallHierarchyItem): List<CallHierarchyOutgoingCall> =
        if (hasProject(item.uri)) {
            workspaceNavigation(item.uri)?.outgoing(item).orEmpty()
        } else {
            module(item.uri)?.calls?.outgoing(item).orEmpty()
        }

    private fun SemanticModel.Range.toRange(): Range =
        Range(Position(start.line, start.column), Position(end.line, end.column))

    private fun locationOf(
        uri: String,
        range: Range,
    ): Location =
        Location(uri, range.start.line, range.start.column, range.end.line, range.end.column)

    // ----- copied cursor facts -------------------------------------------------------------------

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
        triggerCharacter: String?,
    ): List<CompletionItem> = getCompletionsAsync(uri, line, column, triggerCharacter).join()

    override fun getCompletionsAsync(
        uri: String,
        line: Int,
        column: Int,
        triggerCharacter: String?,
    ): CompletableFuture<List<CompletionItem>> =
        analyzeAtAsync(CursorKey(uri, CursorKind.COMPLETION), Position(line, column))
            .mapCancellable { it?.let(XdkCursorQueries::completions).orEmpty() }

    override fun getSignatureHelp(
        uri: String,
        line: Int,
        column: Int,
    ): SignatureHelp? = getSignatureHelpAsync(uri, line, column).join()

    override fun getSignatureHelpAsync(
        uri: String,
        line: Int,
        column: Int,
    ): CompletableFuture<SignatureHelp?> {
        if (line < 0 || column < 0) return CompletableFuture.completedFuture(null)
        val position = SemanticModel.Position(line, column)
        analysis(uri)?.semantics?.let { model ->
            XdkCursorQueries.signatureHelp(model, position)?.let {
                return CompletableFuture.completedFuture(it)
            }
        }
        return analyzeAtAsync(CursorKey(uri, CursorKind.SIGNATURE), Position(line, column))
            .mapCancellable { it?.let { model -> XdkCursorQueries.signatureHelp(model, position) } }
    }

    private val queueTrace = CompilerQueueTrace()
    private val lifecycle = Any()
    private var closed = false

    /** Replaced only under lifecycle; each request captures its immutable dependency set. */
    private var dependencies = XdkDependencies(emptyList())
    private var project = XdkProject(emptyList())
    private val builds = ConcurrentHashMap<String, CachedBuild>()
    private val scheduled = mutableMapOf<Request, ScheduledFuture<*>>()
    private val debouncer =
        ScheduledThreadPoolExecutor(1) { work ->
                Thread(work, "xtc-debounce").apply { isDaemon = true }
            }
            .apply { removeOnCancelPolicy = true }

    /**
     * Only current, completed module analyses belong here; a member edit drops all previous views.
     */
    private val completed = ConcurrentHashMap<String, ModuleAnalysis>()
    private val overlays = linkedMapOf<String, String>()
    private val scopes = mutableMapOf<String, String>()
    private val requests = ConcurrentHashMap<String, Request>()
    private val cursors = ConcurrentHashMap<CursorKey, CursorRequest>()
    private val renames = ConcurrentHashMap<String, RenameRequest>()
    private val projectQueries = ConcurrentHashMap<ProjectQueryKey, ProjectRequest<*>>()
    private val diagnosticCache = XdkDiagnosticIndex()
    private val navigationCache = AtomicReference<Map<String, XdkWorkspaceNavigation>>(emptyMap())

    /**
     * A ThreadPoolExecutor rather than Executors.newSingleThreadExecutor, because the latter wraps
     * the queue where nothing can see it, and how many documents are waiting is the number that
     * says whether one-at-a-time has become a problem.
     */
    private val compiles =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            ThreadFactory { r -> Thread(r, "xtc-compile").apply { isDaemon = true } },
        )

    private companion object {
        const val SOURCE = "xtc"
        const val SHUTDOWN_SECONDS = 5L
        const val DEBOUNCE_MILLIS = 100L

        /** Translate a UTF-16 editor position without consuming or modifying the compiler input. */
        fun cursorPosition(
            source: Source,
            position: Position,
            errors: ErrorListener,
        ): Long? {
            if (position.line < 0 || position.column < 0) return null
            val cursor = source.clone()
            while (
                cursor.hasNext() &&
                    (cursor.line < position.line ||
                        (cursor.line == position.line && cursor.offset < position.column))
            ) {
                if (errors.isAbortDesired) throw CancellationException()
                cursor.next()
            }
            return cursor.position.takeIf {
                cursor.line == position.line && cursor.offset == position.column
            }
        }

        fun analyzeIncomplete(
            source: Source,
            sources: ModuleInfo?,
            cursor: Long,
            repository: ModuleRepository?,
            errors: ErrorListener,
        ): EmbeddingSupport.PartialAnalysis {
            XdkLibraries.configure()
            val support = EmbeddingSupport.instance()
            return if (sources == null) {
                support.analyzeIncomplete(source, cursor, repository, errors)
            } else {
                support.analyzeIncomplete(
                    sources,
                    checkNotNull(XdkSources.file(source.fileName)),
                    cursor,
                    repository,
                    errors,
                )
            }
        }
    }
}
