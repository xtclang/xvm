package org.xvm.lsp.util

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT
import java.nio.file.attribute.AclEntryFlag.FILE_INHERIT
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType.ALLOW
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission.GROUP_WRITE
import java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal

/**
 * The directory in a temporary directory, [temporary], that this user's servers keep their temporary files in: the
 * bundled libraries' directory, which they share, the directories that each process owns ([ProcessDirectories]), and
 * the lock files of both ([SessionLock]). Its name, `xtc-lsp-<user>`, is one that anyone can predict, and on Linux
 * /tmp is every user's, so another user can make it first, or make it again once a cleaner of old files deleted it.
 * [root] checks it at each use: one that is a link, no directory, another user's ([owned]), or one that others can
 * write is not used, and the process makes a private directory of its own instead, keeps it, says why in the log, and
 * deletes it as it exits. [delete] deletes only in the root, only what this user owns, and never what a link names;
 * [write] writes only in the root, and never through a link.
 */
internal class UserTemporary(
    private val temporary: Path,
    user: String = System.getProperty("user.name"),
    private val owned: (Path) -> Boolean = ownedByUser(temporary),
) {
    /** The root that this user's servers share, named by the user. */
    private val shared: Path = temporary.resolve("xtc-lsp-${user.replace(unsafe, "_")}")

    /** This process's own root, made the first time [shared] is not to be used, and used from then on. */
    private val fallback =
        lazy {
            Files.createTempDirectory(temporary, "${shared.fileName}-").also { directory ->
                ownerOnly(directory)
                try {
                    Runtime.getRuntime().addShutdownHook(Thread({ removed(directory) }, "xtc-lsp-temporary-cleanup"))
                } catch (_: IllegalStateException) {
                    // the JVM is exiting already, and its process's directories with it
                }
            }
        }

    /**
     * The root, checked at each call, which a caller takes once for each thing it does: the shared one, made for its
     * owner alone, while it is a directory of this user's that no one else can write, or else this process's own, which
     * stays its root. Throws [IllegalStateException] where [temporary] lets others replace what is in it.
     */
    fun root(): Path {
        open(temporary)?.let { throw IllegalStateException("Temporary files cannot be kept in $temporary, as $it") }
        if (fallback.isInitialized()) return fallback.value
        val refusal = refusal(made(shared), owned)
        return if (refusal == null) {
            shared
        } else {
            fallback.value.also { logger.warn("Temporary files are not in {}, as {}, but in {}", shared, refusal, it) }
        }
    }

    /**
     * Deletes [path], a file, or a directory with everything in it, and returns whether it is gone. A link is deleted
     * itself, never what it names, in a directory too. A path that is not in the root in use, whose folder is a link to
     * one outside it, or that another user owns, is not deleted, and the log says why; nor is anything while no root is
     * in use, which a delete does not make.
     */
    fun delete(path: Path): Boolean {
        val absolute = path.toAbsolutePath().normalize()
        if (Files.notExists(absolute, NOFOLLOW_LINKS)) return true
        return try {
            val root = existing()?.toRealPath() ?: return refused(path, "no temporary root of this user's is in use")
            // where its folder's links lead, but not its own
            val real = absolute.parent?.toRealPath()?.resolve(absolute.fileName)
            when {
                real == null || !real.startsWith(root) || real == root -> refused(path, "it is not in $root")
                !owned(absolute) -> refused(path, "another user owns it")
                else -> removed(absolute)
            }
        } catch (failure: IOException) {
            logger.warn("Could not delete {}: {}", path, failure.toString())
            Files.notExists(absolute, NOFOLLOW_LINKS)
        }
    }

    /**
     * Writes [bytes] to [path], a new file in the [root], and returns it. The folders on its way from the root are made
     * one at a time, and none may be a link, nor the file: a file or a link at its name, or a link on its way, throws
     * [FileAlreadyExistsException], and a path that is not in the root, as one of a root that is no longer used,
     * [IllegalStateException].
     */
    fun write(
        path: Path,
        bytes: ByteArray,
    ): Path {
        val root = root()
        val absolute = path.toAbsolutePath().normalize()
        check(absolute.startsWith(root) && absolute != root) { "$path is not in this user's temporary root, $root" }
        root.relativize(absolute.parent).filter { it.toString().isNotEmpty() }.fold(root) { folder, name ->
            folder.resolve(name).also { next ->
                try {
                    if (!Files.isDirectory(next, NOFOLLOW_LINKS)) Files.createDirectory(next)
                } catch (failure: FileAlreadyExistsException) {
                    // another thread's, unless it is a link or a file
                    if (!Files.isDirectory(next, NOFOLLOW_LINKS)) throw failure
                }
            }
        }
        return Files.write(absolute, bytes, CREATE_NEW, WRITE, NOFOLLOW_LINKS)
    }

    /** The root in use, without making one: this process's own once it has one, the shared one where it is to be used. */
    private fun existing(): Path? =
        when {
            open(temporary) != null -> null
            fallback.isInitialized() -> fallback.value
            refusal(shared, owned) == null -> shared
            else -> null
        }

    /** False, as [path] is not deleted, for [reason]. */
    private fun refused(
        path: Path,
        reason: String,
    ): Boolean {
        logger.warn("Not deleting {}, as {}", path, reason)
        return false
    }

    /** Deletes [target] and everything in it, never following a link, and returns whether it is gone. */
    private fun removed(target: Path): Boolean {
        Files.walkFileTree(
            target,
            object : SimpleFileVisitor<Path>() {
                // a Windows junction, a link that is a directory too, is deleted itself, as a link is
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    if (!attributes.isOther) return CONTINUE
                    deleted(directory)
                    return SKIP_SUBTREE
                }

                // a link is deleted itself; a read-only file is what Windows does not delete
                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    if (attributes.isRegularFile) file.toFile().setWritable(true)
                    deleted(file)
                    return CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    failure: IOException,
                ): FileVisitResult = CONTINUE

                override fun postVisitDirectory(
                    directory: Path,
                    failure: IOException?,
                ): FileVisitResult {
                    deleted(directory)
                    return CONTINUE
                }
            },
        )
        return Files.notExists(target, NOFOLLOW_LINKS)
    }

    /** Deletes [path], a file, a link or an empty directory, where it can; [removed] tells whether all of it went. */
    private fun deleted(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (failure: IOException) {
            logger.debug("Could not delete {}: {}", path, failure.toString())
        }
    }

    /** [directory], made for its owner alone where the file system has permissions or ACLs, unless something is there. */
    private fun made(directory: Path): Path {
        try {
            if ("posix" in temporary.fileSystem.supportedFileAttributeViews()) {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            } else {
                ownerOnly(Files.createDirectory(directory))
            }
        } catch (_: FileAlreadyExistsException) {
            // made by another server of this user's, or by someone else, which refusal tells
        }
        return directory
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserTemporary::class.java)

        /** What a user's name may keep in the root's name; any other character is an underscore there. */
        private val unsafe = Regex("[^A-Za-z0-9._-]")

        /** The permissions of an ACL entry that let its principal change a directory, or what is in it. */
        private val changes =
            setOf(
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.DELETE_CHILD,
                AclEntryPermission.DELETE,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.WRITE_OWNER,
            )

        /** A unix mode's bit that lets others write, and the sticky bit, which keeps each one's entries their own. */
        private const val OTHERS_WRITABLE = 0b000_000_010
        private const val STICKY = 0b1_000_000_000

        /** This process's: in [base], for `user.name`. */
        val current: UserTemporary by lazy { UserTemporary(base()) }

        /**
         * Where this user's root is made: on Linux, the user's `$XDG_RUNTIME_DIR`, which is theirs alone and which no
         * cleaner of old files empties, or else their `~/.cache` where they have one, each where it is a directory of the
         * user's that no one else can write; and [temporary], `java.io.tmpdir`, otherwise, which macOS and Windows give
         * each user, and which on Linux is the last resort.
         */
        internal fun base(
            environment: Map<String, String> = System.getenv(),
            home: String = System.getProperty("user.home"),
            temporary: Path = Path.of(System.getProperty("java.io.tmpdir")),
            linux: Boolean = System.getProperty("os.name").startsWith("Linux"),
        ): Path {
            if (!linux) return temporary
            val owned = ownedByUser(temporary)
            return listOfNotNull(environment["XDG_RUNTIME_DIR"], "$home/.cache")
                .map { Path.of(it) }
                .firstOrNull { usable(it, owned) } ?: temporary
        }

        /** Whether [directory] is an absolute path to a directory, where links lead, that [refusal] does not refuse. */
        private fun usable(
            directory: Path,
            owned: (Path) -> Boolean,
        ): Boolean =
            try {
                directory.isAbsolute && Files.isDirectory(directory) && refusal(directory.toRealPath(), owned) == null
            } catch (_: IOException) {
                false
            }

        /**
         * Why [directory] is not to be a root, or null when it is a directory, not a link, that [owned] says is this
         * user's and that no one else can write.
         */
        private fun refusal(
            directory: Path,
            owned: (Path) -> Boolean,
        ): String? =
            try {
                val attributes = Files.readAttributes(directory, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                when {
                    attributes.isSymbolicLink || attributes.isOther -> "it is a link"
                    !attributes.isDirectory -> "it is not a directory"
                    !owned(directory) -> "another user owns it"
                    else -> writers(directory)
                }
            } catch (failure: IOException) {
                "it cannot be read: $failure"
            }

        /**
         * Why others can write [directory], or null when only its owner can: by its permissions, whose group's are the
         * mask of a Linux ACL's other entries, or else by the entries of its ACL, as on Windows.
         */
        private fun writers(directory: Path): String? {
            val posix = Files.getFileAttributeView(directory, PosixFileAttributeView::class.java, NOFOLLOW_LINKS)
            return if (posix != null) byPermissions(posix.readAttributes().permissions()) else byAcl(directory)
        }

        /** Why others can write a directory whose permissions are [permissions], or null when only its owner can. */
        private fun byPermissions(permissions: Set<PosixFilePermission>): String? =
            if (GROUP_WRITE in permissions || OTHERS_WRITE in permissions) {
                "its group or others can write it (${PosixFilePermissions.toString(permissions)})"
            } else {
                null
            }

        /** Why others can write [directory] by an entry of its ACL, or null when no entry lets anyone but its owner. */
        private fun byAcl(directory: Path): String? {
            val view = Files.getFileAttributeView(directory, AclFileAttributeView::class.java, NOFOLLOW_LINKS) ?: return null
            val owner = view.owner
            return view.acl
                .firstOrNull { it.type() == ALLOW && it.principal() != owner && it.permissions().any(changes::contains) }
                ?.let { "its ACL lets ${it.principal().name} write it" }
        }

        /** Why others can replace what this user makes in [temporary], or null: one that anyone can write, without the sticky bit. */
        private fun open(temporary: Path): String? {
            val mode =
                try {
                    Files.getAttribute(temporary, "unix:mode") as Int
                } catch (_: UnsupportedOperationException) {
                    return null
                } catch (_: IllegalArgumentException) {
                    return null
                } catch (failure: IOException) {
                    return "it cannot be read: $failure"
                }
            return if (mode and OTHERS_WRITABLE != 0 && mode and STICKY == 0) "anyone can write it, and it has no sticky bit" else null
        }

        /** Gives [directory]'s owner alone access to it where the file system has ACLs but no permissions, as Windows. */
        private fun ownerOnly(directory: Path) {
            if ("posix" in directory.fileSystem.supportedFileAttributeViews()) return
            val view = Files.getFileAttributeView(directory, AclFileAttributeView::class.java, NOFOLLOW_LINKS) ?: return
            view.acl =
                listOf(
                    AclEntry
                        .newBuilder()
                        .setType(ALLOW)
                        .setPrincipal(view.owner)
                        .setPermissions(AclEntryPermission.entries.toSet())
                        .setFlags(FILE_INHERIT, DIRECTORY_INHERIT)
                        .build(),
                )
        }

        /**
         * Whether this process's user owns a path, as it owns a file that it makes in [temporary]; true of every path on
         * a file system without owners.
         */
        internal fun ownedByUser(temporary: Path): (Path) -> Boolean {
            val user = owner(temporary) ?: return { true }
            return { path -> Files.getOwner(path, NOFOLLOW_LINKS) == user }
        }

        /** The owner of a file that this process makes in [temporary], or null on a file system without owners. */
        private fun owner(temporary: Path): UserPrincipal? {
            val probe = Files.createTempFile(temporary, ".xtc-lsp-owner-", null)
            return try {
                Files.getOwner(probe, NOFOLLOW_LINKS)
            } catch (_: UnsupportedOperationException) {
                null
            } finally {
                Files.deleteIfExists(probe)
            }
        }
    }
}
