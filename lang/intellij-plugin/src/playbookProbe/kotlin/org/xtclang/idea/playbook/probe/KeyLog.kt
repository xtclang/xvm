package org.xtclang.idea.playbook.probe

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Dialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentLinkedDeque
import javax.swing.SwingUtilities

/**
 * What the IDE got since the tour last pressed keys, for a failure's message: each key pressed and released, at which
 * component of which window; each change of the keyboard focus and of the focused window; and each action that ran,
 * by its ID; with the time since the keys were pressed. A chord that does nothing says whether its keys reached the
 * IDE, and where. Loaded only in the test IDE, from the first press on.
 */
object KeyLog {
    private const val KEPT = 60

    private val entries = ConcurrentLinkedDeque<String>()

    @Volatile
    private var since = System.nanoTime()

    init {
        // the keys as the IDE's event queue takes them, before its key dispatcher, which consumes a shortcut's keys
        IdeEventQueue.getInstance().addPreprocessor(
            { event ->
                (event as? KeyEvent)?.let(::describe)?.let(::add)
                false
            },
            Disposer.newDisposable("KeyLog"),
        )
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event -> describe(event)?.let(::add) },
            AWTEvent.FOCUS_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK,
        )
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            AnActionListener.TOPIC,
            object : AnActionListener {
                override fun beforeActionPerformed(
                    action: AnAction,
                    event: AnActionEvent,
                ) {
                    add("action ${ActionManager.getInstance().getId(action) ?: action.javaClass.simpleName} (${event.place})")
                }
            },
        )
    }

    /** Begins the log anew, as the tour presses keys. */
    fun mark() {
        entries.clear()
        since = System.nanoTime()
    }

    /** What the IDE got since [mark], oldest first; `nothing` for nothing. */
    fun got(): String = entries.joinToString("; ").ifEmpty { "nothing" }

    private fun add(entry: String) {
        entries.addLast("+${(System.nanoTime() - since) / 1_000_000} ms $entry")
        while (entries.size > KEPT) entries.pollFirst()
    }

    private fun describe(event: AWTEvent): String? =
        when (event) {
            is KeyEvent ->
                when (event.id) {
                    KeyEvent.KEY_PRESSED -> "pressed ${KeyEvent.getKeyText(event.keyCode)}${modifiers(event)} at ${where(event.component)}"
                    KeyEvent.KEY_RELEASED -> "released ${KeyEvent.getKeyText(event.keyCode)} at ${where(event.component)}"
                    else -> null
                }

            is FocusEvent -> if (event.id == FocusEvent.FOCUS_GAINED) "focus to ${where(event.component)}" else null

            is WindowEvent -> if (event.id == WindowEvent.WINDOW_GAINED_FOCUS) "window focused: ${title(event.window)}" else null

            else -> null
        }

    private fun modifiers(event: KeyEvent): String =
        KeyEvent.getModifiersExText(event.modifiersEx).let { if (it.isEmpty()) "" else " ($it)" }

    private fun where(component: Component?): String =
        component?.let { "${it.javaClass.simpleName} in ${title(SwingUtilities.getWindowAncestor(it) ?: it as? Window)}" } ?: "none"

    private fun title(window: Window?): String =
        when (window) {
            null -> "no window"
            is Frame -> "frame '${window.title}'"
            is Dialog -> "dialog '${window.title}'"
            else -> window.javaClass.simpleName
        }
}
