package org.xtclang.idea.playbook.probe

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.ui.GotItTooltip
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.AbstractButton

/**
 * Got It tooltips, which the playbook IDE starts with off (`ide.integration.test.disable.got.it.tooltips`), so that
 * none shows in the middle of a case: a check turns them on for itself, with its tooltip unseen, finds it over the
 * editor, and clicks it away, which turns them off again.
 */
object GotItUi {
    private const val DISABLED = "ide.integration.test.disable.got.it.tooltips"

    /** Turns Got It tooltips on, with [id] unseen, as in an IDE that has never shown it. */
    @JvmStatic
    fun enable(id: String) {
        PropertiesComponent.getInstance().unsetValue("${GotItTooltip.PROPERTY_PREFIX}.$id")
        System.setProperty(DISABLED, "false")
    }

    /** Whether a Got It tooltip shows over [editor]. */
    @JvmStatic
    fun shown(editor: Editor): Boolean = boundedEdt(ModalityState.any()) { GotItTooltip.isCurrentlyShownFor(editor.contentComponent) }

    /** Clicks the Got It button of the tooltip that shows, if one does, and turns Got It tooltips off again. */
    @JvmStatic
    fun dismiss(): Boolean =
        try {
            boundedEdt(ModalityState.any()) {
                Window
                    .getWindows()
                    .asSequence()
                    .filter { it.isShowing }
                    .flatMap(::descendants)
                    .filterIsInstance<AbstractButton>()
                    .firstOrNull { it.isShowing && it.text.equals("Got it", ignoreCase = true) }
                    ?.also { it.doClick() } != null
            }
        } finally {
            System.setProperty(DISABLED, "true")
        }

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }
}
