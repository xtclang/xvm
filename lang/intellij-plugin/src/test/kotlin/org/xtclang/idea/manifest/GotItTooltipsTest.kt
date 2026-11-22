package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xtclang.idea.lsp.XtcGotItTooltips
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies the actions whose shortcuts the Got It tooltips show. IntelliJ reports no mistake there: a tooltip that
 * names an undeclared action, or one with no shortcut, shows an empty shortcut in its text.
 */
@DisplayName("Got It tooltips (XtcGotItTooltips)")
class GotItTooltipsTest {
    private val actions: Map<String, Element> by lazy {
        val url = javaClass.classLoader.getResource("META-INF/plugin.xml") ?: error("plugin.xml not on the test classpath")
        val manifest =
            url.openStream().use {
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(it)
                    .documentElement
            }
        manifest.elements("action").associateBy { it.getAttribute("id") }
    }

    @Test
    @DisplayName("each action a tooltip shows the shortcut of is declared, with a shortcut in the default and macOS keymaps")
    fun shortcutsExist() {
        listOf(XtcGotItTooltips.SELECT_ADAPTER, XtcGotItTooltips.RESTART_SERVER, XtcGotItTooltips.SERVER_LOG).forEach { id ->
            val keymaps = actions[id]?.elements("keyboard-shortcut")?.map { it.getAttribute("keymap") }
            assertThat(keymaps).describedAs(id).isNotNull.contains("\$default", "Mac OS X")
        }
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
