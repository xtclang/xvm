import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IntellijInstallationTest {
    @TempDir
    lateinit var directory: Path

    // the launch entries of IntelliJ IDEA 2026.3's multi-platform distribution, plus a macOS bundle's
    private val productInfo by lazy {
        directory.resolve("Contents/Resources").toFile().apply { mkdirs() }.resolve("product-info.json").apply {
            writeText(
                """
                {"name": "IntelliJ IDEA", "buildNumber": "263.7307.200", "launch": [
                  {"os": "Windows", "arch": "amd64", "launcherPath": "bin/idea.bat"},
                  {"os": "Linux", "arch": "amd64", "launcherPath": "bin/idea.sh"},
                  {"os": "Linux", "arch": "aarch64", "launcherPath": "bin/idea-arm.sh"},
                  {"os": "macOS", "arch": "aarch64", "launcherPath": "../MacOS/idea"}
                ]}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `the launcher is the one product-info names for this OS and architecture`() {
        val resources = productInfo.parentFile
        assertEquals(resources.resolve("../MacOS/idea").normalize(), IntellijInstallation.of(productInfo, "Mac OS X", "aarch64").launcher)
        assertEquals(resources.resolve("bin/idea.bat"), IntellijInstallation.of(productInfo, "Windows 11", "amd64").launcher)
        assertEquals(resources.resolve("bin/idea-arm.sh"), IntellijInstallation.of(productInfo, "Linux", "aarch64").launcher)
        // Java says x86_64 where product-info says amd64
        assertEquals(resources.resolve("bin/idea.sh"), IntellijInstallation.of(productInfo, "Linux", "x86_64").launcher)
        assertEquals("IntelliJ IDEA", IntellijInstallation.of(productInfo, "Linux", "amd64").name)
        assertEquals("263.7307.200", IntellijInstallation.of(productInfo, "Linux", "amd64").build)
    }

    @Test
    fun `an installation is found from its home, a macOS bundle included`() {
        productInfo
        assertEquals("263.7307.200", IntellijInstallation.of(directory.toFile()).build)
    }

    @Test
    fun `a build is compared with a since-build component by component`() {
        assertTrue(IntellijInstallation.isAtLeast("263.7307.200", "262"))
        assertTrue(IntellijInstallation.isAtLeast("262.1", "262"))
        assertTrue(IntellijInstallation.isAtLeast("262", "262.0"))
        assertFalse(IntellijInstallation.isAtLeast("261.9999", "262"))
        assertFalse(IntellijInstallation.isAtLeast("262.10", "262.11"))
    }

    @Test
    fun `the plugins directory is the last one the IDE logged, without the note on a missing directory`() {
        val log = directory.resolve("idea.log").toFile()
        log.writeText(
            """
            |2026-11-10 14:14:35,053 [    103]   INFO - #c.i.p.i.b.AppStarter - JVM options: [...]
            |    idea.config.path=/old/config
            |    idea.plugins.path=/old/plugins -> java.nio.file.NoSuchFileException: /old/plugins
            |2026-11-10 14:17:53,461 [     26]   INFO - #c.i.p.i.b.AppStarter - JVM options: [...]
            |    idea.plugins.path=/Users/someone/Library/Application Support/JetBrains/IntelliJIdea2026.3/plugins
            |    idea.log.path=/tmp/log
            """.trimMargin(),
        )
        assertEquals(
            "/Users/someone/Library/Application Support/JetBrains/IntelliJIdea2026.3/plugins",
            IntellijInstallation.pluginsPathFromLog(log),
        )
        log.writeText("    idea.plugins.path=/missing/plugins -> java.nio.file.NoSuchFileException: /missing/plugins\n")
        assertEquals("/missing/plugins", IntellijInstallation.pluginsPathFromLog(log))
        assertNull(IntellijInstallation.pluginsPathFromLog(directory.resolve("absent.log").toFile()))
    }

    @Test
    fun `a plugin zip unpacks into the directory at its root`() {
        val zip = directory.resolve("plugin.zip").toFile()
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf("intellij-plugin/lib/plugin.jar", "intellij-plugin/bin/xtc-lsp").forEach {
                out.putNextEntry(ZipEntry(it))
                out.closeEntry()
            }
        }
        assertEquals("intellij-plugin", IntellijInstallation.rootEntry(zip))
    }
}
