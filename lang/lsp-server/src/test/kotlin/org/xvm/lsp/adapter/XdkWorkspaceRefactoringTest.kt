package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
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

    @Test
    fun `property families rename declarations and uses without changing accessor names or unrelated properties`() {
        val text =
            "module Library { class Base { Int value { Int get()=1; void set(Int value) {} } } " +
                "class Child extends Base { @Override Int value { Int get()=2; } } class Other { Int value=3; } }"
        val library = source("Library", text)
        val consumer =
            source(
                "Consumer",
                "module Consumer { package lib import Library; Int run(lib.Base base, lib.Child child)=base.value+child.value; }",
            )
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(library, text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(library, 0, text.indexOf("value"), "amount"))
            assertThat(edit.changes.getValue(library)).hasSize(2)
            assertThat(edit.changes.getValue(consumer)).hasSize(2)
            assertThat(edit.renames).isEmpty()
            val changed = apply(text, edit.changes.getValue(library))
            assertThat(changed).contains("Int get()=1", "void set(Int value)", "Other { Int value=3")
            assertThat(adapter.compile(library, changed).diagnostics).isEmpty()
            assertThat(
                adapter
                    .compile(
                        consumer,
                        apply(directory.resolve("Consumer.x").toFile().readText(), edit.changes.getValue(consumer)),
                    ).diagnostics,
            ).isEmpty()
        }
    }

    @Test
    fun `property renames reject accidental capture of an existing derived property`() {
        val text = "module Library { class Base { Int value=1; } class Child extends Base { Int amount=2; } }"
        val library = source("Library", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(library, text).diagnostics).isEmpty()
            assertThat(adapter.rename(library, 0, text.indexOf("value"), "amount")).isNull()
        }
    }

    @Test
    fun `member type rename proves new source membership and returns a nonoverwriting file move`() {
        val text = "module App { Item make()=new Item(); }"
        val uri = source("App", text)
        val member =
            directory.resolve("App/Item.x").toFile().also {
                it.parentFile.mkdirs()
                it.writeText("class Item {}")
            }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("Item"), "Renamed"))
            assertThat(edit.renames).containsExactlyEntriesOf(
                mapOf(
                    member.canonicalFile.toURI().toString() to
                        member.parentFile
                            .resolve("Renamed.x")
                            .canonicalFile
                            .toURI()
                            .toString(),
                ),
            )
            assertThat(edit.changes).hasSize(2)
            assertThat(member.readText()).isEqualTo("class Item {}")
            member.parentFile.resolve("Renamed.x").writeText("class Renamed {}")
            assertThat(adapter.rename(uri, 0, text.indexOf("Item"), "Renamed")).isNull()
        }
    }

    @Test
    fun `explicit import aliases rename locally while the imported binary identity stays unchanged`() {
        val text = "module App { import ecstasy.text.StringBuffer as Buffer; Buffer make()=new Buffer(); }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.indexOf("as Buffer") + 3
            assertThat(adapter.prepareRename(uri, 0, at)?.placeholder).isEqualTo("Buffer")
            val edit = requireNotNull(adapter.rename(uri, 0, at, "Builder"))
            assertThat(edit.changes.getValue(uri)).hasSize(3)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("import ecstasy.text.StringBuffer as Builder", "Builder make()=new Builder()")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `alias renames reject a new name that captures an existing binding`() {
        val text = "module App { import ecstasy.text.StringBuffer as Buffer; Buffer make()=new Buffer(); String text=\"ok\"; }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            assertThat(adapter.rename(uri, 0, text.indexOf("as Buffer") + 3, "String")).isNull()
        }
    }

    @Test
    fun `auto import repairs a missing bundled type without changing known bindings`() {
        val text = "module App { String text=\"ok\"; Document echo(Document doc)=doc; }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isNotEmpty()
            val at = text.indexOf("Document")
            val actions = adapter.getCodeActions(uri, Range(Position(0, at), Position(0, at)), emptyList())
            val action = actions.single { it.title == "Import 'Document' from xml.xtclang.org" }
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("import xml.Document;", "String text=\"ok\"")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `auto import proves a newly discovered source dependency and rejects inaccessible candidates`() {
        source("Library", "module Library { class Widget {} private class Hidden {} }")
        val text = "module App { Widget make()=new Widget(); }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isNotEmpty()
            val at = text.indexOf("Widget")
            val action =
                adapter
                    .getCodeActions(uri, Range(Position(0, at), Position(0, at)), emptyList())
                    .single { it.title == "Import 'Widget' from Library" }
            val changed = apply(text, requireNotNull(action.edit).changes.getValue(uri))
            assertThat(changed).contains("package library import Library;", "import library.Widget;")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val hidden = text.replace("Widget", "Hidden")
            assertThat(adapter.compile(uri, hidden).diagnostics).isNotEmpty()
            assertThat(adapter.getCodeActions(uri, Range(Position(0, 0), Position(0, hidden.length)), emptyList())).isEmpty()
        }
    }

    @Test
    fun `auto import withholds edits when a separate error prevents a complete proof`() {
        val text = "module App { Document echo(Document doc)=doc; Missing broken; }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isNotEmpty()
            assertThat(adapter.getCodeActions(uri, Range(Position(0, 0), Position(0, text.length)), emptyList())).isEmpty()
        }
    }

    @Test
    fun `auto import offers separate proven choices and respects explicit dependency configuration`() {
        val first = source("First", "module First { class Widget {} }")
        source("Second", "module Second { class Widget {} }")
        val text = "module App { Widget make()=new Widget(); }"
        val uri = source("App", text)
        val range = Range(Position(0, 13), Position(0, 19))
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isNotEmpty()
            assertThat(adapter.getCodeActions(uri, range, emptyList()).map { it.title })
                .containsExactly("Import 'Widget' from First", "Import 'Widget' from Second")
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri), XdkSourceModule("First", first)))
            assertThat(adapter.compile(uri, text).diagnostics).isNotEmpty()
            assertThat(adapter.getCodeActions(uri, range, emptyList())).isEmpty()
        }
    }

    @Test
    fun `alias use rename preserves a nested alias with the same spelling`() {
        val text =
            "module App { import ecstasy.text.StringBuffer as Buffer; Buffer make()=new Buffer(); " +
                "class Nested { import ecstasy.text.StringBuffer as Buffer; Buffer make()=new Buffer(); } }"
        val uri = source("App", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("Buffer make"), "Builder"))
            assertThat(edit.changes.getValue(uri)).hasSize(3)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).endsWith("class Nested { import ecstasy.text.StringBuffer as Buffer; Buffer make()=new Buffer(); } }")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedByDescending { it.range.start.column }
            .fold(text) { value, edit -> value.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText) }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory.resolve("$name.x").toFile().let {
            it.writeText(text)
            it.canonicalFile.toURI().toString()
        }
}
