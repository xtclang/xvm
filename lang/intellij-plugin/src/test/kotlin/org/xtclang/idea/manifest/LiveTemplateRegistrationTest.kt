package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that the live-template file referenced by plugin.xml's
 * `<defaultLiveTemplates>/liveTemplates/XTC</defaultLiveTemplates>`
 * actually exists at the expected path, parses, and contains at least
 * the snippet shortcuts we expose to users in the README + manual test
 * plan.
 *
 * Counterpart to VS Code's `snippets.test.ts` — same failure mode: a
 * snippet vanishes from the bundled file or its shortcut gets renamed,
 * and users see the editor "still work" but tab-expansion silently
 * fails. Without this test the only catch would be someone noticing
 * during interactive QA.
 */
@DisplayName("Live template registration (liveTemplates/XTC.xml)")
class LiveTemplateRegistrationTest {
    private val templateSet: Element by lazy {
        // openStream() handles both loose-on-disk and packaged-in-JAR
        // classpath entries (see comment in PluginManifestTest).
        val resourceUrl =
            javaClass.classLoader.getResource("liveTemplates/XTC.xml")
                ?: error("liveTemplates/XTC.xml not on test classpath — file path drifted from plugin.xml")
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(resourceUrl.openStream())
            .documentElement
    }

    @Test
    @DisplayName("templateSet group attribute is set")
    fun groupAttributeSet() {
        assertThat(templateSet.tagName).isEqualTo("templateSet")
        val group = templateSet.getAttribute("group")
        assertThat(group)
            .withFailMessage(
                "liveTemplates/XTC.xml has no <templateSet group='...'> — the file would " +
                    "load with templates dumped into an unnamed group in Settings -> Live Templates.",
            ).isNotEmpty
    }

    @Test
    @DisplayName("contains all snippet shortcuts the README + MANUAL_TEST_PLAN promise")
    fun coreShortcutsPresent() {
        val templates = templateSet.getElementsByTagName("template")
        val names =
            (0 until templates.length)
                .map { (templates.item(it) as Element).getAttribute("name") }
                .filter { it.isNotEmpty() }
                .toSet()

        // The README's snippet table and MANUAL_TEST_PLAN section 17 promise
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
                "live template shortcuts missing from XTC.xml that are documented in the README/test plan: %s. " +
                    "Either restore the templates in liveTemplates/XTC.xml or remove the row from the docs.",
                missing,
            ).isEmpty()
    }

    @Test
    @DisplayName("at least one template uses the OTHER context")
    fun atLeastOneOtherContextTemplate() {
        // Sanity-check that templates carry a <context> child with a sensible
        // option, so they actually trigger in editor. The OTHER value is the
        // catch-all IntelliJ uses for TextMate-backed languages like ours.
        val templates = templateSet.getElementsByTagName("template")
        val withOtherContext =
            (0 until templates.length)
                .map { templates.item(it) as Element }
                .count { template ->
                    val contexts = template.getElementsByTagName("context")
                    (0 until contexts.length).any { ci ->
                        val ctx = contexts.item(ci) as Element
                        val options = ctx.getElementsByTagName("option")
                        (0 until options.length).any { oi ->
                            val opt = options.item(oi) as Element
                            opt.getAttribute("name") == "OTHER" && opt.getAttribute("value") == "true"
                        }
                    }
                }

        assertThat(withOtherContext)
            .withFailMessage(
                "no template in liveTemplates/XTC.xml declares <context><option name='OTHER' value='true'/></context>. " +
                    "Without an applicable context the templates won't trigger in TextMate-backed editors like ours.",
            ).isGreaterThan(0)
    }
}
