package org.xtclang.idea.manifest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xtclang.idea.PluginPaths
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Self-consistency tests for META-INF/plugin.xml.
 *
 * Counterpart to VS Code's `activation.test.ts` — both guard the same failure
 * mode: an extension point / command / configuration declared in the
 * manifest but referenced from the wrong place in code (or vice versa)
 * silently fails to register at runtime. We can't fully verify
 * runtime registration without `BasePlatformTestCase`, but we can at
 * least guarantee that the manifest carries every extension point our
 * Kotlin code expects, and that the plugin ID matches the single
 * source of truth in `PluginPaths.PLUGIN_ID`.
 */
@DisplayName("Plugin manifest (META-INF/plugin.xml)")
class PluginManifestTest {
    private val pluginXml: Element by lazy {
        // Read via openStream() rather than File(uri) so we work both when
        // the resource is loose on disk (build/resources/main/...) and when
        // it ships inside the instrumented plugin JAR on the test classpath
        // — File(URI) throws IllegalArgumentException for jar: URLs.
        val resourceUrl =
            javaClass.classLoader.getResource("META-INF/plugin.xml")
                ?: error("META-INF/plugin.xml not on test classpath")
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(resourceUrl.openStream())
            .documentElement
    }

    @Test
    @DisplayName("<id> matches PluginPaths.PLUGIN_ID")
    fun pluginIdMatchesConstant() {
        val id =
            pluginXml
                .getElementsByTagName("id")
                .item(0)
                .textContent
                .trim()
        assertThat(id)
            .withFailMessage(
                "plugin.xml <id> is '%s' but PluginPaths.PLUGIN_ID is '%s'. " +
                    "The two MUST agree or PluginManager.findEnabledPlugin returns null at runtime, " +
                    "which propagates as opaque NullPointerException in the path-resolution code.",
                id,
                PluginPaths.PLUGIN_ID,
            ).isEqualTo(PluginPaths.PLUGIN_ID)
    }

    @Test
    @DisplayName("declares all required extension points (newProjectWizard, configurationType, lang.commenter, LSP server, ...)")
    fun declaresAllRequiredExtensions() {
        // Collect every <extensions defaultExtensionNs="..."> child element name
        // across the entire manifest. We don't care which <extensions> block they
        // sit in — only that they exist somewhere.
        val extensionsBlocks = pluginXml.getElementsByTagName("extensions")
        val declaredPoints = mutableSetOf<String>()
        for (i in 0 until extensionsBlocks.length) {
            val block = extensionsBlocks.item(i) as Element
            val children = block.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                if (node is Element) {
                    declaredPoints += node.tagName
                }
            }
        }

        val required =
            setOf(
                "notificationGroup", // notification group "XTC Language Server" used by XtcLspServerSupportProvider
                "fileType", // registers *.x as Ecstasy so IntelliJ does not suggest unrelated plugins
                "newProjectWizard.generator", // wizard entry created by XtcNewProjectWizard
                "configurationType", // run-config type created by XtcRunConfigurationType
                "runConfigurationProducer", // auto-create run configs from .x context
                "iconProvider", // file icons via XtcIconProvider
                "defaultLiveTemplates", // /liveTemplates/XTC snippets
                "lang.commenter", // Ctrl+/ via XtcCommenter
                "langCodeStyleSettingsProvider", // Settings -> Code Style -> Ecstasy
                "enterHandlerDelegate", // auto-indent on Enter
                "postStartupActivity", // XtcEditorStartupActivity
                "server", // LSP4IJ server registration
                "fileNamePatternMapping", // *.x -> xtcLanguageServer
                "textmate.bundleProvider", // syntax highlighting via XtcTextMateBundleProvider
            )

        val missing = required - declaredPoints
        assertThat(missing)
            .withFailMessage(
                "plugin.xml is missing extension points required by the Kotlin source: %s. " +
                    "If you removed an extension from plugin.xml, also remove the corresponding " +
                    "Kotlin class — and update this list. If you added a new Kotlin extension " +
                    "implementation, register it here AND add it to the required set.",
                missing,
            ).isEmpty()
    }

    @Test
    @DisplayName("LSP server registration points at xtcLanguageServer and maps *.x")
    fun lspServerWiring() {
        // The LSP server element lives inside <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">.
        // We look for the <server> child with id="xtcLanguageServer" and the
        // <fileNamePatternMapping> with patterns="*.x".
        val servers = pluginXml.getElementsByTagName("server")
        val server =
            (0 until servers.length)
                .map { servers.item(it) as Element }
                .firstOrNull { it.getAttribute("id") == "xtcLanguageServer" }
        assertThat(server)
            .withFailMessage("plugin.xml is missing the LSP4IJ <server id='xtcLanguageServer'> element")
            .isNotNull

        val mappings = pluginXml.getElementsByTagName("fileNamePatternMapping")
        val mapping =
            (0 until mappings.length)
                .map { mappings.item(it) as Element }
                .firstOrNull { it.getAttribute("serverId") == "xtcLanguageServer" }
        assertThat(mapping)
            .withFailMessage(
                "plugin.xml has no <fileNamePatternMapping serverId='xtcLanguageServer'> — " +
                    ".x files will not route to our LSP server.",
            ).isNotNull
        assertThat(mapping!!.getAttribute("patterns"))
            .withFailMessage("LSP fileNamePatternMapping patterns must include *.x")
            .contains("*.x")
    }

    @Test
    @DisplayName("registers .x as the Ecstasy file type")
    fun fileTypeWiring() {
        val fileTypes = pluginXml.getElementsByTagName("fileType")
        val fileType =
            (0 until fileTypes.length)
                .map { fileTypes.item(it) as Element }
                .firstOrNull { it.getAttribute("implementationClass") == "org.xtclang.idea.XtcFileType" }
        assertThat(fileType)
            .withFailMessage(
                "plugin.xml must register org.xtclang.idea.XtcFileType. Without this, IntelliJ treats .x as " +
                    "unclaimed and offers unrelated Marketplace plugins that also support the extension.",
            ).isNotNull
        assertThat(fileType!!.getAttribute("name")).isEqualTo("Ecstasy")
        assertThat(fileType.getAttribute("language")).isEqualTo("Ecstasy")
        assertThat(fileType.getAttribute("fieldName")).isEqualTo("INSTANCE")
        assertThat(fileType.getAttribute("extensions").split(';'))
            .withFailMessage("Ecstasy file type must claim the x extension.")
            .contains("x")
    }
}
