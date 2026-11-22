package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xtclang.idea.XtcProductivityFeatures
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that plugin.xml's tips, XtcProductivityFeatures.xml's features and their names agree. IntelliJ reports
 * none of these mistakes: a tip whose file is missing, a feature that tracks an action no one declares or a name
 * missing from the bundle shows up only as a tip that never appears or a feature that never counts as used.
 */
@DisplayName("Tip of the Day (tips/ and XtcProductivityFeatures.xml)")
class TipOfTheDayTest {
    private val classLoader = javaClass.classLoader

    private val manifest: Element by lazy { parse("META-INF/plugin.xml") }

    private val features: Element by lazy { parse(XtcProductivityFeatures().xmlFilesUrls.single()) }

    private val tips: List<String> by lazy { manifest.elements("tipAndTrick").map { it.getAttribute("file") } }

    private val actions: Set<String> by lazy { manifest.elements("action").map { it.getAttribute("id") }.toSet() }

    @Test
    @DisplayName("each tip plugin.xml registers is a file under tips/")
    fun tipsExist() {
        assertThat(tips).isNotEmpty.allSatisfy { assertThat(classLoader.getResource("tips/$it")).describedAs(it).isNotNull }
    }

    @Test
    @DisplayName("each tip is the tip of exactly one feature, and each feature's tip is registered")
    fun tipsAreFeatures() {
        val tipIds = features.elements("feature").map { it.getAttribute("tip-id") }
        assertThat(tipIds).containsExactlyInAnyOrderElementsOf(tips.map { it.removeSuffix(".html") })
    }

    @Test
    @DisplayName("each action a feature tracks or an Ecstasy tip's shortcut shows is one plugin.xml declares")
    fun actionsAreDeclared() {
        val tracked = features.elements("track-action").map { it.getAttribute("id") }
        val shown =
            tips.flatMap { tip ->
                val text = classLoader.getResource("tips/$tip")!!.readText()
                SHORTCUT
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .filter { it.startsWith("xtc.") }
                    .toList()
            }
        assertThat(tracked + shown).isNotEmpty.allSatisfy { assertThat(actions).contains(it) }
    }

    @Test
    @DisplayName("the bundle plugin.xml registers names the group and each feature")
    fun featuresAreNamed() {
        val bundle = manifest.elements("featureStatisticsBundle").single().getAttribute("qualifiedName")
        val names = Properties().apply { classLoader.getResourceAsStream(bundle.replace('.', '/') + ".properties")!!.use(::load) }
        val keys =
            features.elements("group").map { "group." + it.getAttribute("id") } + features.elements("feature").map { it.getAttribute("id") }
        assertThat(keys).allSatisfy { assertThat(names.getProperty(it)).describedAs(it).isNotBlank }
    }

    @Test
    @DisplayName("plugin.xml registers XtcProductivityFeatures")
    fun providerIsRegistered() {
        val providers = manifest.elements("productivityFeaturesProvider").map { it.getAttribute("implementation") }
        assertThat(providers).containsExactly(XtcProductivityFeatures::class.java.name)
    }

    private fun parse(resource: String): Element {
        val url = classLoader.getResource(resource) ?: error("$resource not on the test classpath")
        return url.openStream().use {
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(it)
                .documentElement
        }
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    companion object {
        private val SHORTCUT = Regex("&shortcut:([^;]+);")
    }
}
