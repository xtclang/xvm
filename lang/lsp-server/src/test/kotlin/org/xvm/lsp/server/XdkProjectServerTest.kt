package org.xvm.lsp.server

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.Slow
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.hookedAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.logged
import org.xvm.lsp.protocol.BuildParams
import org.xvm.lsp.protocol.GradleProjectsParams
import org.xvm.lsp.protocol.ReplacedLibrariesParams
import org.xvm.lsp.protocol.RunExitedParams
import org.xvm.lsp.protocol.RunOutputParams
import org.xvm.lsp.protocol.RunParams
import org.xvm.lsp.protocol.XtcClientProtocol
import org.xvm.lsp.run.LIVELOCK_LIMITS
import org.xvm.testing.awaitAsserted
import org.xvm.testing.awaitUntil
import org.xvm.testing.awaited
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exercises dependency/resource edits through server notifications and open-buffer overlays. Failed or
 * deleted inputs must retire stale consumer facts; repair and closing an overlay restore the appropriate
 * analysis.
 */
class XdkProjectServerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `resource deletion and repair refresh an unchanged open consumer`() {
        CompilerTestSupport.configure()
        val library = file("Library", "module Library { static String value() = $./data.txt; }")
        val consumerText =
            "module Consumer { package lib import Library; String run() = lib.value(); }"
        val consumer = file("Consumer", consumerText)
        val resources = directory.resolve("assets").toFile().apply { mkdirs() }
        val resource = resources.resolve("data.txt").apply { writeText("template") }
        Session(library, consumer).use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule(
                        "Library",
                        library.toURI().toString(),
                        resourceRoots = listOf(resources.toURI().toString()),
                    ),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                ),
            )
            session.open(consumer, consumerText, 1)
            session.expect(consumer, 0, 1, false)
            val deletion = session.published.size
            Files.delete(resource.toPath())
            session.watched(resource, FileChangeType.Deleted)
            session.expect(consumer, deletion, 1, true, "DEPENDENCY-FAILED")
            val repair = session.published.size
            resource.writeText("repaired")
            session.watched(resource, FileChangeType.Created)
            session.expect(consumer, repair, 1, false)
        }
    }

    @Test
    fun `unsaved dependency edits recompile an unchanged consumer and closing restores disk`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(library, INCOMPATIBLE, 2)
            session.expect(consumer, mark, 7, true)
            session.expect(library, mark, 2, false)
            assertThat(library.readText()).isEqualTo(LIBRARY)
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            val target =
                session.documents
                    .definition(
                        DefinitionParams(
                            TextDocumentIdentifier(consumer.toURI().toString()),
                            Position(0, CONSUMER.indexOf("value")),
                        ),
                    ).awaited()
                    .left
                    .single()
            assertThat(target.uri).isEqualTo(library.toURI().toString())
            mark = session.published.size
            session.change(library, INCOMPATIBLE, 4)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            session.closeDocument(library)
            session.expect(consumer, mark, 7, false)
            mark = session.published.size
            session.open(library, INCOMPATIBLE, 1)
            session.expect(consumer, mark, 7, true)
        }
    }

    @Test
    fun `failed dependency publishes its own diagnostics and blocks old consumer facts until correction`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(library, "module Library { MissingType broken; }", 2)
            session.expect(library, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(
                session.adapter.findDefinition(
                    consumer.toURI().toString(),
                    0,
                    CONSUMER.indexOf("value"),
                ),
            ).isNull()
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            session.expect(library, mark, 3, false)
        }
    }

    @Test
    fun `closed dependency files are rebuilt on filesystem events including deletion and restoration`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            library.writeText(INCOMPATIBLE)
            session.watched(library, FileChangeType.Changed)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            assertThat(library.delete()).isTrue()
            session.watched(library, FileChangeType.Deleted)
            session.expect(library, mark, null, true, "SOURCE-UNAVAILABLE")
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            mark = session.published.size
            library.writeText(LIBRARY)
            session.watched(library, FileChangeType.Created)
            session.expect(consumer, mark, 7, false)
            session.expect(library, mark, null, false)
        }
    }

    @Test
    fun `unsaved and removed dependency members update consumers and clear deleted source diagnostics`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        val member = directory.resolve("Library/Extra.x").toFile()
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(member, "class Extra { MissingType broken; }", 2)
            session.expect(member, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(member.exists()).isFalse()
            mark = session.published.size
            session.closeDocument(member)
            session.expect(consumer, mark, 7, false)
            member.parentFile.mkdirs()
            member.writeText("class Extra { MissingType broken; }")
            mark = session.published.size
            session.watched(member, FileChangeType.Created)
            session.expect(member, mark, null, true)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            assertThat(member.delete()).isTrue()
            session.watched(member, FileChangeType.Deleted)
            session.expect(consumer, mark, 7, false)
            session.expect(member, mark, null, false)
        }
    }

    @Test
    fun `transitive source changes rebuild consumers while an unrelated session stays available`() {
        val library = file("Library", LIBRARY)
        val bridge =
            file(
                "Bridge",
                "module Bridge { package lib import Library; static Int value() = lib.value(); }",
            )
        val consumer = file("Consumer", CONSUMER.replace("import Library", "import Bridge"))
        val unrelated = file("Unrelated", "module Unrelated { Int value = 1; }")
        Session(library, consumer, bridge).use { session ->
            session.open(consumer, consumer.readText(), 7)
            session.expect(consumer, 0, 7, false)
            session.open(unrelated, unrelated.readText(), 1)
            session.expect(unrelated, 0, 1, false)
            val cached = session.adapter.getCachedResult(unrelated.toURI().toString())
            var mark = session.published.size
            session.open(library, INCOMPATIBLE, 2)
            session.expect(bridge, mark, null, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(session.adapter.getCachedResult(unrelated.toURI().toString()))
                .isEqualTo(cached)
            assertThat(session.published.drop(mark)).noneMatch {
                it.uri == unrelated.toURI().toString()
            }
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            session.expect(bridge, mark, null, false)
        }
    }

    @Test
    fun `binary replacement rebuilds open source dependencies before their consumers`() {
        CompilerTestSupport.configure()

        fun artifact(type: String) =
            EmbeddingSupport
                .instance()
                .compileModule(
                    Source(
                        "module Binary { static $type value() =" +
                            (if (type == "Int") "1" else "\"text\"") +
                            "; }",
                        "file:///Binary.x",
                    ),
                    null,
                    ErrorList(),
                ).toDependency()
        val first = artifact("Int")
        val incompatible = artifact("String")
        val library =
            file(
                "Library",
                "module Library { package base import Binary; static Int value() = base.value(); }",
            )
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.server.replaceCompilerDependencies(listOf(first))
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            session.open(library, library.readText(), 2)
            session.expect(library, 0, 2, false)
            var mark = session.published.size
            session.server.replaceCompilerDependencies(listOf(incompatible))
            session.expect(library, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            mark = session.published.size
            session.server.replaceCompilerDependencies(listOf(first))
            session.expect(library, mark, 2, false)
            session.expect(consumer, mark, 7, false)
        }
    }

    @Test
    fun `closing one consumer does not clear shared dependency errors owned by another`() {
        val library = file("Library", "module Library { MissingType broken; }")
        val consumer = file("Consumer", CONSUMER)
        val other = file("Other", CONSUMER.replace("module Consumer", "module Other"))
        Session(library, consumer).use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                    XdkSourceModule("Other", other.toURI().toString(), setOf("Library")),
                ),
            )
            session.open(consumer, consumer.readText(), 7)
            session.expect(consumer, 0, 7, true)
            session.open(other, other.readText(), 1)
            session.expect(other, 0, 1, true)
            val mark = session.published.size
            session.closeDocument(consumer)
            assertThat(session.published.drop(mark)).noneMatch {
                it.uri == library.toURI().toString() && it.diagnostics.isEmpty()
            }
        }
    }

    @Test
    fun `discovered modules that share a name stay out of the graph with a warning each, and the rest is analyzed`() {
        val workspace =
            directory
                .toRealPath()
                .resolve("twins")
                .toFile()
                .also { it.mkdirs() }
        val library = workspace.resolve("Library.x").also { it.writeText(LIBRARY) }
        val consumer = workspace.resolve("Consumer.x").also { it.writeText(CONSUMER) }
        val (first, second) =
            listOf("one", "two").map { folder ->
                workspace.resolve("$folder/Twin.x").also {
                    it.parentFile.mkdirs()
                    it.writeText("module Twin {}")
                }
            }
        Session(library, consumer, automatic = true).use { session ->
            // the modules that import each other are analyzed as before, without a graph error for the twins
            session.open(consumer, CONSUMER, 1)
            session.expect(consumer, 0, 1, false)
            session.open(first, first.readText(), 1)
            awaitAsserted {
                val report = session.published.lastOrNull { it.uri == first.toURI().toString() && it.version == 1 }
                assertThat(report).describedAs(session.published.toString()).isNotNull()
                assertThat(report!!.diagnostics).singleElement().satisfies({ warning ->
                    assertThat(warning.severity).isEqualTo(DiagnosticSeverity.Warning)
                    assertThat(warning.code?.left).isEqualTo("SOURCE-GRAPH")
                    assertThat(warning.message.left).contains(second.path)
                })
            }
            // once the host names the modules itself, no discovered duplicate remains to warn of
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                ),
            )
            val mark = session.published.size
            session.change(first, first.readText(), 2)
            session.expect(first, mark, 2, false)
        }
    }

    @Test
    fun `the client hears each module's Gradle project from the imported build model, and none once there is no model`() {
        val workspace =
            directory
                .toRealPath()
                .resolve("gradle")
                .toFile()
                .also { it.mkdirs() }
        val json = workspace.resolve("lib/Json.x").also { it.parentFile.mkdirs() }.also { it.writeText("module Json {}") }
        val entry =
            mapOf(
                "projectId" to "lib",
                "sourceSet" to "main",
                "sourceFiles" to listOf(json.toURI().toString()),
                "moduleRoots" to listOf(json.toURI().toString()),
                "resourceRoots" to emptyList<String>(),
                "projectDependencies" to emptyList<String>(),
                "modulePath" to emptyList<String>(),
                "buildTreePath" to ":xdk:lib-json",
            )
        val heard = CopyOnWriteArrayList<Map<String, String>>()
        val client =
            object : XtcClientProtocol, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {}

                override fun runExited(params: RunExitedParams) {}

                override fun gradleProjects(params: GradleProjectsParams) {
                    heard += params.projects
                }
            }
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            server
                .initialize(
                    editorInitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "gradle"))
                        initializationOptions =
                            mapOf(
                                "xtcCompiler" to mapOf("buildModels" to listOf(mapOf("schemaVersion" to 1, "sourceSets" to listOf(entry)))),
                            )
                    },
                ).awaited()
            awaitAsserted { assertThat(heard.lastOrNull()).isEqualTo(mapOf("Json" to ":xdk:lib-json")) }
            // the workspace's conventions, without a model, name no project
            server.changeCompilerConfig(JsonParser.parseString("""{"xtc": {"compiler": {"sourceModules": null}}}"""))
            awaitAsserted { assertThat(heard.lastOrNull()).isEmpty() }
        }
    }

    @Test
    @Slow
    fun `an embedded run takes xtcRun's settings from the imported build model, and none once there is no model`() {
        CompilerTestSupport.configure()
        val workspace =
            directory
                .toRealPath()
                .resolve("gradled")
                .toFile()
                .also { it.mkdirs() }
        val app = workspace.resolve("app/App.x").also { it.parentFile.mkdirs() }.also { it.writeText(GRADLED_APP) }
        val run =
            mapOf(
                "jvmArgs" to listOf("-Dxtc.gradle.origin=model"),
                "modules" to mapOf("App" to mapOf("methodName" to "greet", "moduleArgs" to listOf("from the model"))),
            )
        val entry =
            mapOf(
                "projectId" to "app",
                "sourceSet" to "main",
                "sourceFiles" to listOf(app.toURI().toString()),
                "moduleRoots" to listOf(app.toURI().toString()),
                "resourceRoots" to emptyList<String>(),
                "projectDependencies" to emptyList<String>(),
                "modulePath" to emptyList<String>(),
                "buildTreePath" to ":app",
                "run" to run,
                "test" to mapOf("jvmArgs" to emptyList<String>()),
            )
        val heard = CopyOnWriteArrayList<Map<String, String>>()
        val output = StringBuffer()
        val exits = LinkedBlockingQueue<RunExitedParams>()
        val client =
            object : XtcClientProtocol, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {
                    output.append(params.text)
                }

                override fun runExited(params: RunExitedParams) {
                    exits.add(params)
                }

                override fun gradleProjects(params: GradleProjectsParams) {
                    heard += params.projects
                }
            }
        XtcLanguageServer(XdkAdapter(), workerLimits = LIVELOCK_LIMITS).use { server ->
            server.connect(client)
            server
                .initialize(
                    editorInitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "gradled"))
                        initializationOptions =
                            mapOf(
                                "xtcCompiler" to mapOf("buildModels" to listOf(mapOf("schemaVersion" to 1, "sourceSets" to listOf(entry)))),
                            )
                    },
                ).awaited()
            awaitAsserted { assertThat(heard.lastOrNull()).isEqualTo(mapOf("App" to ":app")) }
            assertThat(server.run(RunParams(module = "App", runId = "model")).awaited().runId).isEqualTo("model")
            assertThat(exits.awaited()!!.exitCode).describedAs(output.toString()).isZero()
            assertThat(output.toString())
                .contains(
                    "From Gradle project :app's xtcRun: method greet, arguments [from the model], JVM options [-Dxtc.gradle.origin=model].",
                ).contains("greet from the model")
            val worker = server.runWorkerPids().single()
            assertThat(server.runWorkerCommands().getValue(worker)).contains("-Dxtc.gradle.origin=model")

            // the workspace's conventions, without a model, run the module as it is
            server.changeCompilerConfig(JsonParser.parseString("""{"xtc": {"compiler": {"sourceModules": null}}}"""))
            awaitAsserted { assertThat(heard.lastOrNull()).isEmpty() }
            output.setLength(0)
            assertThat(server.run(RunParams(module = "App", runId = "conventions")).awaited().runId).isEqualTo("conventions")
            assertThat(exits.awaited()!!.exitCode).describedAs(output.toString()).isZero()
            assertThat(output.toString()).doesNotContain("Gradle's").contains("run, as it is")
            awaitUntil { server.runWorkerPids().size == 1 && worker !in server.runWorkerPids() }
            assertThat(server.runWorkerCommands().getValue(server.runWorkerPids().single())).doesNotContain("-Dxtc.gradle.origin=model")
        }
    }

    @Test
    fun `a model whose run settings are malformed, as a blank method, is applied at initialize without them`() {
        val workspace =
            directory
                .toRealPath()
                .resolve("malformed")
                .toFile()
                .also { it.mkdirs() }
        val app = workspace.resolve("App.x").also { it.writeText("module App { void run() {} }") }
        val entry =
            mapOf(
                "projectId" to "app",
                "sourceSet" to "main",
                "sourceFiles" to listOf(app.toURI().toString()),
                "moduleRoots" to listOf(app.toURI().toString()),
                "resourceRoots" to emptyList<String>(),
                "projectDependencies" to emptyList<String>(),
                "modulePath" to emptyList<String>(),
                "buildTreePath" to ":app",
                "run" to
                    mapOf(
                        "jvmArgs" to listOf("-ea"),
                        "modules" to mapOf("App" to mapOf("methodName" to "", "moduleArgs" to emptyList<String>())),
                    ),
                "test" to "not an object",
            )
        val heard = CopyOnWriteArrayList<Map<String, String>>()
        val client =
            object : XtcClientProtocol, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {}

                override fun runExited(params: RunExitedParams) {}

                override fun gradleProjects(params: GradleProjectsParams) {
                    heard += params.projects
                }
            }
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            // a VS Code client sends the model at initialize, which would fail, and the server with it, if it refused the model
            server
                .initialize(
                    editorInitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "malformed"))
                        initializationOptions =
                            mapOf(
                                "xtcCompiler" to mapOf("buildModels" to listOf(mapOf("schemaVersion" to 1, "sourceSets" to listOf(entry)))),
                            )
                    },
                ).awaited()
            awaitAsserted { assertThat(heard.lastOrNull()).isEqualTo(mapOf("App" to ":app")) }
        }
    }

    @Test
    fun `a workspace's own json replaces the bundled one, the client hears so each time that changes, and builds refuse`() {
        val workspace =
            directory
                .toRealPath()
                .resolve("own")
                .toFile()
                .also { it.mkdirs() }
        val json = workspace.resolve("json.x").also { it.writeText("module json.xtclang.org {}") }
        val app = workspace.resolve("App.x").also { it.writeText("module App { package json import json.xtclang.org; }") }
        val heard = CopyOnWriteArrayList<List<String>>()
        val client =
            object : XtcClientProtocol, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {}

                override fun runExited(params: RunExitedParams) {}

                override fun replacedLibraries(params: ReplacedLibrariesParams) {
                    heard += params.libraries
                }
            }
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            server
                .initialize(
                    editorInitializeParams().apply { workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "own")) },
                ).awaited()
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(app.toURI().toString(), "ecstasy", 1, app.readText())),
            )
            awaitAsserted { assertThat(heard.lastOrNull()).isEqualTo(listOf("json.xtclang.org")) }
            assertThatThrownBy { server.build(BuildParams()).awaited() }.hasStackTraceContaining("own modules replace bundled libraries")
            json.delete()
            server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(listOf(FileEvent(json.toURI().toString(), FileChangeType.Deleted))),
            )
            awaitAsserted { assertThat(heard.lastOrNull()).isEmpty() }
        }
    }

    @Test
    fun `live discovered edges propagate unsaved edits and folder notifications refresh diagnostics`() {
        val first =
            directory
                .toRealPath()
                .resolve("first")
                .toFile()
                .also { it.mkdirs() }
        val library = first.resolve("Library.x").also { it.writeText(LIBRARY) }
        val consumer = first.resolve("Consumer.x").also { it.writeText("module Consumer {}") }
        Session(library, consumer, automatic = true).use { session ->
            session.open(consumer, consumer.readText(), 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.change(consumer, CONSUMER, 8)
            session.expect(consumer, mark, 8, false)
            mark = session.published.size
            session.open(library, INCOMPATIBLE, 2)
            session.expect(consumer, mark, 8, true)
            session.closeDocument(library)
            session.expect(consumer, mark, 8, false)

            val second = directory.resolve("second").toFile().also { it.mkdirs() }
            val external =
                second.resolve("External.x").also {
                    it.writeText("module External { static Int value() = 1; }")
                }
            val folder = WorkspaceFolder(second.toURI().toString(), "second")
            // The second folder is outside the original folder's automatic scan.
            session.server.workspaceService.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(listOf(folder), emptyList()),
                ),
            )
            mark = session.published.size
            session.change(consumer, CONSUMER.replace("Library", "External"), 9)
            session.expect(consumer, mark, 9, false)
            mark = session.published.size
            session.server.workspaceService.didChangeWorkspaceFolders(
                DidChangeWorkspaceFoldersParams(
                    WorkspaceFoldersChangeEvent(emptyList(), listOf(folder)),
                ),
            )
            session.expect(consumer, mark, 9, true)
            assertThat(external.isFile).isTrue()
        }
    }

    @Test
    fun `a workspace-wide request sent after a folder is added waits for that folder's walk`() {
        CompilerTestSupport.configure()
        val first =
            directory
                .toRealPath()
                .resolve("first")
                .toFile()
                .also { it.mkdirs() }
        first.resolve("App.x").writeText("module App {}")
        val second =
            directory
                .toRealPath()
                .resolve("second")
                .toFile()
                .also { it.mkdirs() }
        second.resolve("External.x").writeText("module External { static Int externalValue() = 1; }")
        val hold = AtomicBoolean()
        val walking = CountDownLatch(1)
        val release = CountDownLatch(1)
        XtcLanguageServer(XdkAdapter(), rescans = { rescan ->
            if (hold.get()) {
                walking.countDown()
                check(release.awaited())
            }
            rescan()
        }).use { server ->
            try {
                server.connect(mock(LanguageClient::class.java))
                server
                    .initialize(
                        editorInitializeParams().apply {
                            workspaceFolders = listOf(WorkspaceFolder(first.toURI().toString(), "first"))
                        },
                    ).awaited()
                hold.set(true)
                server.workspaceService.didChangeWorkspaceFolders(
                    DidChangeWorkspaceFoldersParams(
                        WorkspaceFoldersChangeEvent(listOf(WorkspaceFolder(second.toURI().toString(), "second")), emptyList()),
                    ),
                )
                assertThat(walking.awaited()).isTrue()
                val symbols = server.workspaceService.symbol(WorkspaceSymbolParams("externalValue"))
                assertThat(symbols).describedAs("The request waits while the added folder is walked").isNotDone()
                release.countDown()
                val found = symbols.awaited()
                assertThat(if (found.isLeft) found.left.map { it.name } else found.right.map { it.name })
                    .contains("externalValue")
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `a build that the client cancels stops at the adapter`() {
        CompilerTestSupport.configure()
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val lines = CopyOnWriteArrayList<String>()
        logged(lines) {
            XtcLanguageServer(adapter).use { server ->
                try {
                    server.connect(mock(LanguageClient::class.java))
                    server.initialize(editorInitializeParams()).awaited()
                    server.replaceCompilerSourceModules(
                        listOf(
                            XdkSourceModule("Library", library.toURI().toString()),
                            XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                        ),
                    )
                    hold.set(true)
                    val built = server.build(BuildParams("Consumer", rebuild = true))
                    // the build's compile of Library, the first of the two
                    assertThat(entered.awaited()).isTrue()
                    // as the client's cancellation does, as when VS Code's build terminal closes
                    assertThat(built.cancel(false)).isTrue()
                    release.countDown()
                    awaitUntil { lines.any { it.startsWith("embedded build:") } }
                    // it stops after the compile it was in, where it would go on to compile Consumer
                    assertThat(lines.single { it.startsWith("embedded build:") }).contains("outcome=cancelled")
                } finally {
                    release.countDown()
                }
            }
        }
    }

    private fun file(
        name: String,
        text: String,
    ): File {
        directory = directory.toRealPath()
        return directory.resolve("$name.x").toFile().also { it.writeText(text) }
    }

    /** Configures a real compiler server with dependency fixtures and records consumer diagnostics. */
    private class Session(
        library: File,
        consumer: File,
        bridge: File? = null,
        automatic: Boolean = false,
    ) : AutoCloseable {
        val adapter = XdkAdapter()
        val server = XtcLanguageServer(adapter)
        val documents = server.textDocumentService
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()

        init {
            val client = mock(LanguageClient::class.java)
            doAnswer { call ->
                published.add(call.getArgument(0))
                null
            }.`when`(client)
                .publishDiagnostics(any())
            server.connect(client)
            if (automatic) {
                server
                    .initialize(
                        editorInitializeParams().apply {
                            workspaceFolders =
                                listOf(
                                    WorkspaceFolder(
                                        consumer.parentFile.toURI().toString(),
                                        "workspace",
                                    ),
                                )
                        },
                    ).awaited()
            } else {
                server.initialize(editorInitializeParams()).get()
                server.replaceCompilerSourceModules(
                    buildList {
                        add(XdkSourceModule("Library", library.toURI().toString()))
                        bridge?.let {
                            add(XdkSourceModule("Bridge", it.toURI().toString(), setOf("Library")))
                        }
                        add(
                            XdkSourceModule(
                                "Consumer",
                                consumer.toURI().toString(),
                                setOf(if (bridge == null) "Library" else "Bridge"),
                            ),
                        )
                    },
                )
            }
        }

        fun open(
            file: File,
            text: String,
            version: Int,
        ) = documents.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(file.toURI().toString(), "xtc", version, text),
            ),
        )

        fun change(
            file: File,
            text: String,
            version: Int,
        ) = documents.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(file.toURI().toString(), version),
                listOf(TextDocumentContentChangeEvent(text)),
            ),
        )

        fun closeDocument(file: File) =
            documents.didClose(
                DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString())),
            )

        fun watched(
            file: File,
            kind: FileChangeType,
        ) = server.workspaceService.didChangeWatchedFiles(
            DidChangeWatchedFilesParams(listOf(FileEvent(file.toURI().toString(), kind))),
        )

        fun expect(
            file: File,
            after: Int,
            version: Int?,
            errors: Boolean,
            code: String? = null,
        ) {
            awaitAsserted {
                val last =
                    published.drop(after).lastOrNull {
                        it.uri == file.toURI().toString() && it.version == version
                    }
                assertThat(last).describedAs(published.drop(after).toString()).isNotNull()
                assertThat(last!!.diagnostics.isNotEmpty())
                    .describedAs(last.toString())
                    .isEqualTo(errors)
                assertThat(last.diagnostics).noneMatch {
                    it.code?.left in setOf("ANALYSIS-FAILED", "EMB-5")
                }
                if (code != null) assertThat(last.diagnostics).anyMatch { it.code?.left == code }
            }
        }

        override fun close() {
            server.shutdown().awaited()
        }
    }

    private companion object {
        const val LIBRARY = "module Library { static Int value() = 1; }"
        const val INCOMPATIBLE = "module Library { static String value() = \"text\"; }"
        const val CONSUMER =
            "module Consumer { package lib import Library; Int run() = lib.value(); }"

        /** Prints which entry method runs, and its argument. */
        val GRADLED_APP =
            """
            module App {
                void run() {
                    @Inject Console console;
                    console.print("run, as it is");
                }

                void greet(String[] args) {
                    @Inject Console console;
                    console.print($"greet {args[0]}");
                }
            }
            """.trimIndent()
    }
}
