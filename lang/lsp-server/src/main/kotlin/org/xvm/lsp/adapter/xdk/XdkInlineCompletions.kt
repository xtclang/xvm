package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.InlineCompletionContext
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.TextEdit

/** Conservative projection of compiler cursor facts; never adds imports or invents method bodies. */
internal object XdkInlineCompletions {
    fun eligible(text: String, position: Position, context: InlineCompletionContext): Boolean {
        if (position.line < 0 || position.column < 0) return false
        val line = text.lineSequence().elementAtOrNull(position.line) ?: return false
        if (position.column > line.length || line.getOrNull(position.column)?.isJavaIdentifierPart() == true) return false
        return !context.automatic || line.getOrNull(position.column - 1)?.isJavaIdentifierPart() == true
    }

    fun project(
        text: String,
        position: Position,
        context: InlineCompletionContext,
        candidates: List<CompletionItem>,
    ): List<TextEdit> {
        if (!eligible(text, position, context)) return emptyList()
        val line = text.lineSequence().elementAt(position.line)
        val suggestions = candidates.asSequence()
            .filter { it.snippet == null && it.additionalTextEdits.isEmpty() }
            .sortedBy { it.sortText ?: it.label }
            .mapNotNull { it.textEdit }
            .filter { edit ->
                val start = edit.range.start
                val end = edit.range.end
                start.line == position.line && end == position && start.column in 0..end.column &&
                    edit.newText.length > end.column - start.column &&
                    edit.newText.startsWith(line.substring(start.column, end.column)) &&
                    (!context.automatic || start.column < end.column) &&
                    context.selectedCompletion.let { selected ->
                        selected == null || selected.range == edit.range &&
                            edit.newText.startsWith(selected.newText) && edit.newText.length > selected.newText.length
                    }
            }
            .distinct()
            .toList()
        return if (context.automatic) suggestions.singleOrNull()?.let(::listOf).orEmpty() else suggestions
    }
}
