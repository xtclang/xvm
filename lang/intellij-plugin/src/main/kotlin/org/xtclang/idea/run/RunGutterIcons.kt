package org.xtclang.idea.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.lsp.CompilerSettings
import org.xtclang.idea.lsp.RunLens
import org.xtclang.idea.lsp.XtcLanguageServer
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

/**
 * Run icons in the gutter of Ecstasy files, on the lines of their Run lenses. Ecstasy files have no PSI for a run
 * line marker to attach to, so the icons are document line highlighters, placed from the server's Run lenses.
 */
internal object RunGutterIcons {
    private val placed = Key.create<List<RangeHighlighter>>("xtc.runGutterIcons")

    /** Marks the lines of [file]'s Run lenses, and clears marks whose lens is gone. */
    fun refresh(
        project: Project,
        file: VirtualFile,
    ) {
        if (project.isDisposed || file.fileType != XtcFileType || !file.isInLocalFileSystem) return
        val uri = LSPIJUtils.toUriAsString(file)
        lenses(project, uri).thenAccept { lenses ->
            ApplicationManager.getApplication().invokeLater({
                FileDocumentManager.getInstance().getCachedDocument(file)?.let { show(project, it, lenses, uri) }
            }, project.disposed)
        }
    }

    private fun show(
        project: Project,
        document: Document,
        lenses: List<RunLens>,
        uri: String,
    ) {
        val markup = DocumentMarkupModel.forDocument(document, project, true)
        document
            .getUserData(placed)
            .orEmpty()
            .filter { it.isValid }
            .forEach(markup::removeHighlighter)
        document.putUserData(
            placed,
            lenses.filter { it.line < document.lineCount }.map { lens ->
                markup.addLineHighlighter(lens.line, HighlighterLayer.ADDITIONAL_SYNTAX, null).apply {
                    gutterIconRenderer = RunIcon(project, lens.module, uri)
                }
            },
        )
    }

    private fun lenses(
        project: Project,
        uri: String,
    ): CompletableFuture<List<RunLens>> {
        val requests =
            LanguageServiceAccessor
                .getInstance(project)
                .startedServers
                .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
                .map { wrapper ->
                    wrapper.initializedServer.thenCompose { server ->
                        (server as? XtcLanguageServer)?.runLenses(TextDocumentIdentifier(uri))
                            ?: CompletableFuture.completedFuture(emptyList())
                    }
                }
        return CompletableFuture.allOf(*requests.toTypedArray()).thenApply { requests.flatMap { it.join() } }
    }

    /** Runs the module the way its Run lens does. */
    private class RunIcon(
        private val project: Project,
        private val module: String,
        private val uri: String,
    ) : GutterIconRenderer() {
        override fun getIcon(): Icon = AllIcons.RunConfigurations.TestState.Run

        override fun getTooltipText(): String = "Run $module"

        override fun isNavigateAction(): Boolean = true

        override fun getClickAction(): AnAction =
            object : DumbAwareAction() {
                override fun actionPerformed(event: AnActionEvent) = XtcRunModuleAction.run(project, module, uri)
            }

        override fun equals(other: Any?): Boolean = other is RunIcon && other.module == module && other.uri == uri

        override fun hashCode(): Int = 31 * module.hashCode() + uri.hashCode()
    }
}
