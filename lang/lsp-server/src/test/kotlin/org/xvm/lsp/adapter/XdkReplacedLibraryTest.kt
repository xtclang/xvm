package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/**
 * A workspace whose own module has a bundled XDK library's name, which then replaces the bundled one, as the XVM
 * repository's replace them all ([org.xvm.lsp.adapter.xdk.XdkLibraryReplacement]): the server treats the library as
 * the workspace's sources, not as the bundle.
 */
class XdkReplacedLibraryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename of a workspace library's public declaration edits the modules that import it`() {
        val json = "module json.xtclang.org { class Answer {} }"
        val app = "module App { package json import json.xtclang.org; json.Answer? find() = Null; }"
        val jsonUri = source("json", json)
        val appUri = source("App", app)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule(JSON, jsonUri),
                    XdkSourceModule("App", appUri, setOf(JSON)),
                ),
            )
            assertThat(adapter.compile(jsonUri, json).diagnostics).isEmpty()
            // App compiles against the workspace's json, which has Answer, and not the bundled one
            assertThat(adapter.compile(appUri, app).diagnostics).isEmpty()
            val at = json.positionOf("class Answer", "Answer")
            val edit = requireNotNull(adapter.rename(jsonUri, at.line, at.column, "Reply")) { "No rename of the workspace's json" }
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(jsonUri, appUri)
            assertThat(apply(json, edit.changes.getValue(jsonUri))).isEqualTo(json.replace("Answer", "Reply"))
            assertThat(apply(app, edit.changes.getValue(appUri))).isEqualTo(app.replace("Answer", "Reply"))
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
        const val JSON = "json.xtclang.org"
    }
}
