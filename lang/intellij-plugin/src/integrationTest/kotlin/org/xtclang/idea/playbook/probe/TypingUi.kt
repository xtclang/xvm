package org.xtclang.idea.playbook.probe

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction

/**
 * Types into an editor as a user does: characters go through the typed action, and a line break
 * through the Enter action, so the editor's own and the plugin's Enter handlers run as for key presses.
 */
object TypingUi {
    @JvmStatic
    fun type(
        editor: Editor,
        text: String,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val context = DataManager.getInstance().getDataContext(editor.contentComponent)
        WriteIntentReadAction.run {
            text.forEach { ch ->
                if (ch == '\n') {
                    val enter = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
                    CommandProcessor.getInstance().executeCommand(
                        editor.project,
                        { enter.execute(editor, editor.caretModel.currentCaret, context) },
                        "Enter",
                        null,
                    )
                } else {
                    TypedAction.getInstance().actionPerformed(editor, ch, context)
                }
            }
        }
    }

    /** The caret's offset, read on the EDT. */
    @JvmStatic
    fun caretOffset(editor: Editor): Int {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return editor.caretModel.offset
    }
}
