package org.xvm.lsp.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.testing.awaited
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks this user's temporary root in a temporary directory of the test's, as another user could have made the
 * directory at its name in a shared /tmp, or made it again once a cleaner of old files deleted it: a link, a file,
 * another user's directory, or one that others can write, is not used, and neither is a temporary directory that anyone
 * can write without the sticky bit; a delete stays in the root, deletes only what the user owns, and never what a link
 * names; and a write stays in the root as it is now, and goes through no link. Another user's file is one that the
 * root's ownership check, which the cases give, says is not the user's; the real check is a case of its own. The cases
 * need POSIX permissions and links, which a Windows run has not.
 */
class UserTemporaryTest {
    @TempDir lateinit var directory: Path

    @BeforeEach
    fun posix() {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews(), "links and POSIX permissions")
    }

    @Test
    fun `the root is the user's alone, one that its group or others can write is not used at any use, and the process's own stays`() {
        val temporary = UserTemporary(directory, "tester")
        val root = temporary.root()
        assertThat(root).isEqualTo(directory.resolve("xtc-lsp-tester"))
        assertThat(permissions(root)).isEqualTo("rwx------")
        // in use, then made writable for its group
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx-w----"))
        val own = temporary.root()
        fallback(own, "The root that its group can write")
        // which the process keeps once it has it, as what it made there is there
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
        assertThat(temporary.root()).describedAs("The process's own root, once the shared one is right again").isEqualTo(own)

        Files.setPosixFilePermissions(
            Files.createDirectory(directory.resolve("xtc-lsp-others")),
            PosixFilePermissions.fromString("rwx----w-"),
        )
        fallback(UserTemporary(directory, "others").root(), "The root that others can write")
        val readable = Files.createDirectory(directory.resolve("xtc-lsp-readable"))
        Files.setPosixFilePermissions(readable, PosixFilePermissions.fromString("rwxr-xr-x"))
        assertThat(UserTemporary(directory, "readable").root()).describedAs("The root that others can only read").isEqualTo(readable)
    }

    @Test
    fun `a user's name keeps only the characters of a file name in the root's name`() {
        assertThat(UserTemporary(directory, "DOMAIN\\Some User/../x").root()).isEqualTo(directory.resolve("xtc-lsp-DOMAIN_Some_User_.._x"))
    }

    @Test
    fun `a temporary directory that anyone can write without the sticky bit has no root, and one with it, as tmp, has`() {
        val open = Files.createDirectory(directory.resolve("open"))
        chmod("777", open)
        assertThatThrownBy { UserTemporary(open, "tester").root() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("sticky")
        assertThat(open.resolve("xtc-lsp-tester")).doesNotExist()
        chmod("1777", open)
        assertThat(UserTemporary(open, "tester").root()).isEqualTo(open.resolve("xtc-lsp-tester"))
    }

    @Test
    fun `the owner check takes the process's user for the owner of what it makes, and no other user`() {
        val owned = UserTemporary.ownedByUser(directory)
        val mine = Files.createFile(directory.resolve("mine"))
        assertThat(owned(mine)).isTrue()
        assertThat(owned(directory)).isTrue()
        // the file system's root is another user's where the tests do not run as that user, as on macOS
        val others = listOf(Path.of("/")).filter { Files.getOwner(it, NOFOLLOW_LINKS) != Files.getOwner(mine, NOFOLLOW_LINKS) }
        assertThat(others).noneMatch { owned(it) }
    }

    @Test
    fun `on Linux the root goes in the user's runtime directory, else in their cache, else in the temporary directory`() {
        val runtime = private(directory.resolve("runtime"))
        val home = Files.createDirectory(directory.resolve("home"))
        val cache = private(home.resolve(".cache"))
        val temporary = Files.createDirectory(directory.resolve("tmp"))

        fun base(
            runtimeDirectory: String?,
            linux: Boolean = true,
        ) = UserTemporary.base(listOfNotNull(runtimeDirectory?.let { "XDG_RUNTIME_DIR" to it }).toMap(), home.toString(), temporary, linux)

        assertThat(base(runtime.toString())).isEqualTo(runtime)
        assertThat(base(runtime.toString(), linux = false)).describedAs("Elsewhere than on Linux").isEqualTo(temporary)
        // a runtime directory that others can write, that is not there, or that is relative, is passed over
        Files.setPosixFilePermissions(runtime, PosixFilePermissions.fromString("rwxrwxrwx"))
        assertThat(base(runtime.toString())).describedAs("A runtime directory that others can write").isEqualTo(cache)
        assertThat(base(directory.resolve("none").toString())).describedAs("No runtime directory").isEqualTo(cache)
        assertThat(base("runtime")).describedAs("A relative runtime directory").isEqualTo(cache)
        assertThat(base(null)).isEqualTo(cache)
        // and so is a cache that others can write, or none
        Files.setPosixFilePermissions(cache, PosixFilePermissions.fromString("rwxrwxrwx"))
        assertThat(base(null)).describedAs("A cache that others can write").isEqualTo(temporary)
        Files.delete(cache)
        assertThat(base(null)).describedAs("No cache").isEqualTo(temporary)
    }

    @Test
    fun `a write stays in the root as it is now, makes the folders on its way, and goes through no link`() {
        // the root that is made again is another user's once this is set, as one who makes it after a cleaner
        val stranger = AtomicBoolean()
        val temporary = UserTemporary(directory, "tester") { !(stranger.get() && it.fileName.toString() == "xtc-lsp-tester") }
        val root = temporary.root()
        val copies = root.resolve("copies")
        assertThat(temporary.write(copies.resolve("ecstasy/Array.x"), "class Array {}".toByteArray())).hasContent("class Array {}")
        val kept = Files.writeString(Files.createDirectory(directory.resolve("outside")).resolve("kept.txt"), "kept")
        // a link at the file's name, and one on its way, as another user could put in a root made again
        Files.createSymbolicLink(copies.resolve("kept.txt"), kept)
        Files.createSymbolicLink(copies.resolve("through"), kept.parent)
        for (path in listOf("kept.txt", "through/kept.txt", "through/new.x")) {
            assertThatThrownBy { temporary.write(copies.resolve(path), "planted".toByteArray()) }
                .describedAs(path)
                .isInstanceOf(FileAlreadyExistsException::class.java)
        }
        // nor anything outside the root, or in a root that is no longer used
        assertThatThrownBy { temporary.write(kept.resolveSibling("new.x"), "planted".toByteArray()) }
            .isInstanceOf(IllegalStateException::class.java)
        stranger.set(true)
        assertThatThrownBy { temporary.write(copies.resolve("ecstasy/Map.x"), "class Map {}".toByteArray()) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(copies.resolve("ecstasy/Map.x")).doesNotExist()
        assertThat(kept).hasContent("kept")
        assertThat(kept.resolveSibling("new.x")).doesNotExist()
    }

    @Test
    fun `a link, a file, or another user's directory at the root's name is not used`() {
        // a link to a directory of the user's, for the user alone, which a check that followed it would take
        val elsewhere = Files.createDirectory(directory.resolve("elsewhere"))
        Files.setPosixFilePermissions(elsewhere, PosixFilePermissions.fromString("rwx------"))
        Files.createSymbolicLink(directory.resolve("xtc-lsp-linked"), elsewhere)
        fallback(UserTemporary(directory, "linked").root(), "The root that is a link")
        assertThat(Files.list(elsewhere).use { it.toList() }).isEmpty()

        Files.writeString(directory.resolve("xtc-lsp-file"), "a file")
        fallback(UserTemporary(directory, "file").root(), "The root that is a file")

        val foreign = UserTemporary(directory, "foreign") { it.fileName.toString() != "xtc-lsp-foreign" }
        fallback(foreign.root(), "The root that another user owns")
        assertThat(directory.resolve("xtc-lsp-foreign")).isDirectory()
    }

    @Test
    fun `a delete stays in the root, deletes only what the user owns there, and never what a link names`() {
        val temporary = UserTemporary(directory, "tester") { it.fileName.toString() != "foreign" }
        val root = temporary.root()
        val kept = Files.writeString(Files.createDirectory(directory.resolve("outside")).resolve("kept.txt"), "kept")
        val tree = Files.createDirectories(root.resolve("tree/folder"))
        Files.writeString(tree.resolve("copy.x"), "module copy {}").toFile().setReadOnly()
        // links in the tree to a folder and a file outside the root, which a delete that followed them would empty
        Files.createSymbolicLink(tree.resolve("outside"), kept.parent)
        Files.createSymbolicLink(tree.resolve("kept.txt"), kept)
        assertThat(temporary.delete(tree.parent)).isTrue()
        assertThat(tree.parent).doesNotExist()
        assertThat(kept).hasContent("kept")

        // a link is deleted itself
        val link = Files.createSymbolicLink(root.resolve("link"), kept.parent)
        assertThat(temporary.delete(link)).isTrue()
        assertThat(link).doesNotExist()
        assertThat(kept).hasContent("kept")

        // not outside the root, not by a folder that links to outside, not the root, not another user's
        assertThat(temporary.delete(kept.parent)).isFalse()
        Files.createSymbolicLink(root.resolve("through"), kept.parent)
        assertThat(temporary.delete(root.resolve("through/kept.txt"))).isFalse()
        assertThat(temporary.delete(root)).isFalse()
        val foreign = Files.createDirectory(root.resolve("foreign"))
        assertThat(temporary.delete(foreign)).isFalse()
        assertThat(listOf(kept, root, foreign)).allMatch { Files.exists(it, NOFOLLOW_LINKS) }
        assertThat(kept).hasContent("kept")
    }

    /** That [root], which [case] gave, is a process's own root in the test's directory, for its user alone. */
    private fun fallback(
        root: Path,
        case: String,
    ) {
        assertThat(root.parent).describedAs(case).isEqualTo(directory)
        assertThat(root.fileName.toString()).describedAs(case).matches("xtc-lsp-[a-z]+-[0-9]+")
        assertThat(Files.isDirectory(root, NOFOLLOW_LINKS)).describedAs(case).isTrue()
        assertThat(permissions(root)).describedAs(case).isEqualTo("rwx------")
    }

    private fun permissions(path: Path): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(path, NOFOLLOW_LINKS))

    /** [path], made a directory for its owner alone. */
    private fun private(path: Path): Path =
        Files.createDirectory(path).also { Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwx------")) }

    /** Sets [path]'s mode to [mode], in octal, as chmod does, which can set the sticky bit that Java cannot. */
    private fun chmod(
        mode: String,
        path: Path,
    ) {
        val process = ProcessBuilder("chmod", mode, path.toString()).inheritIO().start()
        check(process.awaited() && process.exitValue() == 0) { "chmod $mode $path" }
    }
}
