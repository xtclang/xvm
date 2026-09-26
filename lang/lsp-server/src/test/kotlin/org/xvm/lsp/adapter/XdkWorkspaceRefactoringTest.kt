package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkWorkspaceRefactoringTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["Box", "answer", "number"])
    fun `inline types static functions and constants rename through unopened consumers`(name: String) {
        val libraryText = "module Library { class Box {} static Int answer()=number; static Int number=42; }"
        val library = source("Library", libraryText)
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; lib.Box make()=new lib.Box(); Int run()=lib.answer()+lib.number; }",
            )
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(library, libraryText).diagnostics).isEmpty()
            val position = libraryText.indexOf(name)
            assertThat(adapter.prepareRename(library, 0, position)).isNotNull()
            val edit = requireNotNull(adapter.rename(library, 0, position, "renamed"))
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(library, consumer)
            assertThat(
                edit.changes.values
                    .flatten()
                    .map { it.newText },
            ).containsOnly("renamed")
            assertThat(directory.resolve("Library.x").toFile().readText()).isEqualTo(libraryText)
        }
    }

    @Test
    fun `import cleanup preserves used aliases and comments and returns only compiled proofs`() {
        val text =
            "module App {\r\n" +
                "    import ecstasy.text.StringBuffer as Buffer;\r\n" +
                "    import ecstasy.maps.HashMap;\r\n" +
                "    // Keep this comment 😀\r\n" +
                "    Buffer make()=new Buffer();\r\n}"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val actions = adapter.getCodeActions(uri, Range(Position(0, 0), Position(5, 0)), emptyList())
            assertThat(actions.map { it.title })
                .contains("Organize imports", "Remove unused import 'HashMap'")
                .doesNotContain("Remove unused import 'Buffer'")
            actions.forEach { action ->
                val edit = requireNotNull(action.edit)
                assertThat(edit.versioned).isTrue()
                val changed =
                    edit.changes
                        .getValue(uri)
                        .sortedWith(
                            compareByDescending<TextEdit> { it.range.start.line }
                                .thenByDescending { it.range.start.column },
                        ).fold(text) { value, replacement ->
                            fun offset(position: Position): Int =
                                value.splitToSequence("\n").take(position.line).sumOf { it.length + 1 } + position.column
                            value.replaceRange(offset(replacement.range.start), offset(replacement.range.end), replacement.newText)
                        }
                assertThat(changed).contains("// Keep this comment 😀", "Buffer make()")
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
        }
    }

    @Test
    fun `binary types and broken graphs do not yield source refactoring edits`() {
        val text = "module App { package xml import xml.xtclang.org; void use(xml.Document doc) {} }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            assertThat(adapter.rename(uri, 0, text.indexOf("Document"), "Renamed")).isNull()
            source("Broken", "module Broken { Missing value; }")
            adapter.refreshDiscoveredSources()
            assertThat(adapter.getCodeActions(uri, Range(Position(0, 0), Position(0, text.length)), emptyList())).isEmpty()
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory.resolve("$name.x").toFile().let {
            it.writeText(text)
            it.canonicalFile.toURI().toString()
        }
}
