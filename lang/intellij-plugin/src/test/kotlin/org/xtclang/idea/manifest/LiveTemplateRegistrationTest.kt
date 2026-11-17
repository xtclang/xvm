package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xtclang.idea.XtcTemplateContextType
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that the live-template file referenced by plugin.xml's
 * `<defaultLiveTemplates file="/liveTemplates/Ecstasy.xml"/>` actually exists at the expected
 * path, parses, and contains at least the snippet shortcuts we expose to users in the README +
 * manual test plan.
 *
 * Counterpart to VS Code's `snippets.test.ts` — same failure mode: a snippet vanishes from the
 * bundled file or its shortcut gets renamed, and users see the editor "still work" but
 * tab-expansion silently fails. Without this test the only catch would be someone noticing during
 * interactive QA.
 */
@DisplayName("Live template registration (liveTemplates/Ecstasy.xml)")
class LiveTemplateRegistrationTest {
    private val templateSet: Element by lazy {
        // openStream() handles both loose-on-disk and packaged-in-JAR
        // classpath entries (see comment in PluginManifestTest).
        val resourceUrl =
            javaClass.classLoader.getResource("liveTemplates/Ecstasy.xml")
                ?: error(
                    "liveTemplates/Ecstasy.xml not on test classpath — file path drifted from plugin.xml",
                )
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(resourceUrl.openStream())
            .documentElement
    }

    @Test
    @DisplayName("plugin.xml names the template file in defaultLiveTemplates' file attribute, which IntelliJ reads")
    fun registrationNamesTheFile() {
        // IntelliJ's DefaultLiveTemplateEP reads only its required file attribute; a path written as the element's
        // text left it unset, and IntelliJ loaded none of the templates
        val registrations = manifest().getElementsByTagName("defaultLiveTemplates")
        val files = (0 until registrations.length).map { (registrations.item(it) as Element).getAttribute("file") }
        assertThat(files).containsExactly("/liveTemplates/Ecstasy.xml")
        assertThat(javaClass.getResource(files.single())).isNotNull
    }

    @Test
    @DisplayName("templateSet group attribute is set")
    fun groupAttributeSet() {
        assertThat(templateSet.tagName).isEqualTo("templateSet")
        val group = templateSet.getAttribute("group")
        assertThat(group)
            .withFailMessage(
                "liveTemplates/Ecstasy.xml has no <templateSet group='...'> — the file would " +
                    "load with templates dumped into an unnamed group in Settings -> Live Templates.",
            ).isNotEmpty
    }

    @Test
    @DisplayName("contains all snippet shortcuts the README + manual-test-plan promise")
    fun coreShortcutsPresent() {
        val templates = templateSet.getElementsByTagName("template")
        val names =
            (0 until templates.length)
                .map { (templates.item(it) as Element).getAttribute("name") }
                .filter { it.isNotEmpty() }
                .toSet()

        // The README's snippet table and manual-test-plan section 17 promise
        // these shortcuts. If a shortcut is renamed or removed without
        // updating either doc, users typing the documented prefix get nothing.
        val required =
            setOf(
                "mod",
                "cls",
                "iface",
                "svc",
                "mix",
                "enu",
                "con",
                "pkg",
                "meth",
                "run",
                "prop",
                "construct",
                "if",
                "ife",
            )

        val missing = required - names
        assertThat(missing)
            .withFailMessage(
                "live template shortcuts missing from Ecstasy.xml that are documented in the README/test plan: %s. " +
                    "Either restore the templates in liveTemplates/Ecstasy.xml or remove the row from the docs.",
                missing,
            ).isEmpty()
    }

    @Test
    @DisplayName("every template applies in Ecstasy files, through the context plugin.xml registers")
    fun everyTemplateUsesTheEcstasyContext() {
        // The platform's OTHER ("Everywhere") context is every file, so a template enabled there is
        // offered in other languages' files too. Each template enables the plugin's ECSTASY context,
        // which XtcTemplateContextType implements, and so applies in Ecstasy files only.
        val templates = templateSet.getElementsByTagName("template")
        val outside =
            (0 until templates.length)
                .map { templates.item(it) as Element }
                .filterNot { template ->
                    val options = template.getElementsByTagName("option")
                    (0 until options.length).map { options.item(it) as Element }.any {
                        it.getAttribute("name") == CONTEXT && it.getAttribute("value") == "true"
                    }
                }.map { it.getAttribute("name") }
        assertThat(outside)
            .withFailMessage("templates in liveTemplates/Ecstasy.xml without the $CONTEXT context: %s", outside)
            .isEmpty()

        val contexts = manifest().getElementsByTagName("liveTemplateContext")
        val registered =
            (0 until contexts.length)
                .map { contexts.item(it) as Element }
                .associate { it.getAttribute("contextId") to it.getAttribute("implementation") }
        assertThat(registered).containsEntry(CONTEXT, XtcTemplateContextType::class.java.name)
    }

    private fun manifest(): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(checkNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml")).openStream())
            .documentElement

    private companion object {
        const val CONTEXT = "ECSTASY"
    }
}
