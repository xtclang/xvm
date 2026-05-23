package org.xtclang.idea

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared utility for resolving files bundled with the XTC plugin.
 *
 * Server JARs (LSP, DAP) are placed in the plugin's `bin/` directory -- NOT `lib/`.
 * If placed in `lib/`, IntelliJ loads their bundled lsp4j classes which conflict
 * with LSP4IJ's lsp4j. The `bin/` directory is not on IntelliJ's classloader path.
 */
object PluginPaths {
    /** Plugin ID, must match `<id>` in `META-INF/plugin.xml`. The single source
     *  of truth for the string — every other call site that needs it should
     *  reference `PluginPaths.PLUGIN_ID` rather than duplicate the literal. */
    const val PLUGIN_ID = "org.xtclang.idea"
    private val logger = logger<PluginPaths>()

    /**
     * Find a server JAR in the plugin's `bin/` directory.
     *
     * Resolution order:
     * 1. `PluginManager.findEnabledPlugin` plugin path (public IntelliJ Platform API)
     * 2. Classloader-based fallback (for development/test scenarios)
     *
     * @param jarName the JAR filename, e.g. `"xtc-lsp-server.jar"` or `"xtc-dap-server.jar"`
     * @throws IllegalStateException if the JAR cannot be found
     */
    fun findServerJar(jarName: String): Path {
        val searchedPaths = mutableListOf<Path>()

        PluginManager.getInstance().findEnabledPlugin(PluginId.getId(PLUGIN_ID))?.let { plugin ->
            val candidate = plugin.pluginPath.resolve("bin/$jarName")
            searchedPaths.add(candidate)
            resolveInBin(plugin.pluginPath, jarName)?.let { return it }
            logger.warn("$jarName not at expected location: $candidate")
            logger.warn("Plugin directory contents: ${plugin.pluginPath.toFile().listFiles()?.map { it.name }}")
        }

        // Fallback: find via classloader (our class is in lib/, JAR is in bin/)
        PluginPaths::class.java.protectionDomain?.codeSource?.location?.let { classUrl ->
            val pluginDir = Path.of(classUrl.toURI()).parent.parent
            val candidate = pluginDir.resolve("bin/$jarName")
            searchedPaths.add(candidate)
            resolveInBin(pluginDir, jarName)?.let { return it }
            logger.warn("$jarName not found via classloader either: $candidate")
        }

        throw IllegalStateException(
            buildString {
                appendLine("$jarName not found. Searched locations:")
                searchedPaths.forEach { appendLine("  - $it") }
                appendLine("JARs must be in bin/ (NOT lib/) to avoid classloader conflicts with LSP4IJ.")
                append("This is a plugin packaging issue. Please report it.")
            },
        )
    }

    /**
     * Resolve a JAR in a plugin directory's `bin/` subdirectory.
     * Returns the path if the file exists, null otherwise.
     */
    internal fun resolveInBin(
        pluginDir: Path,
        jarName: String,
    ): Path? {
        val serverJar = pluginDir.resolve("bin/$jarName")
        return if (Files.exists(serverJar)) serverJar else null
    }
}
