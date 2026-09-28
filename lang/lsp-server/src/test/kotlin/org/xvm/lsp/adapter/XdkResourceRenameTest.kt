package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class XdkResourceRenameTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["class", "package"])
    fun `member rename moves its companion tree and reverse rename restores the sources`(kind: String) {
        val original = mapOf(
            "App.x" to "module App { void accept(Box.Inner value) {} }",
            "App/Box.x" to "$kind Box {}",
            "App/Box/Inner.x" to "class Inner {}",
        )
        original.forEach { (file, text) -> source(file, text) }
        val root = uri("App.x")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(root, original.getValue("App.x")).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(root, 0, original.getValue("App.x").indexOf("Box"), "Renamed"))
            assertThat(edit.renames).hasSize(2)
            assertThat(edit.renames).containsEntry(uri("App/Box.x"), uri("App/Renamed.x"))
            assertThat(edit.renames).containsEntry(uri("App/Box").removeSuffix("/"), uri("App/Renamed").removeSuffix("/"))
            original.forEach { (file, text) -> assertThat(directory.resolve(file).toFile().readText()).isEqualTo(text) }
            apply(edit)
        }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val changed = directory.resolve("App.x").toFile().readText()
            assertThat(adapter.compile(root, changed).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(root, 0, changed.indexOf("Inner"))?.uri).isEqualTo(uri("App/Renamed/Inner.x"))
            apply(requireNotNull(adapter.rename(root, 0, changed.indexOf("Renamed"), "Box")))
        }
        original.forEach { (file, text) -> assertThat(directory.resolve(file).toFile().readText()).isEqualTo(text) }
    }

    @Test
    fun `discovered module rename updates closed imports and moves root and companion directory`() {
        val library = "module Library { Box make() = new Box(); }"
        val consumer = "module Consumer { package lib import Library; lib.Box make() = new lib.Box(); }"
        source("Library.x", library)
        source("Library/Box.x", "class Box {}")
        source("Consumer.x", consumer)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri("Library.x"), library).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri("Library.x"), 0, library.indexOf("Library"), "Renamed"))
            assertThat(edit.renames).hasSize(2)
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(uri("Library.x"), uri("Consumer.x"))
            apply(edit)
        }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val changed = directory.resolve("Consumer.x").toFile().readText()
            assertThat(changed).contains("package lib import Renamed")
            assertThat(adapter.compile(uri("Consumer.x"), changed).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(uri("Consumer.x"), 0, changed.indexOf("Box"))?.uri).isEqualTo(uri("Renamed/Box.x"))
            apply(requireNotNull(adapter.rename(uri("Renamed.x"), 0, library.indexOf("Library"), "Library")))
        }
        assertThat(directory.resolve("Library.x").toFile().readText()).isEqualTo(library)
        assertThat(directory.resolve("Consumer.x").toFile().readText()).isEqualTo(consumer)
    }

    @Test
    fun `explicit module configuration and existing destination directories prevent resource edits`() {
        val text = "module App { Box make() = new Box(); }"
        source("App.x", text)
        source("App/Box.x", "class Box { construct() {} }")
        Files.createDirectories(directory.resolve("App/Renamed"))
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("App.x"))))
            assertThat(adapter.compile(uri("App.x"), text).diagnostics).isEmpty()
            assertThat(adapter.rename(uri("App.x"), 0, text.indexOf("App"), "Changed")).isNull()
            assertThat(adapter.rename(uri("App.x"), 0, text.indexOf("Box"), "Renamed")).isNull()
            val member = directory.resolve("App/Box.x").toFile().readText()
            assertThat(adapter.rename(uri("App/Box.x"), 0, member.indexOf("construct"), "build")).isNull()
        }
    }

    @Test
    fun `type rename includes constructor uses but does not rename the construct keyword`() {
        val text = "module App { class Box { construct(Int value) {} } Box make() = new Box(1); }"
        source("App.x", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri("App.x"), text).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uri("App.x"), 0, text.indexOf("Box"), "Renamed"))
            assertThat(edit.changes.getValue(uri("App.x"))).hasSize(3)
            apply(edit)
            val changed = directory.resolve("App.x").toFile().readText()
            assertThat(changed).contains("construct(Int value)", "new Renamed(1)")
            assertThat(adapter.compile(uri("App.x"), changed).diagnostics).isEmpty()
            assertThat(adapter.rename(uri("App.x"), 0, changed.indexOf("value"), "argument")).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "interface Api { Int read(); } class Forward(Api target) delegates Api(target) {}",
        "class Base { Int read = 1; } mixin Loud into Base { @Override Int read.get() = 2; } class Host extends Base incorporates Loud {}",
        "class Holder { @Lazy Int read.calc() = 1; }",
    ])
    fun `unsupported delegation mixin and annotated property families remain refused`(body: String) {
        val text = "module App { $body }"
        source("App.x", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri("App.x"), text).diagnostics).isEmpty()
            assertThat(adapter.rename(uri("App.x"), 0, text.indexOf("read"), "changed")).isNull()
            assertThat(directory.resolve("App.x").toFile().readText()).isEqualTo(text)
        }
    }

    private fun apply(edit: WorkspaceEdit) {
        edit.changes.forEach { (uri, edits) ->
            val file = Path.of(URI(uri)).toFile()
            val original = file.readText()
            fun offset(position: Position) = original.lineSequence().take(position.line).sumOf { it.length + 1 } + position.column
            file.writeText(edits.sortedByDescending { offset(it.range.start) }.fold(original) { text, change ->
                text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
            })
        }
        edit.renames.forEach { (from, to) -> Files.move(Path.of(URI(from)), Path.of(URI(to))) }
    }

    private fun source(file: String, text: String) = directory.resolve(file).toFile().apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun uri(file: String): String = directory.resolve(file).toFile().canonicalFile.toURI().toString()
}
