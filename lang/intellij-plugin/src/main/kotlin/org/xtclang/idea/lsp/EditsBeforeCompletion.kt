package org.xtclang.idea.lsp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet

// TODO LSP4IJ: UP73 — LSP4IJ asks for completions without sending the edits it holds first (#1729). Ctrl+Space
// commits the document and then waits on the EDT for the first items, so the edit typed just before it went out after
// the request, which the server answered for the text before the edit. Goes with sendHeldEdits.

/**
 * Sends the language server the edits of an Ecstasy file that LSP4IJ still holds, before LSP4IJ's contributor, which
 * plugin.xml orders after this one, asks the server for the file's completions. It adds no items of its own.
 */
class EditsBeforeCompletion : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile.virtualFile ?: return
        sendHeldEdits(parameters.originalFile.project, file)
    }
}
