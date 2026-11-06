package org.xtclang.idea.lsp

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Presses the active keymap's Undo key in the selected editor, on the EDT, as a key event that the IDE's event queue
 * dispatches, which runs the action that the keymap binds, as a user's key does, and checks that it ran Undo.
 */
internal fun pressUndo(project: Project) {
    val stroke =
        checkNotNull(
            KeymapUtil
                .getActiveKeymapShortcuts(IdeActions.ACTION_UNDO)
                .shortcuts
                .filterIsInstance<KeyboardShortcut>()
                .firstOrNull { it.secondKeyStroke == null }
                ?.firstKeyStroke,
        ) { "No Undo key in the keymap ${KeymapManager.getInstance().activeKeymap.name}" }
    val textEditor = checkNotNull(FileEditorManager.getInstance(project).selectedTextEditor) { "No selected editor" }
    val performed = CopyOnWriteArrayList<String>()
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(
        AnActionListener.TOPIC,
        object : AnActionListener {
            override fun afterActionPerformed(
                action: AnAction,
                event: AnActionEvent,
                result: AnActionResult,
            ) {
                if (result.isPerformed) performed += ActionManager.getInstance().getId(action) ?: action.javaClass.name
            }
        },
    )
    try {
        val key =
            KeyEvent(
                textEditor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                stroke.modifiers,
                stroke.keyCode,
                KeyEvent.CHAR_UNDEFINED,
            )
        IdeEventQueue.getInstance().dispatchEvent(key)
        UIUtil.dispatchAllInvocationEvents()
    } finally {
        connection.disconnect()
    }
    assertThat(performed).containsExactly(IdeActions.ACTION_UNDO)
}
