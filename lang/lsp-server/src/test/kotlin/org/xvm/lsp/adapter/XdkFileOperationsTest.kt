package org.xvm.lsp.adapter

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule

class XdkFileOperationsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `batch file rename proves the combined edits and leaves requested resource operations to host`() {
        write("App.x", "module App { Box make() = new Box(); Other second() = new Other(); }")
        write("App/Box.x", "class Box {}")
        write("App/Other.x", "class Other {}")
        session { adapter ->
            val moves =
                mapOf(
                    uri("App/Box.x") to uri("App/Crate.x"),
                    uri("App/Other.x") to uri("App/Second.x"),
                )
            val edit = requireNotNull(adapter.renameFilesAsync(moves).get(30, SECONDS))
            assertThat(edit.versioned).isTrue()
            assertThat(edit.renames).isEmpty()
            assertThat(Files.readString(directory.resolve("App.x"))).contains("new Box()")
            apply(edit, moves)
            assertThat(Files.readString(directory.resolve("App.x")))
                .contains("new Crate()", "new Second()")
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `implicit package folder rename updates references and all descendants`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        session { adapter ->
            val moves = mapOf(uri("App/tools") to uri("App/util"))
            val edit = requireNotNull(adapter.renameFilesAsync(moves).get(30, SECONDS))
            assertThat(edit.renames).isEmpty()
            apply(edit, moves)
            assertThat(Files.readString(directory.resolve("App.x")))
                .contains("util.Box")
                .doesNotContain("tools.Box")
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `discovered module file rename updates imports and returns only companion move`() {
        write("Library.x", "module Library {}")
        write("Library/Box.x", "class Box {}")
        write(
            "Consumer.x",
            "module Consumer { package lib import Library; lib.Box make() = new lib.Box(); }",
        )
        session { adapter ->
            val moves = mapOf(uri("Library.x") to uri("Renamed.x"))
            val edit = requireNotNull(adapter.renameFilesAsync(moves).get(30, SECONDS))
            assertThat(edit.renames.keys.map { Path.of(URI(it)).fileName.toString() })
                .containsExactly("Library")
            apply(edit, moves)
            assertThat(Files.readString(directory.resolve("Consumer.x")))
                .contains("package lib import Renamed")
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `moving a module container without changing names preserves the discovered graph`() {
        write("old/App.x", "module App { Box make() = new Box(); }")
        write("old/App/Box.x", "class Box {}")
        session { adapter ->
            val moves = mapOf(uri("old") to uri("moved"))
            val edit = requireNotNull(adapter.renameFilesAsync(moves).get(30, SECONDS))
            assertThat(edit.changes).isEmpty()
            apply(edit, moves)
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `configured root moves collisions symlinks and overlapping requests are refused without writes`() {
        write("App.x", "module App { Box make() = new Box(); }")
        write("App/Box.x", "class Box {}")
        write("App/Occupied.x", "class Occupied {}")
        session { adapter ->
            assertThat(
                    adapter
                        .renameFilesAsync(mapOf(uri("App/Box.x") to uri("App/Occupied.x")))
                        .get(30, SECONDS)
                )
                .isNull()
            assertThat(
                    adapter
                        .renameFilesAsync(
                            mapOf(uri("App") to uri("Other"), uri("App/Box.x") to uri("App/New.x"))
                        )
                        .get(30, SECONDS)
                )
                .isNull()
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("App.x"))))
            assertThat(
                    adapter
                        .renameFilesAsync(mapOf(uri("App.x") to uri("Renamed.x")))
                        .get(30, SECONDS)
                )
                .isNull()
            Files.createSymbolicLink(directory.resolve("alias.x"), directory.resolve("App/Box.x"))
            assertThat(
                    adapter
                        .renameFilesAsync(mapOf(uri("alias.x") to uri("renamed.x")))
                        .get(30, SECONDS)
                )
                .isNull()
            assertThat(Files.readString(directory.resolve("App.x"))).contains("new Box()")
        }
    }

    private fun session(body: (XdkAdapter) -> Unit) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            body(adapter)
        }
    }

    private fun write(file: String, source: String) {
        directory.resolve(file).toFile().apply {
            parentFile.mkdirs()
            writeText(source)
        }
    }

    private fun uri(file: String) =
        directory.resolve(file).toFile().toURI().toString().removeSuffix("/")

    private fun apply(edit: WorkspaceEdit, requested: Map<String, String>) {
        edit.changes.forEach { (uri, changes) ->
            val path = Path.of(URI(uri))
            val text = Files.readString(path)
            fun offset(position: Position): Int =
                text.lineSequence().take(position.line).sumOf { it.length + 1 } + position.column
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
