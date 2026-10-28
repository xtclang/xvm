package org.xtclang.idea.playbook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.xtclang.idea.playbook.probe.NavigationChooser
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class NavigationChooserTest {
    @Test
    fun `the chosen row reaches the popup action once without keyboard focus`() =
        SwingUtilities.invokeAndWait {
            val calls = AtomicInteger()
            val table =
                table {
                    assertEquals(1, (it.source as JTable).selectedRow)
                    calls.incrementAndGet()
                }
            NavigationChooser.choose(table, 1)
            assertEquals(1, calls.get())
        }

    @Test
    fun `a disabled action refuses before selection or navigation`() =
        SwingUtilities.invokeAndWait {
            val table = table { error("Disabled action must not run") }
            table.actionMap.get("choose").isEnabled = false
            assertThrows(IllegalStateException::class.java) { NavigationChooser.choose(table, 1) }
            assertEquals(-1, table.selectedRow)
        }

    @Test
    fun `a missing row or popup binding refuses without choosing a different target`() =
        SwingUtilities.invokeAndWait {
            val table = table { error("Invalid selection must not run") }
            assertThrows(IllegalArgumentException::class.java) { NavigationChooser.choose(table, 2) }
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).clear()
            assertThrows(IllegalArgumentException::class.java) { NavigationChooser.choose(table, 0) }
            assertEquals(-1, table.selectedRow)
        }

    private fun table(chosen: (ActionEvent) -> Unit): JTable =
        object : JTable(2, 1) {
            override fun isShowing(): Boolean = true
        }.apply {
            val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
            getInputMap(JComponent.WHEN_FOCUSED).put(enter, "wrong-context")
            actionMap.put(
                "wrong-context",
                object : AbstractAction() {
                    override fun actionPerformed(event: ActionEvent) = error("Used the focused-cell action")
                },
            )
            getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "choose")
            actionMap.put(
                "choose",
                object : AbstractAction() {
                    override fun actionPerformed(event: ActionEvent) = chosen(event)
                },
            )
        }
}
