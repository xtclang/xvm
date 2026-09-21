package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.CompletionItem
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
        val (diagnostics, waited) =
            compiles
                .submit<Pair<List<Diagnostic>, Duration>> {
                    val waited = (System.nanoTime() - queued).nanoseconds
                    if (isStale(uri, generation)) {
                        // a newer edit arrived while this one waited; analysing the old text
                        // would publish diagnostics for a document that no longer exists
                        logger.info("compile: uri={} superseded before it started, skipped", uri)
                        emptyList<Diagnostic>() to waited
                    } else {
                        compileNow(uri, content, generation) to waited
                    }
                }.get()

        // queue wait is reported separately from compile time on purpose: it is the number that
        // says whether serialising compilations has started to hurt, and it is the one that would
        // otherwise be invisible inside a single "how long did that take"
        logger.info(
            "compile: uri={}, {} bytes, {} diagnostic(s), queue={}, waited {}, compiled in {} [{}]",
            uri,
            content.length,
            diagnostics.size,
            depth,
            waited,
            lastCompile,
            footprint(),
        )
        return CompilationResult.withDiagnostics(uri, diagnostics, emptyList())
    }

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
    ): List<Diagnostic> {
        val heard = mutableListOf<ErrorListener.ErrorInfo>()

        val started = System.nanoTime()
        try {
            // the document is named so that its diagnostics are distinguishable from another
            // unsaved document's; the listener answers for what it heard, which a bare lambda
            // would not
            // the compiler asks isAbortDesired at around twenty points; this is what answers yes
            // when the user has typed again and the answer is no longer wanted
            EmbeddingSupport.instance().compile(
                Source(content, uri),
                null,
                ErrorListener.cancellable(ErrorListener.collecting(heard::add)) {
                    isStale(uri, generation)
                },
            )
        } catch (e: IllegalStateException) {
            // no XDK to compile against: report it where the user can see it rather than throwing
            // at the language server, and let them keep editing
            logger.warn("compile: uri={} has no XDK to resolve against: {}", uri, e.message)
            return listOf(
                Diagnostic(
                    location = wholeDocument(uri),
                    severity = Diagnostic.Severity.WARNING,
                    message = "XTC analysis unavailable: ${e.message}",
                    code = NO_XDK,
                    source = SOURCE,
                ),
            )
        }

        lastCompile = (System.nanoTime() - started).nanoseconds
        if (isStale(uri, generation)) {
            // abandoned part-way: what it managed to report describes text the user has already
            // replaced, so publishing it would put stale squiggles in the editor
            logger.info("compile: uri={} superseded after {}, abandoned", uri, lastCompile)
            return emptyList()
        }
        if (compiled.incrementAndGet() == 1L) {
            // the first compilation pays for class loading, reading the XDK and an interpreter
            // still warming up; measured over a long run it is about fourteen times the steady
            // state, so it is worth telling apart from a slow one
            logger.info("compile: first compilation in this server took {} (cold)", lastCompile)
        }
        return heard.map { it.toDiagnostic(uri) }
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

    // ----- not implemented here: these need the symbol table, not the diagnostics ----------------

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? = null

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
        triggerCharacter: String?,
    ): List<CompletionItem> = emptyList()

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = null

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> = emptyList()

    /** How long the last compilation took, for the line that reports it. Worker thread only. */
    @Volatile private var lastCompile: Duration = Duration.ZERO

    private val compiled = AtomicLong()

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
