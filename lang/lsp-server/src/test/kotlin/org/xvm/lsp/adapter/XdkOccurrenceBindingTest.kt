package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Location
import java.nio.file.Path

/**
 * Written names that the semantic model once left without a symbol, which navigation then missed: Find References,
 * highlighting and hover find each, and a rename covers it.
 */
class XdkOccurrenceBindingTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a local or parameter used for its formal type, as in t1 DataType, is an occurrence of it`() {
        val source =
            """
            module Formals {
                String describe(Type /*parameter*/t0, Object value) {
                    Type /*local*/t1 = /*copied*/t0;
                    /*declared*/t1.DataType typed = value.as(/*cast*/t1.DataType);
                    return typed.toString() + value.as(/*formal*/t0.DataType).toString();
                }
            }
            """.trimIndent()
        withSource("Formals", source) { adapter, uri ->
            val local = listOf(range(source, "local", "t1"), range(source, "declared", "t1"), range(source, "cast", "t1"))
            val at = position(source, "cast")
            assertThat(adapter.findReferences(uri, at.line, at.column, true).map(::range)).containsExactlyElementsOf(local)
            assertThat(adapter.getDocumentHighlights(uri, at.line, at.column).map { it.range }).containsExactlyInAnyOrderElementsOf(local)
            assertThat(adapter.getHoverInfo(uri, at.line, at.column)).contains("t1")
            val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "t2")) { "No rename of t1" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("t1", "t2"))

            // a parameter's rename is the workspace's, which proves it over the module's compile
            val formal = position(source, "formal")
            assertThat(adapter.findReferences(uri, formal.line, formal.column, true).map(::range))
                .containsExactly(range(source, "parameter", "t0"), range(source, "copied", "t0"), range(source, "formal", "t0"))
            val parameter = requireNotNull(adapter.rename(uri, formal.line, formal.column, "t9")) { "No rename of t0" }
            assertThat(apply(source, parameter.changes.getValue(uri))).isEqualTo(source.replace("t0", "t9"))
        }
    }

    private fun withSource(
        name: String,
        text: String,
        check: (XdkAdapter, String) -> Unit,
    ) {
        val uri =
            directory
                .resolve("$name.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
                .toURI()
                .toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            check(adapter, uri)
        }
    }

    /** The position just after the marker comment that names [marker]. */
    private fun position(
        source: String,
        marker: String,
    ): Position = "/*$marker*/".let { comment -> source.positionOf(comment, "").let { Position(it.line, it.column + comment.length) } }

    private fun range(
        source: String,
        marker: String,
        name: String,
    ): Range = position(source, marker).let { Range(it, Position(it.line, it.column + name.length)) }

    private fun range(location: Location): Range =
        Range(Position(location.startLine, location.startColumn), Position(location.endLine, location.endColumn))

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
