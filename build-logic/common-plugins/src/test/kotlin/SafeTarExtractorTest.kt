import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class SafeTarExtractorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `extracts nested content and executable tools`() {
        extract("tool/bin/compiler", mode = 0b111_101_101)
        val file = directory.resolve("output/tool/bin/compiler")
        assertEquals("fixture", Files.readString(file))
        assertTrue(Files.isExecutable(file))
    }

    @Test
    fun `extracts directory entries`() {
        extract("tool/include/", TarConstants.LF_DIR)
        assertTrue(Files.isDirectory(directory.resolve("output/tool/include")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["../escaped", "nested/../../escaped", "C:/escaped", "nested\\..\\escaped"])
    fun `rejects paths outside the archive namespace`(name: String) {
        assertThrows(IOException::class.java) { extract(name) }
        assertFalse(Files.exists(directory.resolve("escaped")))
    }

    @Test
    fun `rejects absolute paths`() {
        val outside = directory.resolve("escaped")
        assertThrows(IOException::class.java) { extract(outside.toString()) }
        assertFalse(Files.exists(outside))
    }

    @Test
    fun `rejects links and special entries`() {
        for (type in listOf(TarConstants.LF_SYMLINK, TarConstants.LF_LINK, TarConstants.LF_FIFO)) {
            assertThrows(IOException::class.java) { extract("entry-$type", type) }
        }
    }

    @Test
    fun `rejects an existing symlink leading outside the destination`() {
        val outside = Files.createDirectory(directory.resolve("outside"))
        val output = Files.createDirectory(directory.resolve("output"))
        Files.createSymbolicLink(output.resolve("link"), outside)
        assertThrows(IOException::class.java) { extract("link/escaped") }
        assertFalse(Files.exists(outside.resolve("escaped")))
    }

    private fun extract(name: String, type: Byte = TarConstants.LF_NORMAL, mode: Int = 0b110_100_100) {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { archive ->
            val entry = TarArchiveEntry(name, type, true).apply {
                this.mode = mode
                if (type == TarConstants.LF_NORMAL) size = "fixture".length.toLong()
                if (isLink || isSymbolicLink) linkName = "../escaped"
            }
            archive.putArchiveEntry(entry)
            if (type == TarConstants.LF_NORMAL) archive.write("fixture".toByteArray())
            archive.closeArchiveEntry()
        }
        val output = Files.createDirectories(directory.resolve("output"))
        TarArchiveInputStream(bytes.toByteArray().inputStream()).use {
            SafeTarExtractor.extract(it, output.toFile())
        }
    }
}
