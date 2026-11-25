package org.xvm.lsp.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Directories in the temporary directory that one server process owns, such as its read-only copies of library
 * sources: it holds a [SessionLock] on each while it runs, and deletes each when its JVM exits. A process that is
 * killed cannot delete its own, so making one deletes the others of its kind that no process holds.
 */
internal object ProcessDirectories {
    /** The temporary directory as canonical paths name it, which [holder] compares them with. */
    private val canonicalTemporary by lazy { File(System.getProperty("java.io.tmpdir")).canonicalFile.toPath() }

    /**
     * A new directory whose name starts with [prefix], made under the [SessionLock.guard] of its kind, so that no
     * server sees it before this process holds it; and, under that guard, the others of its kind that no process
     * holds are deleted ([deleteAbandoned]).
     */
    fun create(prefix: String): Path {
        val temporary = Path.of(System.getProperty("java.io.tmpdir"))
        return SessionLock.guard(temporary.resolve(".${prefix}lock")).use { guard ->
            Files.createTempDirectory(temporary, prefix).also { directory ->
                val lock = SessionLock.hold(directory, SessionLock.beside(directory))
                Runtime.getRuntime().addShutdownHook(
                    Thread(
                        {
                            // Windows deletes no file that is open
                            lock?.channel()?.close()
                            delete(directory)
                        },
                        "${prefix}cleanup",
                    ),
                )
                if (guard != null) deleteAbandoned(directory, prefix)
            }
        }
    }

    /**
     * The directory of [prefix]'s kind in the temporary directory that holds [path], a canonical path, whichever server
     * process made it, or null when none does: one named as [create] names it, [prefix] and the digits that
     * [Files.createTempDirectory] adds, and not a folder of a user's whose name only starts with [prefix].
     */
    fun holder(
        path: Path,
        prefix: String,
    ): Path? {
        val temporary = canonicalTemporary
        if (!path.startsWith(temporary) || path.nameCount <= temporary.nameCount + 1) return null
        return temporary.resolve(path.getName(temporary.nameCount)).takeIf { made(it.fileName.toString(), prefix) }
    }

    /** Whether [name] is one that [create] gives a directory of [prefix]'s kind: [prefix] and digits. */
    private fun made(
        name: String,
        prefix: String,
    ): Boolean = name.startsWith(prefix) && name.length > prefix.length && name.substring(prefix.length).all { it in '0'..'9' }

    /** Deletes the other directories beside [own] whose names start with [prefix] that [SessionLock.unheld] finds. */
    internal fun deleteAbandoned(
        own: Path,
        prefix: String,
    ) {
        SessionLock.unheld(own, "$prefix*").forEach { delete(it) }
    }

    /**
     * Deletes [directory] and the files in it, each made writable first, since a read-only file is what Windows does
     * not delete, and then its lock file [SessionLock.beside] it. Returns whether the directory is gone.
     */
    internal fun delete(directory: Path): Boolean =
        directory
            .toFile()
            .walkBottomUp()
            .fold(true) { deleted, file -> file.setWritable(true) && file.delete() && deleted }
            .also { SessionLock.beside(directory).toFile().delete() }
}
