package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks revision-owned library source documents and monikers in virtual and file-backed views. Navigation
 * must find declarations independently of which symbols a consumer previously queried.
 */
class XdkLibraryDocumentsTest {
    @Test
    fun `matching bundled sources have revision owned content and monikers in file and virtual views`() {
        XdkAdapter().use { adapter ->
            val uri = "file:///App.x"
            val text = "module App { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }"
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.indexOf("Document")
            val imported = adapter.findMonikers(uri, 0, at).single()
            val location = requireNotNull(adapter.findDefinition(uri, 0, at))
            val document = requireNotNull(adapter.readOnlyDocument(location.uri))
            assertThat(URI(document.uri).scheme).isEqualTo("ecstasy-library")
            assertThat(URI(document.uri).authority).isEqualTo("xml.xtclang.org")
            assertThat(document.text).isEqualTo(Files.readString(Path.of(URI(location.uri))))
            assertThat(adapter.readOnlyDocument(document.uri)).isEqualTo(document)
            listOf(location.uri, document.uri).forEach { source ->
                assertThat(adapter.findMonikers(source, location.startLine, location.startColumn))
                    .containsExactly(imported.copy(kind = SymbolMoniker.Kind.EXPORT))
                assertThat(adapter.compile(source, "module Forged {} ").diagnostics).isEmpty()
                assertThat(adapter.prepareRename(source, location.startLine, location.startColumn)).isNull()
                assertThat(adapter.formatDocument(source, document.text, FormattingOptions(4, true))).isEmpty()
                assertThat(adapter.readOnlyDocument(source)?.text).isEqualTo(document.text)
            }
            adapter.replaceDependencies(emptyList())
            assertThat(adapter.readOnlyDocument(document.uri)).isEqualTo(document)
            assertThat(adapter.readOnlyDocument(document.uri.replace("/xml/", "/../"))).isNull()
            assertThat(adapter.readOnlyDocument("file:///etc/passwd")).isNull()
            assertThat(adapter.findMonikers(document.uri, 0, 0)).isEmpty()
            adapter.close()
            assertThat(adapter.readOnlyDocument(document.uri)).isNull()
        }
    }

    @Test
    fun `library view resolves declarations never previously queried by the consumer`() {
        XdkAdapter().use { adapter ->
            val text = "module App { String text = \"value\"; }"
            assertThat(adapter.compile("file:///App.x", text).diagnostics).isEmpty()
            val location = requireNotNull(adapter.findDefinition("file:///App.x", 0, text.indexOf("String")))
            val document = requireNotNull(adapter.readOnlyDocument(location.uri))
            val lines = document.text.lines()
            val line = lines.indexOfFirst { it.contains("Int size.get()") }
            assertThat(line).isNotNegative()
            val column = lines[line].indexOf("size")
            val moniker = adapter.findMonikers(document.uri, line, column).single()
            assertThat(moniker.kind).isEqualTo(SymbolMoniker.Kind.EXPORT)
            val consumer = "module App { Int length(String text) = text.size; }"
            assertThat(adapter.compile("file:///App.x", consumer).diagnostics).isEmpty()
            assertThat(adapter.findMonikers("file:///App.x", 0, consumer.indexOf("size")))
                .containsExactly(moniker.copy(kind = SymbolMoniker.Kind.IMPORT))
        }
    }

    @Test
    fun `a library source's imports and type names go to their declarations in the library`() {
        // the copy that navigation or a stack frame opens, which is compiled on its own
        val uri = Path.of(requireNotNull(XdkLibrarySources.file("ecstasy.xtclang.org", "ecstasy/mgmt/Container.x"))).toUri().toString()
        val text = Files.readString(Path.of(URI(uri)))
        val lines = text.lines()
        XdkAdapter().use { adapter ->
            adapter.compile(uri, text)

            // the file the declaration of the name on the line is in, and the name it declares
            fun target(
                line: String,
                name: String,
            ): Pair<String, String> {
                val row = lines.indexOfFirst { it.contains(line) }
                val location = requireNotNull(adapter.findDefinition(uri, row, lines[row].indexOf(name))) { "$name in $line" }
                val declared = Files.readAllLines(Path.of(URI(location.uri)))[location.startLine]
                return location.uri.substringAfter("xtc-library-sources-").substringAfter('/') to
                    declared.substring(location.startColumn, location.endColumn)
            }
            val template = "ecstasy/reflect/ModuleTemplate.x" to "ModuleTemplate"
            assertThat(target("import reflect.ModuleTemplate;", "ModuleTemplate")).isEqualTo(template)
            assertThat(target("import reflect.ModuleTemplate;", "reflect")).isEqualTo("ecstasy/reflect.x" to "reflect")
            // by the file's import
            assertThat(target("collectInjections(ModuleTemplate template", "ModuleTemplate")).isEqualTo(template)
            // by an implicit import
            assertThat(target("limitCompute(Duration max", "Duration")).isEqualTo("ecstasy/temporal/Duration.x" to "Duration")
            assertThat(target("extends Service.ServiceControl", "ServiceControl")).isEqualTo("ecstasy/Service.x" to "ServiceControl")
            // a declaration's own name
            assertThat(target("Tuple invoke(String methodName", "invoke")).isEqualTo("ecstasy/mgmt/Container.x" to "invoke")
        }
    }
}
