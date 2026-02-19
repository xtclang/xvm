package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.PluginPaths
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for JAR resolution logic used by both the LSP and DAP servers.
 *
 * The XTC plugin bundles server JARs in `bin/` (NOT `lib/`) to avoid classloader
 * conflicts with LSP4IJ's own lsp4j classes. These tests verify that the resolution
 * logic correctly finds JARs in the expected location and rejects incorrect layouts.
 *
 * ## Deployment scenarios covered
 *
 * | Scenario                  | Layout                                   |
 * |---------------------------|------------------------------------------|
 * | Plugin sandbox (runIde)   | `plugins/intellij-plugin/bin/<jar>`       |
 * | ZIP install (from disk)   | `intellij-plugin/bin/<jar>`               |
 * | Marketplace install       | `intellij-plugin/bin/<jar>`               |
 *
 * All three scenarios use the same directory structure, so `resolveInBin` covers them all.
 */
@DisplayName("Server JAR Resolution")
class LspServerJarResolutionTest {
    @TempDir
    lateinit var pluginDir: Path

    // ========================================================================
    // LSP Server JAR Resolution (via XtcLspConnectionProvider)
    // ========================================================================

    @Nested
    @DisplayName("LSP Server JAR")
    inner class LspServerJar {
        @Test
        @DisplayName("resolves JAR from correct layout (bin/xtc-lsp-server.jar)")
        fun correctLayout() {
            val binDir = pluginDir.resolve("bin")
            Files.createDirectories(binDir)
            Files.createFile(binDir.resolve("xtc-lsp-server.jar"))
            Files.createDirectories(pluginDir.resolve("lib"))
            Files.createFile(pluginDir.resolve("lib/intellij-plugin.jar"))

            val result = XtcLspConnectionProvider.resolveServerJar(pluginDir)

            assertThat(result).isNotNull()
            assertThat(result).isEqualTo(binDir.resolve("xtc-lsp-server.jar"))
        }

        @Test
        @DisplayName("returns null when bin directory is missing")
        fun missingBinDir() {
            Files.createDirectories(pluginDir.resolve("lib"))

            val result = XtcLspConnectionProvider.resolveServerJar(pluginDir)

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("returns null when JAR is in lib instead of bin")
        fun jarInLibNotBin() {
            Files.createDirectories(pluginDir.resolve("lib"))
            Files.createFile(pluginDir.resolve("lib/xtc-lsp-server.jar"))

            val result = XtcLspConnectionProvider.resolveServerJar(pluginDir)

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("returns null when bin directory is empty")
        fun emptyBinDir() {
            Files.createDirectories(pluginDir.resolve("bin"))

            val result = XtcLspConnectionProvider.resolveServerJar(pluginDir)

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("returns null when JAR has wrong name")
        fun wrongJarName() {
            val binDir = pluginDir.resolve("bin")
            Files.createDirectories(binDir)
            Files.createFile(binDir.resolve("lsp-server.jar"))

            val result = XtcLspConnectionProvider.resolveServerJar(pluginDir)

            assertThat(result).isNull()
        }
    }

    // ========================================================================
    // PluginPaths.resolveInBin (shared by LSP and DAP)
    // ========================================================================

    @Nested
    @DisplayName("PluginPaths.resolveInBin")
    inner class ResolveInBin {
        @Test
        @DisplayName("resolves LSP server JAR")
        fun resolvesLspJar() {
            val binDir = pluginDir.resolve("bin")
            Files.createDirectories(binDir)
            Files.createFile(binDir.resolve("xtc-lsp-server.jar"))

            val result = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")

            assertThat(result).isNotNull()
            assertThat(result).isEqualTo(binDir.resolve("xtc-lsp-server.jar"))
        }

        @Test
        @DisplayName("resolves DAP server JAR")
        fun resolvesDapJar() {
            val binDir = pluginDir.resolve("bin")
            Files.createDirectories(binDir)
            Files.createFile(binDir.resolve("xtc-dap-server.jar"))

            val result = PluginPaths.resolveInBin(pluginDir, "xtc-dap-server.jar")

            assertThat(result).isNotNull()
            assertThat(result).isEqualTo(binDir.resolve("xtc-dap-server.jar"))
        }

        @Test
        @DisplayName("resolves both JARs side by side")
        fun resolvesBothJars() {
            val binDir = pluginDir.resolve("bin")
            Files.createDirectories(binDir)
            Files.createFile(binDir.resolve("xtc-lsp-server.jar"))
            Files.createFile(binDir.resolve("xtc-dap-server.jar"))

            val lsp = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")
            val dap = PluginPaths.resolveInBin(pluginDir, "xtc-dap-server.jar")

            assertThat(lsp).isNotNull()
            assertThat(dap).isNotNull()
            assertThat(lsp).isNotEqualTo(dap)
        }

        @Test
        @DisplayName("returns null for missing JAR")
        fun missingJar() {
            Files.createDirectories(pluginDir.resolve("bin"))

            val result = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("returns null when bin directory does not exist")
        fun noBinDir() {
            val result = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("does not resolve JAR from lib directory")
        fun notFromLib() {
            Files.createDirectories(pluginDir.resolve("lib"))
            Files.createFile(pluginDir.resolve("lib/xtc-lsp-server.jar"))

            val result = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("simulates sandbox layout: plugins/<name>/bin/<jar>")
        fun sandboxLayout() {
            // Simulate: plugins/intellij-plugin/bin/xtc-lsp-server.jar
            val pluginsRoot = pluginDir.resolve("plugins/intellij-plugin")
            val binDir = pluginsRoot.resolve("bin")
            Files.createDirectories(binDir)
            Files.createDirectories(pluginsRoot.resolve("lib"))
            Files.createFile(binDir.resolve("xtc-lsp-server.jar"))
            Files.createFile(pluginsRoot.resolve("lib/intellij-plugin.jar"))

            val result = PluginPaths.resolveInBin(pluginsRoot, "xtc-lsp-server.jar")

            assertThat(result).isNotNull()
            assertThat(result!!.fileName.toString()).isEqualTo("xtc-lsp-server.jar")
        }

        @Test
        @DisplayName("simulates ZIP install layout with lib and bin")
        fun zipInstallLayout() {
            // ZIP layout: intellij-plugin/bin/<jar> + intellij-plugin/lib/<plugin-jar>
            val binDir = pluginDir.resolve("bin")
            val libDir = pluginDir.resolve("lib")
            Files.createDirectories(binDir)
            Files.createDirectories(libDir)
            Files.createFile(binDir.resolve("xtc-lsp-server.jar"))
            Files.createFile(binDir.resolve("xtc-dap-server.jar"))
            Files.createFile(libDir.resolve("intellij-plugin.jar"))

            val lsp = PluginPaths.resolveInBin(pluginDir, "xtc-lsp-server.jar")
            val dap = PluginPaths.resolveInBin(pluginDir, "xtc-dap-server.jar")

            assertThat(lsp).isNotNull()
            assertThat(dap).isNotNull()
        }
    }
}
