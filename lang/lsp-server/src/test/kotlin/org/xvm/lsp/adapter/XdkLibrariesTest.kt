package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.util.UserTemporary
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * Exercises the production bundled XDK library set. Confirms modules resolve without an external installation
 * and bundled source remains navigable, read-only and outside rename/replacement authority.
 */
class XdkLibrariesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `the production bundle resolves the complete distribution library set`() {
        val names =
            listOf(
                "aggregate",
                "cli",
                "collections",
                "convert",
                "crypto",
                "ecstasy",
                "json",
                "jsondb",
                "metrics",
                "net",
                "oodb",
                "runner",
                "runner_client",
                "sec",
                "web",
                "webauth",
                "webcli",
                "xenia",
                "xml",
                "xunit",
                "xunit_db",
                "xunit_engine",
                "mack",
                "_native",
            ).map { "$it.xtclang.org" }
        assertThat(XdkLibraries.moduleNames).containsExactlyInAnyOrderElementsOf(names)
        val imports =
            names
                .filterNot { it.startsWith("mack.") || it.startsWith("_native.") }
                .mapIndexed { index, name -> "package lib$index import $name;" }
                .joinToString(" ")
        XdkAdapter().use { adapter ->
            val result = adapter.compile("untitled:Libraries.x", "module Libraries { $imports }")
            assertThat(result.diagnostics).isEmpty()
            assertThat(result.success).isTrue()
        }
    }

    @Test
    fun `bundled library source is navigable read only and cannot be renamed or replaced by a binary`() {
        val uri = directory.resolve("App.x").toUri().toString()
        val source =
            "module App { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val column = source.indexOf("Document")
            assertThat(adapter.prepareRename(uri, 0, column)).isNull()
            val target = requireNotNull(adapter.findDefinition(uri, 0, column))
            val path = Path.of(URI(target.uri))
            val line = Files.readAllLines(path)[target.startLine]
            assertThat(line.substring(target.startColumn, target.endColumn)).isEqualTo("Document")
            assertThat(Files.getPosixFilePermissions(path))
                .doesNotContain(
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_WRITE,
                )
            assertThat(adapter.compile(target.uri, Files.readString(path)).diagnostics).isEmpty()
            assertThat(adapter.rename(target.uri, target.startLine, target.startColumn, "Renamed"))
                .isNull()
            assertThat(
                adapter.formatDocument(
                    target.uri,
                    Files.readString(path),
                    FormattingOptions(4, true),
                ),
            ).isEmpty()
        }
        // A source module of a bundled library's name replaces it (XdkLibraryReplacement); only the
        // bootstrap modules' names are refused
        assertThatThrownBy { XdkSourceModule("mack.xtclang.org", uri) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val binary =
            checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/xml.xtc")).use {
                XdkDependency.fromBinary(it.readBytes())
            }
        assertThatThrownBy { XdkDependencies(listOf(binary)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a Gradle build's XDK is the bundled one exactly when its module files and its javatools are the bundle's`() {
        // the build recorded the revision of the javatools jar that the server bundles, which is the one the tests run
        // with, over its files, its build information without the commit and branch it was built at
        val jar =
            File(
                EmbeddingSupport::class.java.protectionDomain.codeSource.location
                    .toURI(),
            )
        assertThat(jar.name).endsWith(".jar")
        assertThat(XdkLibraries.javatools).isEqualTo(javatoolsRevision(jar))
        val modules = XdkLibraries.revisions
        assertThat(modules).hasSize(XdkLibraries.packagedResources.size)
        assertThat(XdkLibraries.bundles(modules.shuffled(), XdkLibraries.javatools)).isTrue()
        // another build of one module, a module more or less, or another javatools, is another XDK
        val json = modules.first()
        assertThat(XdkLibraries.bundles(modules - json + sha256("another json".toByteArray()), XdkLibraries.javatools)).isFalse()
        assertThat(XdkLibraries.bundles(modules + sha256("another module".toByteArray()), XdkLibraries.javatools)).isFalse()
        assertThat(XdkLibraries.bundles(modules - json, XdkLibraries.javatools)).isFalse()
        assertThat(XdkLibraries.bundles(modules, sha256("another javatools".toByteArray()))).isFalse()
        assertThat(XdkLibraries.bundles(modules, null)).isFalse()
    }

    @Test
    fun `the bundled libraries' directory is written again, not used, where its files are not the bundle's`() {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews(), "links and POSIX permissions")
        val temporary = UserTemporary(directory, "tester")
        val written = XdkLibraries.bundled(temporary)
        assertThat(written.parent).isEqualTo(temporary.root())
        assertThat(revisions(written)).isEqualTo(bundled)
        // `xtc test` loads every module in it: a bundled module's file with other content, and a module more, are not
        //  to be loaded
        val module = written.resolve(XdkLibraries.packagedResources.first())
        Files.writeString(module, "another module")
        Files.writeString(written.resolve("planted.xtc"), "a module more")
        assertThat(XdkLibraries.bundled(temporary)).isEqualTo(written)
        assertThat(revisions(written)).isEqualTo(bundled)
        // nor is a directory without one of them, as a server that was killed while it wrote them left it
        Files.delete(module)
        assertThat(revisions(XdkLibraries.bundled(temporary))).describedAs("A module less").isEqualTo(bundled)
        // nor one whose module is a link, though to a file of the module's very bytes, which can change under it
        val copy = Files.copy(module, directory.resolve("copy.xtc"))
        Files.delete(module)
        Files.createSymbolicLink(module, copy)
        assertThat(revisions(XdkLibraries.bundled(temporary))).describedAs("A module that is a link").isEqualTo(bundled)
        // nor one with a folder at a module's name, or a folder more
        Files.delete(module)
        Files.createDirectory(module)
        assertThat(revisions(XdkLibraries.bundled(temporary))).describedAs("A folder at a module's name").isEqualTo(bundled)
        Files.createDirectory(written.resolve("planted"))
        assertThat(revisions(XdkLibraries.bundled(temporary))).describedAs("A folder more").isEqualTo(bundled)
    }

    @Test
    fun `the bundled libraries' directory is checked again at each use, as a cleaner of old files can delete its root`() {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews(), "POSIX permissions")
        // the root that is made again is another user's once this is set, as one who makes it after the cleaner
        val stranger = AtomicBoolean()
        val temporary = UserTemporary(directory, "tester") { !(stranger.get() && it.fileName.toString() == "xtc-lsp-tester") }
        val written = XdkLibraries.bundled(temporary)
        // the root is gone, and made again with other modules at the bundle's directory: the next launch's is checked
        remade(written)
        assertThat(XdkLibraries.bundled(temporary)).isEqualTo(written)
        assertThat(revisions(written)).isEqualTo(bundled)
        // and where another user made it again, theirs is not used at all
        stranger.set(true)
        remade(written)
        val own = XdkLibraries.bundled(temporary)
        assertThat(own.parent).isNotEqualTo(written.parent).isEqualTo(temporary.root())
        assertThat(revisions(own)).isEqualTo(bundled)
        assertThat(revisions(written)).describedAs("Another user's directory, which the server leaves as it is").isNotEqualTo(bundled)
    }

    /** The SHA-256 of each bundled module's file, as the server's resources have it, by its name. */
    private val bundled by lazy {
        XdkLibraries.packagedResources.associateWith { name ->
            sha256(checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/$name")).use { it.readBytes() })
        }
    }

    /** Deletes the root of [written], as a cleaner of old files does, and makes [written] again, with other modules. */
    private fun remade(written: Path) {
        check(written.parent.toFile().deleteRecursively())
        Files.createDirectories(written)
        XdkLibraries.packagedResources.forEach { Files.writeString(written.resolve(it), "another module") }
    }

    /** The SHA-256 of each file in [directory], by its name; an entry that is no file, not followed, has none. */
    private fun revisions(directory: Path): Map<String, String?> =
        Files.list(directory).use { files -> files.toList() }.associate {
            it.fileName.toString() to
                it.takeIf { Files.isRegularFile(it, NOFOLLOW_LINKS) }?.let { file -> sha256(Files.readAllBytes(file)) }
        }

    private fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** The revision of [jar], as the server's build and the XTC plugin's export give a javatools jar's. */
    private fun javatoolsRevision(jar: File): String =
        ZipFile(jar).use { zip ->
            zip
                .entries()
                .asSequence()
                .filterNot { it.isDirectory }
                .associate { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    entry.name to sha256(if (entry.name == "build-info.properties") withoutGit(bytes) else bytes)
                }.toSortedMap()
                .entries
                .joinToString("") { (name, revision) -> "$name=$revision\n" }
                .let { sha256(it.toByteArray()) }
        }

    /** The lines of [properties] but the blank ones and the `git.` ones, which name a build's commit and branch. */
    private fun withoutGit(properties: ByteArray): ByteArray =
        String(properties, Charsets.ISO_8859_1)
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("git.") }
            .joinToString("\n")
            .toByteArray(Charsets.ISO_8859_1)
}
