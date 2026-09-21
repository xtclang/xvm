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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
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
        val diagnostics = compiles.submit<List<Diagnostic>> { compileNow(uri, content) }.get()
        logger.debug("compile: uri={}, {} diagnostic(s)", uri, diagnostics.size)
        return CompilationResult.withDiagnostics(uri, diagnostics, emptyList())
    }

    override fun close() {
        compiles.shutdown()
        if (!compiles.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            compiles.shutdownNow()
        }
    }

    /**
     * Runs on the worker thread, one document at a time.
     */
    private fun compileNow(
        uri: String,
        content: String,
    ): List<Diagnostic> {
        val heard = mutableListOf<ErrorListener.ErrorInfo>()

        try {
            // the document is named so that its diagnostics are distinguishable from another
            // unsaved document's; the listener answers for what it heard, which a bare lambda
            // would not
            EmbeddingSupport.instance().compile(content, uri, null, ErrorListener.collecting(heard::add))
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

    private val compiles =
        Executors.newSingleThreadExecutor(
            ThreadFactory { r ->
                Thread(r, "xtc-compile").apply { isDaemon = true }
            },
        )

    private companion object {
        const val SOURCE = "xtc"
        const val NO_XDK = "XDK-UNAVAILABLE"
        const val SHUTDOWN_SECONDS = 5L
    }
}
