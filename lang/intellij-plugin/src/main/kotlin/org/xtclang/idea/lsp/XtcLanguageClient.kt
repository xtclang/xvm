package org.xtclang.idea.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.problems.ProblemListener
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsListener
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettingsListener
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.xtclang.idea.XtcIntelliJLanguage
import org.xtclang.idea.dap.DebugSessions
import org.xtclang.idea.dap.DebugTasks
import org.xtclang.idea.run.EmbeddedRunSessions
import org.xtclang.idea.run.RunGutterIcons
import org.xtclang.idea.run.TestGutterIcons
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Refreshes compiler semantic caches and bridges Ecstasy Code Style settings. Compiler source
 * modules use LSP4IJ's server Configuration settings under `xtc.compiler`; the base client owns
 * section lookup, change notifications and listener disposal.
 *
 * When the LSP server sends a `workspace/configuration` request for section `"xtc.formatting"`,
 * this client reads the current IntelliJ Code Style settings for the Ecstasy language and returns
 * them as a JSON-compatible map. Code Style changes refresh the server's immutable formatting
 * snapshot. Compiler mode uses the right margin for bounded expression/list wrapping;
 * `xtc-format.toml` is not implemented.
 */
class XtcLanguageClient(
    project: Project,
) : LanguageClientImpl(project) {
    private val editorRefresh = EditorRefresh(project, this) { isDisposed }
    private val compilerWatches = CompilerVfsWatches()
    private val preferences = AtomicReference(LanguageServiceSettings.validated(project))
    private val updateQueued = AtomicBoolean()
    private val settingsStores =
        listOf(LanguageServiceSettings.store(null), LanguageServiceSettings.store(project))
    private val settingsListener =
        LanguageServerSettingsListener { event ->
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
                            if (next.requiresRestart(before)) {
                                LanguageServiceAccessor
                                    .getInstance(project)
                                    .startedServers
                                    .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
                                    .forEach { it.restart() }
                            } else {
                                if (before.inlayHints != next.inlayHints) refreshInlayHints()
                                if (before.referenceCodeLens != next.referenceCodeLens) refreshCodeLenses()
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
        if (!project.isDisposed) {
            EmbeddedRunSessions.getInstance(project).endAll("The Ecstasy language server stopped")
            // a project that never debugged has no sessions, and its service is not created while it closes
            project.getServiceIfCreated(DebugSessions::class.java)?.endAll("The Ecstasy language server stopped, ending the debug session")
            // as LSP4IJ clears the problem files of the documents it held for the server
            project.getServiceIfCreated(ProjectErrors::class.java)?.disconnected()
        }
        super.dispose()
    }

    /** Output of an embedded run, for the console registered under its run ID. */
    @JsonNotification("xtc/runOutput")
    fun runOutput(params: RunOutputParams) {
        if (!project.isDisposed) EmbeddedRunSessions.getInstance(project).output(params)
    }

    /** An event of the tests of an embedded run, for the test console registered under its run ID. */
    @JsonNotification("xtc/testEvent")
    fun testEvent(params: TestEventParams) {
        if (!project.isDisposed) EmbeddedRunSessions.getInstance(project).tested(params)
    }

    @JsonNotification("xtc/runExited")
    fun runExited(params: RunExitedParams) {
        if (!project.isDisposed) EmbeddedRunSessions.getInstance(project).exited(params)
    }

    /** A DAP message of a debug session, for the process its debugger connects through. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams) {
        if (!project.isDisposed) DebugSessions.getInstance(project).receive(params)
    }

    /** Runs a Gradle-mode debug session's module with its Gradle task, and answers once the task has ended. */
    @JsonRequest("xtc/debugTask")
    fun debugTask(params: DebugTaskParams): CompletableFuture<DebugTaskResult> = DebugTasks.run(project, params)

    override fun setServerWrapper(wrapper: LanguageServerWrapper) {
        super.setServerWrapper(wrapper)
        ApplicationManager
            .getApplication()
            .messageBus
            .connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, DirectoryDocumentMoves(wrapper) { isDisposed })
        // TODO LSP4IJ: UP70 — as a file closes, LSP4IJ clears its problem mark in a background task that nothing orders
        // with the report of the errors then published for its closed document, so the clear can come last and leave a
        // closed file with errors unmarked. Each clear is announced; report the file again then, from what LSP4IJ holds,
        // so the last report computes the right mark. Remove with an LSP4IJ release that orders the two.
        project.messageBus
            .connect(this)
            .subscribe(
                ProblemListener.TOPIC,
                object : ProblemListener {
                    override fun problemsDisappeared(file: VirtualFile) = remark(wrapper, file)
                },
            )
    }

    /** Reports [file] to IntelliJ's problem files again when LSP4IJ holds errors for it, which its mark should show. */
    private fun remark(
        wrapper: LanguageServerWrapper,
        file: VirtualFile,
    ) {
        if (isDisposed || project.isDisposed || wrapper.isDisposed) return
        val uri = wrapper.clientFeatures.getFileUri(file) ?: return
        val errors =
            wrapper.getClosedDocument(uri, false)?.takeIf { it.hasErrors() }
                ?: wrapper.getOpenedDocument(uri)?.takeIf { it.hasErrors() }
                ?: return
        LSPDiagnosticUtils.reportProblem(file, errors, project)
    }

    /** Once the server has started, record where it logs, which only it can name, for an export after it stops. */
    override fun handleServerStatusChanged(serverStatus: ServerStatus) {
        super.handleServerStatusChanged(serverStatus)
        if (serverStatus == ServerStatus.started && !project.isDisposed) {
            (languageServer as? XtcLanguageServer)?.languageServiceStatus()?.whenComplete { status, failure ->
                if (project.isDisposed) return@whenComplete
                if (failure == null) {
                    project.getService(ServerSupportLogs::class.java).remember(status)
                } else if (!isDisposed) {
                    logger.warn("Could not record the Ecstasy server's log session", failure)
                }
            }
        }
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> =
        if (isDisposed || project.isDisposed) {
            CompletableFuture.completedFuture(ServerWorkspaceEdit.refused("Connection is closed"))
        } else {
            (clientFeatures as XtcClientFeatures).applyEdit(params)
        }

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

    // TODO LSP4IJ: UP69 — LSP4IJ 0.21.0 asks for no workspace report, so the diagnostics of closed documents come from
    // the report this asks for. Remove it, its ClosedDocumentConnection and its refresh with an LSP4IJ release whose
    // own workspace pull updates closed documents.
    private val closedDocuments = ClosedDocumentDiagnostics(ClosedDocumentConnection(), semanticUpdates)

    // UP13, by design upstream: LSP4IJ keeps server configuration in its global store and
    // leaves other sources to clients through createSettings. The Ecstasy project settings
    // page writes project configuration, so the store that owns the compiler graph wins here.
    override fun createSettings(): Any? =
        CompilerBuildModel.settings(
            project,
            CompilerSettings
                .store(project, serverDefinition.id)
                .getLanguageServerSettings(serverDefinition.id)
                ?.getLanguageServerConfiguration(project),
        )

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        semanticUpdate {
            val file = clientFeatures.findFileByUri(params.uri)
            file?.let(::invalidateSemanticFacts)
            super.publishDiagnostics(params)
            // a publication follows each analysis, which is what decides whether the module can run
            file?.let {
                RunGutterIcons.refresh(project, it)
                TestGutterIcons.refresh(project, it)
            }
        }.exceptionally { failure ->
            if (!isDisposed && !project.isDisposed) {
                logger.warn("Failed to publish Ecstasy diagnostics", failure)
            }
            null
        }
    }

    override fun refreshDiagnostics(): CompletableFuture<Void> {
        // TODO LSP4IJ: UP69 — the workspace report, asked for before this refresh is answered, for the closed documents
        closedDocuments.refresh()
        return semanticUpdate {
            FileEditorManager.getInstance(project).openFiles.forEach(::invalidateSemanticFacts)
            // IntelliJ pulls diagnostics, so this refresh, not a publication, follows each analysis
            refreshRunIcons()
        }.thenCompose {
            if (isDisposed || project.isDisposed) {
                CompletableFuture.completedFuture(null)
            } else {
                super.refreshDiagnostics()
            }
        }
    }

    // TODO LSP4IJ: UP69 — what ClosedDocumentDiagnostics needs of the server and of LSP4IJ, until LSP4IJ pulls the
    // workspace's diagnostics itself.

    /** The server's workspace report and LSP4IJ's documents, for [closedDocuments]. */
    private inner class ClosedDocumentConnection : ClosedDocumentDiagnostics.Connection {
        override fun report(): CompletableFuture<WorkspaceDiagnosticReport>? {
            if (isDisposed || project.isDisposed) return null
            val provider = clientFeatures.serverWrapper.serverCapabilitiesSync?.diagnosticProvider
            if (provider?.isWorkspaceDiagnostics != true) return null
            return languageServer?.workspaceService?.diagnostic(
                WorkspaceDiagnosticParams(emptyList()).apply { identifier = provider.identifier },
            )
        }

        override fun document(uri: String): ClosedDocumentDiagnostics.Document {
            val file = clientFeatures.findFileByUri(uri) ?: return ClosedDocumentDiagnostics.Document.MISSING
            val opened = clientFeatures.serverWrapper.getOpenedDocument(clientFeatures.getFileUri(file))
            return when {
                opened?.synchronizer != null -> ClosedDocumentDiagnostics.Document.OPEN
                opened != null || FileEditorManager.getInstance(project).isFileOpen(file) -> ClosedDocumentDiagnostics.Document.OPENING
                else -> ClosedDocumentDiagnostics.Document.CLOSED
            }
        }

        // LSP4IJ's own handling of a publication, without this client's refreshes of open editors
        override fun publish(
            uri: String,
            diagnostics: List<Diagnostic>,
        ) {
            if (!isDisposed && !project.isDisposed && clientFeatures.serverStatus == ServerStatus.started) {
                super@XtcLanguageClient.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
            }
        }

        override fun opened(uri: String) {
            // a publication that reached the document as it opened stays beside its pulled diagnostics, unless cleared
            publish(uri, emptyList())
            val file = clientFeatures.findFileByUri(uri) ?: return
            // TODO LSP4IJ: UP70 — LSP4IJ drops a closed document's diagnostics when it opens without telling
            // WolfTheProblemSolver, which keeps the file red when its first pull has no errors. Report the file from
            // what LSP4IJ holds now. Remove with an LSP4IJ release that reports the file when it opens (#1861).
            LSPDiagnosticUtils.reportProblem(file, clientFeatures.serverWrapper.getOpenedDocument(clientFeatures.getFileUri(file)), project)
        }

        // TODO LSP4IJ: UP69 — the workspace report is what Project Errors lists of the server's errors while the plugin
        // asks for it. With an LSP4IJ release that pulls it (WorkspaceDiagnosticManager) and tells its client features
        // each file's merged diagnostics (LSPDiagnosticFeature.diagnosticsUpdated, #1704), the plugin's diagnostic feature
        // calls ProjectErrors.reported from there instead, with its own getMessage, for open and closed files alike, and
        // this goes with the bridge.
        override fun reported(
            uri: String,
            diagnostics: List<Diagnostic>,
        ) {
            if (isDisposed || project.isDisposed) return
            val file = clientFeatures.findFileByUri(uri) ?: return
            ProjectErrors.getInstance(project).reported(file, diagnostics, clientFeatures.diagnosticFeature::getMessage)
        }
    }

    override fun refreshCodeLenses(): CompletableFuture<Void> {
        refreshRunIcons()
        return refreshEditors(EditorRefresh.Feature.LENSES)
    }

    /** The gutter Run icons follow the Run lenses, from the same analysis, and the test icons its tests. */
    private fun refreshRunIcons() =
        FileEditorManager.getInstance(project).openFiles.forEach {
            RunGutterIcons.refresh(project, it)
            TestGutterIcons.refresh(project, it)
        }

    override fun refreshInlayHints(): CompletableFuture<Void> = refreshEditors(EditorRefresh.Feature.HINTS)

    override fun refreshSemanticTokens(): CompletableFuture<Void> = refreshEditors(EditorRefresh.Feature.TOKENS)

    private fun refreshEditors(feature: EditorRefresh.Feature): CompletableFuture<Void> =
        if (isDisposed || project.isDisposed) {
            CompletableFuture.completedFuture(null)
        } else {
            editorRefresh.request(clientFeatures.serverWrapper, feature)
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
            // TODO LSP4IJ: UP14 — invalidate semantic caches on diagnostic refresh, not just PSI changes.
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
                ).forEach { feature -> if (feature.future?.isDone == true) feature.cancel() }
            }
        }
    }

    /**
     * Specialize editor formatting and reference-lens preferences. Other sections preserve compiler graphs,
     * null entries for unknown sections and the base client's asynchronous response ordering.
     */
    override fun findSettings(section: String?): Any? =
        when (section) {
            FORMATTING_SECTION -> readFormattingSettings()
            "xtc.codeLens" -> mapOf("references" to LanguageServiceSettings.validated(project).referenceCodeLens)
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
