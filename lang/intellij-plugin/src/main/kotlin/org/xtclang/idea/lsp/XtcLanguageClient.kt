package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.xtclang.idea.XtcIntelliJLanguage

/**
 * Refreshes compiler semantic caches and bridges Ecstasy Code Style settings. Compiler source
 * modules use LSP4IJ's server Configuration settings under `xtc.compiler`; the base client owns
 * section lookup, change notifications and listener disposal.
 *
 * When the LSP server sends a `workspace/configuration` request for section `"xtc.formatting"`,
 * this client reads the current IntelliJ Code Style settings for the Ecstasy language and returns
 * them as a JSON-compatible map. This implements Phase 3 of the formatting plan: IntelliJ Code
 * Style settings flow to the LSP server as a fallback when no `xtc-format.toml` config file is
 * present.
 *
 * Resolution chain (highest priority first):
 * 1. `xtc-format.toml` in the project tree (not yet implemented)
 * 2. IntelliJ Code Style settings (this client provides them)
 * 3. LSP `FormattingOptions` from the editor (tabSize / insertSpaces)
 * 4. XTC defaults (4-space indent, 8-space continuation, no tabs)
 */
class XtcLanguageClient(project: Project) : LanguageClientImpl(project) {
    // Rename/file-operation listeners can hold the IDE write lock while awaiting an LSP reply.
    // Never acquire a read lock on the transport thread. Use the shared application pool and
    // preserve notification order without creating a dedicated thread per connection.
    private val semanticUpdates =
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("XTC semantic updates")

    // LSP4IJ already subscribes/disposes listeners for both stores, but its default
    // createSettings reads only the global store. Project settings take precedence here.
    override fun createSettings(): Any? =
        CompilerBuildModel.settings(
            project,
            CompilerSettings.store(project, serverDefinition.id)
                .getLanguageServerSettings(serverDefinition.id)
                ?.getLanguageServerConfiguration(project),
        )

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        semanticUpdate {
            clientFeatures.findFileByUri(params.uri)?.let(::invalidateSemanticFacts)
            super.publishDiagnostics(params)
        }
            .exceptionally { failure ->
                if (!isDisposed && !project.isDisposed)
                    logger.warn("Failed to publish XTC diagnostics", failure)
                null
            }
    }

    override fun refreshDiagnostics(): CompletableFuture<Void> = semanticUpdate {
        FileEditorManager.getInstance(project).openFiles.forEach(::invalidateSemanticFacts)
    }
        .thenCompose {
            if (isDisposed || project.isDisposed) CompletableFuture.completedFuture(null)
            else super.refreshDiagnostics()
        }

    private fun semanticUpdate(update: () -> Unit): CompletableFuture<Void> =
        CompletableFuture.runAsync(
            { if (!isDisposed && !project.isDisposed) update() },
            semanticUpdates,
        )

    private fun invalidateSemanticFacts(file: VirtualFile) {
        ReadAction.runBlocking<RuntimeException> {
            if (project.isDisposed) return@runBlocking
            val psi = PsiManager.getInstance(project).findFile(file) ?: return@runBlocking
            if (!LSPFileSupport.hasSupport(psi)) return@runBlocking
            val support = LSPFileSupport.getSupport(psi)
            // A dependency can change this file's semantics without changing its PSI stamp.
            // LSP4IJ keys these caches to that stamp, so retire completed results when the
            // compiler publishes a new analysis. Pending requests already target the current
            // compiler queue and retain their normal cancellation/version checks.
            with(support) {
                listOf(
                        completionSupport,
                        definitionSupport,
                        typeDefinitionSupport,
                        implementationSupport,
                        referenceSupport,
                        hoverSupport,
                        signatureHelpSupport,
                        highlightSupport,
                        prepareRenameSupport,
                        renameSupport,
                        intentionCodeActionSupport,
                        codeLensSupport,
                        documentSymbolSupport,
                        semanticTokensSupport,
                        inlayHintsSupport,
                        prepareTypeHierarchySupport,
                        typeHierarchySupertypesSupport,
                        typeHierarchySubtypesSupport,
                        prepareCallHierarchySupport,
                        callHierarchyIncomingCallsSupport,
                        callHierarchyOutgoingCallsSupport,
                    )
                    .forEach { feature -> if (feature.future?.isDone == true) feature.cancel() }
            }
        }
    }

    /**
     * Specialize only formatting. Delegating other sections preserves configured compiler graphs,
     * null entries for unknown sections and the base client's asynchronous response ordering.
     */
    override fun findSettings(section: String?): Any? =
        when (section) {
            FORMATTING_SECTION -> readFormattingSettings()
            else -> super.findSettings(section)
        }

    private fun readFormattingSettings(): Map<String, Any> {
        val settings = CodeStyle.getProjectOrDefaultSettings(project)
        val commonSettings = settings.getCommonSettings(XtcIntelliJLanguage)
        val indentOptions = commonSettings.indentOptions

        val config =
            if (indentOptions != null) {
                mapOf(
                    "indentSize" to indentOptions.INDENT_SIZE,
                    "continuationIndentSize" to indentOptions.CONTINUATION_INDENT_SIZE,
                    "tabSize" to indentOptions.TAB_SIZE,
                    "insertSpaces" to !indentOptions.USE_TAB_CHARACTER,
                    "maxLineWidth" to commonSettings.RIGHT_MARGIN,
                )
            } else {
                mapOf(
                    "indentSize" to 4,
                    "continuationIndentSize" to 8,
                    "tabSize" to 4,
                    "insertSpaces" to true,
                    "maxLineWidth" to 120,
                )
            }
        logger.info("workspace/configuration: returning formatting config: $config")
        return config
    }

    companion object {
        private val logger = logger<XtcLanguageClient>()

        /** The configuration section name for XTC formatting settings. */
        const val FORMATTING_SECTION = "xtc.formatting"
    }
}
