package org.xtclang.idea

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.exists

/**
 * Provides TextMate bundle for XTC syntax highlighting.
 * The bundle is located in the plugin's lib/textmate directory.
 *
 * // TODO LSP: TextMate grammars are regex-based and have limitations:
 * // - Cannot distinguish type names from variable names
 * // - Cannot highlight based on semantic information (is this a field? parameter?)
 * // - Regex patterns can be slow and fragile for complex syntax
 * //
 * // When the parallel compiler is complete, REPLACE TextMate with LSP semantic tokens:
 * // 1. LSP server provides getSemanticTokens() using real lexer + symbol resolution
 * // 2. IntelliJ LSP client receives and applies semantic token highlighting
 * // 3. TextMate becomes fallback only (for when LSP is not available)
 * //
 * // See: PLAN_LSP_PARALLEL_LEXER.md (Phase 1 - Semantic Tokens)
 * // See: XtcCompilerAdapterFull.getSemanticTokens()
 */
class XtcTextMateBundleProvider : TextMateBundleProvider {

    private val logger = logger<XtcTextMateBundleProvider>()

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        logger.warn("XtcTextMateBundleProvider.getBundles() called")

        val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))
        if (plugin == null) {
            logger.warn("XTC plugin not found in PluginManagerCore")
            return emptyList()
        }

        logger.warn("XTC plugin path: ${plugin.pluginPath}")

        val textmatePath = plugin.pluginPath.resolve("lib/textmate")
        val exists = textmatePath.exists()
        logger.warn("TextMate bundle path: $textmatePath (exists=$exists)")

        if (!exists) {
            logger.warn("TextMate bundle not found at: $textmatePath")
            // List what IS in lib directory
            val libPath = plugin.pluginPath.resolve("lib")
            if (libPath.exists()) {
                logger.warn("Contents of lib: ${libPath.toFile().listFiles()?.map { it.name }}")
            }
            return emptyList()
        }

        // List contents of textmate directory
        logger.warn("Contents of textmate: ${textmatePath.toFile().listFiles()?.map { it.name }}")

        val bundle = TextMateBundleProvider.PluginBundle("XTC", textmatePath)
        logger.warn("Registered TextMate bundle: XTC at $textmatePath")
        return listOf(bundle)
    }
}
