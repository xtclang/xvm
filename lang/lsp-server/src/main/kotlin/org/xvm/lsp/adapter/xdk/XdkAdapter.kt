package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
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
 * Diagnostics from the real XTC compiler, through the embedding API.
 *
 * ## One compilation at a time
 *
 * Compilations are serialised onto a single worker thread. This is not caution for its own sake:
 * [EmbeddingSupport] is a singleton holding a configured repository, the compiler interns types in
 * a shared constant pool, and several stages report through a destination held on the object being
 * compiled. None of that was written with two concurrent compilations in mind, and an editor is
 * exactly the caller that would produce them - a keystroke in one file while another is still
 * being analysed. Queueing is the honest way to use it until the compiler says otherwise.
 *
 * Edits replace queued work for the same document and cooperatively cancel any older compilation.
 * Only the current request may install an AST in the cache. Closing a document invalidates its
 * request, including across a later reopen of the same URI.
 *
 * ## What this reports
 *
 * Everything the compiler has to say about one document: syntax and semantics both, with the
 * compiler's own error codes, messages and source spans. It needs an XDK to resolve the core
 * library against - [EmbeddingSupport] finds one from `XDK_HOME` - and says so as a diagnostic
 * rather than failing, because an editor with no XDK configured should still open files.
 *
 * The parsed tree supplies outlines, hover, folding and selection; resolved names supply
 * definitions and references within the document. Project-wide compilation remains unsupported.
 */
class XdkAdapter internal constructor(
    private val compileSource: (Source, ErrorListener) -> EmbeddingSupport.Compilation,
) : AbstractAdapter() {
    constructor() : this({ source, errs -> EmbeddingSupport.instance().compileModule(source, null, errs) })

    override val displayName: String = "XDK"

    override val capabilities: Set<AdapterCapability> =
        setOf(
            AdapterCapability.HOVER,
            AdapterCapability.DEFINITION,
            AdapterCapability.REFERENCES,
            AdapterCapability.DOCUMENT_SYMBOL,
            AdapterCapability.DOCUMENT_HIGHLIGHT,
            AdapterCapability.SELECTION_RANGE,
            AdapterCapability.FOLDING_RANGE,
            AdapterCapability.WORKSPACE_SYMBOL,
        )

    override fun healthCheck(): Boolean = runCatching { EmbeddingSupport.instance() }.isSuccess

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult = compileAsync(uri, content).join()

    override fun compileAsync(
        uri: String,
        content: String,
    ): CompletableFuture<CompilationResult> {
        val request = Request()
        val queued = System.nanoTime()
        request.task =
            Runnable {
                try {
                    if (isStale(uri, request)) throw CancellationException()
                    val started = System.nanoTime()
                    val result = compileNow(uri, content, request)
                    synchronized(lifecycle) {
                        if (isStale(uri, request)) throw CancellationException()
                        cached[uri] = result
                    }
                    logger.info(
                        "compile: uri={}, {} bytes, {} diagnostic(s), {} symbol(s), waited {}, compiled in {}",
                        uri,
                        content.length,
                        result.diagnostics.size,
                        result.symbols.size,
                        (started - queued).nanoseconds,
                        (System.nanoTime() - started).nanoseconds,
                    )
                    // Completing a future invokes its callbacks. Never do that under our lock:
                    // a callback can publish diagnostics while the server handles another edit.
                    request.result.complete(CompilationResult.withDiagnostics(uri, result.diagnostics, result.symbols))
                } catch (_: CancellationException) {
                    request.result.cancel(false)
                } catch (e: Exception) {
                    request.result.completeExceptionally(e)
                } catch (e: Error) {
                    request.result.completeExceptionally(e)
                    throw e
                }
            }
        request.result.whenComplete { _, _ ->
            if (request.result.isCancelled) {
                synchronized(lifecycle) {
                    if (requests.remove(uri, request)) cached.remove(uri)
                    compiles.remove(request.task)
                }
            }
        }
        val previous =
            synchronized(lifecycle) {
                if (closed) return CompletableFuture.failedFuture(IllegalStateException("XDK adapter is closed"))
                val previous = requests.put(uri, request)
                cached.remove(uri)
                previous?.let { compiles.remove(it.task) }
                compiles.execute(request.task)
                previous
            }
        previous?.result?.cancel(false)
        return request.result
    }

    override fun getCachedResult(uri: String): CompilationResult? =
        cached[uri]?.let { CompilationResult.withDiagnostics(uri, it.diagnostics, it.symbols) }

    private class Request {
        val result = CompletableFuture<CompilationResult>()
        lateinit var task: Runnable
    }

    /**
     * What one compilation produced: the problems, the shape of what was written, and the tree
     * itself.
     *
     * The tree is kept because every question about a position needs it, and re-parsing to answer
     * one would mean a compilation per keystroke of hovering. It is the largest thing the adapter
     * holds - one validated AST per open document - so it is worth watching `heap` in the
     * footprint line if a lot of documents are open at once.
     */
    private data class Analysis(
        val diagnostics: List<Diagnostic> = emptyList(),
        val symbols: List<SymbolInfo> = emptyList(),
        val ast: AstNode? = null,
    )

    override fun closeDocument(uri: String) {
        val previous =
            synchronized(lifecycle) {
                cached.remove(uri)
                requests.remove(uri)?.also { compiles.remove(it.task) }
            }
        previous?.result?.cancel(false)
    }

    override fun close() {
        val pending =
            synchronized(lifecycle) {
                closed = true
                val pending = requests.values.toList()
                requests.clear()
                cached.clear()
                compiles.queue.clear()
                compiles.shutdown()
                pending
            }
        pending.forEach { it.result.cancel(false) }
        if (!compiles.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            compiles.shutdownNow()
        }
    }

    private fun isStale(
        uri: String,
        request: Request,
    ): Boolean = requests[uri] !== request || request.result.isCancelled

    /** Runs on the single compiler worker. */
    private fun compileNow(
        uri: String,
        content: String,
        request: Request,
    ): Analysis {
        // Deduplicate TypeInfo replay and retain the compiler's normal error budget.
        val heard = ErrorList()
        val compilation =
            try {
                compileSource(Source(content, uri), ErrorListener.cancellable(heard) { isStale(uri, request) })
            } catch (e: IllegalStateException) {
                logger.warn("compile: uri={} has no XDK to resolve against: {}", uri, e.message)
                return Analysis(
                    listOf(
                        Diagnostic(
                            location = wholeDocument(uri),
                            severity = Diagnostic.Severity.WARNING,
                            message = "XTC analysis unavailable: ${e.message}",
                            code = NO_XDK,
                            source = SOURCE,
                        ),
                    ),
                )
            }
        if (isStale(uri, request)) throw CancellationException()
        logger.info("compile: uri={} [{}]", uri, EmbeddingSupport.instance().footprint(compilation))
        if (compiled.incrementAndGet() == 1L) {
            logger.info("compile: first compilation in this server completed (cold)")
        }
        val parsed = compilation.parsed()
        return Analysis(heard.errors.map { it.toDiagnostic(uri) }, XdkSymbols.of(uri, parsed), parsed)
    }

    /**
     * The compiler places a diagnostic in one of three ways, and Site being a closed set is what
     * lets this be exhaustive rather than a hunt for whichever field happens to be populated.
     */
    private fun ErrorListener.ErrorInfo.toDiagnostic(uri: String): Diagnostic =
        Diagnostic(
            location =
                when (val where = site()) {
                    is ErrorListener.Site.In -> spanOf(uri, where)

                    // a structure has no source location of its own
                    is ErrorListener.Site.At -> wholeDocument(uri)

                    // a whole-compilation failure belongs to the document, not to a line in it
                    else -> wholeDocument(uri)
                },
            severity = severity.toLspSeverity(),
            message = message,
            code = code,
            source = SOURCE,
        )

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
    ): SymbolInfo? = XdkSymbols.at(cached[uri]?.symbols ?: emptyList(), line, column)

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
        val type = XdkAst.typeAt(cached[uri]?.ast, line, column)
        return when {
            type == null -> declared
            declared == null -> "```xtc\n$type\n```"
            else -> "$declared\n\n```xtc\n$type\n```"
        }
    }

    /**
     * Where else the name under the cursor is written in this document.
     *
     * By name, not by meaning: two unrelated locals called `count` highlight together. Telling
     * them apart is a resolution question, and the answer to it is not reachable from here -
     * see the note on [findDefinition].
     */
    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        val ast = cached[uri]?.ast ?: return emptyList()
        val under = XdkAst.nameAt(ast, line, column) ?: return emptyList()
        return XdkAst
            .namesIn(ast)
            .filter { it.name == under.name }
            .map { DocumentHighlight(XdkAst.rangeOf(it), DocumentHighlight.HighlightKind.TEXT) }
    }

    /**
     * Blocks and declarations that span more than one line. An editor offers a fold per region,
     * so a region per expression would be noise rather than help.
     */
    override fun getFoldingRanges(uri: String): List<FoldingRange> =
        XdkAst.foldingRegions(cached[uri]?.ast).map { (start, end) -> FoldingRange(start, end) }

    /**
     * Expanding a selection walks out through the tree, which is exactly what the parent chain of
     * the innermost node containing the cursor is.
     */
    override fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange> {
        val ast = cached[uri]?.ast
        return positions.map { position ->
            XdkAst
                .chainAt(ast, position.line, position.column)
                .fold(null as SelectionRange?) { parent, node ->
                    SelectionRange(XdkAst.rangeOf(node), parent)
                } ?: SelectionRange(Range(position, position))
        }
    }

    /**
     * Across every document this server has compiled - which is every document that has been
     * opened, not the whole project. A workspace-wide answer would mean compiling files nobody
     * has looked at.
     */
    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> =
        cached.values
            .flatMap { flatten(it.symbols) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    private fun flatten(symbols: List<SymbolInfo>): List<SymbolInfo> = symbols.flatMap { listOf(it) + flatten(it.children) }

    // ----- what the name resolved to -------------------------------------------------------------

    /**
     * Within this document. A name that refers to something declared elsewhere - anything from
     * the core library - resolves perfectly well and still has nowhere here to point at, so the
     * answer is null rather than a guess.
     */
    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? =
        XdkResolution
            .declarationOf(cached[uri]?.ast, line, column)
            ?.let { locationOf(uri, it) }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> =
        XdkResolution
            .referencesTo(cached[uri]?.ast, line, column, includeDeclaration)
            .map { locationOf(uri, it) }

    private fun locationOf(
        uri: String,
        node: AstNode,
    ): Location =
        XdkAst.rangeOf(node).let {
            Location(uri, it.start.line, it.start.column, it.end.line, it.end.column)
        }

    // ----- not implemented here ------------------------------------------------------------------

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
        triggerCharacter: String?,
    ): List<CompletionItem> = emptyList()

    private val compiled = AtomicLong()
    private val lifecycle = Any()
    private var closed = false

    /** Only current, completed analyses belong here; an edit drops the previous AST. */
    private val cached = ConcurrentHashMap<String, Analysis>()
    private val requests = ConcurrentHashMap<String, Request>()

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
        const val NO_XDK = "XDK-UNAVAILABLE"
        const val SHUTDOWN_SECONDS = 5L
    }
}
