package org.xtclang.idea.lsp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP73 — 0.21.0 sends an edit once IntelliJ commits its document, and asks for completions without
// waiting for it (#1729). Ctrl+Space commits the document and then waits on the EDT for the first items, so the
// edit typed just before it went out after the request, which the server answered for the text before the edit.
// This contributor can go once a release has our #1738, where a request waits for the edits made before it.

/**
 * Sends the language server the edits of an Ecstasy file that LSP4IJ still holds, before LSP4IJ's contributor, which
 * plugin.xml orders after this one, asks the server for the file's completions. It adds no items of its own, and a
 * failed edit fails no completion: LSP4IJ's request then goes as it would have.
 */
class EditsBeforeCompletion : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val file = parameters.originalFile.virtualFile ?: return
        val sent =
            LanguageServiceAccessor
                .getInstance(parameters.originalFile.project)
                .startedServers
                .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
                .mapNotNull { server -> server.getOpenedDocument(server.toUri(file))?.synchronizer }
                .map { synchronizer -> synchronizer.didOpenFuture.thenCompose { synchronizer.flushPendingChanges() } }
        if (sent.isNotEmpty()) {
            ProgressIndicatorUtils.awaitWithCheckCanceled(CompletableFuture.allOf(*sent.toTypedArray()).handle { _, _ -> null })
        }
    }
}
