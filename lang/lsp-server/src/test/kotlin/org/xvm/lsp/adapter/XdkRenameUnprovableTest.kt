package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

/**
 * A rename's proof where the semantic model cannot prove every binding, such as Element in CompileType.Element, a formal
 * type of a method's type parameter: the rename is proven, unless such a site spells the name it changes. An occurrence
 * that the model leaves unbound is compared the same way.
 */
class XdkRenameUnprovableTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename is proven in a module with a binding the model cannot prove`() {
        val uri = source("Pairs", PAIRS)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, PAIRS).diagnostics).isEmpty()
            val at = PAIRS.positionOf("Int total", "total")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width")) { "No rename of total" }
            assertThat(apply(PAIRS, edit.changes.getValue(uri))).isEqualTo(PAIRS.replace("total", "width"))
        }
    }

    @Test
    fun `a rename to a name that an unprovable binding spells is refused, as nothing shows it is not captured`() {
        val uri = source("Pairs", PAIRS)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, PAIRS).diagnostics).isEmpty()
            val at = PAIRS.positionOf("Int total", "total")
            assertThat(adapter.rename(uri, at.line, at.column, "Element")).isNull()
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
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }

    private companion object {
        // the names of Pairing's return values are occurrences that the model leaves without a symbol
        const val PAIRS =
            "module Pairs { <CompileType extends List> Int count(CompileType list) { " +
                "CompileType.Element[] items = list.toArray(); return items.size; } Int total() = 2; Int use() = total(); }"
    }
}
