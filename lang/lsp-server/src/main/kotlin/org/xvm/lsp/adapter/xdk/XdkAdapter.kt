package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.asm.MultiMethodStructure
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.CallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeLens
import org.xvm.lsp.adapter.CodeLensCommand
import org.xvm.lsp.adapter.ColorPresentation
import org.xvm.lsp.adapter.ColorValue
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.DocumentColor
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.DocumentLink
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions
import org.xvm.lsp.adapter.InlayHint
import org.xvm.lsp.adapter.InlineCompletionContext
import org.xvm.lsp.adapter.LinkedEditingRanges
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.PrepareRenameResult
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.ReadOnlyDocument
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.SymbolMoniker
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.adapter.composeCancellable
import org.xvm.lsp.adapter.mapCancellable
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.tool.ModuleInfo
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
        analyzeCursor: (
            Source,
            ModuleInfo?,
            Long,
            ModuleRepository?,
            ErrorListener,
        ) -> EmbeddingSupport.PartialAnalysis,
        private val colorPrototype: Boolean = false,
    ) : AbstractAdapter() {
        private val compiler = CompilerCalls(compileSource, compileTree, analyzeCursor)
        private val formattingPreferences = AtomicReference<FormattingConfig?>()

        override var editorFormattingConfig: FormattingConfig?
            get() = formattingPreferences.get()
            set(value) = formattingPreferences.set(value)

        internal constructor(
            compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation,
            compileTree: (ModuleInfo, ErrorListener) -> EmbeddingSupport.Compilation,
            analyzeCursor: (Source, ModuleInfo?, Long, ErrorListener) -> EmbeddingSupport.PartialAnalysis =
                { source, sources, cursor, errors ->
                    analyzeIncomplete(source, sources, cursor, null, errors)
                },
        ) : this(
            { source, _, errors -> compileSource(source, errors) },
            { sources, _, errors -> compileTree(sources, errors) },
            { source, sources, cursor, _, errors -> analyzeCursor(source, sources, cursor, errors) },
        )

        internal constructor(
            compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation,
        ) : this(
            compileSource,
            { sources, errs ->
                XdkLibraries.configure()
                EmbeddingSupport.instance().compileModule(sources, null, errs)
            },
        )

        constructor() : this(false)

        /** Opt-in test-fixture color contract; not a standard Ecstasy color library. */
        constructor(colorPrototype: Boolean) :
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
                colorPrototype,
            )

        internal fun compilerQueueSnapshot(): Map<String, Any> = queueTrace.snapshot()

        internal fun compilerProgressDescription(): String? = queueTrace.progressDescription()

        override val displayName: String = "XDK"

        /** Queries read completed module analyses, which are immutable and replaced whole on each edit. */
        override val concurrentQueries: Boolean get() = true

        override val capabilities: Set<AdapterCapability> =
            setOf(
                AdapterCapability.COMPLETION,
                AdapterCapability.INLINE_COMPLETION,
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
                AdapterCapability.MONIKER,
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
            ) + if (colorPrototype) setOf(AdapterCapability.DOCUMENT_COLOR) else emptySet()

        /** Workspace discovery inputs and the catalog built from them, including any configuration problem. */
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
        fun refreshDiscoveredSources(): Set<String> = scanDiscoveredSources()?.install?.invoke().orEmpty()

        /**
         * A workspace rescan, taken without holding any lock so that a slow filesystem walk never delays
         * editing. [install] adopts it and returns the affected scopes, or null when an edit, a closed
         * adapter or a newer graph changed what the scan read, in which case the caller scans again.
         */
        class DiscoveryScan internal constructor(
            val install: () -> Set<String>?,
        )

        /** Rescans the discovered workspace; null when an explicit graph leaves nothing to discover. */
        fun scanDiscoveredSources(): DiscoveryScan? {
            val settings = discovery.get()
            if (settings.explicit) return null
            val (buffers, previous) = synchronized(lifecycle) { overlays.toMap() to project }
            val superseded = { closed || discovery.get() !== settings }
            val catalog =
                try {
                    settings.catalog.rescan(settings.folders, buffers, superseded)
                } catch (failure: RuntimeException) {
                    // a superseded walk stops by aborting tokenization; the caller scans again
                    if (superseded()) return DiscoveryScan { null } else throw failure
                }
            return DiscoveryScan { installDiscoveredCatalog(catalog, settings, previous) { overlays == buffers } }
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
                    if (closed || isLibraryDocument(uri) || overlays[uri] == content) {
                        return emptySet()
                    }
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
            if (settings.explicit || !XdkWorkspaceDiscovery.includes(settings.folders, file)) {
                return emptySet()
            }
            val catalog =
                settings.catalog.withSource(file, content) { closed || discovery.get() !== settings }
            return installDiscoveredCatalog(catalog, settings, previous) {
                if (closing) uri !in overlays else overlays[uri] == content
            }.orEmpty()
        }

        /**
         * Applies file events to the discovered sources, reading only the files and folders they name, and returns
         * the retired scopes. The server calls this before reading its next message, so a request sent after the
         * events sees them and no later walk retires it; [scanDiscoveredSources] then only catches events the watcher
         * missed. Null when concurrent discovery changes, such as a background walk installing, kept the update from
         * being installed each time it was tried.
         */
        fun applyFileEvents(uris: Collection<String>): Set<String>? {
            val paths = uris.mapNotNull(XdkSources::file).distinct()
            repeat(MAX_EVENT_ATTEMPTS) {
                val settings = discovery.get()
                if (settings.explicit || paths.isEmpty()) return emptySet()
                val (buffers, previous) = synchronized(lifecycle) { overlays.toMap() to project }
                val superseded = { closed || discovery.get() !== settings }
                val catalog =
                    try {
                        settings.catalog.withEvents(settings.folders, paths, buffers, superseded)
                    } catch (failure: RuntimeException) {
                        // a superseded read stops by aborting tokenization; read again from the newer catalog
                        if (superseded()) null else throw failure
                    }
                catalog?.let { installDiscoveredCatalog(it, settings, previous) { overlays == buffers } }?.let { return it }
            }
            return null
        }

        /** Null when an edit, a closed adapter or a newer graph or catalog kept [catalog] from being installed. */
        private fun installDiscoveredCatalog(
            catalog: XdkWorkspaceDiscovery.Catalog,
            settings: Discovery,
            previous: XdkProject,
            current: () -> Boolean,
        ): Set<String>? {
            if (synchronized(lifecycle) { closed }) return null
            val candidate = runCatching { XdkProject(catalog.modules(previous.modules.values)) }
            val failure = candidate.exceptionOrNull()
            if (failure != null && failure !is IllegalArgumentException) throw failure
            val problem = failure?.message
            var installed = false
            val retired =
                installSourceModules(
                    (candidate.getOrNull() ?: previous).modules.values.toList(),
                    explicit = false,
                    force = settings.problem != problem,
                ) {
                    (
                        !closed &&
                            project === previous &&
                            current() &&
                            discovery.compareAndSet(
                                settings,
                                settings.copy(catalog = catalog, problem = problem),
                            )
                    ).also { installed = it }
                }
            return retired.takeIf { installed }
        }

        fun changeWorkspaceFolders(
            added: List<String>,
            removed: List<String>,
        ): Set<String> {
            updateWorkspaceFolders(added, removed)
            return refreshDiscoveredSources()
        }

        /** Records the folders to discover; the next [scanDiscoveredSources] reads them. */
        fun updateWorkspaceFolders(
            added: List<String>,
            removed: List<String>,
        ) {
            val deleted = removed.mapNotNull(XdkSources::file).toSet()
            discovery.updateAndGet { settings ->
                settings.copy(
                    folders =
                        (
                            settings.folders.filterNot { it in deleted } +
                                added.mapNotNull(XdkSources::file)
                        ).distinct(),
                )
            }
        }

        override val readOnlyDocumentSchemes = setOf(XdkLibrarySources.SCHEME)

        override fun readOnlyDocument(uri: String): ReadOnlyDocument? =
            synchronized(lifecycle) {
                if (closed) {
                    null
                } else {
                    XdkLibrarySources.document(uri) ?: dependencies.modules.values.firstNotNullOfOrNull { dependency ->
                        dependency.documents[uri] ?: dependency.documents.values.find { it.uri == uri }
                    }
                }
            }

        private fun isLibraryDocument(uri: String): Boolean =
            uri.startsWith("${XdkLibrarySources.SCHEME}:") ||
                XdkLibrarySources.owns(uri) || synchronized(lifecycle) { dependencies.modules.values.any { uri in it.documents } }

        internal fun libraryConfiguration(): List<Map<String, Any>> =
            synchronized(lifecycle) {
                dependencies.modules.values.map { dependency ->
                    mapOf(
                        "module" to dependency.module,
                        "revision" to dependency.revision,
                        "readOnly" to true,
                        "sources" to dependency.documents.values.map { it.uri },
                    )
                }
            }

        /** Library queries share compiler serialization, cancellation, tracing and shutdown. */
        private fun libraryMonikers(
            uri: String,
            line: Int,
            column: Int,
        ): CompletableFuture<List<SymbolMoniker>> =
            synchronized(lifecycle) {
                if (closed) return closedAdapter()
                val result = CompletableFuture<List<SymbolMoniker>>()
                val task =
                    queueTrace.task("library-monikers", uri, result) {
                        try {
                            if (!result.isDone) result.complete(XdkLibrarySources.monikers(uri, line, column))
                        } catch (failure: Exception) {
                            result.completeExceptionally(failure)
                        } catch (failure: Error) {
                            result.completeExceptionally(failure)
                            throw failure
                        } finally {
                            libraryQueries.remove(result)
                        }
                    }
                libraryQueries[result] = task
                result.whenComplete { _, _ ->
                    if (result.isCancelled) {
                        libraryQueries.remove(result)
                        compiles.remove(task)
                    }
                }
                compiles.execute(task.ready())
                result
            }

        /**
         * Compile [target] (a module name or root URI) and its source dependencies, or every source module
         * when [target] is null, into the embedded build directory of the owning workspace folder. Shares the
         * compiler worker with editor analysis, so it never runs concurrently with another compilation.
         */
        fun buildAsync(
            target: String?,
            rebuild: Boolean,
        ): CompletableFuture<XdkBuildResult> =
            synchronized(lifecycle) {
                if (closed) return closedAdapter()
                val graph = project
                val inputs = dependencies
                val module =
                    target?.let { name ->
                        graph.modules[name] ?: graph.modules.values.firstOrNull { it.uri == name || it.uri == graph.scope(name) }
                    }
                if (target != null && module == null) {
                    return CompletableFuture.failedFuture(IllegalArgumentException("Unknown source module: $target"))
                }
                val order = module?.let { graph.buildOrder(it.uri) } ?: graph.buildOrder()
                val folder = buildFolder(module ?: order.firstOrNull())
                val scope = module?.uri ?: folder.toURI().toString()
                // the exact text the editor shows now, saved or not; later edits are not part of this build
                val snapshot = overlays.toMap()
                val problem = discovery.get().problem
                val result = CompletableFuture<XdkBuildResult>()
                val task =
                    queueTrace.task("embedded-build", scope, result) {
                        try {
                            if (!result.isDone) {
                                result.complete(build(scope, order, snapshot, inputs, graph, problem, folder, rebuild, result))
                            }
                        } catch (failure: Exception) {
                            result.completeExceptionally(failure)
                        } catch (failure: Error) {
                            result.completeExceptionally(failure)
                            throw failure
                        }
                    }
                result.whenComplete { _, _ -> if (result.isCancelled) compiles.remove(task) }
                compiles.execute(task.ready())
                result
            }

        /**
         * Compiles [order] from [snapshot] through the same closure compile as editor analysis, so a module
         * whose sources and dependency revisions are unchanged since it was last compiled is not compiled
         * again, unless [rebuild]. Runs on the compiler worker.
         */
        private fun build(
            scope: String,
            order: List<XdkSourceModule>,
            snapshot: Map<String, String>,
            inputs: XdkDependencies,
            graph: XdkProject,
            problem: String?,
            folder: File,
            rebuild: Boolean,
            result: CompletableFuture<XdkBuildResult>,
        ): XdkBuildResult {
            if (problem != null) {
                return XdkBuild.failed(
                    folder,
                    Diagnostic(
                        Location(scope, 0, 0, 0, 0),
                        Diagnostic.Severity.ERROR,
                        problem,
                        Diagnostic.Codes.SOURCE_GRAPH,
                        Diagnostic.SOURCE,
                    ),
                )
            }
            val binaries =
                XdkDependencies(
                    inputs.modules
                        .filterKeys { it !in graph.modules }
                        .values
                        .toList(),
                )
            if (order.isEmpty()) return XdkBuild.write(emptyList(), binaries, folder)
            val compile =
                Request(scope, scope, snapshot, inputs, graph, null, queueTrace, {}, buildCancelled = { closed || result.isCancelled })
            val modules = mutableListOf<XdkBuildModule>()
            return try {
                compileClosure(compile, order, { !rebuild }) { module, analysis, reused, sources ->
                    modules +=
                        XdkBuildModule(
                            module,
                            analysis.artifact?.takeIf { analysis.succeeded },
                            analysis.diagnostics,
                            skipped = analysis.diagnostics.any { it.code == Diagnostic.Codes.DEPENDENCY_FAILED },
                            reused = reused,
                            sources =
                                sources
                                    ?.inputs
                                    ?.text
                                    ?.entries
                                    ?.associate { (file, text) -> sources.uri(file) to XdkBuild.sha256(text) }
                                    .orEmpty(),
                            dependencies = analysis.inputs.modules.mapValues { (_, artifact) -> artifact.revision },
                        )
                }
                XdkBuild.write(modules, binaries, folder)
            } catch (_: CancellationException) {
                XdkBuild.cancelled(folder)
            }
        }

        /** Source modules whose latest compilation declares a callable entry point. */
        fun runnableTargets(): List<XdkRunnableTarget> =
            project.buildOrder().flatMap { module ->
                entryMethods(module.uri).map { XdkRunnableTarget(module.name, module.uri, it) }
            }

        /** Entry methods the runner can call in the module [uri] belongs to, from its latest compilation. */
        private fun entryMethods(uri: String): List<String> = module(uri)?.entryMethods.orEmpty()

        private fun moduleDeclaration(node: AstNode): TypeCompositionStatement? =
            if (node is TypeCompositionStatement && node.category.id == Token.Id.MODULE) {
                node
            } else {
                node.childNodes().firstNotNullOfOrNull(::moduleDeclaration)
            }

        /** The workspace folder that owns [module]'s embedded build output and is its runs' working directory. */
        private fun buildFolder(module: XdkSourceModule?): File {
            val folders = discovery.get().folders
            return module?.let { owned -> folders.filter { owned.root.toPath().startsWith(it.toPath()) }.maxByOrNull { it.path.length } }
                ?: folders.firstOrNull()
                ?: module?.root?.parentFile
                ?: File(".").absoluteFile
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

        /** One operation's ownership view; canonicalize each open path once, without a stale cache. */
        internal fun analysisScopes(uris: Collection<String>): Map<String, String> =
            synchronized(lifecycle) {
                val files = (uris + overlays.keys).distinct().associateWith(XdkSources::file)
                val openFiles = overlays.keys.mapNotNull(files::get).toSet()
                uris.associateWith { uri ->
                    val file = files[uri]
                    file?.let(project::scope) ?: file?.let {
                        val root = XdkSources.moduleRoot(it, openFiles)
                        if (root == file) scopes[uri] ?: root.toURI().toString() else root.toURI().toString()
                    } ?: uri
                }
            }

        /** Unsaved source buffers do not change the resource files read from disk. */
        internal fun affectedSourceScopes(uri: String): Set<String> = synchronized(lifecycle) { project.affected(analysisScope(uri)) }

        override fun affectedAnalysisScopes(uri: String): Set<String> =
            synchronized(lifecycle) {
                project.orderedScopes(inputScopes(uri).flatMapTo(linkedSetOf(), project::affected))
            }

        /**
         * Only filesystem refreshes may coalesce against compiled inputs; editor changes still retire
         * queries.
         */
        internal fun changedFileScopes(uri: String): Set<String> =
            synchronized(lifecycle) {
                val file = XdkSources.file(uri)
                val openSources =
                    overlays
                        .mapNotNull { (uri, text) -> XdkSources.file(uri)?.let { it to text } }
                        .toMap()
                val changed =
                    inputScopes(uri).filter { scope ->
                        val module = project.modules.values.firstOrNull { it.uri == scope }
                        val root = module?.root ?: XdkSources.file(scope)
                        val running = requests[scope]?.takeUnless { it.result.isDone }
                        when {
                            file == null || root == null -> true

                            // judged against what the running analysis reads, which no finished one has yet
                            running != null -> !readsUnchanged(running, file, root, openSources)

                            else -> {
                                val inputs = sourceInputs(scope)
                                inputs == null ||
                                    // A failed current analysis still owns its captured source inputs.
                                    // Only fallback builds can predate a failure (e.g. a missing dependency);
                                    // matching that old build must not suppress recovery after recreation.
                                    (
                                        completed[scope]?.sourceInputs == null &&
                                            project.affected(scope).any { completed[it]?.succeeded == false }
                                    ) ||
                                    !XdkFileChanges.unchanged(file, root, inputs, openSources)
                            }
                        }
                    }
                project.orderedScopes(changed.flatMapTo(linkedSetOf(), project::affected))
            }

        /**
         * Whether a file event at [file] leaves the sources of [request], a running analysis, as it reads them.
         * The watcher reports a change after making it, so an analysis that has not read its sources yet will
         * read the changed files; the watcher's report of a folder the editor just created then cancels nothing.
         */
        private fun readsUnchanged(
            request: Request,
            file: File,
            root: File,
            overlays: Map<File, String>,
        ): Boolean =
            when (val read = request.sources) {
                SourceRead.Pending -> true
                SourceRead.Reading -> false
                is SourceRead.Read -> read.inputs != null && XdkFileChanges.unchanged(file, root, read.inputs, overlays)
            }

        /** Records on [request] what [capture] read, so a file event during the analysis is judged against it. */
        private fun <T> reading(
            request: Request,
            inputs: (T) -> XdkSources.Inputs?,
            capture: () -> T,
        ): T {
            request.sources = SourceRead.Reading
            return capture().also { request.sources = SourceRead.Read(inputs(it)) }
        }

        private fun sourceInputs(scope: String): XdkSources.Inputs? =
            completed[scope]?.sourceInputs
                ?: project.modules.values
                    .firstOrNull { it.uri == scope }
                    ?.let { builds[it.name]?.key?.sources }
                ?: diagnosticCache
                    .snapshot()
                    .builds[scope]
                    ?.key
                    ?.sources

        private fun inputScopes(uri: String): Set<String> {
            val file = XdkSources.file(uri)?.toPath()
            val known =
                project.modules.values
                    .map { it.uri }
                    .toSet() + requests.keys + completed.keys
            val sources =
                known.filter { scope ->
                    val root = XdkSources.file(scope)?.toPath()
                    if (root == null || file == null) {
                        scope == uri
                    } else {
                        val members = root.parent.resolve(root.fileName.toString().removeSuffix(".x"))
                        root.startsWith(file) || file.startsWith(members)
                    }
                }
            val resources =
                project.resourceScopes(uri).filter { scope ->
                    val inputs = sourceInputs(scope)
                    requests[scope]?.result?.isDone == false ||
                        inputs == null ||
                        inputs.resources.entries.isNotEmpty()
                }
            return (sources + resources).toSet()
        }

        fun inputWatchRoots(): Set<File> = synchronized(lifecycle) { project.inputWatchRoots() }

        override fun compileAsync(
            uri: String,
            content: String,
        ): CompletableFuture<CompilationResult> {
            if (isLibraryDocument(uri)) {
                return CompletableFuture.completedFuture(CompilationResult.success(uri, emptyList()))
            }
            updateDocument(uri, content)
            val submission =
                synchronized(lifecycle) {
                    if (closed) return closedAdapter()
                    overlays[uri] = content
                    val scope = analysisScope(uri)
                    scopes[uri] = scope
                    val sourceScopes = project.buildOrder(scope).mapTo(mutableSetOf(scope)) { it.uri }
                    val overlayScopes = analysisScopes(overlays.keys)
                    val request =
                        Request(
                            scope,
                            uri,
                            overlays.filterKeys {
                                overlayScopes[it] in sourceScopes
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
                                    if (scheduled.remove(request) != null && !isStale(request)) {
                                        compiles.execute(request.task.ready())
                                    }
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
        ): CompletableFuture<PartialSemanticModel?> = analyzeAtAsync(CursorKey(uri, CursorKind.PROBE), position)

        private fun analyzeAtAsync(
            key: CursorKey,
            position: Position,
        ): CompletableFuture<PartialSemanticModel?> {
            val uri = key.uri
            val (request, previous) =
                synchronized(lifecycle) {
                    if (closed) return closedAdapter()
                    val compilation = currentCompilation(uri) ?: return CompletableFuture.completedFuture(null)
                    if (uri !in compilation.overlays) return CompletableFuture.completedFuture(null)
                    val request =
                        CursorRequest(compilation, key, position, queueTrace, ::runCursorAnalysis)
                    request to replacePending(cursors, key, request)
                }
            previous?.result?.cancel(false)
            return request.result
        }

        /** Cursor query category used when coalescing work for the same document. */
        private enum class CursorKind {
            PROBE,
            COMPLETION,
            INLINE_COMPLETION,
            SIGNATURE,
        }

        /** Document and query category whose pending cursor request can supersede an older request. */
        private data class CursorKey(
            val uri: String,
            val kind: CursorKind,
        )

        /** Cancelable result and queued compiler work owned by a dependent query. */
        private sealed interface QueryWork {
            val result: CompletableFuture<*>
            val task: CompilerQueueTrace.Work
        }

        /** Query work tied to the compilation request whose snapshot it consumes. */
        private sealed interface QueryRequest : QueryWork {
            val compilation: Request
        }

        /** Project-wide query categories with separate scheduling and cancellation identities. */
        private enum class ProjectQueryKind {
            REFERENCES,
            CODE_LENSES,
            MONIKERS,
            RENAME,
            RENAME_PROPOSAL,
            FILE_RENAME,
            SYMBOLS,
            NAVIGATION,
            CODE_ACTIONS,
            COMPLETION_IMPORTS,
            DIAGNOSTICS,
        }

        /** Identity used to coalesce equivalent project queries without conflating distinct operations. */
        private data class ProjectQueryKey(
            val uri: String,
            val kind: ProjectQueryKind,
            val range: Range? = null,
            val requestId: UUID? = null,
        )

        /**
         * Project query with captured dependencies and overlays, executed against those inputs on the
         * compiler worker.
         */
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

        /** Cursor query and source position bound to one compilation request. */
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

        /** Rename query bound to the compilation, module and position on which its proof depends. */
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
                navigationIndex.retire(project.modules.values.mapTo(hashSetOf()) { it.uri })
                retired.forEach { compiles.remove(it.task) }
            }

        private fun <T> projectQuery(
            key: ProjectQueryKey,
            unavailable: T,
            query: (XdkProjectQueries) -> T,
        ): CompletableFuture<T> {
            val (request, previous) =
                synchronized(lifecycle) {
                    if (closed) return closedAdapter()
                    if (discovery.get().problem != null || project.scope(key.uri) == null) {
                        return CompletableFuture.completedFuture(unavailable)
                    }
                    val request =
                        ProjectRequest(key, project, dependencies, overlays.toMap(), queueTrace) { work: ProjectRequest<T> ->
                            fun stale(): Boolean = projectQueries[key] !== work || work.result.isCancelled
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
                                                discoverImports = !discovery.get().explicit,
                                                diagnosticCache = diagnosticCache,
                                                navigationIndex = navigationIndex,
                                            ),
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
                    request to replacePending(projectQueries, key, request)
                }
            previous?.result?.cancel(false)
            return request.result
        }

        /** The future for a request made after [close]. */
        private fun <T> closedAdapter(): CompletableFuture<T> =
            CompletableFuture.failedFuture(IllegalStateException("XDK adapter is closed"))

        /**
         * The compilation request that a document query reads, or null when discovery failed or the
         * document has no module analysis. Call while holding [lifecycle].
         */
        private fun currentCompilation(uri: String): Request? = if (discovery.get().problem != null) null else requests[analysisScope(uri)]

        /**
         * Replace the pending query for [key] with [request] and queue its compiler work; cancelling
         * the request removes it again. Call while holding [lifecycle], and cancel the returned
         * previous query after releasing it.
         */
        private fun <K : Any, R : QueryWork> replacePending(
            pending: MutableMap<K, R>,
            key: K,
            request: R,
        ): R? {
            request.result.whenComplete { _, _ ->
                if (request.result.isCancelled) {
                    synchronized(lifecycle) {
                        pending.remove(key, request)
                        compiles.remove(request.task)
                    }
                }
            }
            val previous = pending.put(key, request)
            previous?.let { compiles.remove(it.task) }
            compiles.execute(request.task.ready())
            return previous
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
                val facts =
                    cursor?.let {
                        val sources = captureSources(request.compilation) { isStale(request) }
                        val dependencies =
                            (
                                completed[request.compilation.scope]?.inputs
                                    ?: request.compilation.dependencies
                            ).open()
                        val model =
                            compiler
                                .analyzeCursor(source, sources, it, dependencies.repository, errors)
                                .semanticSnapshot(errors)
                        val syntax =
                            if (request.key.kind == CursorKind.COMPLETION) {
                                XdkSyntaxCompletions.complete(
                                    source.toRawString(),
                                    request.position,
                                    model.sites.singleOrNull()?.declarationNameType,
                                ) { isStale(request) }
                            } else {
                                emptyList()
                            }
                        model.withSyntaxCompletions(syntax)
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
                    ),
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

        /**
         * Captured compilation inputs and scheduling state for one module analysis. Later workspace changes
         * create a new request rather than mutating these inputs.
         */
        private class Request(
            val scope: String,
            val uri: String,
            val overlays: Map<String, String>,
            val dependencies: XdkDependencies,
            val project: XdkProject,
            val problem: String?,
            trace: CompilerQueueTrace,
            work: (Request) -> Unit,
            /** An embedded build's compile: ends when the build is cancelled, never superseded by an edit. */
            val buildCancelled: (() -> Boolean)? = null,
        ) {
            val result = CompletableFuture<CompilationResult>()

            /** A build's compile runs inside the build's own queued task, so it registers no work of its own. */
            private val queued = if (buildCancelled == null) trace.task("compile", scope, result) { work(this) } else null

            /** The queued work of an editor compile. */
            val task: CompilerQueueTrace.Work get() = checkNotNull(queued) { "A build compile is never queued on its own" }

            /** How far this analysis has got in reading its sources; see [readsUnchanged]. */
            @Volatile
            var sources: SourceRead = SourceRead.Pending
        }

        /** How far an analysis has got in reading its sources from disk and the editor's buffers. */
        private sealed interface SourceRead {
            /** Not read yet: it reads the files as they are after every file event received so far. */
            data object Pending : SourceRead

            /** Being read: a file may have been read before or after a change to it. */
            data object Reading : SourceRead

            /** Read; null when the analysis compiles only an open buffer. */
            data class Read(
                val inputs: XdkSources.Inputs?,
            ) : SourceRead
        }

        /** New compilation request together with the superseded work that must be retired. */
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
            /** The module's callable entry methods, found on the compiler worker ([entryMethodsOf]). */
            val entryMethods: List<String> = emptyList(),
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
                        }?.value

            fun sourceUri(name: String?): String? =
                documents.entries.firstOrNull { it.value.semantics?.sourceName == name }?.key
                    ?: dependencySources[name]
                    ?: XdkLibrarySources.sourceUri(name)
        }

        /**
         * Compiler output assembled for publication; AST and semantic data may be absent after a failed
         * attempt.
         */
        private data class Analysis(
            val diagnostics: List<Diagnostic> = emptyList(),
            val symbols: List<SymbolInfo> = emptyList(),
            val ast: AstNode? = null,
            val semantics: SemanticModel? = null,
            val colors: List<XdkColorPrototype.Site> = emptyList(),
        )

        private fun module(uri: String): ModuleAnalysis? = completed[analysisScope(uri)]

        private fun analysis(uri: String): Analysis? = module(uri)?.document(uri)

        /** Explicit source graph; changed inputs retire affected analyses and all pending attempts. */
        fun replaceSourceModules(modules: List<XdkSourceModule>): Set<String> = installSourceModules(modules, explicit = true)

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
                    ) {
                        return emptySet()
                    }
                    replaceProject(
                        replacement,
                        if (clearBuildArtifacts) XdkDependencies(emptyList()) else dependencies,
                        force || recovered,
                    )
                }
            retired.forEach { it.result.cancel(false) }
            probes.forEach { it.result.cancel(false) }
            return replacement.orderedScopes(
                retired.flatMapTo(linkedSetOf()) { replacement.affected(it.scope) + it.scope },
            )
        }

        /** Called under lifecycle. Only successful analyses with unchanged inputs may survive. */
        private fun replaceProject(
            replacement: XdkProject,
            artifacts: XdkDependencies,
            force: Boolean = false,
        ): Pair<List<Request>, List<QueryWork>> {
            val changed =
                project.changedModules(replacement) +
                    (dependencies.modules.keys + artifacts.modules.keys).filter {
                        dependencies.modules[it]?.revision != artifacts.modules[it]?.revision
                    }
            val retired =
                requests.values
                    .filter { request ->
                        val analysis = completed[request.scope]
                        force || !request.result.isDone || analysis?.succeeded != true ||
                            analysis.dependencies.any(changed::contains) ||
                            project.buildOrder(request.scope).any { it.name in changed } ||
                            replacement.buildOrder(request.scope).any { it.name in changed } ||
                            request.overlays.keys.any { project.scope(it) != replacement.scope(it) }
                    }.mapTo(linkedSetOf()) { it.scope }
            project = replacement
            dependencies = artifacts
            if (force) builds.clear() else builds.keys.removeAll { it in changed || it !in replacement.modules }
            return retireRequests(retired)
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
        internal fun replaceBuildInputs(
            inputs: XdkBuildModel.Inputs,
            automatic: Boolean = false,
        ): Set<String> {
            val replacement = XdkProject(inputs.modules)
            val artifacts = XdkDependencies(inputs.binaries)
            val (retired, probes) =
                synchronized(lifecycle) {
                    check(!closed) { "XDK adapter is closed" }
                    discovery.updateAndGet {
                        it.copy(explicit = !automatic, buildModel = !automatic, problem = null)
                    }
                    if (
                        project.sameConfiguration(replacement) &&
                        dependencies.modules.mapValues { it.value.revision } ==
                        artifacts.modules.mapValues { it.value.revision }
                    ) {
                        return emptySet()
                    }
                    replaceProject(replacement, artifacts)
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
            if (isLibraryDocument(uri)) return emptySet()
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
                        }?.readText()
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
                            projectQueries.values.map { it.result } + libraryQueries.keys
                    requests.clear()
                    cursors.clear()
                    renames.clear()
                    projectQueries.clear()
                    libraryQueries.clear()
                    diagnosticCache.clear()
                    navigationIndex.clear()
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
            request.buildCancelled?.invoke() ?: (requests[request.scope] !== request || request.result.isCancelled)

        private fun captureSources(
            request: Request,
            cancelled: () -> Boolean,
        ): XdkSources? =
            XdkSources
                .file(request.scope)
                ?.takeIf {
                    it.isFile ||
                        it != XdkSources.file(request.uri) ||
                        File(it.parentFile, it.nameWithoutExtension).isDirectory
                }?.let { root ->
                    val resources =
                        request.project.modules.values
                            .firstOrNull { it.root == root }
                            ?.resourceFiles
                    XdkSources.capture(root, request.overlays, resources, cancelled)
                }

        /** Exact source inputs and dependency revisions that determine whether a compiled module can be reused. */
        private data class BuildKey(
            val sources: XdkSources.Inputs,
            val dependencies: Map<String, String>,
        )

        /** Reusable build output and diagnostics indexed by the exact inputs that produced them. */
        private data class CachedBuild(
            val key: BuildKey,
            val artifact: XdkDependency?,
            val diagnostics: List<Diagnostic>,
            val documentUris: Set<String>,
        )

        /**
         * Capture the complete dependency closure before compiling any of it. Retain detached
         * dependency outputs; only the target analysis needs a live AST after this loop.
         */
        private fun compileNow(request: Request): ModuleAnalysis {
            request.problem?.let {
                return unavailableProjectModule(request.uri, request.dependencies, Diagnostic.Codes.SOURCE_GRAPH, it)
            }
            val order = request.project.buildOrder(request.scope)
            if (order.isEmpty()) {
                return compileOne(
                    request,
                    request.uri,
                    reading(request, { it?.inputs }) { captureSources(request) { isStale(request) } },
                    request.dependencies,
                )
            }
            val diagnostics = mutableListOf<Diagnostic>()
            val documents = linkedSetOf<String>()
            val target =
                compileClosure(request, order, { it.uri != request.scope }) { _, analysis, _, _ ->
                    diagnostics += analysis.diagnostics
                    documents += analysis.documentUris
                }
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
                entryMethods = target.entryMethods,
            )
        }

        /**
         * Compiles [order], a dependency-ordered closure, from one capture of its sources. A module whose
         * sources and dependency revisions exactly match an earlier compile is answered from that compile
         * when [reusable] allows it. [each] sees every module's outcome in order, so callers keep only what
         * they need and dependency trees are released as the loop moves on.
         *
         * @return the last module's analysis
         */
        private fun compileClosure(
            request: Request,
            order: List<XdkSourceModule>,
            reusable: (XdkSourceModule) -> Boolean,
            each: (module: XdkSourceModule, analysis: ModuleAnalysis, reused: Boolean, sources: XdkSources?) -> Unit,
        ): ModuleAnalysis {
            val captured =
                reading(
                    request,
                    { reads ->
                        reads.entries
                            .firstOrNull { it.key.uri == request.scope }
                            ?.value
                            ?.getOrNull()
                            ?.inputs
                    },
                ) {
                    order.associateWith { module ->
                        try {
                            Result.success(
                                XdkSources.capture(module.root, request.overlays, module.resourceFiles) {
                                    isStale(request)
                                },
                            )
                        } catch (failure: IOException) {
                            Result.failure(failure)
                        }
                    }
                }
            // A source-owned module must never fall back to an older host-supplied binary.
            val artifacts =
                request.dependencies.modules
                    .filterKeys { it !in request.project.modules }
                    .toMutableMap()
            var last: ModuleAnalysis? = null
            for (module in order) {
                if (isStale(request)) throw CancellationException()
                val closure = request.project.buildOrder(module.uri).mapTo(hashSetOf()) { it.name }
                val inputs =
                    XdkDependencies(
                        artifacts.filterKeys { it !in request.project.modules || it in closure }.values.toList(),
                    )
                val sources = captured.getValue(module).getOrNull()
                val uri = sources?.uri(module.root) ?: module.uri
                val missing =
                    module.dependencies.filter { it !in artifacts && it !in XdkLibraries.moduleNames }
                val key =
                    sources?.let {
                        BuildKey(it.inputs, inputs.modules.mapValues { (_, artifact) -> artifact.revision })
                    }
                val cached =
                    builds[module.name]?.takeIf {
                        key != null && it.key == key && reusable(module)
                    }
                val analysis =
                    when {
                        sources == null -> {
                            val reason = captured.getValue(module).exceptionOrNull()?.message
                            unavailableProjectModule(
                                uri,
                                inputs,
                                Diagnostic.Codes.SOURCE_UNAVAILABLE,
                                "Cannot read source for ${module.name}: $reason",
                            )
                        }

                        missing.isNotEmpty() -> {
                            unavailableProjectModule(
                                uri,
                                inputs,
                                Diagnostic.Codes.DEPENDENCY_FAILED,
                                "Dependencies unavailable: ${missing.joinToString()}",
                                sources.documentUris,
                                structure = sources,
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

                        else -> compileOne(request, uri, sources, inputs, module.name)
                    }
                synchronized(lifecycle) {
                    if (isStale(request)) throw CancellationException()
                    if (key != null) {
                        builds[module.name] =
                            CachedBuild(
                                key,
                                analysis.artifact,
                                analysis.diagnostics,
                                analysis.documentUris,
                            )
                    }
                }
                analysis.artifact?.let { artifacts[module.name] = it }
                each(module, analysis, cached != null, sources)
                last = analysis
            }
            return checkNotNull(last) { "An empty closure has no module to compile" }
        }

        /**
         * A module that cannot be compiled reports only why. With readable [structure] it keeps syntax-only
         * views, outline, folding and selection, from parsing each source alone, which needs no dependency;
         * semantic views stay absent rather than guess at a module that did not compile.
         */
        private fun unavailableProjectModule(
            uri: String,
            inputs: XdkDependencies,
            code: String,
            message: String,
            documents: Set<String> = setOf(uri),
            structure: XdkSources? = null,
        ): ModuleAnalysis {
            val diagnostics = listOf(Diagnostic(Location(uri, 0, 0, 0, 0), Diagnostic.Severity.ERROR, message, code, Diagnostic.SOURCE))
            return ModuleAnalysis(
                documents = structure?.let { structuralViews(it, diagnostics) }.orEmpty(),
                diagnostics = diagnostics,
                dependencies = emptySet(),
                succeeded = false,
                dependencySources = emptyMap(),
                inputs = inputs,
                documentUris = documents,
            )
        }

        /** Parse errors are not reported: the module's own diagnostic already says why it was not compiled. */
        private fun structuralViews(
            sources: XdkSources,
            diagnostics: List<Diagnostic>,
        ): Map<String, Analysis> =
            sources.inputs.text.entries.associate { (file, text) ->
                val uri = sources.uri(file)
                val ast =
                    ExecutionTrace.api("Parser.parseSource(structure)", uri) {
                        runCatching { Parser(Source(text, file.path), ErrorList(MAX_STRUCTURE_ERRORS)).parseSource() }.getOrNull()
                    }
                uri to Analysis(diagnostics.filter { it.location.uri == uri }, XdkSymbols.of(uri, ast), ast)
            }

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
                    compiler.compileTree(sources.freshCompilationInput(), dependencies.repository, errs)
                }
            if (isStale(request)) throw CancellationException()
            if (sources != null && !sources.resourcesCurrent { isStale(request) }) {
                throw CancellationException()
            }
            val footprint =
                ExecutionTrace.api("EmbeddingSupport.footprint", request.uri) {
                    EmbeddingSupport.instance().footprint(compilation)
                }
            logger.info("compile: scope={} [{}]", request.scope, footprint)
            val roots =
                buildMap {
                    compilation.sourceTrees().forEach { putAll(XdkAst.rootsBySource(it)) }
                }
            val semantics = compilation.compiledSemantics(dependencies, errs, navigation = moduleName != null)
            val colors = if (colorPrototype) XdkColorPrototype.capture(compilation) else emptyMap()
            val navigation = semantics.navigation
            val views = semantics.models
            val sourceUris = sources?.sourceUris ?: roots.keys.associateWith { it }
            val fallback = if (sources == null) source else Source("", sources.uri(sources.sourceFile))
            val declarations = XdkAst.declarationLocations(compilation.sourceTrees(), sourceUris)
            val diagnostics = heard.errors.map { it.toDiagnostic(fallback, sourceUris, declarations) }
            val documentUris = sources?.documentUris ?: setOf(uri)
            val entryMethods =
                (roots.values.firstNotNullOfOrNull(::moduleDeclaration)?.component as? ModuleStructure)
                    ?.let { entryMethodsOf(it, compilation.succeeded()) }
                    .orEmpty()
            val documents =
                documentUris.associateWith { uri ->
                    val sourceName = sourceUris.entries.firstOrNull { it.value == uri }?.key ?: uri
                    val ast = roots[sourceName]
                    Analysis(
                        diagnostics.filter { it.location.uri == uri },
                        XdkSymbols.of(uri, ast),
                        ast,
                        views.firstOrNull { it.sourceName == sourceName },
                        colors[sourceName].orEmpty(),
                    )
                }
            val dependencySources =
                dependencies.declarations.values
                    .mapNotNull { declaration ->
                        val name = declaration.location.sourceName ?: return@mapNotNull null
                        val uri = XdkSources.sourceUri(name)
                        uri?.let { name to it }
                    }.toMap()
            val artifact = semantics.artifact?.takeIf { moduleName != null }
            if (artifact != null && artifact.module != moduleName) {
                return unavailableProjectModule(
                    uri,
                    inputs,
                    Diagnostic.Codes.PROJECT_MODULE,
                    "Expected module $moduleName, found ${artifact.module}",
                    documentUris,
                )
            }
            if (navigation != null && artifact != null && sources != null) {
                synchronized(lifecycle) {
                    if (isStale(request)) throw CancellationException()
                    navigationIndex.record(
                        request.project.modules
                            .getValue(artifact.module)
                            .uri,
                        XdkNavigationIndex.Build(
                            XdkNavigationIndex.Key(
                                artifact.module,
                                sources.inputs,
                                inputs.modules.mapValues { it.value.revision },
                            ),
                            navigation,
                            artifact,
                        ),
                    )
                }
            }
            return ModuleAnalysis(
                documents = documents,
                diagnostics = diagnostics,
                dependencies =
                    compilation
                        .file()
                        ?.moduleIds()
                        ?.mapTo(linkedSetOf()) { it.name }
                        .orEmpty(),
                succeeded = compilation.succeeded(),
                dependencySources = dependencySources,
                inputs = inputs,
                artifact = artifact,
                sourceInputs = sources?.inputs,
                sourceTexts =
                    sources
                        ?.inputs
                        ?.text
                        ?.entries
                        ?.associate { (file, text) -> file.path to text }
                        ?: mapOf(uri to request.overlays.getValue(uri)),
                entryMethods = entryMethods,
            )
        }

        /**
         * Entry methods the runner can call in [module]: a `run` method, not a function, with at most one
         * required parameter. A module that [compiled] is asked through its type, which includes the methods
         * its annotations contribute, as the runner finds them; one that did not offers the `run` it declares,
         * so its run reports why it cannot start. Runs on the compiler worker, which owns the module's
         * structures.
         */
        private fun entryMethodsOf(
            module: ModuleStructure,
            compiled: Boolean,
        ): List<String> {
            val runs =
                if (compiled) {
                    ConstantPool.withPool(module.constantPool).use {
                        val info =
                            ExecutionTrace.api("TypeConstant.ensureTypeInfo(entry-methods)") {
                                module.identityConstant.type.ensureTypeInfo(ErrorList())
                            }
                        info.findMethods("run", -1, TypeInfo.MethodKind.Method).mapNotNull { info.getMethodById(it)?.head?.methodStructure }
                    }
                } else {
                    (module.getChild("run") as? MultiMethodStructure)?.methods().orEmpty()
                }
            return if (runs.any { !it.isFunction && it.requiredParamCount <= 1 }) listOf("run") else emptyList()
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
                            SemanticModel.Usage.READ_WRITE,
                            -> DocumentHighlight.HighlightKind.WRITE

                            null -> DocumentHighlight.HighlightKind.TEXT
                        }
                    DocumentHighlight(it.range.toRange(), kind)
                }
        }

        override fun getSemanticTokens(uri: String): SemanticTokens? =
            analysis(uri)?.semantics?.let {
                XdkPresentation.tokens(it, XdkLexical.tokens(currentText(uri).orEmpty()))
            }

        override fun getDocumentColors(uri: String): List<DocumentColor> = XdkColorPrototype.colors(analysis(uri)?.colors.orEmpty())

        override fun getColorPresentations(
            uri: String,
            range: Range,
            color: ColorValue,
        ): List<ColorPresentation> =
            if (readOnlyDocument(uri) != null) {
                emptyList()
            } else {
                listOfNotNull(analysis(uri)?.colors?.singleOrNull { it.range == range }?.presentation(color))
            }

        private fun currentText(uri: String): String? =
            analysis(uri)?.ast?.source?.toRawString() ?: synchronized(lifecycle) { overlays[uri] }

        override fun formatDocument(
            uri: String,
            content: String,
            options: FormattingOptions,
        ): List<TextEdit> =
            if (isLibraryDocument(uri)) {
                emptyList()
            } else {
                XdkLexical.format(
                    content,
                    FormattingConfig.resolve(options, editorFormattingConfig),
                    options,
                )
            }

        override fun formatRange(
            uri: String,
            content: String,
            range: Range,
            options: FormattingOptions,
        ): List<TextEdit> =
            if (isLibraryDocument(uri)) {
                emptyList()
            } else {
                XdkLexical.format(
                    content,
                    FormattingConfig.resolve(options, editorFormattingConfig),
                    options,
                    range,
                )
            }

        /**
         * Re-indents the line being typed. Its trailing whitespace is kept, as the cursor is there (such as
         * after a comment continuation's `// `). After Enter, a blank new line gets the indentation of the
         * code that will be typed on it, rather than keeping or losing whatever the editor guessed.
         */
        override fun onTypeFormatting(
            uri: String,
            line: Int,
            column: Int,
            ch: String,
            options: FormattingOptions,
        ): List<TextEdit> =
            currentText(uri)
                ?.let { text ->
                    val config = FormattingConfig.resolve(options, editorFormattingConfig)
                    val typing = options.copy(trimTrailingWhitespace = false)
                    val current = text.lines().getOrNull(line) ?: return@let emptyList()
                    if (ch == "\n" && current.isBlank()) {
                        XdkLexical
                            .blankLineIndentation(text, line, config, typing)
                            ?.takeIf { it != current }
                            ?.let { listOf(TextEdit(Range(Position(line, 0), Position(line, current.length)), it)) }
                            .orEmpty()
                    } else {
                        XdkLexical.format(text, config, typing, Range(Position(line, 0), Position(line, current.length)), wrapLines = false)
                    }
                }.orEmpty()

        override fun getDocumentLinks(
            uri: String,
            content: String,
        ): List<DocumentLink> {
            val module = module(uri)
            val document = module?.document(uri)
            val links =
                document
                    ?.takeIf { it.ast?.source?.toRawString() == content }
                    ?.semantics
                    ?.sourceLinks
                    .orEmpty()
                    .mapNotNull { link ->
                        module?.sourceUri(link.target.sourceName)?.let { target ->
                            DocumentLink(link.range.toRange(), target, "Open imported source")
                        }
                    }
            return (XdkLexical.links(content) + links).distinctBy { it.range }.sortedBy { it.range.start.line }
        }

        override fun getCodeLenses(uri: String): List<CodeLens> = getCodeLensesAsync(uri, true).join()

        override fun getCodeLensesAsync(
            uri: String,
            references: Boolean,
        ): CompletableFuture<List<CodeLens>> {
            val run = runLenses(uri)
            return if (references && hasProject(uri) && !isLibraryDocument(uri)) {
                projectQuery(ProjectQueryKey(uri, ProjectQueryKind.CODE_LENSES), emptyList()) {
                    run + it.referenceLenses(uri)
                }
            } else {
                CompletableFuture.completedFuture(run)
            }
        }

        /** Only modules with a callable entry point get a Run lens. */
        private fun runLenses(uri: String): List<CodeLens> =
            analysis(uri)
                ?.symbols
                .orEmpty()
                .filter { it.kind == SymbolInfo.SymbolKind.MODULE }
                .filter { entryMethods(uri).isNotEmpty() }
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
            if (isLibraryDocument(uri)) return null
            val model =
                analysis(uri)?.semantics?.takeIf { it.status == SemanticModel.Status.COMPLETE }
                    ?: return null
            model.importAt(line, column)?.let { alias ->
                val ranges = (listOf(alias.declaration) + alias.uses).distinct().sortedBy { it.start }
                return ranges.takeIf { it.size > 1 }?.let { LinkedEditingRanges(it.map { range -> range.toRange() }) }
            }
            val symbol =
                model.symbolAt(line, column)?.takeIf {
                    // Lambda arguments are positional and their names are local to this source.
                    // Method parameters have callable slots and may have named callers elsewhere;
                    // those require the complete graph rename operation, even for private methods.
                    it.renameable && it.declarationSource == model.sourceName &&
                        (
                            it.kind == SemanticModel.SymbolKind.VARIABLE ||
                                (it.kind == SemanticModel.SymbolKind.PARAMETER && it.id !in model.parameters)
                        )
                } ?: return null
            val ranges =
                model.occurrences
                    .filter { it.symbol == symbol.id && it.name == symbol.name }
                    .map { it.range.toRange() }
                    .distinct()
                    .sortedWith(compareBy({ it.start.line }, { it.start.column }))
            return ranges.takeIf { it.size > 1 }?.let { LinkedEditingRanges(it) }
        }

        override fun getInlayHints(
            uri: String,
            range: Range,
        ): List<InlayHint> = analysis(uri)?.semantics?.let { XdkPresentation.hints(it, range) }.orEmpty()

        /**
         * Blocks and declarations that span more than one line. An editor offers a fold per region, so
         * a region per expression would be noise rather than help.
         */
        override fun getFoldingRanges(uri: String): List<FoldingRange> = XdkAst.foldingRegions(analysis(uri)?.ast)

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
                XdkAst.chainAt(ast, position.line, position.column).map(XdkAst::rangeOf).distinct().fold(null as SelectionRange?) {
                    parent,
                    range,
                    ->
                    SelectionRange(range, parent)
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
                }.join()
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

        private fun flatten(symbols: List<SymbolInfo>): List<SymbolInfo> =
            symbols.flatMap {
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
            if (module(uri) == null && hasProject(uri)) {
                return workspaceNavigation(uri)?.definition(uri, line, column)
            }
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
            if (module(uri) == null && hasProject(uri)) {
                return workspaceNavigation(uri)?.declarations(uri, line, column).orEmpty()
            }
            val module = module(uri) ?: return emptyList()
            return module.locations(
                module
                    .document(uri)
                    ?.semantics
                    ?.declarationLocationsAt(line, column)
                    .orEmpty(),
            )
        }

        override fun findMonikers(
            uri: String,
            line: Int,
            column: Int,
        ): List<SymbolMoniker> = findMonikersAsync(uri, line, column).join()

        override fun findMonikersAsync(
            uri: String,
            line: Int,
            column: Int,
        ): CompletableFuture<List<SymbolMoniker>> {
            if (isLibraryDocument(uri)) return libraryMonikers(uri, line, column)
            val current = module(uri)
            if (current == null && hasProject(uri)) {
                return projectQuery(ProjectQueryKey(uri, ProjectQueryKind.MONIKERS), emptyList()) {
                    it.navigation()?.monikers(uri, line, column).orEmpty()
                }
            }
            return CompletableFuture.completedFuture(
                current
                    ?.document(uri)
                    ?.semantics
                    ?.monikersAt(line, column)
                    .orEmpty(),
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
                    moduleReferences(uri, line, column, includeDeclaration),
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
                        }.map { locationOf(sourceUri, it.range.toRange()) }
                }.distinct()
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
            ) {
                return null
            }
            val range =
                model.occurrences
                    .firstOrNull {
                        it.symbol == symbol.id &&
                            it.name != "super" &&
                            SemanticModel.Position(line, column) in it.range
                    }?.range ?: return null
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
            (
                symbol.kind == SemanticModel.SymbolKind.PACKAGE &&
                    symbol.declarationSource == null &&
                    symbol.dependency == null &&
                    hasProject(uri)
            ) ||
                (
                    symbol.name != "construct" &&
                        (
                            symbol.kind != SemanticModel.SymbolKind.PARAMETER ||
                                symbol.id in model.parameters
                        ) &&
                        symbol.kind in
                        setOf(
                            SemanticModel.SymbolKind.METHOD,
                            SemanticModel.SymbolKind.TYPE,
                            SemanticModel.SymbolKind.PROPERTY,
                            SemanticModel.SymbolKind.PACKAGE,
                            SemanticModel.SymbolKind.MODULE,
                            SemanticModel.SymbolKind.PARAMETER,
                        ) &&
                        (
                            symbol.declarationSource
                                ?: model.parameters[symbol.id]
                                    ?.method
                                    ?.let(model::symbol)
                                    ?.declarationSource
                        )?.let {
                            synchronized(lifecycle) {
                                project.scope(uri) != null && project.scope(it) != null
                            }
                        } == true
                )

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

        /** Preserve automatic resource roots when replacing only the graph's binary inputs. */
        internal fun sourceModuleInputs(): List<XdkSourceModule> = synchronized(lifecycle) { project.buildOrder() }

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
        fun renameFilesAsync(files: Map<String, String>): CompletableFuture<WorkspaceEdit?> =
            renameFilesProposalAsync(files).mapCancellable { proposal ->
                proposal?.takeIf { it.sourceModules == null }?.edit?.let { edit ->
                    val originals = files.keys.mapNotNull(XdkSources::file).toSet()
                    edit.copy(renames = edit.renames.filterKeys { XdkSources.file(it) !in originals })
                }
            }

        /** Full file-move transaction with an optional host-owned source graph replacement. */
        fun renameFilesProposalAsync(files: Map<String, String>): CompletableFuture<XdkRenameProposal?> {
            // Refuse links before canonicalization loses the requested file identity or captures a
            // source snapshot for an alias. The compiler proof below performs the remaining checks.
            if (
                files.keys.any { uri ->
                    runCatching { Files.isSymbolicLink(Path.of(URI(uri))) }.getOrDefault(true)
                }
            ) {
                return CompletableFuture.completedFuture(null)
            }
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
                it.renameFilesProposal(files)
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
                    it.codeActions(uri, range, diagnostics)
                }
            } else {
                CompletableFuture.completedFuture(
                    if (isLibraryDocument(uri)) {
                        emptyList()
                    } else {
                        analysis(uri)
                            ?.let { analysis ->
                                val model = analysis.semantics
                                val text = currentText(uri)
                                if (model == null || text == null) emptyList() else XdkDocumentation.actions(uri, text, range, model)
                            }.orEmpty()
                    },
                )
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
                    if (closed) return closedAdapter()
                    val compilation = currentCompilation(uri) ?: return CompletableFuture.completedFuture(null)
                    val module =
                        completed[compilation.scope]?.takeIf { it.succeeded }
                            ?: return CompletableFuture.completedFuture(null)
                    if (prepareRename(uri, line, column) == null) {
                        return CompletableFuture.completedFuture(null)
                    }
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
                    request to replacePending(renames, uri, request)
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
                return if (compilation.succeeded() && !heard.hasSeriousErrors()) {
                    compilation.renameFacts(dependencies)
                } else {
                    null
                }
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

        private fun hasProject(uri: String): Boolean = synchronized(lifecycle) { project.scope(uri) != null }

        private fun workspaceNavigation(uri: String): XdkWorkspaceNavigation? =
            projectQuery<XdkWorkspaceNavigation?>(
                ProjectQueryKey(uri, ProjectQueryKind.NAVIGATION),
                null,
            ) {
                it.navigation()
            }.join()

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
            if (module(uri) == null && hasProject(uri)) {
                return workspaceNavigation(uri)?.typeDefinitions(uri, line, column).orEmpty()
            }
            val module = module(uri) ?: return emptyList()
            return module.locations(
                module
                    .document(uri)
                    ?.semantics
                    ?.typeDefinitionLocationsAt(line, column)
                    .orEmpty(),
            )
        }

        override fun findImplementation(
            uri: String,
            line: Int,
            column: Int,
        ): List<Location> {
            if (hasProject(uri)) {
                return workspaceNavigation(uri)?.implementations(uri, line, column).orEmpty()
            }
            val module = module(uri) ?: return emptyList()
            return module.locations(
                module
                    .document(uri)
                    ?.semantics
                    ?.implementationLocationsAt(line, column)
                    .orEmpty(),
            )
        }

        private fun ModuleAnalysis.locations(locations: List<SemanticModel.SourceLocation>): List<Location> =
            locations
                .mapNotNull { target ->
                    sourceUri(target.sourceName)?.let { locationOf(it, target.range.toRange()) }
                }.distinct()
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

        private fun SemanticModel.Range.toRange(): Range = Range(Position(start.line, start.column), Position(end.line, end.column))

        private fun locationOf(
            uri: String,
            range: Range,
        ): Location = Location(uri, range.start.line, range.start.column, range.end.line, range.end.column)

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
        ): CompletableFuture<List<CompletionItem>> {
            val compilation = synchronized(lifecycle) { requests[analysisScope(uri)] }
            val text = compilation?.overlays?.get(uri)
            return analyzeAtAsync(CursorKey(uri, CursorKind.COMPLETION), Position(line, column))
                .composeCancellable { partial ->
                    val ordinary = partial?.let(XdkCursorQueries::completions).orEmpty()
                    val site = partial?.sites?.singleOrNull()
                    val prefix = site?.memberPrefix
                    if (compilation == null || isStale(compilation)) {
                        CompletableFuture.completedFuture(emptyList())
                    } else if (text == null || site?.kind != PartialSemanticModel.Kind.NAME ||
                        prefix == null || prefix.text.length < 2 || !hasProject(uri)
                    ) {
                        CompletableFuture.completedFuture(ordinary)
                    } else {
                        projectQuery(ProjectQueryKey(uri, ProjectQueryKind.COMPLETION_IMPORTS), emptyList()) {
                            if (isStale(compilation)) return@projectQuery emptyList()
                            val imports = it.importCompletions(uri, text, prefix, ordinary.map { item -> item.label }.toSet())
                            if (isStale(compilation)) emptyList() else ordinary + imports
                        }
                    }
                }
        }

        override fun getInlineCompletionsAsync(
            uri: String,
            position: Position,
            context: InlineCompletionContext,
        ): CompletableFuture<List<TextEdit>> {
            val compilation =
                synchronized(lifecycle) { requests[analysisScope(uri)] }
                    ?: return CompletableFuture.completedFuture(emptyList())
            val text = compilation.overlays[uri] ?: return CompletableFuture.completedFuture(emptyList())
            if (isLibraryDocument(uri) || !XdkInlineCompletions.eligible(text, position, context)) {
                return CompletableFuture.completedFuture(emptyList())
            }
            return analyzeAtAsync(CursorKey(uri, CursorKind.INLINE_COMPLETION), position)
                .mapCancellable { partial ->
                    if (partial == null || isStale(compilation)) {
                        emptyList()
                    } else {
                        XdkInlineCompletions.project(text, position, context, XdkCursorQueries.completions(partial))
                    }
                }
        }

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
            }.apply { removeOnCancelPolicy = true }

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
        private val libraryQueries = ConcurrentHashMap<CompletableFuture<*>, CompilerQueueTrace.Work>()
        private val diagnosticCache = XdkDiagnosticIndex()
        private val navigationIndex = XdkNavigationIndex()

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
            const val SHUTDOWN_SECONDS = 5L

            /** Enough to keep parsing past ordinary mistakes; structural views do not report them. */
            const val MAX_STRUCTURE_ERRORS = 100
            const val DEBOUNCE_MILLIS = 100L

            /** A file event's update is read again when a background walk installs first; it rarely needs a second try. */
            const val MAX_EVENT_ATTEMPTS = 3

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
                    (
                        cursor.line < position.line ||
                            (cursor.line == position.line && cursor.offset < position.column)
                    )
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
