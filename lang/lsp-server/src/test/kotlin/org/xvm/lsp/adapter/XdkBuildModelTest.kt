package org.xvm.lsp.adapter

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkRunSettings
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.testing.awaited
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Compiles evaluated build-model inputs across source sets and projects. Checks dependency visibility,
 * processed resources, member imports and replacement of imported binary paths.
 */
class XdkBuildModelTest {
    @TempDir lateinit var directory: Path

    @BeforeEach
    fun canonicalDirectory() {
        directory = directory.toRealPath()
    }

    @Test
    fun `binary module path is imported and removing the model retires its artifacts`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport
                .instance()
                .compileModule(
                    Source("module BinaryLibrary { static Int value() = 7; }", "BinaryLibrary.x"),
                    null,
                    errors,
                )
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val binary =
            directory.resolve("BinaryLibrary.xtc").toFile().apply {
                writeBytes(compilation.toDependency().bytes())
            }
        val consumer =
            source(
                "Consumer.x",
                "module Consumer { package lib import BinaryLibrary; Int run() = lib.value(); }",
            )
        val inputs =
            entry("app", "main", listOf(consumer)).apply {
                add("modulePath", Gson().toJsonTree(listOf(binary.toURI().toString())))
            }
        XdkAdapter().use { adapter ->
            adapter.replaceBuildInputs(model(inputs).resolve())
            assertThat(adapter.workspaceDiagnosticsAsync().awaited()).allMatch { it.success }
            adapter.replaceSourceModules(listOf(XdkSourceModule("Consumer", consumer)))
            assertThat(
                adapter.compile(consumer, Path.of(URI(consumer)).toFile().readText()).success,
            ).isFalse()
        }
    }

    @Test
    fun `evaluated source dependencies and processed resources compile across projects`() {
        CompilerTestSupport.configure()
        val library =
            source("library/code/Assets.x", "module Assets { static String text() = $./data.txt; }")
        val consumer =
            source(
                "app/code/App.x",
                "module App { package assets import Assets; String run() = assets.text(); }",
            )
        val processed = directory.resolve("outside-layout/processed").toFile().apply { mkdirs() }
        processed.resolve("data.txt").writeText("filtered resource")
        val inputs =
            model(
                entry(
                    "library",
                    "main",
                    listOf(library),
                    resources = listOf(processed.toURI().toString()),
                ),
                entry("app", "main", listOf(consumer), dependencies = listOf("library")),
            ).resolve()
        assertThat(inputs.modules.single { it.name == "App" }.dependencies)
            .containsExactly("Assets")
        XdkAdapter().use { adapter ->
            adapter.replaceBuildInputs(inputs)
            assertThat(adapter.workspaceDiagnosticsAsync().awaited()).allMatch { it.success }
            assertThat(
                adapter.effectiveSourceModules().single { it.name == "Assets" }.resourceRoots,
            ).containsExactly(processed.toURI().toString())
            adapter.replaceBuildInputs(
                model(entry("library", "main", listOf(library), resources = emptyList())).resolve(),
            )
            assertThat(adapter.effectiveSourceModules().map { it.name }).containsExactly("Assets")
            assertThat(
                adapter
                    .workspaceDiagnosticsAsync()
                    .awaited()
                    .single()
                    .success,
            ).isFalse()
            adapter.replaceBuildInputs(inputs)
            assertThat(adapter.workspaceDiagnosticsAsync().awaited()).allMatch { it.success }
        }
    }

    @Test
    fun `each module's Gradle project is its source set's path in the build tree, where the model says it`() {
        val json = source("lib/json.x", "module Json {}")
        val tests = source("lib/test/JsonTest.x", "module JsonTest { package json import Json; }")
        val old = source("old/Old.x", "module Old {}")
        val inputs =
            model(
                entry("lib", "main", listOf(json)).apply { addProperty("buildTreePath", ":xdk:lib-json") },
                entry("lib", "test", listOf(tests)).apply { addProperty("buildTreePath", ":xdk:lib-json") },
                // a model from a plugin that does not write the field names no project
                entry("old", "main", listOf(old)),
            ).resolve()
        assertThat(inputs.projects).containsExactlyInAnyOrderEntriesOf(mapOf("Json" to ":xdk:lib-json", "JsonTest" to ":xdk:lib-json"))
    }

    @Test
    fun `each module's run settings are its project's, by the name runXtc gives it, and a model without them has none`() {
        val app = source("app/App.x", "module App.example.org {}")
        val tests = source("app/test/AppTest.x", "module AppTest {}")
        val other = source("other/Other.x", "module Other {}")
        val old = source("old/Old.x", "module Old {}")
        val runs =
            mapOf(
                "run" to
                    mapOf(
                        "jvmArgs" to listOf("-ea", "-Dxvm.origin=gradle"),
                        // runXtc may name a qualified module by its first part, as the runner finds it
                        "modules" to mapOf("App" to mapOf("methodName" to "greet", "moduleArgs" to listOf("hello world", "ü"))),
                    ),
                "test" to mapOf("jvmArgs" to listOf("-Dxvm.origin=test")),
            )
        val inputs =
            model(
                entry("app", "main", listOf(app)).with(runs + ("buildTreePath" to ":app")),
                entry("app", "test", listOf(tests)).with(runs + ("buildTreePath" to ":app")),
                // a project whose runXtc names none of its modules, at the root of its build
                entry("other", "main", listOf(other)).with(
                    mapOf("run" to mapOf("jvmArgs" to listOf("-ea"), "modules" to emptyMap<String, Any>()), "buildTreePath" to ":"),
                ),
                // a model from a plugin that does not write them
                entry("old", "main", listOf(old)),
            ).resolve()
        val gradle = listOf("-ea", "-Dxvm.origin=gradle")
        val test = listOf("-Dxvm.origin=test")
        assertThat(inputs.runs).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "App.example.org" to XdkRunSettings(":app", "greet", listOf("hello world", "ü"), gradle, test),
                "AppTest" to XdkRunSettings(":app", null, null, gradle, test),
                "Other" to XdkRunSettings(":", null, null, listOf("-ea"), emptyList()),
            ),
        )
        assertThat(inputs.runs.getValue("App.example.org").source("xtcRun")).isEqualTo("Gradle project :app's xtcRun")
        assertThat(inputs.runs.getValue("Other").source("xtcTest")).isEqualTo("the Gradle root project's xtcTest")
        assertThat(XdkRunSettings(null, null, null, emptyList(), emptyList()).source("xtcRun")).isEqualTo("Gradle's xtcRun")
    }

    @Test
    fun `malformed run settings are left out, and never make the model refused`() {
        val root = source("Bad.x", "module Bad {}")
        val good = mapOf("Good" to mapOf("methodName" to "greet", "moduleArgs" to listOf("a")))
        val none = XdkRunSettings("app", null, null, emptyList(), emptyList())
        listOf(
            mapOf("run" to "not an object") to null,
            mapOf("run" to mapOf("jvmArgs" to listOf(1), "problems" to "not a list")) to none,
            mapOf("run" to mapOf("modules" to listOf("Bad"))) to none,
            mapOf("run" to mapOf("modules" to good + mapOf("Bad" to mapOf("moduleArgs" to emptyList<String>())))) to none,
            mapOf("run" to mapOf("modules" to good + mapOf("Bad" to mapOf("methodName" to "", "moduleArgs" to listOf<String>())))) to none,
            mapOf("run" to mapOf("modules" to good + mapOf("Bad" to mapOf("methodName" to "run", "moduleArgs" to "a,b")))) to none,
            mapOf("run" to mapOf("modules" to good + mapOf("" to mapOf("methodName" to "run")))) to none,
            mapOf("run" to mapOf("jvmArgs" to listOf("-ea")), "test" to mapOf("jvmArgs" to "-ea")) to none.copy(jvmArgs = listOf("-ea")),
        ).forEach { (bad, expected) ->
            val inputs = model(entry("app", "main", listOf(root)).apply { addProperty("buildTreePath", "app") }.with(bad)).resolve()
            assertThat(inputs.modules.map { it.name }).describedAs(bad.toString()).containsExactly("Bad")
            assertThat(inputs.runs["Bad"]).describedAs(bad.toString()).isEqualTo(expected)
        }
        // what the export could not read reaches each module's settings
        val read =
            model(
                entry("app", "main", listOf(root)).with(
                    mapOf(
                        "run" to mapOf("problems" to listOf("module Other's moduleArgs: it has no value")),
                        "test" to mapOf("problems" to listOf("jvmArgs: no test options")),
                    ),
                ),
            ).resolve()
        assertThat(read.runs.getValue("Bad").problems).containsExactly("module Other's moduleArgs: it has no value")
        assertThat(read.runs.getValue("Bad").testProblems).containsExactly("jvmArgs: no test options")
    }

    @Test
    fun `what the export could not read, and what is malformed, is logged once a read, though each source set has it`() {
        val main = source("one/Main.x", "module Main {}")
        val tests = source("one/test/Tests.x", "module Tests {}")
        val runs = mapOf("run" to mapOf("problems" to listOf("module Other's moduleArgs: it has no value")), "test" to "not an object")
        val logger = LoggerFactory.getLogger(XdkBuildModel::class.java) as Logger
        val events = CopyOnWriteArrayList<ILoggingEvent>()
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    events += event
                }
            }.apply { start() }
        logger.addAppender(appender)
        try {
            model(entry("one", "main", listOf(main)).with(runs), entry("one", "test", listOf(tests)).with(runs)).resolve()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        assertThat(events.map { it.formattedMessage }).containsExactly(
            "build model: test of one is malformed, and left out",
            "build model: the export of one could not read module Other's moduleArgs: it has no value",
        )
    }

    @Test
    fun `a module whose project builds with another XDK than the bundled one has it, and one without an XDK keeps to the bundled one`() {
        fun lib(project: String) = directory.resolve("$project/build/xtc/xdk/lib").toFile()
        val json = XdkLibraries.revisions.first()
        val inputs =
            model(
                entry("same", "main", listOf(source("same/Same.x", "module Same {}"))).with(
                    mapOf("xdk" to CompilerTestSupport.xdk(lib("same"))),
                ),
                entry("same", "test", listOf(source("same/test/SameTest.x", "module SameTest {}")))
                    .with(mapOf("xdk" to CompilerTestSupport.xdk(lib("same")))),
                // another version, with another build of one module
                entry("other", "main", listOf(source("other/Other.x", "module Other {}"))).with(
                    mapOf("xdk" to CompilerTestSupport.xdk(lib("other"), "9.9.9", XdkLibraries.revisions - json + "0".repeat(64))),
                ),
                // a snapshot of the bundled version that is another build of it: its modules are the bundle's, its javatools not
                entry("snapshot", "main", listOf(source("snapshot/Snapshot.x", "module Snapshot {}"))).with(
                    mapOf("xdk" to CompilerTestSupport.xdk(lib("snapshot"), javatools = "1".repeat(64))),
                ),
                // one that the build tree makes, which the export neither builds nor reads, whatever an earlier build left
                entry("composite", "main", listOf(source("composite/Composite.x", "module Composite {}"))).with(
                    mapOf(
                        "xdk" to
                            mapOf(
                                "version" to XdkLibraries.version,
                                "libraries" to lib("composite").toURI().toString(),
                                "project" to ":xdk",
                            ),
                    ),
                ),
                entry("unfetched", "main", listOf(source("unfetched/Unfetched.x", "module Unfetched {}"))).with(
                    mapOf("xdk" to mapOf("version" to "9.9.9", "problem" to "the import could not fetch its distribution")),
                ),
                entry("malformed", "main", listOf(source("malformed/Malformed.x", "module Malformed {}"))).with(mapOf("xdk" to "9.9.9")),
                // a model from a plugin that does not write it, or of a project without an XDK dependency
                entry("old", "main", listOf(source("old/Old.x", "module Old {}"))),
            ).resolve()
        assertThat(inputs.modules.map { it.name }).hasSize(8)
        assertThat(inputs.xdks.keys).containsExactlyInAnyOrder("Other", "Snapshot", "Composite", "Unfetched", "Malformed")
        val reasons = inputs.xdks.mapValues { (name, xdk) -> xdk.noEmbedded(name).reason }
        val against = "the bundled XDK ${XdkLibraries.version}"
        val unknown = "which the language server cannot compare with $against"
        assertThat(reasons.getValue("Other")).isEqualTo("builds with XDK 9.9.9, not $against")
        assertThat(reasons.getValue("Snapshot")).isEqualTo("builds with another build of XDK ${XdkLibraries.version} than the bundled one")
        assertThat(
            reasons.getValue("Composite"),
        ).isEqualTo("builds with XDK ${XdkLibraries.version} that the build's own project :xdk makes, $unknown")
        assertThat(
            reasons.getValue("Unfetched"),
        ).isEqualTo("builds with XDK 9.9.9, $unknown, as the import could not fetch its distribution")
        assertThat(reasons.getValue("Malformed")).isEqualTo("builds with an XDK, $unknown, as the Gradle model does not say what it holds")
        assertThat(
            inputs.xdks
                .getValue("Other")
                .noEmbedded("Other")
                .refusal,
        ).startsWith("The module Other builds with XDK 9.9.9, not $against")
    }

    @Test
    fun `a folder whose module depends on another folder's module on another XDK says so, though its own build has the bundled one`() {
        val app = source("foreign/App.x", "module App {}")
        val tool = source("consumer/Tool.x", "module Tool { package app import App; }")
        val foreign = directory.resolve("foreign").toFile()
        val consumer = directory.resolve("consumer").toFile()
        val inputs =
            model(
                entry("foreign", "main", listOf(app)).with(
                    mapOf("xdk" to CompilerTestSupport.xdk(foreign.resolve("build/xtc/xdk/lib"), "9.9.9", listOf("0".repeat(64)))),
                ),
                entry("consumer", "main", listOf(tool), dependencies = listOf("foreign"))
                    .with(mapOf("xdk" to CompilerTestSupport.xdk(consumer.resolve("build/xtc/xdk/lib")))),
            ).resolve()
        assertThat(inputs.xdks.keys).containsExactly("App")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(foreign.path, consumer.path))
            adapter.replaceBuildInputs(inputs)
            val reason = "builds with XDK 9.9.9, not the bundled XDK ${XdkLibraries.version}"
            assertThat(adapter.gradleFolders()).isEqualTo(
                mapOf(foreign.toURI().toString() to reason, consumer.toURI().toString() to "depends on the module App, which $reason"),
            )
            // a build of the module, or of its folder, says so too, as the editors do
            for (refused in listOf(
                { adapter.buildAsync("Tool", false) },
                { adapter.buildAsync(null, false, folder = consumer.toURI().toString()) },
            )) {
                assertThatThrownBy { refused().get() }.hasMessageContaining("The module Tool depends on the module App, which $reason")
            }
            assertThatThrownBy { adapter.buildAsync("App", false).get() }.hasMessageContaining("The module App $reason")
        }
    }

    @Test
    fun `test sees own main and declared project dependencies but unrelated main does not`() {
        val main = source("main/Main.x", "module Main {}")
        val tests = source("test/Tests.x", "module Tests { package main import Main; }")
        val unrelated = source("unrelated/Other.x", "module Other { package main import Main; }")
        val inputs =
            model(
                entry("one", "main", listOf(main)),
                entry("one", "test", listOf(tests)),
                entry("two", "main", listOf(unrelated)),
            ).resolve()
        assertThat(inputs.modules.single { it.name == "Tests" }.dependencies)
            .containsExactly("Main")
        assertThat(inputs.modules.single { it.name == "Other" }.dependencies).isEmpty()
    }

    @Test
    fun `the XDK's libraries in Gradle's model replace the bundled ones, each depending on ecstasy without saying so`() {
        val ecstasy = source("lib_ecstasy/src/main/x/ecstasy.x", "module ecstasy.xtclang.org {}")
        val json = source("lib_json/src/main/x/json.x", "module json.xtclang.org {}")
        val inputs =
            model(
                entry(":lib_ecstasy", "main", listOf(ecstasy)),
                entry(":lib_json", "main", listOf(json)),
            ).resolve()
        assertThat(inputs.modules.associate { it.name to it.dependencies }).isEqualTo(
            mapOf("ecstasy.xtclang.org" to emptySet(), "json.xtclang.org" to setOf("ecstasy.xtclang.org")),
        )
    }

    @Test
    fun `member imports contribute dependencies and excluded files are not mistaken for roots`() {
        val library = source("Library.x", "module Library {}")
        val app = source("App.x", "module App {}")
        val member = source("App/Member.x", "class Member { package lib import Library; }")
        val appEntry =
            entry("app", "main", listOf(app, member), dependencies = listOf("lib")).apply {
                add("moduleRoots", Gson().toJsonTree(listOf(app)))
            }
        val inputs = model(entry("lib", "main", listOf(library)), appEntry).resolve()
        assertThat(inputs.modules.map { it.name }).containsExactlyInAnyOrder("Library", "App")
        assertThat(inputs.modules.single { it.name == "App" }.dependencies)
            .containsExactly("Library")
    }

    @Test
    fun `malformed overlapping and cyclic imports preserve the installed graph`() {
        CompilerTestSupport.configure()
        val root = source("Good.x", "module Good {}")
        val valid = entry("app", "main", listOf(root))
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("Good", root)))
            listOf(
                valid.deepCopy().apply { addProperty("sourceFiles", "not an array") },
                valid.deepCopy().apply {
                    add("sourceFiles", Gson().toJsonTree(listOf("https://example.org/Bad.x")))
                },
                valid.deepCopy().apply { addProperty("projectId", 1) },
            ).forEach { bad ->
                assertThatThrownBy { adapter.replaceBuildInputs(model(bad).resolve()) }
                    .isInstanceOf(IllegalArgumentException::class.java)
            }
            assertThatThrownBy { model(valid, valid).resolve() }.hasMessageContaining("Duplicate")
            assertThatThrownBy {
                model(valid, valid.deepCopy().apply { addProperty("projectId", "other") })
                    .resolve()
            }.hasMessageContaining("Overlapping")
            val first = source("First.x", "module First { package second import Second; }")
            val second = source("Second.x", "module Second { package first import First; }")
            assertThatThrownBy {
                adapter.replaceBuildInputs(
                    model(entry("app", "main", listOf(first, second))).resolve(),
                )
            }.hasMessageContaining("Cyclic")
            assertThat(adapter.effectiveSourceModules().map { it.name }).containsExactly("Good")
            assertThat(adapter.workspaceDiagnosticsAsync().awaited()).allMatch { it.success }
        }
    }

    private fun source(
        path: String,
        text: String,
    ): String =
        directory
            .resolve(path)
            .toFile()
            .apply {
                parentFile.mkdirs()
                writeText(text)
            }.toURI()
            .toString()

    private fun entry(
        project: String,
        sourceSet: String,
        sources: List<String>,
        resources: List<String> = emptyList(),
        dependencies: List<String> = emptyList(),
    ): JsonObject =
        Gson()
            .toJsonTree(
                mapOf(
                    "projectId" to project,
                    "sourceSet" to sourceSet,
                    "sourceFiles" to sources,
                    "moduleRoots" to sources,
                    "resourceRoots" to resources,
                    "projectDependencies" to dependencies,
                    "modulePath" to emptyList<String>(),
                ),
            ).asJsonObject

    /** The entry with [fields] added, as JSON. */
    private fun JsonObject.with(fields: Map<String, Any>): JsonObject =
        apply {
            Gson()
                .toJsonTree(fields)
                .asJsonObject
                .entrySet()
                .forEach { (key, value) -> add(key, value) }
        }

    private fun model(vararg entries: JsonObject): XdkBuildModel =
        XdkBuildModel.read(
            listOf(
                JsonObject().apply {
                    addProperty("schemaVersion", 1)
                    add("sourceSets", Gson().toJsonTree(entries.toList()))
                },
            ),
        )
}
