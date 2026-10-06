package org.xtclang.idea.playbook.probe

import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** Submit the actual Show Usages chooser without sending an untargeted desktop keystroke. */
object NavigationChooser {
    @JvmStatic
    fun choose(
        component: Component,
        row: Int,
    ) {
        check(SwingUtilities.isEventDispatchThread())
        val table = component as JTable
        check(table.isShowing) { "Navigation chooser is no longer visible" }
        require(row in 0 until table.rowCount) { "Missing navigation row $row" }
        // PopupChooserBuilder registers its Enter callback on this map. WHEN_FOCUSED can
        // instead contain JTable's ordinary next-row action, which does not navigate.
        val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        val bindings = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        require(enter in bindings.keys().orEmpty()) { "Navigation chooser has no local Enter binding" }
        val binding =
            requireNotNull(bindings.get(enter)) {
                "Navigation chooser has no Enter binding"
            }
        val action = requireNotNull(table.actionMap.get(binding)) { "Navigation chooser has no Enter action" }
        check(action.isEnabled) { "Navigation chooser Enter action is disabled" }
        table.setRowSelectionInterval(row, row)
        action.actionPerformed(ActionEvent(table, ActionEvent.ACTION_PERFORMED, enter.toString()))
    }
}
