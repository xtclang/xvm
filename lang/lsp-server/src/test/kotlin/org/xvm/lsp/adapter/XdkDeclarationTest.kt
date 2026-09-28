package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Location
import java.net.URI
import java.nio.file.Path

class XdkDeclarationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `overrides expose all written contracts rather than selecting an implementation`() {
        val text =
            "module Declarations { interface A { Int /*a*/read(); } interface B { Int /*b*/read(); } " +
                "class Both implements A, B { @Override Int /*body*/read() = 1; } " +
                "Int use(Both value) = value. /*use*/read(); }"
        withSource(text) { adapter, uri ->
            val at = text.indexOf("/*use*/") + "/*use*/".length
            assertThat(adapter.findDeclarations(uri, 0, at).map { it.startColumn })
                .containsExactlyInAnyOrder(offset(text, "a"), offset(text, "b"))
            assertThat(adapter.findDefinition(uri, 0, at)?.startColumn).isEqualTo(offset(text, "body"))
            assertThat(adapter.findDeclaration(uri, 0, at)).isNull()
        }
    }

    @Test
    fun `property overrides navigate to their written contract`() {
        val text =
            "module Declarations { interface Named { @RO String /*contract*/name; } " +
                "class Stored implements Named { @Override String name = \"x\"; } " +
                "String run(Stored stored) = stored. /*use*/name; }"
        withSource(text) { adapter, uri ->
            assertThat(adapter.findDeclarations(uri, 0, offset(text, "use")).map { it.startColumn })
                .containsExactly(offset(text, "contract"))
        }
    }

    @Test
    fun `locals and import aliases retain lexical declarations and library targets stay indexed`() {
        val text =
            "module Declarations { import ecstasy.text.StringBuffer as /*alias*/Buffer; " +
                "Buffer run(String /*local*/text) { /*aliasUse*/Buffer buffer = new Buffer(); " +
                "buffer.append(/*localUse*/text); return buffer; } }"
        withSource(text) { adapter, uri ->
            mapOf("aliasUse" to "alias", "localUse" to "local").forEach { (use, declaration) ->
                assertThat(adapter.findDeclarations(uri, 0, offset(text, use)).map { it.startColumn })
                    .containsExactly(offset(text, declaration))
            }
            val library = adapter.findDeclarations(uri, 0, text.indexOf("String /*local*/")).single()
            assertThat(written(library)).isEqualTo("String")
            adapter.closeDocument(uri)
            assertThat(adapter.findDeclarations(uri, 0, offset(text, "localUse"))).isEmpty()
        }
    }

    @Test
    fun `closed consumer declarations resolve across the discovered graph`() {
        val library = "module Library { interface Api { Int /*contract*/read(); } }"
        val consumer = "module Consumer { package lib import Library; Int use(lib.Api api) = api.read(); }"
        directory.resolve("Library.x").toFile().writeText(library)
        val file = directory.resolve("Consumer.x").toFile().apply { writeText(consumer) }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val targets = adapter.findDeclarations(file.canonicalFile.toURI().toString(), 0, consumer.indexOf("read"))
            assertThat(targets).hasSize(1)
            assertThat(targets.single().startColumn).isEqualTo(offset(library, "contract"))
            assertThat(written(targets.single())).isEqualTo("read")
        }
    }

    @Test
    fun `broken names do not reuse a previous declaration`() {
        val text = "module Declarations { Int value = 1; Int run() = /*use*/value; }"
        withSource(text) { adapter, uri ->
            assertThat(adapter.findDeclarations(uri, 0, offset(text, "use"))).hasSize(1)
            adapter.compile(uri, text.replace("/*use*/value", "/*use*/missing"))
            assertThat(adapter.findDeclarations(uri, 0, offset(text, "use"))).isEmpty()
        }
    }

    private fun withSource(
        text: String,
        action: (XdkAdapter, String) -> Unit,
    ) {
        val uri =
            directory
                .resolve("Declarations.x")
                .toFile()
                .canonicalFile
                .toURI()
                .toString()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            action(adapter, uri)
        }
    }

    private fun offset(
        text: String,
        marker: String,
    ): Int = text.indexOf("/*$marker*/") + marker.length + 4

    private fun written(location: Location): String =
        Path
            .of(URI(location.uri))
            .toFile()
            .readLines()[location.startLine]
            .substring(location.startColumn, location.endColumn)
}
