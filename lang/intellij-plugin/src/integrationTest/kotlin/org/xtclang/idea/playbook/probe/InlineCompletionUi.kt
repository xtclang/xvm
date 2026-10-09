package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedAction

/** Observe native ghost text; insertion and dismissal still go through the real IDE actions. */
object InlineCompletionUi {
    @JvmStatic
    fun type(
        editor: Editor,
        text: String,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        WriteIntentReadAction.run {
            val context = DataManager.getInstance().getDataContext(editor.contentComponent)
            text.forEach { TypedAction.getInstance().actionPerformed(editor, it, context) }
        }
    }

    @JvmStatic
    fun visibleText(editor: Editor): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val session = InlineCompletionSession.getOrNull(editor) ?: return ""
        return if (session.context.isCurrentlyDisplaying()) session.context.textToInsert() else ""
    }
}
