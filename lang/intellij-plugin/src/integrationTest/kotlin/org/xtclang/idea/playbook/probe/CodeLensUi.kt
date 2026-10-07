package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.codeVision.lensContextIfCreated
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import java.awt.event.MouseEvent

/** Inspect installed Code Vision entries and activate their real click handler without moving the pointer. */
object CodeLensUi {
    @JvmStatic
    fun titles(editor: Editor): List<String> {
        val context = editor.lensContextIfCreated ?: return emptyList()
        return context
            .getValidPairResult()
            .map { it.second }
            .filterIsInstance<TextCodeVisionEntry>()
            .map { it.text }
            .toList()
    }

    @JvmStatic
    fun click(
        editor: Editor,
        title: String,
    ) {
        val entry =
            requireNotNull(editor.lensContextIfCreated)
                .getValidPairResult()
                .map { it.second }
                .filterIsInstance<ClickableTextCodeVisionEntry>()
                .single { it.text == title }
        val point = editor.offsetToXY(editor.caretModel.offset)
        val event =
            MouseEvent(
                editor.contentComponent,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                point.x,
                point.y,
                1,
                false,
                MouseEvent.BUTTON1,
            )
        entry.onClick.invoke(event, editor)
    }
}
