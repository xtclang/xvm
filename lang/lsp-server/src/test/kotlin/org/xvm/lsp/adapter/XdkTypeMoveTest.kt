package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

class XdkTypeMoveTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `moving a type between packages rewrites constructors and closed consumers`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box { static Int number() = 1; }")
        write("App/util/Marker.x", "class Marker {}")
        write(
            "Consumer.x",
            "module Consumer { package app import App; app.tools.Box make() = new app.tools.Box(); Int read() = app.tools.Box.number(); }",
        )
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("util.Box").doesNotContain("tools.Box")
            assertThat(read("Consumer.x")).contains("app.util.Box", "app.util.Box.number()").doesNotContain("tools.Box")
        }
    }

    @Test
    fun `moving a type preserves explicit import aliases and qualifies bare names`() {
        write("App.x", "module App { import tools.Box as Crate; Crate make() = new Crate(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/tools/Factory.x", "class Factory { Box make() = new Box(); }")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("import util.Box as Crate", "Crate make() = new Crate()")
            assertThat(read("App/tools/Factory.x")).contains("util.Box make() = new util.Box()")
        }
    }

    @Test
    fun `moving companion sources preserves resources and old package sibling bindings`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box { Helper make() = new Helper(); }")
        write("App/tools/Helper.x", "class Helper {}")
        write("App/tools/Box/Part.x", "class Part {}")
        write("App/tools/Box/data.txt", "keep this resource")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            val edit = move(adapter)
            assertThat(edit.renames).containsEntry(uri("App/tools/Box"), uri("App/util/Box"))
            assertThat(read("App/util/Box/data.txt")).isEqualTo("keep this resource")
            assertThat(read("App/util/Box/Part.x")).isEqualTo("class Part {}")
            assertThat(read("App/util/Box.x")).contains("tools.Helper")
        }
    }

    @Test
    fun `move leaves unrelated same named types and their calls unchanged`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); other.Box second() = new other.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/other/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("util.Box make() = new util.Box()", "other.Box second() = new other.Box()")
        }
    }

    @Test
    fun `target collisions or a different module refuse the entire move`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Box.x", "class Box {}")
        write("Other.x", "module Other {}")
        write("Other/Marker.x", "class Marker {}")
        session { adapter ->
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(adapter.renameFilesAsync(mapOf(uri("App/tools/Box.x") to uri("Other/Box.x"))).get(30, SECONDS)).isNull()
            assertThat(read("App.x")).contains("tools.Box")
            assertThat(directory.resolve("App/tools/Box.x")).exists()
        }
    }

    @Test
    fun `moving back to the module namespace removes package prefixes and preserves nested types`() {
        write("App.x", "module App { package other import Other; tools.Box.Part make() = new tools.Box.Part(); }")
        write("Other.x", "module Other {}")
        write("App/tools/Box.x", "class Box {}")
        write("App/tools/Box/Part.x", "static class Part {}")
        session { adapter ->
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            val requested = mapOf(uri("App/tools/Box.x") to uri("App/Box.x"))
            val edit = requireNotNull(adapter.renameFilesAsync(requested).get(30, SECONDS))
            apply(edit, requested)
            assertThat(read("App.x")).isEqualTo("module App { package other import Other; Box.Part make() = new Box.Part(); }")
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `a compiling move that would capture an unrelated bare method is refused`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools.x", "package tools { static Int number() = 1; }")
        write("App/tools/Box.x", "class Box { Int read() = number(); }")
        write("App/util.x", "package util { static Int number() = 2; }")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(read("App/tools/Box.x")).isEqualTo("class Box { Int read() = number(); }")
            // Compilation alone accepts the wrong package's number(). The refusal above must
            // come from comparing the original call target, not from a syntax/access failure.
            write("App.x", read("App.x").replace("tools.Box", "util.Box"))
            Files.move(directory.resolve("App/tools/Box.x"), directory.resolve("App/util/Box.x"))
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `occupied companion directory refuses a move before touching files`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        write("App/util/Box/data.txt", "unrelated existing resource")
        session { adapter ->
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(read("App/util/Box/data.txt")).isEqualTo("unrelated existing resource")
            assertThat(directory.resolve("App/tools/Box.x")).exists()
        }
    }

    @Test
    fun `configured source graph includes closed consumers without replacing settings`() {
        write("App.x", "module App {}")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        write("Consumer.x", "module Consumer { package app import App; import app.tools.Box; Box make() = new Box(); }")
        session { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("App", uri("App.x")),
                    XdkSourceModule("Consumer", uri("Consumer.x"), setOf("App")),
                ),
            )
            val edit = requireNotNull(adapter.renameFilesAsync(request()).get(30, SECONDS))
            apply(edit, request())
            assertThat(read("Consumer.x")).contains("import app.util.Box", "Box make() = new Box()")
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    private fun request() = mapOf(uri("App/tools/Box.x") to uri("App/util/Box.x"))

    private fun read(file: String) = Files.readString(directory.resolve(file))

    private fun move(adapter: XdkAdapter): WorkspaceEdit {
        assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        val edit = requireNotNull(adapter.renameFilesAsync(request()).get(30, SECONDS))
        assertThat(edit.versioned).isTrue()
        apply(edit, request())
        adapter.initializeWorkspace(listOf(directory.toString()))
        assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        return edit
    }

    private fun session(body: (XdkAdapter) -> Unit) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            body(adapter)
        }
    }

    private fun write(
        file: String,
        source: String,
    ) {
        directory.resolve(file).toFile().apply {
            parentFile.mkdirs()
            writeText(source)
        }
    }

    private fun uri(file: String) =
        directory
            .resolve(file)
            .toFile()
            .toURI()
            .toString()
            .removeSuffix("/")

    private fun apply(
        edit: WorkspaceEdit,
        requested: Map<String, String>,
    ) {
        edit.changes.forEach { (uri, changes) ->
            val path = Path.of(URI(uri))
            val text = Files.readString(path)

            fun offset(position: Position): Int = text.lineSequence().take(position.line).sumOf { it.length + 1 } + position.column
            Files.writeString(
                path,
                changes
                    .sortedByDescending { offset(it.range.start) }
                    .fold(text) { value, change ->
                        value.replaceRange(
                            offset(change.range.start),
                            offset(change.range.end),
                            change.newText,
                        )
                    },
            )
        }
        (edit.renames + requested).forEach { (from, to) ->
            Files.move(Path.of(URI(from)), Path.of(URI(to)))
        }
    }
}
