package org.xtclang.idea.playbook.probe

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** Invoke the adapter action from the editor and answer its real chooser, without a desktop keystroke. */
object AdapterChooserUi {
    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    /**
     * Whether the active keymap binds [actionId] to [shortcut], written as VS Code writes keybindings so both
     * editors are checked against one value: `+` joins a key to its modifiers, a space separates chord parts.
     */
    @JvmStatic
    fun hasShortcut(
        actionId: String,
        shortcut: String,
    ): Boolean {
        val strokes =
            shortcut.split(' ').map { part ->
                val tokens = part.split('+')
                val modifiers = tokens.dropLast(1).joinToString(" ") { if (it == "cmd") "meta" else it }
                requireNotNull(KeyStroke.getKeyStroke("$modifiers ${tokens.last().uppercase()}".trim())) { "Unreadable shortcut $shortcut" }
            }
        require(strokes.size in 1..2) { "A keymap shortcut has one or two keystrokes: $shortcut" }
        val expected = KeyboardShortcut(strokes.first(), strokes.getOrNull(1))
        return KeymapManager
            .getInstance()
            .activeKeymap
            .getShortcuts(actionId)
            .any { it == expected }
    }

    /** The active keymap's name and [actionId]'s shortcuts there, for failure messages. */
    @JvmStatic
    fun describeShortcuts(actionId: String): String {
        val keymap = KeymapManager.getInstance().activeKeymap
        return "${keymap.name}: " +
            keymap.getShortcuts(actionId).joinToString { shortcut ->
                if (shortcut is KeyboardShortcut) "${shortcut.firstKeyStroke} / ${shortcut.secondKeyStroke}" else shortcut.toString()
            }
    }

    /** Whether [actionId] is registered in the action group [groupId], such as the Tools menu. */
    @JvmStatic
    fun inGroup(
        groupId: String,
        actionId: String,
    ): Boolean {
        val manager = ActionManager.getInstance()
        val group = manager.getAction(groupId) as? DefaultActionGroup ?: return false
        val action = manager.getAction(actionId) ?: return false
        return group.childActionsOrStubs.any { it === action || manager.getId(it) == actionId }
    }

    /** Performs the action as its shortcut does, from the editor's own component. */
    @JvmStatic
    fun invoke(
        editor: Editor,
        actionId: String,
    ) {
        check(SwingUtilities.isEventDispatchThread())
        val action = requireNotNull(ActionManager.getInstance().getAction(actionId)) { "Missing action $actionId" }
        val context = DataManager.getInstance().getDataContext(editor.contentComponent)
        val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
        ActionUtil.performAction(action, event)
    }

    /** Chooses [label] in the showing chooser through its own Enter binding; false while no chooser lists it. */
    @JvmStatic
    fun pick(label: String): Boolean {
        check(SwingUtilities.isEventDispatchThread())
        val list =
            Window
                .getWindows()
                .asSequence()
                .filter { it.isShowing }
                .flatMap { descendants(it) }
                .filterIsInstance<JList<*>>()
                .firstOrNull { list -> list.isShowing && (0 until list.model.size).any { list.model.getElementAt(it).toString() == label } }
                ?: return false
        val row = (0 until list.model.size).first { list.model.getElementAt(it).toString() == label }
        val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        val binding =
            listOf(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_FOCUSED)
                .firstNotNullOfOrNull { list.getInputMap(it).get(enter) }
        val action = requireNotNull(binding?.let(list.actionMap::get)) { "The chooser has no Enter action" }
        check(action.isEnabled) { "The chooser's Enter action is disabled" }
        list.selectedIndex = row
        action.actionPerformed(ActionEvent(list, ActionEvent.ACTION_PERFORMED, enter.toString()))
        return true
    }
}
