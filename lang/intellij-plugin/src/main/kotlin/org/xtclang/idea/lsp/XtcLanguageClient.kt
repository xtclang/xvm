package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsListener
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettingsListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.UnregistrationParams
import org.xtclang.idea.XtcIntelliJLanguage

/**
 * Refreshes compiler semantic caches and bridges Ecstasy Code Style settings. Compiler source
 * modules use LSP4IJ's server Configuration settings under `xtc.compiler`; the base client owns
 * section lookup, change notifications and listener disposal.
 *
 * When the LSP server sends a `workspace/configuration` request for section `"xtc.formatting"`,
 * this client reads the current IntelliJ Code Style settings for the Ecstasy language and returns
 * them as a JSON-compatible map. Code Style changes refresh the server's immutable formatting
 * snapshot. `xtc-format.toml` and line wrapping are not implemented.
 */
class XtcLanguageClient(project: Project) : LanguageClientImpl(project) {
    private val compilerWatches = CompilerVfsWatches()
    private val preferences = AtomicReference(LanguageServiceSettings.validated(project))
    private val updateQueued = AtomicBoolean()
    private val settingsStores =
        listOf(LanguageServiceSettings.store(null), LanguageServiceSettings.store(project))
    private val settingsListener = LanguageServerSettingsListener { event ->
        if (
            event.languageServerId() == CompilerSettings.SERVER_ID &&
                event.configurationContentChanged()
        ) {
            if (updateQueued.compareAndSet(false, true)) {
                ApplicationManager.getApplication().invokeLater {
                    updateQueued.set(false)
                    if (!isDisposed && !project.isDisposed) {
                        val next = LanguageServiceSettings.validated(project)
                        val before = preferences.getAndSet(next)
                        if (before.textSynchronization != next.textSynchronization) {
                            LanguageServiceAccessor.getInstance(project)
                                .startedServers
                                .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
                                .forEach { it.restart() }
                        } else if (before.inlayHints != next.inlayHints) {
                            refreshInlayHints()
                        }
                    }
                }
            }
        }
    }

    init {
        settingsStores.forEach { it.addSettingsListener(settingsListener) }
        project.messageBus
            .connect(this)
            .subscribe(
                CodeStyleSettingsListener.TOPIC,
                CodeStyleSettingsListener {
                    if (!isDisposed && !project.isDisposed) triggerChangeConfiguration()
                },
            )
    }

    override fun dispose() {
        settingsStores.forEach { it.removeSettingsListener(settingsListener) }
        compilerWatches.dispose()
        super.dispose()
    }

    override fun applyEdit(
        params: ApplyWorkspaceEditParams
    ): CompletableFuture<ApplyWorkspaceEditResponse> =
        if (isDisposed || project.isDisposed)
            CompletableFuture.completedFuture(ServerWorkspaceEdit.refused("Connection is closed"))
        else (clientFeatures as XtcClientFeatures).applyEdit(params)

    override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> =
        super.registerCapability(params).thenRunAsync {
            if (!isDisposed && !project.isDisposed) compilerWatches.register(params.registrations)
        }

    override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> =
        super.unregisterCapability(params).thenRun {
            compilerWatches.unregister(params.unregisterations.map { it.id }.toSet())
        }

    // Rename/file-operation listeners can hold the IDE write lock while awaiting an LSP reply.
    // Never acquire a read lock on the transport thread. Use the shared application pool and
    // preserve notification order without creating a dedicated thread per connection.
    private val semanticUpdates =
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Ecstasy semantic updates")

    // TODO LSP4IJ: merge project/global configuration in createSettings upstream.
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
                    logger.warn("Failed to publish Ecstasy diagnostics", failure)
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
            // TODO LSP4IJ: invalidate semantic caches on diagnostic refresh, not just PSI changes.
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
