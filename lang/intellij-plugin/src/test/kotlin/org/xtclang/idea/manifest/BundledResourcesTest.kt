package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that the resources referenced by plugin.xml and our Kotlin
 * code actually exist on the runtime classpath.
 *
 * Counterpart to VS Code's `jar-bundling.test.ts` — guards the same
 * class of failure: a build step (e.g. `copyTextMateToSandbox`,
 * `copyLspServerToSandbox`) gets removed or renamed and the manifest
 * keeps pointing at a path that no longer exists, with no signal until
 * a user installs the plugin and gets a NullPointerException loading a
 * `.x` file. The complementary `LspServerJarResolutionTest` already
 * covers the LSP/DAP JAR path semantics; this test covers everything
 * else our manifest claims should be there.
 */
@DisplayName("Bundled plugin resources")
class BundledResourcesTest {
    private val classLoader = javaClass.classLoader

    @Test
    @DisplayName("META-INF/plugin.xml is on the classpath")
    fun pluginXmlPresent() {
        assertThat(classLoader.getResource("META-INF/plugin.xml"))
            .withFailMessage("META-INF/plugin.xml not on test classpath — the IntelliJ Platform won't see the plugin at all.")
            .isNotNull
    }

    @Test
    @DisplayName("liveTemplates/XTC.xml resolves at the path plugin.xml references")
    fun liveTemplatesPresent() {
        // plugin.xml has <defaultLiveTemplates>/liveTemplates/XTC</defaultLiveTemplates>
        // (no .xml extension — IntelliJ appends it). The file MUST live at
        // liveTemplates/XTC.xml relative to the resource root.
        assertThat(classLoader.getResource("liveTemplates/XTC.xml"))
            .withFailMessage(
                "liveTemplates/XTC.xml not on classpath. plugin.xml's <defaultLiveTemplates> " +
                    "tag still points there — either restore the file or update the manifest.",
            ).isNotNull
    }

    @Test
    @DisplayName("icons/xtc.svg is on the classpath")
    fun iconPresent() {
        // XtcIconProvider.XTC_ICON loads icons/xtc.svg via the IntelliJ
        // IconLoader (which resolves against the plugin classloader).
        // Missing icon = file-type / wizard / run-config items render with
        // the generic file glyph instead of our chrome-X.
        assertThat(classLoader.getResource("icons/xtc.svg"))
            .withFailMessage("icons/xtc.svg not on classpath — wizard, run-config, and file icons fall back to defaults.")
            .isNotNull
    }
}
