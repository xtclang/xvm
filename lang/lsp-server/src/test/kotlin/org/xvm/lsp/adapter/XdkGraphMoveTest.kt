package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

class XdkGraphMoveTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `container proposal remaps explicit source and resource roots while preserving dependencies`() {
        write("old/App.x", "module App { Box make() = new Box(); static String text() = $./data.txt; }")
        write("old/App/Box.x", "class Box {}")
        write("old/assets/data.txt", "moved resource")
        write("fallback/data.txt", "fallback resource")
        write("Consumer.x", "module Consumer { package app import App; app.Box make() = new app.Box(); String read() = app.text(); }")
        session { adapter ->
            val graph =
                listOf(
                    XdkSourceModule("App", uri("old/App.x"), resourceRoots = listOf(uri("old/assets"), uri("fallback"))),
                    XdkSourceModule("Consumer", uri("Consumer.x"), setOf("App"), emptyList()),
                )
            adapter.replaceSourceModules(graph)
            val moves = mapOf(uri("old") to uri("moved"))
            assertThat(adapter.renameFilesAsync(moves).get(30, SECONDS)).isNull()
            val proposal = requireNotNull(adapter.renameFilesProposalAsync(moves).get(30, SECONDS))
            assertThat(proposal.previousSourceModules!!.map { it.uri }).containsExactlyInAnyOrderElementsOf(graph.map { it.uri })
            val after = requireNotNull(proposal.sourceModules)
            val app = after.single { it.name == "App" }
            assertThat(app.root).isEqualTo(directory.resolve("moved/App.x").toFile())
            assertThat(
                app.resourceFiles,
            ).containsExactly(directory.resolve("moved/assets").toFile(), directory.resolve("fallback").toFile())
            assertThat(after.single { it.name == "Consumer" }.resourceRoots).isEmpty()
            assertThat(proposal.edit.renames).containsExactlyEntriesOf(moves)
            assertThat(directory.resolve("moved")).doesNotExist()
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            apply(proposal.edit)
            adapter.replaceSourceModules(after)
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(Files.readString(directory.resolve("moved/assets/data.txt"))).isEqualTo("moved resource")
        }
    }

    @Test
    fun `moving only the module root carries its companion and pins default resources left behind`() {
        write("old/App.x", "module App { Box make() = new Box(); static String text() = $./data.txt; }")
        write("old/App/Box.x", "class Box {}")
        write("old/data.txt", "keep original resource lookup")
        Files.createDirectories(directory.resolve("target"))
        session { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("old/App.x"))))
            val moves = mapOf(uri("old/App.x") to uri("target/App.x"))
            val proposal = requireNotNull(adapter.renameFilesProposalAsync(moves).get(30, SECONDS))
            assertThat(proposal.edit.renames).containsEntry(uri("old/App"), uri("target/App"))
            assertThat(proposal.sourceModules!!.single().resourceFiles).containsExactly(directory.resolve("old").toFile())
            assertThat(proposal.previousSourceModules!!.single().resourceRoots).isNull()
            apply(proposal.edit)
            adapter.replaceSourceModules(proposal.sourceModules!!)
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(directory.resolve("old/data.txt")).exists()
        }
    }

    @Test
    fun `batch relocation remaps separately moved resources and retains disabled and missing roots`() {
        write("old/App.x", "module App { static String text() = $./data.txt; }")
        write("assets/data.txt", "resource")
        write("Disabled.x", "module Disabled {}")
        session { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("App", uri("old/App.x"), resourceRoots = listOf(uri("assets"), uri("missing"))),
                    XdkSourceModule("Disabled", uri("Disabled.x"), resourceRoots = emptyList()),
                ),
            )
            val moves = mapOf(uri("old") to uri("new"), uri("assets") to uri("new-assets"))
            val proposal = requireNotNull(adapter.renameFilesProposalAsync(moves).get(30, SECONDS))
            assertThat(proposal.sourceModules!!.single { it.name == "App" }.resourceFiles)
                .containsExactly(directory.resolve("new-assets").toFile(), directory.resolve("missing").toFile())
            assertThat(proposal.sourceModules!!.single { it.name == "Disabled" }.resourceRoots).isEmpty()
            apply(proposal.edit)
            adapter.replaceSourceModules(proposal.sourceModules!!)
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `resource lookup removed by a companion move refuses without reading the old path as future state`() {
        write("old/App.x", "module App { static String text() = $./App/data.txt; }")
        write("old/App/data.txt", "would be left under a different relative path")
        Files.createDirectories(directory.resolve("target"))
        session { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("old/App.x"))))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(adapter.renameFilesProposalAsync(mapOf(uri("old/App.x") to uri("target/App.x"))).get(30, SECONDS)).isNull()
            assertThat(directory.resolve("old/App/data.txt")).exists()
            assertThat(directory.resolve("target/App.x")).doesNotExist()
        }
    }

    @Test
    fun `a moved resource must not silently select different fallback bytes in an independent module`() {
        write("old/App.x", "module App {}")
        write("old/App/data.txt", "original")
        write("fallback/App/data.txt", "different fallback")
        write("Independent.x", "module Independent { static String text() = $./App/data.txt; }")
        Files.createDirectories(directory.resolve("target"))
        session { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("App", uri("old/App.x")),
                    XdkSourceModule("Independent", uri("Independent.x"), resourceRoots = listOf(uri("old"), uri("fallback"))),
                ),
            )
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(adapter.renameFilesProposalAsync(mapOf(uri("old/App.x") to uri("target/App.x"))).get(30, SECONDS)).isNull()
            assertThat(directory.resolve("old/App/data.txt")).exists()
        }
    }

    @Test
    fun `uncaptured incoming resources cannot be approved using the old fallback lookup`() {
        write("old/App.x", "module App { static String text() = $./data.txt; }")
        write("assets/keep.txt", "existing root")
        write("fallback/data.txt", "original")
        write("incoming/data.txt", "would shadow the fallback")
        session { adapter ->
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("App", uri("old/App.x"), resourceRoots = listOf(uri("assets"), uri("fallback")))),
            )
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            val moves = mapOf(uri("old") to uri("moved"), uri("incoming/data.txt") to uri("assets/data.txt"))
            assertThat(adapter.renameFilesProposalAsync(moves).get(30, SECONDS)).isNull()
            assertThat(directory.resolve("assets/data.txt")).doesNotExist()
        }
    }

    @ParameterizedTest
    @CsvSource(
        "String,$./data.txt",
        "Byte[],#./data.txt",
        "File,File:./data.txt",
        "Directory,Directory:./nested/",
        "FileStore,FileStore:./nested/",
    )
    fun `container relocation preserves every embedded resource representation`(
        type: String,
        literal: String,
    ) {
        write(
            "old/App.x",
            "module App { import ecstasy.fs.File; import ecstasy.fs.Directory; import ecstasy.fs.FileStore; " +
                "static $type value() = $literal; }",
        )
        write("old/assets/data.txt", "resource bytes")
        write("old/assets/nested/data.txt", "nested resource")
        session { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("old/App.x"), resourceRoots = listOf(uri("old/assets")))))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            val proposal = requireNotNull(adapter.renameFilesProposalAsync(mapOf(uri("old") to uri("new"))).get(30, SECONDS))
            apply(proposal.edit)
            adapter.replaceSourceModules(requireNotNull(proposal.sourceModules))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `interacting companion moves and directories outside module ownership refuse before replay`() {
        write("old/App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("old/App/tools/Box.x", "class Box {}")
        write("old/App/util/Marker.x", "class Marker {}")
        Files.createDirectories(directory.resolve("target"))
        session { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("old/App.x"))))
            listOf(
                mapOf(uri("old/App.x") to uri("target/App.x"), uri("old/App/tools/Box.x") to uri("old/App/util/Box.x")),
                mapOf(uri("old/App/tools") to uri("target/tools")),
                // VS Code participates again when applying the first proposal's companion move.
                mapOf(uri("old/App") to uri("target/App")),
            ).forEach { moves -> assertThat(adapter.renameFilesProposalAsync(moves).get(30, SECONDS)).isNull() }
            assertThat(directory.resolve("old/App/tools/Box.x")).exists()
        }
    }

    @Test
    fun `existing destination and overlapping requests refuse a graph transaction`() {
        write("old/App.x", "module App {}")
        write("taken/Keep.x", "module Keep {}")
        session { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", uri("old/App.x"))))
            listOf(
                mapOf(uri("old") to uri("taken")),
                mapOf(uri("old") to uri("new"), uri("old/App.x") to uri("elsewhere/App.x")),
            ).forEach { moves -> assertThat(adapter.renameFilesProposalAsync(moves).get(30, SECONDS)).isNull() }
            assertThat(directory.resolve("old/App.x")).exists()
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

    private fun apply(edit: WorkspaceEdit) {
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
        edit.renames.forEach { (from, to) ->
            Files.move(Path.of(URI(from)), Path.of(URI(to)))
        }
    }
}
