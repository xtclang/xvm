package org.xtclang.idea

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.exists

/**
 * Provides TextMate bundle for XTC syntax highlighting. The bundle is located in the plugin's
 * lib/textmate directory.
 *
 * The grammar supplies lexical scopes, including escapes and control keywords. Compiler semantic
 * tokens refine resolved names and their modifiers while leaving that lexical detail intact.
 * plugin.xml explicitly binds TextMate's highlighter providers to the native Ecstasy file type;
 * registering a bundle alone does not associate a highlighter with an independently owned file type.
 */
class XtcTextMateBundleProvider : TextMateBundleProvider {
    private val logger = logger<XtcTextMateBundleProvider>()

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        logger.warn("XtcTextMateBundleProvider.getBundles() called")

        val plugin = PluginPaths.selfDescriptor()
        if (plugin == null) {
            logger.warn("Ecstasy plugin descriptor not found via own PluginAwareClassLoader")
            return emptyList()
        }

        logger.warn("Ecstasy plugin path: ${plugin.pluginPath}")

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

        val bundle = TextMateBundleProvider.PluginBundle("Ecstasy", textmatePath)
        logger.warn("Registered TextMate bundle: Ecstasy at $textmatePath")
        return listOf(bundle)
    }
}
