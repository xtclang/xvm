package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AppIcon
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities

/** Small IDE-side observations avoid walking the entire Swing tree for every focus poll. */
object EditorUi {
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
        LookupManager.getActiveLookup(editor)?.hideLookup(true)
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
