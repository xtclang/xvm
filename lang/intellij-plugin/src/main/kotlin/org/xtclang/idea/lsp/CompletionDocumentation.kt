package org.xtclang.idea.lsp

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.platform.backend.documentation.DocumentationResult
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature.LSPCompletionContext
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal
import org.eclipse.lsp4j.CompletionItem
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP78 — LSP4IJ 0.21's LSPCompletionProposal.computeDocumentation makes the resolve-backed documentation
// of an item that has none and drops it, so Ctrl+Q on its completion items shows nothing

/**
 * LSP4IJ's completion items, whose Quick Documentation (Ctrl+Q) resolves an item that has no documentation, as the
 * language server gives an item's documentation only on completionItem/resolve. IntelliJ asks LSP4IJ's item itself for
 * its documentation, and it answers only what the item has or what an earlier resolve gave it, a resolve that only its
 * rendering of the selected item sends, and only for an item without a detail. Here an item is resolved once, when its
 * documentation is first asked for, never as the list shows it, and LSP4IJ renders what the resolve gives.
 */
internal class CompletionDocumentation : LSPCompletionFeature() {
    override fun createLookupElement(
        item: CompletionItem,
        context: LSPCompletionContext,
    ): LookupElement? = if (item.label.isNullOrBlank()) null else Proposal(item, context, this)

    private class Proposal(
        item: CompletionItem,
        context: LSPCompletionContext,
        feature: CompletionDocumentation,
    ) : LSPCompletionProposal(item, context, feature) {
        private val resolves = context.isResolveCompletionSupported

        /** The server's resolve of the item, sent when its documentation is first asked for. */
        private val resolved: CompletableFuture<CompletionItem> by lazy {
            feature.clientFeatures.serverWrapper.initializedServer
                .thenCompose { it.textDocumentService.resolveCompletionItem(item) }
        }

        override fun computeDocumentation(): DocumentationResult? =
            super.computeDocumentation()
                ?: if (resolves && item.documentation == null) DocumentationResult.asyncDocumentation(::resolvedDocumentation) else null

        /** The documentation that the item's resolve gives it, as LSP4IJ renders an item's own; none if the resolve fails. */
        private fun resolvedDocumentation(): DocumentationResult.Documentation? {
            ProgressIndicatorUtils
                .awaitWithCheckCanceled(resolved.handle { resolvedItem, _ -> resolvedItem })
                ?.documentation
                ?.let { item.documentation = it }
            return super.computeDocumentation() as? DocumentationResult.Documentation
        }
    }
}
