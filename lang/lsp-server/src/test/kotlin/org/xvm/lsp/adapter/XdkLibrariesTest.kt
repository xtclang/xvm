package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.HexFormat
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
