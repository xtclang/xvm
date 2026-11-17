package org.xtclang.idea.playbook.probe

import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.popup.list.ListPopupImpl
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Reads and chooses the rows of the showing list or table popup in one event-dispatch step each. A popup that
 * closes between a presence check and a row read is then seen as closed, and nothing is chosen from it, instead
 * of failing the read; the caller's document-change guard decides whether it may be reopened.
 */
object PopupChoices {
    /** The rows of the one showing list popup, such as Show Intention Actions, or null when there is none. */
    @JvmStatic
    fun listRows(): List<String>? {
        check(SwingUtilities.isEventDispatchThread())
        return listPopup()?.let(::texts)
    }

    /** Chooses the first row of the showing list popup that contains [text], as Enter does; false if none shows it. */
    @JvmStatic
    fun chooseListRow(text: String): Boolean {
        check(SwingUtilities.isEventDispatchThread())
        val popup = listPopup() ?: return false
        val index = texts(popup).indexOfFirst { text in it }
        if (index < 0) return false
        popup.list.selectedIndex = index
        popup.handleSelect(true)
        return true
    }

    /** The rows of the one showing popup table, such as LSP4IJ's navigation chooser, each as its cells' text. */
    @JvmStatic
    fun tableRows(): List<String>? {
        check(SwingUtilities.isEventDispatchThread())
        return popupTable()?.let(::rows)
    }

    /** Chooses row [index] of the showing popup table when it has [rows] rows; false otherwise. */
    @JvmStatic
    fun chooseTableRow(
        index: Int,
        rows: Int,
    ): Boolean {
        check(SwingUtilities.isEventDispatchThread())
        val table = popupTable()?.takeIf { it.rowCount == rows } ?: return false
        NavigationChooser.choose(table, index)
        return true
    }

    /** Chooses the one row of the showing popup table that contains [text] when it has [rows] rows; false otherwise. */
    @JvmStatic
    fun chooseTableRowContaining(
        text: String,
        rows: Int,
    ): Boolean {
        check(SwingUtilities.isEventDispatchThread())
        val table = popupTable()?.takeIf { it.rowCount == rows } ?: return false
        val index = rows(table).withIndex().singleOrNull { text in it.value }?.index ?: return false
        NavigationChooser.choose(table, index)
        return true
    }

    private fun listPopup(): ListPopupImpl? =
        showing(JList::class.java).mapNotNull { PopupUtil.getPopupContainerFor(it) as? ListPopupImpl }.distinct().singleOrNull()

    private fun popupTable(): JTable? = showing(JTable::class.java).filter { PopupUtil.getPopupContainerFor(it) != null }.singleOrNull()

    private fun <T : Component> showing(type: Class<T>): List<T> =
        Window
            .getWindows()
            .filter { it.isShowing }
            .flatMap { descendants(it).toList() }
            .filter { type.isInstance(it) && it.isShowing }
            .map(type::cast)

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun texts(popup: ListPopupImpl): List<String> {
        val step = popup.listStep as ListPopupStep<Any?>
        val model = popup.list.model
        return (0 until model.size).map { step.getTextFor(model.getElementAt(it)) }
    }

    private fun rows(table: JTable): List<String> =
        (0 until table.rowCount).map { row ->
            (0 until table.columnCount).joinToString(" ") { column ->
                text(table.prepareRenderer(table.getCellRenderer(row, column), row, column))
            }
        }

    /** The text a cell renderer shows, from its labels and colored fragments. */
    private fun text(component: Component): String =
        when (component) {
            is SimpleColoredComponent -> component.getCharSequence(false).toString()

            is JLabel -> component.text.orEmpty()

            is AbstractButton -> component.text.orEmpty()

            is JTextComponent -> component.text.orEmpty()

            is Container ->
                component.components
                    .map(::text)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

            else -> ""
        }
}
