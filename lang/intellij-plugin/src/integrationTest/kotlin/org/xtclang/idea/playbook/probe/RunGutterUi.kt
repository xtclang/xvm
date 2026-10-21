package org.xtclang.idea.playbook.probe

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import javax.swing.SwingUtilities

/** Reads and clicks the Run icons in an Ecstasy editor's gutter, as the user sees them. */
object RunGutterUi {
    private fun runIcons(editor: Editor): List<Pair<RangeHighlighter, GutterIconRenderer>> =
        (
            editor.markupModel.allHighlighters.toList() +
                DocumentMarkupModel.forDocument(editor.document, editor.project, false)?.allHighlighters.orEmpty()
        ).filter { it.isValid }
            .mapNotNull { highlighter -> (highlighter.gutterIconRenderer as? GutterIconRenderer)?.let { highlighter to it } }
            .filter { (_, renderer) -> renderer.tooltipText?.startsWith("Run ") == true }

    /** The Run icons' tooltips, each with the zero-based line it is on, as "Run Module@line". */
    @JvmStatic
    fun icons(editor: Editor): List<String> =
        runIcons(editor).map { (highlighter, renderer) ->
            "${renderer.tooltipText}@${editor.document.getLineNumber(highlighter.startOffset)}"
        }

    /** Clicks the Run icon with [tooltip], as a mouse click on the gutter does. */
    @JvmStatic
    fun click(
        editor: Editor,
        tooltip: String,
    ) {
        check(SwingUtilities.isEventDispatchThread())
        val action = requireNotNull(runIcons(editor).single { it.second.tooltipText == tooltip }.second.clickAction)
        val context = DataManager.getInstance().getDataContext(editor.contentComponent)
        ActionUtil.performAction(
            action,
            AnActionEvent.createEvent(action, context, null, ActionPlaces.EDITOR_GUTTER, ActionUiKind.NONE, null),
        )
    }
}
