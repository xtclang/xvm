package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source

/** The embedding's analysis of a source incomplete at a cursor, the errors it reported, the source and the cursor. */
internal data class IncompleteAnalysis(
    val analysis: EmbeddingSupport.PartialAnalysis,
    val errors: ErrorList,
    val source: Source,
    val cursor: Long,
)

/**
 * Analyzes [text] as incomplete at the offset [at], as a completion there does, reporting to [listener] or else to the
 * result's errors. The bundled libraries must be configured first.
 */
internal fun analyzeIncomplete(
    text: String,
    uri: String,
    at: Int,
    listener: ErrorListener? = null,
): IncompleteAnalysis {
    val source = Source(text, uri)
    repeat(at) { source.next() }
    val cursor = source.position
    source.reset()
    val errors = ErrorList()
    val analysis = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener ?: errors)
    return IncompleteAnalysis(analysis, errors, source, cursor)
}

/** A listener that stops at the first error and one that is canceled: an analysis that reports to either stops early. */
internal fun stoppingListeners(): List<ErrorListener> =
    listOf(ErrorList(ErrorList.FIRST_ERROR), ErrorListener.cancellable(ErrorList()) { true })

/** Analyzing the source again, from its start, with each of the [stoppingListeners] yields no pool and no cursor bindings. */
internal fun assertListenersStop(
    source: Source,
    cursor: Long,
) {
    stoppingListeners().forEach { listener ->
        source.reset()
        val stopped = EmbeddingSupport.instance().analyzeIncomplete(source, cursor, null, listener)
        assertThat(stopped.pool()).isNull()
        assertThat(stopped.cursorBindings()).isEmpty()
    }
}
