package org.xvm.lsp.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcLanguageConstants.toHoverMarkdown
import org.xvm.lsp.model.Location

/**
 * Abstract base class for XTC compiler adapters.
 *
 * Provides common functionality shared across all adapter implementations:
 * - Per-class logging with consistent prefix formatting
 * - Default [getHoverInfo] implementation using [findSymbolAt]
 * - Utility method for position-in-range checking
 * - No-op [Closeable] implementation (override in subclasses that need cleanup)
 *
 * @see MockXtcCompilerAdapter for regex-based testing adapter
 * @see TreeSitterAdapter for syntax-aware adapter
 * @see XtcCompilerAdapterStub for placeholder adapter
 */
abstract class AbstractXtcCompilerAdapter : XtcCompilerAdapter {
    /**
     * Logger instance for this adapter, using the concrete class name.
     * Lazily initialized to use the actual subclass type.
     */
    protected val logger: Logger by lazy {
        LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Logging prefix derived from [displayName], e.g., "[Mock]" or "[TreeSitter]".
     */
    protected val logPrefix: String get() = "[$displayName]"

    /**
     * Default hover implementation that finds the symbol at position and formats it.
     *
     * Subclasses can override for custom behavior or additional logging.
     */
    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        logger.info("$logPrefix getHoverInfo: uri={}, line={}, column={}", uri, line, column)
        val symbol = findSymbolAt(uri, line, column)
        if (symbol == null) {
            logger.info("$logPrefix getHoverInfo: no symbol at position")
            return null
        }
        logger.info("$logPrefix getHoverInfo: found symbol '{}' ({})", symbol.name, symbol.kind)
        return symbol.toHoverMarkdown()
    }

    /**
     * Check if a position (line, column) falls within a location's range.
     *
     * @param line 0-based line number
     * @param column 0-based column number
     * @return true if the position is within this location's bounds
     */
    protected fun Location.contains(
        line: Int,
        column: Int,
    ): Boolean {
        if (line < startLine || line > endLine) return false
        if (line == startLine && column < startColumn) return false
        if (line == endLine && column > endColumn) return false
        return true
    }
}
