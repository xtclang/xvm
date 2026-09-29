package org.xvm.lsp.adapter

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency

class XdkBuildModelTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `binary module path is imported and removing the model retires its artifacts`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance()
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
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            adapter.replaceSourceModules(listOf(XdkSourceModule("Consumer", consumer)))
            assertThat(
                    adapter.compile(consumer, Path.of(URI(consumer)).toFile().readText()).success
                )
                .isFalse()
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
                )
                .resolve()
        assertThat(inputs.modules.single { it.name == "App" }.dependencies)
            .containsExactly("Assets")
        XdkAdapter().use { adapter ->
            adapter.replaceBuildInputs(inputs)
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(
                    adapter.effectiveSourceModules().single { it.name == "Assets" }.resourceRoots
                )
                .containsExactly(processed.toURI().toString())
            adapter.replaceBuildInputs(
                model(entry("library", "main", listOf(library), resources = emptyList())).resolve()
            )
            assertThat(adapter.effectiveSourceModules().map { it.name }).containsExactly("Assets")
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS).single().success)
                .isFalse()
            adapter.replaceBuildInputs(inputs)
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
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
                )
                .resolve()
        assertThat(inputs.modules.single { it.name == "Tests" }.dependencies)
            .containsExactly("Main")
        assertThat(inputs.modules.single { it.name == "Other" }.dependencies).isEmpty()
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
                )
                .forEach { bad ->
                    assertThatThrownBy { adapter.replaceBuildInputs(model(bad).resolve()) }
                        .isInstanceOf(IllegalArgumentException::class.java)
                }
            assertThatThrownBy { model(valid, valid).resolve() }.hasMessageContaining("Duplicate")
            assertThatThrownBy {
                    model(valid, valid.deepCopy().apply { addProperty("projectId", "other") })
                        .resolve()
                }
                .hasMessageContaining("Overlapping")
            val first = source("First.x", "module First { package second import Second; }")
            val second = source("Second.x", "module Second { package first import First; }")
            assertThatThrownBy {
                    adapter.replaceBuildInputs(
                        model(entry("app", "main", listOf(first, second))).resolve()
                    )
                }
                .hasMessageContaining("Cyclic")
            assertThat(adapter.effectiveSourceModules().map { it.name }).containsExactly("Good")
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    private fun source(path: String, text: String): String =
        directory
            .resolve(path)
            .toFile()
            .apply {
                parentFile.mkdirs()
                writeText(text)
            }
            .toURI()
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
                )
            )
            .asJsonObject

    private fun model(vararg entries: JsonObject): XdkBuildModel =
        XdkBuildModel.read(
            listOf(
                JsonObject().apply {
                    addProperty("schemaVersion", 1)
                    add("sourceSets", Gson().toJsonTree(entries.toList()))
                }
            )
        )
}
