package org.xtclang.idea.playbook.probe

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.redhat.devtools.lsp4ij.features.linkedEditingRange.LSPLinkedEditingRangeSynchronizer
import com.redhat.devtools.lsp4ij.settings.LSPEditorOptions
import java.util.concurrent.CompletableFuture

/**
 * LSP4IJ's linked editing in an editor, as a user has it: on in Settings | Editor | General | Smart Keys, and the
 * ranges that LSP4IJ links once the caret stays in a name. LSP4IJ keeps its synchronizer's state to itself, so the
 * ranges are read from its fields; a change of them fails here instead of reading nothing.
 */
object LinkedEditingUi {
    // LSP4IJ's key of an editor's synchronizer
    private val synchronizerKey: Key<*> =
        LSPLinkedEditingRangeSynchronizer::class.java
            .getDeclaredField("SYNCHRONIZER_KEY")
            .apply { isAccessible = true }
            .get(null) as Key<*>

    /** Turns LSP4IJ's linked editing [on] or off for every server, and answers whether it was on. */
    @JvmStatic
    fun linkedEditing(on: Boolean): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val options = LSPEditorOptions.getInstance()
        return options.isLinkedEditing.also { options.isLinkedEditing = on }
    }

    /** Moves the caret of [editor] to [offset], where LSP4IJ asks for the linked ranges once the caret stays. */
    @JvmStatic
    fun moveCaret(
        editor: Editor,
        offset: Int,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        editor.caretModel.moveToOffset(offset)
    }

    /**
     * The ranges that LSP4IJ links in [editor], the one with the caret first, as `[[start, end], …]`; empty while
     * LSP4IJ has yet to ask for them, or has yet to apply or drop its answer.
     */
    @JvmStatic
    fun ranges(editor: Editor): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val synchronizer = checkNotNull(editor.getUserData(synchronizerKey)) { "LSP4IJ has no linked editing in the editor" }
        // the request waits until the caret stays, and its answer is applied on the EDT after it comes
        val waiting = !field<Alarm>(synchronizer, "requestAlarm").isEmpty
        if (waiting || field<CompletableFuture<*>?>(synchronizer, "request")?.isDone == false) return ""
        return Gson().toJson(field<List<RangeMarker>>(synchronizer, "ranges").map { listOf(it.startOffset, it.endOffset) })
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(
        owner: Any,
        name: String,
    ): T =
        owner.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(owner) as T
}
