package org.xvm.lsp.adapter.xdk

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
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.InlayHint
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.TypeHierarchyItem
import org.xvm.lsp.adapter.mapCancellable
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.tool.ModuleInfo
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.nanoseconds
import org.xvm.util.Severity as XtcSeverity

/**
 * Compiler diagnostics and semantic navigation for module source trees.
 *
 * A single worker serializes compilations sharing the embedding repository. Each module request
 * captures source membership and text, with open editor buffers taking precedence over disk.
 * A member edit replaces queued module work and cooperatively cancels the previous attempt.
 * Only the current attempt can install its document views, together as one module snapshot.
 *
 * Copied semantic facts support cross-file navigation and declared type hierarchy within a module.
 * Per-source AST roots supply outlines, folding and selection. The server supplies document
 * versions and publishes diagnostics after checking that the whole module request is still current.
 * Matching core/bootstrap XDK libraries are bundled; compilation does not start an interpreter.
 */
class XdkAdapter internal constructor(
    private val compileSource: (Source, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val compileTree: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val analyzeCursor: (Source, ModuleInfo?, Long, ModuleRepository?, ErrorListener) -> EmbeddingSupport.PartialAnalysis,
) : AbstractAdapter() {
    internal constructor(
        compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation,
        compileTree: (ModuleInfo, ErrorListener) -> EmbeddingSupport.Compilation,
        analyzeCursor: (Source, ModuleInfo?, Long, ErrorListener) -> EmbeddingSupport.PartialAnalysis =
            { source, sources, cursor, errors -> analyzeIncomplete(source, sources, cursor, null, errors) },
    ) : this(
        { source, _, errors -> compileSource(source, errors) },
        { sources, _, errors -> compileTree(sources, errors) },
        { source, sources, cursor, _, errors -> analyzeCursor(source, sources, cursor, errors) },
    )

    internal constructor(compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation) : this(
        compileSource,
        { sources, errs ->
            XdkLibraries.configure()
            EmbeddingSupport.instance().compileModule(sources, null, errs)
        },
    )

    constructor() : this(
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
        )

    override fun healthCheck(): Boolean = runCatching { XdkLibraries.configure() }.isSuccess

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult = compileAsync(uri, content).join()

    override fun analysisScope(uri: String): String =
        synchronized(lifecycle) {
            val root = XdkSources.moduleRoot(uri, overlays)
            if (root == XdkSources.file(uri)) {
                scopes[uri] ?: root?.toURI()?.toString() ?: uri
            } else {
                root?.toURI()?.toString() ?: uri
            }
        }

    override fun compileAsync(
        uri: String,
        content: String,
    ): CompletableFuture<CompilationResult> {
        lateinit var request: Request
        val (previous, obsoleteCursors) =
            synchronized(lifecycle) {
                if (closed) return CompletableFuture.failedFuture(IllegalStateException("XDK adapter is closed"))
                overlays[uri] = content
                val scope = analysisScope(uri)
                scopes[uri] = scope
                request = Request(scope, uri, overlays.filterKeys { analysisScope(it) == scope }, dependencies)
                request.task = Runnable { runCompilation(request) }
                request.result.whenComplete { _, _ ->
                    if (request.result.isCancelled) {
                        val obsolete =
                            synchronized(lifecycle) {
                                compiles.remove(request.task)
                                if (requests.remove(scope, request)) {
                                    completed.remove(scope)
                                    retireCursors(scope)
                                } else {
                                    emptyList()
                                }
                            }
                        obsolete.forEach { it.result.cancel(false) }
                    }
                }
                val previous = requests.put(scope, request)
                completed.remove(scope)
                previous?.let { compiles.remove(it.task) }
                compiles.execute(request.task)
                previous to retireCursors(scope)
            }
        previous?.result?.cancel(false)
        obsoleteCursors.forEach { it.result.cancel(false) }
        return request.result
    }

    /**
     * Compiler-worker probe for a current open document. Results contain copied facts only; they
     * neither replace normal diagnostics nor install another module analysis. A newer cursor of
     * the same query kind in this document, or any edit in its module, invalidates the request.
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
                if (closed) return CompletableFuture.failedFuture(IllegalStateException("XDK adapter is closed"))
                val compilation = requests[analysisScope(uri)] ?: return CompletableFuture.completedFuture(null)
                if (uri !in compilation.overlays) return CompletableFuture.completedFuture(null)
                val request = CursorRequest(compilation, key, position, ::runCursorAnalysis)
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
                compiles.execute(request.task)
                request to previous
            }
        previous?.result?.cancel(false)
        return request.result
    }

    private enum class CursorKind { PROBE, COMPLETION, SIGNATURE }

    private data class CursorKey(
        val uri: String,
        val kind: CursorKind,
    )

    private class CursorRequest(
        val compilation: Request,
        val key: CursorKey,
        val position: Position,
        work: (CursorRequest) -> Unit,
    ) {
        val uri: String get() = key.uri
        val result = CompletableFuture<PartialSemanticModel?>()
        val task = Runnable { work(this) }
    }

    /** Called under lifecycle; future callbacks must run after releasing it. */
    private fun retireCursors(scope: String): List<CursorRequest> =
        cursors.values.filter { it.compilation.scope == scope }.onEach {
            cursors.remove(it.key, it)
            compiles.remove(it.task)
        }

    private fun isStale(request: CursorRequest): Boolean =
        cursors[request.key] !== request || request.result.isCancelled || isStale(request.compilation)

    private fun runCursorAnalysis(request: CursorRequest) {
        try {
            if (isStale(request)) throw CancellationException()
            val source = Source(request.compilation.overlays.getValue(request.uri), request.uri)
            val errors = ErrorListener.cancellable(ErrorList()) { isStale(request) }
            val cursor = cursorPosition(source, request.position, errors)
            val facts =
                cursor?.let {
                    val sources = captureSources(request.compilation) { isStale(request) }
                    val dependencies = request.compilation.dependencies.open()
                    analyzeCursor(source, sources, it, dependencies.repository, errors).semanticSnapshot(errors)
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
                    result.documents.keys,
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

    private class Request(
        val scope: String,
        val uri: String,
        val overlays: Map<String, String>,
        val dependencies: XdkDependencies,
    ) {
        val result = CompletableFuture<CompilationResult>()
        lateinit var task: Runnable
    }

    /** Installed atomically, so all member views always belong to the same compilation. */
    private class ModuleAnalysis(
        val documents: Map<String, Analysis>,
        val diagnostics: List<Diagnostic>,
        val dependencies: Set<String>,
        val succeeded: Boolean,
        val dependencySources: Map<String, String>,
    ) {
        val hierarchy = XdkHierarchy(documents.mapNotNull { (uri, analysis) -> analysis.semantics?.let { uri to it } }.toMap())
        val calls = XdkCalls(documents.mapNotNull { (uri, analysis) -> analysis.semantics?.let { uri to it } }.toMap())

        fun document(uri: String): Analysis? =
            documents[uri] ?: documents.entries
                .firstOrNull {
                    XdkSources.file(it.key) != null && XdkSources.file(it.key) == XdkSources.file(uri)
                }?.value

        fun sourceUri(name: String?): String? =
            documents.entries.firstOrNull { it.value.semantics?.sourceName == name }?.key ?: dependencySources[name]
    }

    private data class Analysis(
        val diagnostics: List<Diagnostic> = emptyList(),
        val symbols: List<SymbolInfo> = emptyList(),
        val ast: AstNode? = null,
        val semantics: SemanticModel? = null,
    )

    private fun module(uri: String): ModuleAnalysis? = completed[analysisScope(uri)]

    private fun analysis(uri: String): Analysis? = module(uri)?.document(uri)

    /**
     * Atomically replace host-supplied artifacts and retire affected analyses/cursor probes.
     * The host must reanalyse the returned scopes to publish diagnostics for current document
     * versions; XtcLanguageServer.replaceCompilerDependencies performs that step under its lock.
     * Failed and pending attempts are conservatively retried because their import set is incomplete.
     */
    fun replaceDependencies(artifacts: List<XdkDependency>): Set<String> {
        val replacement = XdkDependencies(artifacts)
        val (retired, probes) =
            synchronized(lifecycle) {
                check(!closed) { "XDK adapter is closed" }
                val changed =
                    (dependencies.modules.keys + replacement.modules.keys).filterTo(linkedSetOf()) {
                        dependencies.modules[it]?.revision != replacement.modules[it]?.revision
                    }
                if (changed.isEmpty()) return emptySet()
                dependencies = replacement
                val retired =
                    requests.values.filter {
                        val analysis = completed[it.scope]
                        analysis == null || !analysis.succeeded || analysis.dependencies.any(changed::contains)
                    }
                val probes =
                    retired.flatMap { request ->
                        requests.remove(request.scope, request)
                        completed.remove(request.scope)
                        compiles.remove(request.task)
                        retireCursors(request.scope)
                    }
                retired to probes
            }
        retired.forEach { it.result.cancel(false) }
        probes.forEach { it.result.cancel(false) }
        return retired.mapTo(linkedSetOf()) { it.scope }
    }

    override fun closeDocument(uri: String) {
        val (previous, obsoleteCursors) =
            synchronized(lifecycle) {
                val scope = scopes.remove(uri) ?: analysisScope(uri)
                overlays.remove(uri)
                completed.remove(scope)
                requests.remove(scope)?.also { compiles.remove(it.task) } to retireCursors(scope)
            }
        previous?.result?.cancel(false)
        obsoleteCursors.forEach { it.result.cancel(false) }
    }

    override fun close() {
        val pending =
            synchronized(lifecycle) {
                closed = true
                val pending = requests.values.map { it.result } + cursors.values.map { it.result }
                requests.clear()
                cursors.clear()
                overlays.clear()
                scopes.clear()
                completed.clear()
                compiles.queue.clear()
                compiles.shutdown()
                pending
            }
        pending.forEach { it.cancel(false) }
        if (!compiles.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) compiles.shutdownNow()
    }

    private fun isStale(request: Request): Boolean = requests[request.scope] !== request || request.result.isCancelled

    private fun captureSources(
        request: Request,
        cancelled: () -> Boolean,
    ): XdkSources? =
        XdkSources
            .file(request.scope)
            ?.takeIf { it.isFile || it != XdkSources.file(request.uri) || File(it.parentFile, it.nameWithoutExtension).isDirectory }
            ?.let { XdkSources.capture(it, request.overlays, cancelled) }

    /** Capture disk and overlays, then compile and copy all source views on the single worker. */
    private fun compileNow(request: Request): ModuleAnalysis {
        val heard = ErrorList()
        val errs = ErrorListener.cancellable(heard) { isStale(request) }
        val source = Source(request.overlays.getValue(request.uri), request.uri)
        val sources = captureSources(request) { isStale(request) }
        val dependencies = request.dependencies.open()
        val compilation =
            if (sources == null) {
                compileSource(source, dependencies.repository, errs)
            } else {
                compileTree(sources, dependencies.repository, errs)
            }
        if (isStale(request)) throw CancellationException()
        logger.info("compile: scope={} [{}]", request.scope, EmbeddingSupport.instance().footprint(compilation))
        if (compiled.incrementAndGet() == 1L) logger.info("compile: first compilation in this server completed (cold)")
        val roots =
            buildMap {
                compilation.sourceTrees().forEach { putAll(XdkAst.rootsBySource(it)) }
            }
        val views = compilation.semanticSnapshots(errs, dependencies)
        val sourceUris = sources?.sourceUris ?: roots.keys.associateWith { it }
        val fallback = if (sources == null) source else Source("", sources.uri(sources.sourceFile))
        val diagnostics = heard.errors.map { it.toDiagnostic(fallback, sourceUris) }
        val documentUris = sources?.documentUris ?: setOf(request.uri)
        val documents =
            documentUris.associateWith { uri ->
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
                }.toMap()
        return ModuleAnalysis(
            documents,
            diagnostics,
            compilation
                .file()
                ?.moduleIds()
                ?.mapTo(linkedSetOf()) { it.name }
                .orEmpty(),
            compilation.succeeded(),
            dependencySources,
        )
    }

    /**
     * The compiler places a diagnostic in one of three ways, and Site being a closed set is what
     * lets this be exhaustive rather than a hunt for whichever field happens to be populated.
     */
    private fun ErrorListener.ErrorInfo.toDiagnostic(
        source: Source,
        sourceUris: Map<String, String>,
    ): Diagnostic {
        val uri = source.fileName
        val where = site()
        val sourceUri =
            if (where is ErrorListener.Site.In) {
                sourceUris[where.source().fileName] ?: if (where.source() === source) uri else where.source().diagnosticUri()
            } else {
                null
            }
        return Diagnostic(
            location =
                when (where) {
                    is ErrorListener.Site.In -> sourceUri?.let { spanOf(it, where) } ?: wholeDocument(uri)

                    // a structure has no source location of its own
                    is ErrorListener.Site.At -> wholeDocument(uri)

                    // a whole-compilation failure belongs to the document, not to a line in it
                    else -> wholeDocument(uri)
                },
            severity = severity.toLspSeverity(),
            message =
                if (where is ErrorListener.Site.In && sourceUri == null) {
                    "In ${where.source().fileName ?: "an unidentified source"}: $message"
                } else {
                    message
                },
            code = code,
            source = SOURCE,
        )
    }

    /** Only absolute source identities can be published as navigable locations. */
    private fun Source.diagnosticUri(): String? {
        val name = fileName ?: return null
        val file = File(name)
        if (file.isAbsolute) return file.toURI().toString()
        return try {
            URI(name).takeIf { it.isAbsolute }?.toString()
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun spanOf(
        uri: String,
        where: ErrorListener.Site.In,
    ): Location =
        Location(
            uri = uri,
            startLine =
                Source.calculateLine(where.lPosStart()),
            startColumn =
                Source.calculateOffset(where.lPosStart()),
            endLine =
                Source.calculateLine(where.lPosEnd()),
            endColumn =
                Source.calculateOffset(where.lPosEnd()),
        )

    private fun wholeDocument(uri: String): Location = Location(uri, 0, 0, 0, 0)

    private fun XtcSeverity.toLspSeverity(): Diagnostic.Severity =
        when (this) {
            XtcSeverity.FATAL, XtcSeverity.ERROR -> Diagnostic.Severity.ERROR
            XtcSeverity.WARNING -> Diagnostic.Severity.WARNING
            XtcSeverity.INFO -> Diagnostic.Severity.INFORMATION
            XtcSeverity.NONE -> Diagnostic.Severity.HINT
        }

    // ----- what the tree can answer --------------------------------------------------------------

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? = XdkSymbols.at(analysis(uri)?.symbols ?: emptyList(), line, column)

    /**
     * The declaration the cursor is in, and - where the compiler validated the expression under
     * it - what that expression's type turned out to be. The type is the half no grammar can
     * supply, and the half an author actually wants from a hover.
     */
    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        val declared = super.getHoverInfo(uri, line, column)
        val type =
            analysis(uri)
                ?.semantics
                ?.typeAt(line, column)
                ?.displayName
        return when {
            type == null -> declared
            declared == null -> "```xtc\n$type\n```"
            else -> "$declared\n\n```xtc\n$type\n```"
        }
    }

    /** Highlight only occurrences of the resolved target, including its declaration when known. */
    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        val model = analysis(uri)?.semantics ?: return emptyList()
        val symbol = model.symbolAt(line, column) ?: return emptyList()
        return model.occurrences.filter { it.symbol == symbol.id }.map {
            val kind =
                when (it.usage) {
                    SemanticModel.Usage.READ -> DocumentHighlight.HighlightKind.READ
                    SemanticModel.Usage.WRITE, SemanticModel.Usage.READ_WRITE -> DocumentHighlight.HighlightKind.WRITE
                    null -> DocumentHighlight.HighlightKind.TEXT
                }
            DocumentHighlight(it.range.toRange(), kind)
        }
    }

    override fun getSemanticTokens(uri: String): SemanticTokens? = analysis(uri)?.semantics?.let(XdkPresentation::tokens)

    override fun getInlayHints(
        uri: String,
        range: Range,
    ): List<InlayHint> = analysis(uri)?.semantics?.let { XdkPresentation.hints(it, range) }.orEmpty()

    /**
     * Blocks and declarations that span more than one line. An editor offers a fold per region,
     * so a region per expression would be noise rather than help.
     */
    override fun getFoldingRanges(uri: String): List<FoldingRange> =
        XdkAst.foldingRegions(analysis(uri)?.ast).map { (start, end) -> FoldingRange(start, end) }

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
            XdkAst
                .chainAt(ast, position.line, position.column)
                .fold(null as SelectionRange?) { parent, node ->
                    SelectionRange(XdkAst.rangeOf(node), parent)
                } ?: SelectionRange(Range(position, position))
        }
    }

    /**
     * Search completed active module sessions, including their closed member files.
     * This does not discover or compile other workspace modules.
     */
    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> =
        completed.values
            .flatMap { it.documents.values }
            .flatMap { flatten(it.symbols) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    private fun flatten(symbols: List<SymbolInfo>): List<SymbolInfo> = symbols.flatMap { listOf(it) + flatten(it.children) }

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
        val module = module(uri) ?: return null
        val declaration = module.document(uri)?.semantics?.definitionLocationAt(line, column) ?: return null
        return module.sourceUri(declaration.sourceName)?.let { locationOf(it, declaration.range.toRange()) }
    }

    override fun findReferences(
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
                        it.symbol == symbol.id && (includeDeclaration || it.role != SemanticModel.Role.DECLARATION)
                    }.map { locationOf(sourceUri, it.range.toRange()) }
            }.distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))
    }

    override fun prepareTypeHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<TypeHierarchyItem> = module(uri)?.hierarchy?.prepare(uri, line, column).orEmpty()

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
            .mapNotNull { target -> sourceUri(target.sourceName)?.let { locationOf(it, target.range.toRange()) } }
            .distinct()
            .sortedWith(compareBy(Location::uri, Location::startLine, Location::startColumn))

    override fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem> = module(item.uri)?.hierarchy?.supertypes(item).orEmpty()

    override fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem> = module(item.uri)?.hierarchy?.subtypes(item).orEmpty()

    override fun prepareCallHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<CallHierarchyItem> = module(uri)?.calls?.prepare(uri, line, column).orEmpty()

    override fun getIncomingCalls(item: CallHierarchyItem): List<CallHierarchyIncomingCall> =
        module(item.uri)?.calls?.incoming(item).orEmpty()

    override fun getOutgoingCalls(item: CallHierarchyItem): List<CallHierarchyOutgoingCall> =
        module(item.uri)?.calls?.outgoing(item).orEmpty()

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
            XdkCursorQueries.signatureHelp(model, position)?.let { return CompletableFuture.completedFuture(it) }
        }
        return analyzeAtAsync(CursorKey(uri, CursorKind.SIGNATURE), Position(line, column))
            .mapCancellable { it?.let { model -> XdkCursorQueries.signatureHelp(model, position) } }
    }

    private val compiled = AtomicLong()
    private val lifecycle = Any()
    private var closed = false

    /** Replaced only under lifecycle; each request captures its immutable dependency set. */
    private var dependencies = XdkDependencies(emptyList())

    /** Only current, completed module analyses belong here; a member edit drops all previous views. */
    private val completed = ConcurrentHashMap<String, ModuleAnalysis>()
    private val overlays = linkedMapOf<String, String>()
    private val scopes = mutableMapOf<String, String>()
    private val requests = ConcurrentHashMap<String, Request>()
    private val cursors = ConcurrentHashMap<CursorKey, CursorRequest>()

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

        /** Translate a UTF-16 editor position without consuming or modifying the compiler input. */
        fun cursorPosition(
            source: Source,
            position: Position,
            errors: ErrorListener,
        ): Long? {
            if (position.line < 0 || position.column < 0) return null
            val cursor = source.clone()
            while (cursor.hasNext() && (cursor.line < position.line || (cursor.line == position.line && cursor.offset < position.column))) {
                if (errors.isAbortDesired) throw CancellationException()
                cursor.next()
            }
            return cursor.position.takeIf { cursor.line == position.line && cursor.offset == position.column }
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
                support.analyzeIncomplete(sources, checkNotNull(XdkSources.file(source.fileName)), cursor, repository, errors)
            }
        }
    }
}
