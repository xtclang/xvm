package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

class XdkDelegatedRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["read", "value"])
    fun `nested generic delegates join written contracts and implementations across modules`(member: String) {
        val library = "module Library { interface Api<T> { T value; T read(); } }"
        val consumer =
            "module Consumer { package lib import Library; " +
                "class Engine implements lib.Api<String> { @Override String value = \"text\"; @Override String read() = value; } " +
                "class Forward(Engine target) delegates lib.Api<String>(target) {} " +
                "class Outer(Forward target) delegates lib.Api<String>(target) {} " +
                "String use(Outer box) = box.read() + box.value; }"
        val libraryUri = source("Library", library)
        val consumerUri = source("Consumer", consumer)
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Library", libraryUri),
                    XdkSourceModule("Consumer", consumerUri, setOf("Library")),
                ),
            )
            assertThat(adapter.compile(consumerUri, consumer).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(consumerUri, 0, consumer.lastIndexOf(member), "renamed"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(libraryUri, consumerUri)
            assertThat(apply(library, edit.changes.getValue(libraryUri))).isEqualTo(library.replace(member, "renamed"))
            assertThat(apply(consumer, edit.changes.getValue(consumerUri))).isEqualTo(consumer.replace(member, "renamed"))
            assertThat(directory.resolve("Library.x").toFile().readText()).isEqualTo(library)
            assertThat(directory.resolve("Consumer.x").toFile().readText()).isEqualTo(consumer)
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory
            .resolve("$name.x")
            .toFile()
            .apply { writeText(text) }
            .canonicalFile
            .toURI()
            .toString()

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits.sortedByDescending { it.range.start.column }.fold(text) { current, edit ->
            current.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)
        }
}
