package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
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
 * The queue is unbounded and strictly ordered, so a request waits for those before it. That is
 * acceptable because the language server already debounces edits; if it stops being acceptable,
 * the fix is cancellation ([ErrorListener.isAbortDesired] is the hook the compiler already has),
 * not concurrency.
 *
 * ## What this reports
 *
 * Everything the compiler has to say about one document: syntax and semantics both, with the
 * compiler's own error codes, messages and source spans. It needs an XDK to resolve the core
 * library against - [EmbeddingSupport] finds one from `XDK_HOME` - and says so as a diagnostic
 * rather than failing, because an editor with no XDK configured should still open files.
 *
 * Symbols and navigation are not implemented here: those need the compiler's symbol table rather
 * than its diagnostics, and the tree-sitter adapter serves them better today.
 */
class XdkAdapter : AbstractAdapter() {
    override val displayName: String = "XDK"

    override fun healthCheck(): Boolean = runCatching { EmbeddingSupport.instance() }.isSuccess

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        val queued = System.nanoTime()
        val depth = compiles.queue.size + 1

        // the edit this call is analysing is the newest one for this document, until it is not
        val generation = edited(uri)
        val (result, waited) =
            compiles
                .submit<Pair<Analysis, Duration>> {
                    val waited = (System.nanoTime() - queued).nanoseconds
                    if (isStale(uri, generation)) {
                        // a newer edit arrived while this one waited; analysing the old text
                        // would publish diagnostics for a document that no longer exists
                        logger.info("compile: uri={} superseded before it started, skipped", uri)
                        Analysis() to waited
                    } else {
                        compileNow(uri, content, generation) to waited
                    }
                }.get()

        // queue wait is reported separately from compile time on purpose: it is the number that
        // says whether serialising compilations has started to hurt, and it is the one that would
        // otherwise be invisible inside a single "how long did that take"
        logger.info(
            "compile: uri={}, {} bytes, {} diagnostic(s), {} symbol(s), queue={}, waited {}, compiled in {} [{}]",
            uri,
            content.length,
            result.diagnostics.size,
            result.symbols.size,
            depth,
            waited,
            lastCompile,
            footprint(),
        )
        cached[uri] = result
        return CompilationResult.withDiagnostics(uri, result.diagnostics, result.symbols)
    }

    override fun getCachedResult(uri: String): CompilationResult? =
        cached[uri]?.let { CompilationResult.withDiagnostics(uri, it.diagnostics, it.symbols) }

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

    override fun close() {
        compiles.shutdown()
        if (!compiles.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            compiles.shutdownNow()
        }
    }

    /**
     * Whether a newer edit has arrived for this document since the given one was queued.
     *
     * Internal rather than private so that it can be tested for what it is - a decision - rather
     * than through a race, which is not a thing a test can make happen on demand.
     */
    internal fun isStale(
        uri: String,
        generation: Long,
    ): Boolean = (newest[uri] ?: generation) > generation

    /**
     * Record an edit of the given document and answer its generation, so that anything queued for
     * an earlier one becomes stale.
     */
    internal fun edited(uri: String): Long = newest.compute(uri) { _, previous -> (previous ?: 0L) + 1 }!!

    /**
     * Runs on the worker thread, one document at a time.
     */
    private fun compileNow(
        uri: String,
        content: String,
        generation: Long,
    ): Analysis {
        // an ErrorList rather than a bare collector, because it is what the compiler's own
        // front end collects into: it filters redundant reports by the compiler's identity rule
        // and stops after a hundred errors. Both matter here. A type's diagnostics are reported
        // once when its TypeInfo is built and again whenever a later stage asks for it, so a
        // collector that keeps everything shows the same warning twice in the Problems panel
        val heard = ErrorList()

        val started = System.nanoTime()
        val parsed: org.xvm.compiler.ast.StatementBlock?
        try {
            // the document is named so that its diagnostics are distinguishable from another
            // unsaved document's
            // the compiler asks isAbortDesired at around twenty points; this is what answers yes
            // when the user has typed again and the answer is no longer wanted, and the ErrorList
            // underneath answers yes once the file has produced more errors than are worth reading
            parsed =
                EmbeddingSupport
                    .instance()
                    .compileModule(
                        Source(content, uri),
                        null,
                        ErrorListener.cancellable(heard) { isStale(uri, generation) },
                    ).parsed()
        } catch (e: IllegalStateException) {
            // no XDK to compile against: report it where the user can see it rather than throwing
            // at the language server, and let them keep editing
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

        lastCompile = (System.nanoTime() - started).nanoseconds
        if (isStale(uri, generation)) {
            // abandoned part-way: what it managed to report describes text the user has already
            // replaced, so publishing it would put stale squiggles in the editor
            logger.info("compile: uri={} superseded after {}, abandoned", uri, lastCompile)
            return Analysis()
        }
        if (compiled.incrementAndGet() == 1L) {
            // the first compilation pays for class loading, reading the XDK and an interpreter
            // still warming up; measured over a long run it is about fourteen times the steady
            // state, so it is worth telling apart from a slow one
            logger.info("compile: first compilation in this server took {} (cold)", lastCompile)
        }
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

    /**
     * What the compiler is holding on to. A server that stays up for a working day either
     * accumulates or it does not, and this is the cheap way to find out which from the log rather
     * than from a profiler.
     */
    private fun footprint(): String =
        runCatching { EmbeddingSupport.instance().footprint().toString() }
            .getOrElse { "footprint unavailable: ${it.message}" }

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
        val ast = cached[uri]?.ast ?: return emptyList()
        return positions.mapNotNull { position ->
            XdkAst
                .chainAt(ast, position.line, position.column)
                .fold(null as SelectionRange?) { parent, node ->
                    SelectionRange(XdkAst.rangeOf(node), parent)
                }
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

    /** How long the last compilation took, for the line that reports it. Worker thread only. */
    @Volatile private var lastCompile: Duration = Duration.ZERO

    private val compiled = AtomicLong()

    /** The last analysis of each open document, for requests that should not recompile. */
    private val cached = ConcurrentHashMap<String, Analysis>()

    /** The generation of the newest edit seen per document; older ones are stale. */
    private val newest = ConcurrentHashMap<String, Long>()

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
