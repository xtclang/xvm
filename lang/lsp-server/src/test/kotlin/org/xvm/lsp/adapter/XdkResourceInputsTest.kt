package org.xvm.lsp.adapter

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule

class XdkResourceInputsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `conventional Gradle resources resolve without changing source ownership`() {
        CompilerTestSupport.configure()
        val source =
            directory.resolve("app/src/main/x/Assets.x").toFile().apply {
                parentFile.mkdirs()
                writeText("module Assets { static String text() = $./data.txt; }")
            }
        directory.resolve("app/src/main/resources/data.txt").toFile().apply {
            parentFile.mkdirs()
            writeText("template")
        }
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Assets", source.toURI().toString()))
            )
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(adapter.compile(source.toURI().toString(), source.readText()).diagnostics)
                .isEmpty()
        }
    }

    @Test
    fun `custom resource contents invalidate only their source consumer closure including same timestamp edits`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val roots = directory.resolve("custom-assets").toFile().apply { mkdirs() }
        val resource = roots.resolve("data.txt").apply { writeText("first") }
        val library =
            directory.resolve("Assets.x").toFile().apply {
                writeText("module Assets { static String text() = $./data.txt; }")
            }
        val consumer =
            directory.resolve("Consumer.x").toFile().apply {
                writeText(
                    "module Consumer { package assets import Assets; String run() = assets.text(); }"
                )
            }
        val independent =
            directory.resolve("Independent.x").toFile().apply { writeText("module Independent {}") }
        val compiled = mutableListOf<String>()
        XdkAdapter(
                { source, repository, errors ->
                    EmbeddingSupport.instance().compileModule(source, repository, errors)
                },
                { sources, repository, errors ->
                    compiled += sources.sourceFile.nameWithoutExtension
                    EmbeddingSupport.instance().compileModule(sources, repository, errors)
                },
                { _, _, _, _, _ -> error("No cursor analysis") },
            )
            .use { adapter ->
                val graph =
                    listOf(
                        XdkSourceModule(
                            "Assets",
                            library.toURI().toString(),
                            resourceRoots = listOf(roots.toURI().toString()),
                        ),
                        XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Assets")),
                        XdkSourceModule("Independent", independent.toURI().toString()),
                    )
                adapter.replaceSourceModules(graph)
                fun pull() = adapter.workspaceDiagnosticsAsync().get(30, SECONDS)
                assertThat(pull()).allMatch { it.success }
                assertThat(compiled).containsExactly("Assets", "Consumer", "Independent")
                compiled.clear()
                assertThat(pull()).allMatch { it.success }
                assertThat(compiled).isEmpty()
                val timestamp = Files.getLastModifiedTime(resource.toPath())
                resource.writeText("other")
                Files.setLastModifiedTime(resource.toPath(), timestamp)
                assertThat(adapter.affectedAnalysisScopes(resource.toURI().toString()))
                    .contains(library.toURI().toString(), consumer.toURI().toString())
                    .doesNotContain(independent.toURI().toString())
                assertThat(pull()).allMatch { it.success }
                assertThat(compiled).containsExactly("Assets", "Consumer")
                compiled.clear()
                Files.delete(resource.toPath())
                val missing = pull()
                assertThat(
                        missing
                            .single { it.uri == library.toURI().toString() }
                            .diagnostics
                            .map { it.code }
                    )
                    .contains("PARSER-24")
                assertThat(
                        missing
                            .single { it.uri == consumer.toURI().toString() }
                            .diagnostics
                            .map { it.code }
                    )
                    .contains("DEPENDENCY-FAILED")
                resource.writeText("again")
                assertThat(pull()).allMatch { it.success }
                adapter.replaceSourceModules(
                    graph.map {
                        if (it.name == "Assets")
                            XdkSourceModule(it.name, it.uri, resourceRoots = emptyList())
                        else it
                    }
                )
                assertThat(pull().single { it.uri == library.toURI().toString() }.success).isFalse()
            }
    }

    @Test
    fun `a missing configured resource directory can be created after a failed pull`() {
        CompilerTestSupport.configure()
        val source =
            directory.resolve("Assets.x").toFile().apply {
                writeText("module Assets { static String text() = $./data.txt; }")
            }
        val root = directory.resolve("generated-resources").toFile()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule(
                        "Assets",
                        source.toURI().toString(),
                        resourceRoots = listOf(root.toURI().toString()),
                    )
                )
            )
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS).single().success)
                .isFalse()
            root.mkdirs()
            root.resolve("data.txt").writeText("generated")
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS).single().success)
                .isTrue()
        }
    }
}
