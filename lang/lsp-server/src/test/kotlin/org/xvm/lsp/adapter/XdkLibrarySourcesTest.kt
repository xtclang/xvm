package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the cleanup of the bundled sources' read-only copies, which the server writes to a temporary folder for
 * clients that open library sources as files, and deletes when its JVM exits.
 */
class XdkLibrarySourcesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `the copies' folder is deleted with the read-only copies in it`() {
        val copies = Files.createDirectories(directory.resolve("xtc-library-sources-1"))
        listOf("ecstasy.x", "ecstasy/collections/Collection.x").forEach { path ->
            val file = copies.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, "module ecstasy {}\n")
            check(file.toFile().setReadOnly())
        }
        assertThat(XdkLibrarySources.deleteCopies(copies)).isTrue()
        assertThat(copies).doesNotExist()
    }
}
