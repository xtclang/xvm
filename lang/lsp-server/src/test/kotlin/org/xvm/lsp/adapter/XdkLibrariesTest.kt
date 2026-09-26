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
import java.nio.file.Path

class XdkLibrariesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `the production bundle resolves the complete distribution library set`() {
        val names = listOf(
            "aggregate", "cli", "collections", "convert", "crypto", "ecstasy", "json", "jsondb",
            "metrics", "net", "oodb", "runner", "runner_client", "sec", "web", "webauth", "webcli",
            "xenia", "xml", "xunit", "xunit_db", "xunit_engine", "mack", "_native",
        ).map { "$it.xtclang.org" }
        assertThat(XdkLibraries.moduleNames).containsExactlyInAnyOrderElementsOf(names)
        val imports = names.filterNot { it.startsWith("mack.") || it.startsWith("_native.") }
            .mapIndexed { index, name -> "package lib$index import $name;" }.joinToString(" ")
        XdkAdapter().use { adapter ->
            val result = adapter.compile("untitled:Libraries.x", "module Libraries { $imports }")
            assertThat(result.diagnostics).isEmpty()
            assertThat(result.success).isTrue()
        }
    }

    @Test
    fun `non core bundled libraries resolve types but cannot be renamed or replaced`() {
        val uri = directory.resolve("App.x").toUri().toString()
        val source = "module App { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val column = source.indexOf("Document")
            assertThat(adapter.prepareRename(uri, 0, column)).isNull()
            assertThat(adapter.findDefinition(uri, 0, column)).isNull()
        }
        assertThatThrownBy { XdkSourceModule("xml.xtclang.org", uri) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val binary = checkNotNull(javaClass.getResourceAsStream("/org/xvm/lsp/xdk/xml.xtc"))
            .use { XdkDependency.fromBinary(it.readBytes()) }
        assertThatThrownBy { XdkDependencies(listOf(binary)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
