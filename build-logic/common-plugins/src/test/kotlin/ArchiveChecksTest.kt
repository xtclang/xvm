import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveChecksTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a whole jar passes and stays`() {
        val jar = jar("whole.jar", mapOf("a/A.class" to "a".repeat(10_000), "b/B.class" to "b".repeat(10_000)))
        assertDoesNotThrow { requireWholeArchive(jar.toFile(), "The jar") }
        assertTrue(Files.exists(jar))
    }

    @Test
    fun `a jar that another build was still writing fails, and is deleted so that it is made again`() {
        val whole = Files.readAllBytes(jar("whole.jar", mapOf("a/A.class" to "a".repeat(10_000), "b/B.class" to "b".repeat(10_000))))
        // the first half of the jar, and the end of it: its entries are listed, but not where they are said to be
        val torn = directory.resolve("torn.jar")
        Files.write(torn, whole.copyOfRange(0, whole.size / 2) + whole.copyOfRange(whole.size - 200, whole.size))
        val failure = assertThrows(GradleException::class.java) { requireWholeArchive(torn.toFile(), "The jar") }
        assertTrue("another build was probably writing it" in failure.message.orEmpty(), failure.message)
        assertFalse(Files.exists(torn))
    }

    @Test
    fun `an archive must hold the file it was made with, byte for byte`() {
        val server = jar("server.jar", mapOf("Server.class" to "server"))
        val other = jar("other.jar", mapOf("Other.class" to "other"))
        val plugin = jar("plugin.zip", mapOf("bin/server.jar" to String(Files.readAllBytes(server), Charsets.ISO_8859_1)))
        assertDoesNotThrow { requireArchivedCopy(plugin.toFile(), "bin/server.jar", server.toFile(), "The zip") }

        assertThrows(GradleException::class.java) { requireArchivedCopy(plugin.toFile(), "bin/server.jar", other.toFile(), "The zip") }
        assertFalse(Files.exists(plugin))
    }

    @Test
    fun `an archive without the entry fails`() {
        val server = jar("server.jar", mapOf("Server.class" to "server"))
        val plugin = jar("plugin.zip", mapOf("lib/plugin.jar" to "plugin"))
        assertThrows(GradleException::class.java) { requireArchivedCopy(plugin.toFile(), "bin/server.jar", server.toFile(), "The zip") }
        assertFalse(Files.exists(plugin))
    }

    /** A jar of [entries], each a name and its text, stored as bytes one to one. */
    private fun jar(
        name: String,
        entries: Map<String, String>,
    ): Path {
        val jar = directory.resolve(name)
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            entries.forEach { (entry, text) ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray(Charsets.ISO_8859_1))
                zip.closeEntry()
            }
        }
        return jar
    }
}
