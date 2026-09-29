package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AppIcon
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import org.xtclang.idea.lsp.XtcRenameHandler

/** Small IDE-side observations avoid walking the entire Swing tree for every focus poll. */
object EditorUi {
    @JvmStatic
    fun renameAvailable(editor: Editor): Boolean =
        XtcRenameHandler()
            .isAvailableOnDataContext(
                DataManager.getInstance().getDataContext(editor.contentComponent)
            )

    @JvmStatic
    fun renameState(editor: Editor): String {
        val project = requireNotNull(editor.project)
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val available = renameAvailable(editor)
        val servers =
            LanguageServiceAccessor.getInstance(project).startedServers.map { wrapper ->
                "pid=${wrapper.currentProcessId}, rename=${wrapper.serverCapabilitiesSync?.renameProvider}, " +
                    "opened=${wrapper.openedDocuments.map { it.file.path }}, " +
                    "enabled=${file?.let { wrapper.clientFeatures.renameFeature.isEnabled(it) }}, error=${wrapper.serverError}"
            }
        return "available=$available, file=$file, servers=$servers; ${focusState(editor)}"
    }

    @JvmStatic
    fun focusEditor(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val manager = IdeFocusManager.getInstance(editor.project)
        SwingUtilities.invokeLater {
            if (!editor.isDisposed) manager.requestFocus(editor.contentComponent, true)
        }
    }

    @JvmStatic
    fun focusState(editor: Editor): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val window = SwingUtilities.getWindowAncestor(editor.contentComponent)
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        return "editorDisposed=${editor.isDisposed}, window=${window?.javaClass?.simpleName}, " +
            "windowFocused=${window?.isFocused}, active=${manager.activeWindow?.javaClass?.simpleName}, " +
            "focused=${manager.focusedWindow?.javaClass?.simpleName}, owner=${manager.focusOwner?.javaClass?.name}, " +
            "editorFocused=${editor.contentComponent.isFocusOwner}"
    }

    /** A disposable, unowned window exercises real focus loss without touching another app. */
    @JvmStatic
    fun interruptFocus(): Window {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return JFrame("Playbook focus interruption").apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            add(JLabel("Testing focus recovery; this window closes automatically."))
            setSize(420, 100)
            isVisible = true
            AppIcon.getInstance().requestFocus(this)
        }
    }

    @JvmStatic
    fun closeCompletion(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        CompletionServiceImpl.currentCompletionProgressIndicator?.closeAndFinish(true)
        LookupManager.getActiveLookup(editor)?.hideLookup(true)
        // Reopening an unapplied popup after focus loss is a fresh invocation. Retaining the
        // previous phase makes IDEA broaden it to second-invocation word completion instead.
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion)
    }

    @JvmStatic
    fun hasFocus(editor: Editor): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val frame = SwingUtilities.getWindowAncestor(editor.contentComponent) ?: return false
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        return sequenceOf(manager.activeWindow, manager.focusedWindow).filterNotNull().any { active
            ->
            generateSequence(active) { it.owner }.any { it === frame }
        }
    }

    @JvmStatic
    fun scrollToCaret(editor: Editor) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val model = editor.scrollingModel
        model.disableAnimation()
        try {
            model.scrollToCaret(ScrollType.CENTER)
        } finally {
            model.enableAnimation()
        }
    }
}
