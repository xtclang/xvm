package org.xtclang.idea

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
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
     * This plugin's own [PluginDescriptor], or `null` if it cannot be determined.
     *
     * Resolved from our own classes' classloader, which the IntelliJ Platform loads
     * via a [PluginAwareClassLoader] that carries the owning plugin's descriptor. This
     * is the supported, public way for a plugin to obtain its own descriptor (path,
     * version, id). It deliberately avoids `PluginManager.findEnabledPlugin(PluginId)`
     * and `PluginManagerCore.getPlugin(PluginId)`, both of which are marked
     * `@ApiStatus.Internal` as of 2026.2 and flagged by the Plugin Verifier.
     */
    fun selfDescriptor(): PluginDescriptor? = (PluginPaths::class.java.classLoader as? PluginAwareClassLoader)?.pluginDescriptor

    /**
     * Find a server JAR in the plugin's `bin/` directory.
     *
     * Resolution order:
     * 1. Own [PluginDescriptor] plugin path (via [selfDescriptor], public API)
     * 2. Classloader code-source fallback (for development/test scenarios)
     *
     * @param jarName the JAR filename, e.g. `"xtc-lsp-server.jar"` or `"xtc-dap-server.jar"`
     * @throws IllegalStateException if the JAR cannot be found
     */
    fun findServerJar(jarName: String): Path {
        val searchedPaths = mutableListOf<Path>()

        selfDescriptor()?.pluginPath?.let { pluginPath ->
            val candidate = pluginPath.resolve("bin/$jarName")
            searchedPaths.add(candidate)
            resolveInBin(pluginPath, jarName)?.let { return it }
            logger.warn("$jarName not at expected location: $candidate")
            logger.warn("Plugin directory contents: ${pluginPath.toFile().listFiles()?.map { it.name }}")
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
