package org.xtclang.idea.manifest

import com.intellij.refactoring.move.MoveHandlerDelegate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.xtclang.idea.PluginPaths
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Self-consistency tests for META-INF/plugin.xml.
 *
 * Counterpart to VS Code's `activation.test.ts` — both guard the same failure mode: an extension
 * point / command / configuration declared in the manifest but referenced from the wrong place in
 * code (or vice versa) silently fails to register at runtime. We can't fully verify runtime
 * registration without `BasePlatformTestCase`, but we can at least guarantee that the manifest
 * carries every extension point our Kotlin code expects, and that the plugin ID matches the single
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
    @DisplayName(
        "declares all required extension points (newProjectWizard, configurationType, lang.commenter, LSP server, ...)",
    )
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
                "notificationGroup", // notification group "Ecstasy Language Server" used by
                // XtcLspServerSupportProvider
                "fileType", // registers *.x as Ecstasy so IntelliJ does not suggest unrelated
                // plugins
                "lang.syntaxHighlighterFactory", // use the TextMate grammar for our native file type
                "editorHighlighterProvider", // use TextMate's scope-aware token storage
                "lang.parserDefinition", // a PSI of the Ecstasy language via XtcParserDefinition
                "newProjectWizard.generator", // wizard entry created by XtcNewProjectWizard
                "configurationType", // run-config type created by XtcRunConfigurationType
                "runConfigurationProducer", // auto-create run configs from .x context
                "projectTaskRunner", // embedded Build/Rebuild via XtcProjectTaskRunner
                "iconProvider", // file icons via XtcIconProvider
                "defaultLiveTemplates", // /liveTemplates/Ecstasy.xml snippets
                "liveTemplateContext", // the ECSTASY context those snippets apply in
                "lang.commenter", // Ctrl+/ via XtcCommenter
                "braceMatcher", // typed brackets stay unpaired, as in plain text, via XtcBraceMatcher
                "completion.contributor", // LSP4IJ's held edits sent before it asks for completions (UP73)
                "codeInsight.wordCompletionFilter", // the file's words when the server completes nothing
                "lang.importOptimizer", // Optimize Imports via XtcImportOptimizer
                "renameHandler", // guarded compiler rename via XtcRenameHandler
                "refactoring.moveHandler", // compiler preflight before native filesystem mutation
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
    @DisplayName("registers for Ecstasy what LSP4IJ registers for TEXT or TextMate only, or says why not")
    fun lsp4ijTextFeaturesApplyToEcstasy() {
        // LSP4IJ registers some features only for the TEXT and TextMate languages. An Ecstasy file's PSI is of the
        // Ecstasy language since XtcParserDefinition, so each needs a registration for Ecstasy, with LSP4IJ's class
        // or a class of the plugin's that extends it, unless NOT_FOR_ECSTASY says why not
        val lsp4ij = lsp4ijPluginXml()
        val textOnly = registrations(lsp4ij, "TEXT") + registrations(lsp4ij, "textmate")
        assertThat(textOnly.map { it.first })
            .contains("lang.foldingBuilder", "lang.psiStructureViewFactory", "codeInsight.parameterInfo")
        val ecstasy = registrations(pluginXml, "Ecstasy")
        val missing =
            textOnly
                .filterNot { (tag, theirs) ->
                    tag in NOT_FOR_ECSTASY || ecstasy.any { (ours, by) -> ours == tag && extends(by, theirs) }
                }.toSet()
        assertThat(missing).withFailMessage("plugin.xml registers none of these for Ecstasy: %s", missing).isEmpty()
        assertThat(NOT_FOR_ECSTASY.keys - textOnly.map { it.first }.toSet())
            .withFailMessage("LSP4IJ no longer registers these for TEXT or TextMate; drop their reasons")
            .isEmpty()
    }

    @Test
    @DisplayName("accounts for each LSP4IJ extension whose code acts in TEXT or TextMate files only")
    fun lsp4ijTextChecksAreAccountedFor() {
        // these are registered for every language but act only where LSP4IJ's code finds a TEXT or TextMate file;
        // TEXT_CHECKS says what an Ecstasy file has instead, so one that LSP4IJ renames or adds needs a look
        val elements = lsp4ijPluginXml().getElementsByTagName("*")
        val ids = (0 until elements.length).map { (elements.item(it) as Element).getAttribute("id") }.toSet()
        assertThat(TEXT_CHECKS.keys - ids).withFailMessage("LSP4IJ has none of these extensions any more").isEmpty()
    }

    private fun lsp4ijPluginXml(): Element =
        javaClass.classLoader
            .getResources("META-INF/plugin.xml")
            .toList()
            .map { url -> parse(url.openStream()) }
            // the test classpath can hold LSP4IJ's jar more than once
            .first {
                it
                    .getElementsByTagName("id")
                    .item(0)
                    ?.textContent
                    ?.trim() == "com.redhat.devtools.lsp4ij"
            }

    /** Whether the class [ours] is the class [theirs] or extends it. */
    private fun extends(
        ours: String,
        theirs: String,
    ): Boolean = ours == theirs || load(theirs).isAssignableFrom(load(ours))

    private fun load(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    /** The extensions of com.intellij that [root] registers for [language], as their tag and class. */
    private fun registrations(
        root: Element,
        language: String,
    ): Set<Pair<String, String>> {
        val elements = root.getElementsByTagName("*")
        return (0 until elements.length)
            .map { elements.item(it) as Element }
            .filter {
                it.getAttribute("language") == language &&
                    (it.parentNode as? Element)?.getAttribute("defaultExtensionNs") == "com.intellij"
            }.map { it.tagName to it.getAttribute("implementationClass") }
            .toSet()
    }

    private fun parse(input: InputStream): Element =
        input.use {
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(it)
                .documentElement
        }

    @Test
    fun fileMoveUsesThePlatformExtensionPoint() {
        val elements = pluginXml.getElementsByTagName("*")
        val registration =
            (0 until elements.length)
                .map { elements.item(it) as Element }
                .single {
                    it.getAttribute("implementation") == "org.xtclang.idea.lsp.XtcFileMoveHandler"
                }
        val namespace = (registration.parentNode as Element).getAttribute("defaultExtensionNs")
        assertThat("$namespace.${registration.tagName}").isEqualTo(MoveHandlerDelegate.EP_NAME.name)
    }

    @Test
    @DisplayName("requires only Community platform plugins and LSP4IJ")
    fun communityDependencies() {
        val elements = pluginXml.getElementsByTagName("depends")
        val required =
            (0 until elements.length)
                .map { elements.item(it) as Element }
                .filter { it.getAttribute("optional") != "true" }
                .map { it.textContent.trim() }
        assertThat(required)
            .containsExactlyInAnyOrder(
                "com.intellij.modules.platform",
                "com.intellij.modules.java",
                "com.intellij.gradle",
                "org.jetbrains.plugins.textmate",
                "com.redhat.devtools.lsp4ij",
                "intellij.testRunner.plugin",
            )
    }

    @Test
    @DisplayName("depends on IntelliJ 2026.3's Problems view plugin optionally, whose classes Project Errors needs there")
    fun problemsViewDependency() {
        val elements = pluginXml.getElementsByTagName("depends")
        val problemsView =
            (0 until elements.length)
                .map { elements.item(it) as Element }
                .single { it.textContent.trim() == "com.intellij.problemsView" }
        assertThat(problemsView.getAttribute("optional")).isEqualTo("true")
        val config = problemsView.getAttribute("config-file")
        assertThat(javaClass.classLoader.getResource("META-INF/$config")).describedAs(config).isNotNull()
    }

    @Test
    @DisplayName("every default shortcut keeps Control on both macOS keymaps, as VS Code and the docs do")
    fun macShortcutsKeepControl() {
        val elements = pluginXml.getElementsByTagName("action")
        val actions = (0 until elements.length).map { elements.item(it) as Element }
        val checked =
            actions.mapNotNull { action ->
                val shortcuts = action.getElementsByTagName("keyboard-shortcut")
                val byKeymap =
                    (0 until shortcuts.length)
                        .map { shortcuts.item(it) as Element }
                        .associate {
                            it.getAttribute("keymap") to
                                (it.getAttribute("first-keystroke") to it.getAttribute("second-keystroke"))
                        }
                byKeymap["\$default"]?.let { default ->
                    // "Mac OS X 10.5+", the default macOS keymap, otherwise turns $default's ctrl into Command
                    assertThat(byKeymap["Mac OS X"]).describedAs(action.getAttribute("id")).isEqualTo(default)
                    assertThat(byKeymap["Mac OS X 10.5+"]).describedAs(action.getAttribute("id")).isEqualTo(default)
                    action.getAttribute("id")
                }
            }
        assertThat(checked).contains("xtc.selectLanguageAdapter", "xtc.restartLanguageServer", "xtc.toggleServerLog")
    }

    @Test
    @DisplayName("LSP server registration points at xtcLanguageServer and maps *.x")
    fun lspServerWiring() {
        // The LSP server element lives inside <extensions
        // defaultExtensionNs="com.redhat.devtools.lsp4ij">.
        // We look for the <server> child with id="xtcLanguageServer" and the
        // <fileNamePatternMapping> with patterns="*.x".
        val servers = pluginXml.getElementsByTagName("server")
        val server =
            (0 until servers.length)
                .map { servers.item(it) as Element }
                .firstOrNull { it.getAttribute("id") == "xtcLanguageServer" }
        assertThat(server)
            .withFailMessage(
                "plugin.xml is missing the LSP4IJ <server id='xtcLanguageServer'> element",
            ).isNotNull

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
                .firstOrNull {
                    it.getAttribute("implementationClass") == "org.xtclang.idea.XtcFileType"
                }
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

    private companion object {
        /** LSP4IJ's registrations for TEXT or TextMate, by tag, that an Ecstasy file needs none of, and why. */
        val NOT_FOR_ECSTASY =
            mapOf(
                "lang.fileViewProviderFactory" to
                    "a file's view provider comes by its file type's language, Ecstasy before and after the PSI, so " +
                    "LSP4IJ's, which splits a file without structure by its semantic tokens, never applied",
            )

        /** LSP4IJ's extensions, by id, that act in TEXT or TextMate files only, and what an Ecstasy file has instead. */
        val TEXT_CHECKS =
            mapOf(
                "LSPEditorImprovementsTypedHandler" to "abstract and TextMate file types only; XtcBraceMatcher",
                "LSPImprovedTextMateNestedBracesTypedHandler" to "TextMate's file type only; XtcEnterAfterUnmatchedBraceHandler",
                "LSPClientSideOnTypeFormattingTypedHandler" to "TextMate's brace pairs only; the server's on-type formatting",
                "LSPEditorImprovementsBackspaceHandler" to "abstract and TextMate file types only; nothing is paired to delete",
                "LSPEditorImprovementsEnterBetweenBracesHandler" to "abstract and TextMate file types only; XtcEnterHandlerDelegate",
                "LSPCompletionContributor" to
                    "outside TEXT and TextMate, a typed prefix is the PSI leaf at the caret, a word by XtcLexer, and " +
                    "the file's words when the server has nothing come by XtcWordCompletionFilter",
                "LSPSemanticTokensHighlightVisitor" to "outside TEXT and TextMate, highlights the file's tokens at once, not by element",
            )
    }
}
