package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GotItTextBuilder
import com.intellij.ui.GotItTooltip
import org.xtclang.idea.XtcFileType
import java.awt.Point

/**
 * IntelliJ's Got It tooltips for two moments a user meets rarely: the language server's start with the Tree-sitter
 * adapter, which knows the code's syntax but not its types, and the server's unexpected stop, which LSP4IJ reports
 * itself, without the Ecstasy actions that help then. A tooltip shows in the selected Ecstasy editor, at its caret, and
 * IntelliJ shows each only once, ever; with no Ecstasy editor selected, it waits for the next time.
 */
internal object XtcGotItTooltips {
    const val TREE_SITTER = "xtc.treeSitterAdapter"
    const val SERVER_STOPPED = "xtc.serverStopped"

    /** The actions whose shortcuts the tooltips show, each the user's own, whatever their keymap. */
    const val SELECT_ADAPTER = "xtc.selectLanguageAdapter"
    const val RESTART_SERVER = "xtc.restartLanguageServer"
    const val SERVER_LOG = "xtc.toggleServerLog"

    fun treeSitterStarted(project: Project) =
        show(project, TREE_SITTER, "Ecstasy files use Tree-sitter") {
            "Tree-sitter knows the code's syntax but not its types: there are no compiler errors, and completion, " +
                "navigation and renames go by names. ${shortcut(SELECT_ADAPTER)} switches back to the compiler."
        }

    fun serverStopped(project: Project) =
        show(project, SERVER_STOPPED, "The Ecstasy language server stopped") {
            "${shortcut(RESTART_SERVER)} restarts it, and ${shortcut(SERVER_LOG)} shows its log. " +
                "Tools | Export Ecstasy Server Logs saves the logs for a bug report."
        }

    private fun show(
        project: Project,
        id: String,
        header: String,
        text: GotItTextBuilder.() -> String,
    ) = ApplicationManager.getApplication().invokeLater({
        val editor =
            FileEditorManager.getInstance(project).selectedTextEditor?.takeIf { it.virtualFile?.fileType == XtcFileType }
                ?: return@invokeLater
        val tooltip = GotItTooltip(id, text, project).withHeader(header)
        if (tooltip.canShow()) {
            tooltip.show(editor.contentComponent) { _, _ -> below(editor) }
        } else {
            Disposer.dispose(tooltip)
        }
    }, project.disposed)

    /** Below the caret's line, or at the top of the editor's view when the caret is out of sight. */
    private fun below(editor: Editor): Point {
        val visible = editor.scrollingModel.visibleArea
        val caret = editor.visualPositionToXY(editor.caretModel.visualPosition).apply { y += editor.lineHeight }
        return if (visible.contains(caret)) caret else Point(visible.x + visible.width / 2, visible.y)
    }
}
