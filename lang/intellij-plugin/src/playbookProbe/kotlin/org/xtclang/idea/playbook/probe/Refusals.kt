package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.UIUtil
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the IDE says as it refuses an action rather than making its edit, in the order it says it, from the first use of
 * this object on, and where: each error hint that an editor shows, as the rename's "Sources changed; invoke Rename
 * again." or LSP4IJ's refusal of a code action's edit, with its editor, and each notification of a refactoring that ran
 * in the background, as a move's, with its project. Heard as they show, so that a wait for the action's edit fails at
 * once with the IDE's reason, however soon the hint hides; and only where the action is, so that another editor's
 * hint, such as LSP4IJ's "No Implementation found." of a Go to, fails no wait. Loaded only in the test IDE.
 */
object Refusals {
    /** The title of the notification with which the plugin refuses a file's rename or a move, which run in the background. */
    private const val REFACTORING = "Ecstasy Refactoring"

    /** What the IDE said, and the editor of a hint or the project of a notification, which it does not keep alive. */
    private class Said(
        val text: String,
        where: Any,
    ) {
        private val where = WeakReference(where)

        fun isIn(place: Any): Boolean = where.get() === place
    }

    private val said = CopyOnWriteArrayList<Said>()

    /** The projects whose notifications are heard, each until it is disposed. */
    private val heard = ConcurrentHashMap.newKeySet<Project>()

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            EditorHintListener.TOPIC,
            object : EditorHintListener {
                override fun hintShown(
                    editor: Editor,
                    hint: LightweightHint,
                    flags: Int,
                    hintInfo: HintHint,
                ) {
                    UIUtil
                        .uiTraverser(hint.component)
                        .filter(HintUtil.HintLabel::class.java)
                        .filter { it.icon == AllIcons.General.BalloonError }
                        .forEach { said += Said(StringUtil.removeHtmlTags(it.text, true).trim(), editor) }
                }
            },
        )
    }

    /** Where what the IDE says in [project] from now on begins, which [notices] and [refusals] take. */
    @JvmStatic
    fun mark(project: Project): Int {
        if (heard.add(project)) {
            Disposer.register(project) { heard.remove(project) }
            project.messageBus.connect(project).subscribe(
                Notifications.TOPIC,
                object : Notifications {
                    override fun notify(notification: Notification) {
                        if (notification.title == REFACTORING) {
                            said += Said(StringUtil.removeHtmlTags(notification.content, true).trim(), project)
                        }
                    }
                },
            )
        }
        return said.size
    }

    /** The refactorings' notifications in [project] since [mark], the oldest first. */
    @JvmStatic
    fun notices(
        mark: Int,
        project: Project,
    ): List<String> = said.drop(mark).filter { it.isIn(project) }.map { it.text }

    /** The error hints of [editor] and the refactorings' notifications in [project] since [mark], the oldest first. */
    @JvmStatic
    fun refusals(
        mark: Int,
        project: Project,
        editor: Editor,
    ): List<String> = said.drop(mark).filter { it.isIn(editor) || it.isIn(project) }.map { it.text }
}
