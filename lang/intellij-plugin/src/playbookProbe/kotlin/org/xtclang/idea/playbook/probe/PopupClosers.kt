package org.xtclang.idea.playbook.probe

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * What closes an editor's hover popup, as Ctrl+F1's description of a problem is, counted from the first use of this
 * object on: the selected editor's scroll, which a block inlay that arrives above the caret makes, an action, and a
 * switch to another application, and the latest of them in the order they came. A step whose popup did not show says
 * what came after its press. Loaded only in the test IDE.
 */
object PopupClosers {
    private const val KEPT = 8

    private val scrolls = AtomicInteger()
    private val actions = AtomicInteger()
    private val deactivations = AtomicInteger()

    /** The latest of them, the newest last: `scroll`, `deactivation`, or an action's ID. */
    private val latest = ConcurrentLinkedDeque<String>()

    private fun observe(event: String) {
        latest.addLast(event)
        while (latest.size > KEPT) latest.pollFirst()
    }

    init {
        val bus = ApplicationManager.getApplication().messageBus.connect()
        bus.subscribe(
            AnActionListener.TOPIC,
            object : AnActionListener {
                override fun beforeActionPerformed(
                    action: AnAction,
                    event: AnActionEvent,
                ) {
                    actions.incrementAndGet()
                    observe(ActionManager.getInstance().getId(action) ?: action.javaClass.simpleName)
                }
            },
        )
        bus.subscribe(
            ApplicationActivationListener.TOPIC,
            object : ApplicationActivationListener {
                override fun applicationDeactivated(ideFrame: IdeFrame) {
                    deactivations.incrementAndGet()
                    observe("deactivation")
                }
            },
        )
        // the popup closes when its editor's visible area moves, not when it only changes its size; the selected editor's,
        //  as a console's moves as it prints
        EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(
            object : VisibleAreaListener {
                override fun visibleAreaChanged(e: VisibleAreaEvent) {
                    val selected = e.editor.project?.let { FileEditorManager.getInstance(it).selectedTextEditor }
                    if (e.editor == selected && e.oldRectangle != null && e.oldRectangle.location != e.newRectangle.location) {
                        scrolls.incrementAndGet()
                        observe("scroll")
                    }
                }
            },
            Disposer.newDisposable("PopupClosers"),
        )
    }

    /** The counts so far, and the latest of them in order, as a failure's message names them. */
    @JvmStatic
    fun observed(): String =
        "${scrolls.get()} scrolls of the selected editor, ${actions.get()} actions, ${deactivations.get()} deactivations " +
            "(latest, oldest first: ${latest.joinToString()})"
}
