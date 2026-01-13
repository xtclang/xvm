package org.xtclang.idea

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import kotlin.io.path.exists

/**
 * Provides TextMate bundle for XTC syntax highlighting.
 * The bundle is located in the plugin's lib/textmate directory.
 */
class XtcTextMateBundleProvider : TextMateBundleProvider {

    private val log = logger<XtcTextMateBundleProvider>()

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.xtclang.idea"))
            ?: return emptyList<TextMateBundleProvider.PluginBundle>().also {
                log.warn("XTC plugin not found in PluginManagerCore")
            }

        val textmatePath = plugin.pluginPath.resolve("lib/textmate")
        log.info("TextMate bundle path: $textmatePath (exists=${textmatePath.exists()})")

        return when {
            textmatePath.exists() -> listOf(TextMateBundleProvider.PluginBundle("XTC", textmatePath)).also {
                log.info("Registered TextMate bundle: XTC")
            }
            else -> emptyList<TextMateBundleProvider.PluginBundle>().also {
                log.warn("TextMate bundle not found at: $textmatePath")
            }
        }
    }
}
