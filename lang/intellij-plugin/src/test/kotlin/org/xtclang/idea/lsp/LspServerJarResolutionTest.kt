package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("LSP Server JAR Resolution")
class LspServerJarResolutionTest {
    @TempDir
    lateinit var pluginDir: Path

    @Test
    @DisplayName("resolves JAR from correct ZIP layout (bin/xtc-lsp-server.jar)")
    fun correctZipLayout() {
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
