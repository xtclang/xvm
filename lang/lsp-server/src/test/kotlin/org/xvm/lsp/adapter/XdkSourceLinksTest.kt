package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkSourceLinksTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `module and aliased type import links resolve to source without spelling guesses`() {
        val library = source("Library", "module Library { class Box {} }")
        val text =
            "module Consumer {\r\n    package lib import Library;\r\n" +
                "    import lib.Box as Crate;\r\n    Crate value = new Crate();\r\n}"
        val uri = source("Consumer", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val links = adapter.getDocumentLinks(uri, text)
            assertThat(links.map { it.target }).containsExactly(library, library)
            assertThat(links.map { spelling(text, it.range) }).containsExactly("Library", "Crate")
            assertThat(adapter.getDocumentLinks(uri, text.replace("Library;", "Missing;"))).isEmpty()
            adapter.compile(uri, text.replace("Library;", "Missing;"))
            assertThat(adapter.getDocumentLinks(uri, text.replace("Library;", "Missing;"))).isEmpty()
        }
    }

    @Test
    fun `bundled explicit and wildcard import links retain read only source ownership`() {
        val text = "module Links { import ecstasy.maps.ListMap as MapImpl; MapImpl<String, Int> value = new MapImpl(); }"
        XdkAdapter().use { adapter ->
            val uri = "untitled:Links.x"
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val link = adapter.getDocumentLinks(uri, text).single()
            assertThat(spelling(text, link.range)).isEqualTo("MapImpl")
            assertThat(XdkLibrarySources.owns(requireNotNull(link.target))).isTrue()
            assertThat(adapter.formatDocument(link.target, "module ReadOnly {}", FormattingOptions(4, true))).isEmpty()
            val wildcard = "module Links { import ecstasy.collections.*; }"
            assertThat(adapter.compile(uri, wildcard).diagnostics).isEmpty()
            val container = adapter.getDocumentLinks(uri, wildcard).single()
            assertThat(spelling(wildcard, container.range)).isEqualTo("ecstasy.collections")
            assertThat(XdkLibrarySources.owns(requireNotNull(container.target))).isTrue()
        }
    }

    @Test
    fun `explicit import aliases link only uses in the resolved lexical scope`() {
        val text =
            "module Aliases { class Box {} class Other {}\n" +
                "    Int first() { import Box as Item; Item value = new Item(); return 1; }\n" +
                "    Int second() { import Other as Item; Item value = new Item(); return 2; }\n}"
        XdkAdapter().use { adapter ->
            val uri = "untitled:Aliases.x"
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            listOf(1, 2).forEach { line ->
                val column = text.lines()[line].indexOf("Item")
                val linked = requireNotNull(adapter.getLinkedEditingRanges(uri, line, column))
                assertThat(linked.ranges).hasSize(3)
                assertThat(linked.ranges.map { it.start.line }).containsOnly(line)
                assertThat(linked.ranges.map { spelling(text, it) }).containsOnly("Item")
            }
            assertThat(adapter.getLinkedEditingRanges(uri, 0, text.indexOf("Box"))).isNull()
            adapter.compile(uri, text.replace("new Item()", "new Missing()"))
            assertThat(adapter.getLinkedEditingRanges(uri, 1, text.lines()[1].indexOf("Item"))).isNull()
        }
    }

    @Test
    fun `wildcard imports link resolved containers and unsupported conditional syntax cannot invent links`() {
        val library = source("Library", "module Library { package tools { class Box {} } }")
        listOf("import lib.tools.*;", "if (true) { import lib.tools.Box as Crate; }").forEach { statement ->
            val text =
                """
                module Consumer {
                    package lib import Library;
                    $statement
                }
                """.trimIndent().replace("\n", "\r\n")
            val uri = source("Consumer", text)
            XdkAdapter().use { adapter ->
                adapter.initializeWorkspace(listOf(directory.toString()))
                val result = adapter.compile(uri, text)
                if (statement.startsWith("if")) {
                    // The parser does not currently construct conditional ImportStatements from source.
                    assertThat(result.success).isFalse()
                    assertThat(result.diagnostics).isNotEmpty()
                    assertThat(result.diagnostics.map { it.code }).doesNotContain("EMB-5")
                    assertThat(adapter.getDocumentLinks(uri, text)).isEmpty()
                } else {
                    assertThat(result.diagnostics).isEmpty()
                    val links = adapter.getDocumentLinks(uri, text)
                    assertThat(links.map { it.target }).containsExactly(library, library)
                    assertThat(links.map { spelling(text, it.range) }).containsExactly("Library", "lib.tools")
                }
                assertThat(adapter.getDocumentLinks(uri, text.replace("lib.tools", "lib.missing"))).isEmpty()
            }
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory
            .resolve("$name.x")
            .toFile()
            .also { it.writeText(text) }
            .canonicalFile
            .toURI()
            .toString()

    private fun spelling(
        text: String,
        range: Range,
    ): String {
        fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
        return text.substring(offset(range.start), offset(range.end))
    }
}
