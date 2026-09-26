package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class XdkLibrariesTest {
    @TempDir
    lateinit var directory: Path

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
    fun `bundled library source is navigable read only and cannot be renamed or replaced`() {
        val uri = directory.resolve("App.x").toUri().toString()
        val source = "module App { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val column = source.indexOf("Document")
            assertThat(adapter.prepareRename(uri, 0, column)).isNull()
            val target = requireNotNull(adapter.findDefinition(uri, 0, column))
            val path = Path.of(URI(target.uri))
            val line = Files.readAllLines(path)[target.startLine]
            assertThat(line.substring(target.startColumn, target.endColumn)).isEqualTo("Document")
            assertThat(Files.getPosixFilePermissions(path)).doesNotContain(
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_WRITE,
            )
            assertThat(adapter.compile(target.uri, Files.readString(path)).diagnostics).isEmpty()
            assertThat(adapter.rename(target.uri, target.startLine, target.startColumn, "Renamed")).isNull()
            assertThat(adapter.formatDocument(target.uri, Files.readString(path), FormattingOptions(4, true))).isEmpty()
        }
        assertThatThrownBy { XdkSourceModule("xml.xtclang.org", uri) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val binary =
            checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/xml.xtc"))
                .use { XdkDependency.fromBinary(it.readBytes()) }
        assertThatThrownBy { XdkDependencies(listOf(binary)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
