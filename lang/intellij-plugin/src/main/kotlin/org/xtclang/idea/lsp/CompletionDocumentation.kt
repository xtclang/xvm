package org.xtclang.idea.lsp

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.SymbolDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature.LSPCompletionContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.xtclang.idea.XtcIntelliJLanguage
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP78 — LSP4IJ 0.21's completion item makes the resolve-backed documentation of an item that has none
// and drops it, so Ctrl+Q on its completion items shows nothing; LSP4IJ #1764 fixes it, unreleased

/**
 * LSP4IJ's completion items, whose Quick Documentation (Ctrl+Q) resolves an item that has no documentation, as the
 * language server gives an item's documentation only on completionItem/resolve. IntelliJ asks LSP4IJ's item itself for
 * its documentation, and it answers only what the item has or what an earlier resolve gave it, a resolve that only its
 * rendering of the selected item sends, and only for an item without a detail. Here such an item carries its
 * [ResolvedDocumentation], which [CompletionDocumentationTargets] gives IntelliJ before it asks the item; the item is
 * resolved once, when its documentation is first asked for, never as the list shows it. Only public API: LSP4IJ's item
 * stays its own, so that its rendering, insertion and selection work as they do.
 */
internal class CompletionDocumentation : LSPCompletionFeature() {
    override fun createLookupElement(
        item: CompletionItem,
        context: LSPCompletionContext,
    ): LookupElement? =
        if (item.label.isNullOrBlank()) {
            null
        } else {
            super.createLookupElement(item, context)?.also { element ->
                if (context.isResolveCompletionSupported && item.documentation == null) {
                    element.putUserData(RESOLVED, ResolvedDocumentation(item, clientFeatures.project, ::resolve))
                }
            }
        }

    /** The server's resolve of [item]. */
    private fun resolve(item: CompletionItem): CompletableFuture<CompletionItem> =
        clientFeatures.serverWrapper.initializedServer
            .thenCompose { it.textDocumentService.resolveCompletionItem(item) }

    companion object {
        /** The documentation of a completion item that only its resolve gives, on LSP4IJ's lookup element for it. */
        internal val RESOLVED = Key.create<ResolvedDocumentation>("xtc.completion.resolvedDocumentation")
    }
}

/**
 * The documentation of LSP4IJ's completion items that [CompletionDocumentation] marked, which IntelliJ takes from here
 * before it asks the item's own; every other symbol is left to the other providers.
 */
internal class CompletionDocumentationTargets : SymbolDocumentationTargetProvider {
    override fun documentationTargets(
        project: Project,
        symbol: Symbol,
    ): List<DocumentationTarget> = listOfNotNull((symbol as? UserDataHolder)?.getUserData(CompletionDocumentation.RESOLVED))
}

/** A completion item's documentation, from the item's resolve, sent the first time its documentation is asked for. */
internal class ResolvedDocumentation(
    private val item: CompletionItem,
    private val project: Project,
    resolve: (CompletionItem) -> CompletableFuture<CompletionItem>,
) : DocumentationTarget {
    private val resolved: CompletableFuture<CompletionItem> by lazy { resolve(item) }

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(item.label).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation(::documentation)

    /** The item's documentation, as its resolve gives it, which LSP4IJ's item then has too; none if the resolve fails. */
    private fun documentation(): DocumentationResult.Documentation? {
        val documentation =
            item.documentation
                ?: ProgressIndicatorUtils.awaitWithCheckCanceled(resolved.handle { resolvedItem, _ -> resolvedItem })?.documentation
                ?: return null
        item.documentation = documentation
        return DocumentationResult.documentation(html(documentation))
    }

    private fun html(documentation: Either<String, MarkupContent>): String =
        if (documentation.isRight && documentation.right.kind == MarkupKind.MARKDOWN) {
            DocMarkdownToHtmlConverter.convert(project, documentation.right.value, XtcIntelliJLanguage)
        } else {
            "<pre>${StringUtil.escapeXmlEntities(if (documentation.isLeft) documentation.left else documentation.right.value)}</pre>"
        }
}
