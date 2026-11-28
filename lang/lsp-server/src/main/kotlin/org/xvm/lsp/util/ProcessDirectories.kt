package org.xvm.lsp.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Directories in this user's temporary root ([UserTemporary]) that one server process owns, such as its read-only
 * copies of library sources: it holds a [SessionLock] on each while it runs, and deletes each when its JVM exits. A
 * process that is killed cannot delete its own, so making one deletes the others of its kind that no process holds.
 */
internal object ProcessDirectories {
    /**
     * A new directory whose name starts with [prefix], made under the [SessionLock.guard] of its kind, so that no
     * server sees it before this process holds it; and, under that guard, the others of its kind that no process
     * holds are deleted ([deleteAbandoned]).
     */
    fun create(prefix: String): Path {
        val root = UserTemporary.current.root()
        return SessionLock.guard(root.resolve(".${prefix}lock")) { guarded ->
            Files.createTempDirectory(root, prefix).also { directory ->
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
                if (guarded) deleteAbandoned(directory, prefix)
            }
        }
    }

    /**
     * The directory of [prefix]'s kind in this user's temporary root that holds [path], a canonical path, whichever
     * server process made it, or null when none does: one named as [create] names it, [prefix] and the digits that
     * [Files.createTempDirectory] adds, and not a folder of a user's whose name only starts with [prefix].
     */
    fun holder(
        path: Path,
        prefix: String,
    ): Path? {
        // as canonical paths name it; with no root to be used, no directory of a server's is
        val root =
            try {
                UserTemporary.current
                    .root()
                    .toFile()
                    .canonicalFile
                    .toPath()
            } catch (_: IllegalStateException) {
                return null
            } catch (_: IOException) {
                return null
            }
        if (!path.startsWith(root) || path.nameCount <= root.nameCount + 1) return null
        return root.resolve(path.getName(root.nameCount)).takeIf { made(it.fileName.toString(), prefix) }
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
     * Deletes [directory] and the files in it, never what a link in it names, each file made writable first, since a
     * read-only file is what Windows does not delete ([UserTemporary.delete]), and then its lock file [SessionLock.beside]
     * it. Returns whether the directory is gone.
     */
    internal fun delete(directory: Path): Boolean =
        UserTemporary.current.delete(directory).also { UserTemporary.current.delete(SessionLock.beside(directory)) }
}
