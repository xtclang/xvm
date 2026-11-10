package org.xvm.lsp.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.awaited
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the cleanup of the directories a server process owns in the temporary directory, such as its read-only copies
 * of library sources: it deletes its own when its JVM exits, and those of servers that were killed. A running server
 * is one that holds its directory's lock, which a [SessionOwner] in a process of its own does here.
 */
class ProcessDirectoriesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a directory is deleted with the read-only files in it`() {
        val copies = copies("xtc-library-sources-1")
        assertThat(ProcessDirectories.delete(copies)).isTrue()
        assertThat(copies).doesNotExist()
    }

    @Test
    fun `a server deletes the directories of a server that was killed, not those of a running or an older server`() {
        val own = copies("xtc-library-sources-1")
        val lock = SessionLock.hold(own)
        // the system released the killed server's lock, and left its lock file
        val killed = copies("xtc-library-sources-2").also { Files.createFile(it.resolve(SessionLock.NAME)) }
        // an older server took no lock
        val older = copies("xtc-library-sources-3")
        val running = copies("xtc-library-sources-4")
        val owner =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                SessionOwner::class.java.name,
                "hold",
            ).redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        try {
            owner.outputWriter().apply {
                write("$running\n")
                flush()
            }
            assertThat(owner.inputReader().readLine()).describedAs("The other process holds its directory").isEqualTo("true")

            ProcessDirectories.deleteAbandoned(own, "xtc-library-sources-")

            assertThat(killed).doesNotExist()
            assertThat(listOf(own, older, running)).allMatch { Files.isRegularFile(it.resolve(COLLECTION)) }
        } finally {
            owner.outputStream.close()
            if (!owner.awaited()) owner.destroyForcibly()
            lock?.channel()?.close()
        }
    }

    /** A directory of copies, as a server writes it, with read-only copies. */
    private fun copies(name: String): Path =
        Files.createDirectories(directory.resolve(name)).also { copies ->
            listOf("ecstasy.x", COLLECTION).forEach { path ->
                val file = copies.resolve(path)
                Files.createDirectories(file.parent)
                Files.writeString(file, "module ecstasy {}\n")
                check(file.toFile().setReadOnly())
            }
        }

    private companion object {
        const val COLLECTION = "ecstasy/collections/Collection.x"
    }
}
