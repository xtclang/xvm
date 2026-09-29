package org.xvm.lsp.adapter

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule

class XdkDiagnosticIndexTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `closed graph pulls reuse unchanged roots and rebuild only changed dependency closure`() {
        CompilerTestSupport.configure()
        val compiled = ConcurrentHashMap<String, AtomicInteger>()
        val leaves =
            (0 until 20).map { index ->
                val name = "Node$index"
                val file =
                    directory.resolve("$name.x").toFile().apply {
                        writeText("module $name { static Int value() = 1; }")
                    }
                XdkSourceModule(name, file.toURI().toString())
            }
        val app =
            directory.resolve("App.x").toFile().apply {
                writeText(
                    "module App { package dependency import Node0; Int run() = dependency.value(); }"
                )
            }
        val graph = leaves + XdkSourceModule("App", app.toURI().toString(), setOf("Node0"))
        XdkAdapter(
                { source, repository, errors ->
                    EmbeddingSupport.instance().compileModule(source, repository, errors)
                },
                { sources, repository, errors ->
                    compiled
                        .computeIfAbsent(sources.sourceFile.nameWithoutExtension) {
                            AtomicInteger()
                        }
                        .incrementAndGet()
                    EmbeddingSupport.instance().compileModule(sources, repository, errors)
                },
                { _, _, _, _, _ -> error("Diagnostics must not invoke cursor analysis") },
            )
            .use { adapter ->
                adapter.replaceSourceModules(graph)
                val cold = TimeSource.Monotonic.markNow()
                val first = adapter.workspaceDiagnosticsAsync().get(30, SECONDS)
                val coldTime = cold.elapsedNow()
                assertThat(first).hasSize(21).allMatch { it.success }
                assertThat(compiled.values.sumOf { it.get() }).isEqualTo(21)
                val warm = TimeSource.Monotonic.markNow()
                assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).isEqualTo(first)
                val warmTime = warm.elapsedNow()
                assertThat(compiled.values.sumOf { it.get() }).isEqualTo(21)
                directory
                    .resolve("Node0.x")
                    .toFile()
                    .writeText("module Node0 { static String value() = \"changed\"; }")
                val changed = adapter.workspaceDiagnosticsAsync().get(30, SECONDS)
                assertThat(changed.single { it.uri.endsWith("App.x") }.diagnostics).isNotEmpty()
                assertThat(compiled.getValue("Node0").get()).isEqualTo(2)
                assertThat(compiled.getValue("App").get()).isEqualTo(2)
                assertThat(compiled.filterKeys { it != "Node0" && it != "App" }.values).allMatch {
                    it.get() == 1
                }
                assertThat(compiled.values.sumOf { it.get() }).isEqualTo(23)
                adapter.replaceSourceModules(graph.filter { it.name != "Node19" })
                assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).hasSize(20)
                adapter.replaceSourceModules(graph)
                assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).hasSize(21)
                assertThat(compiled.getValue("Node19").get()).isEqualTo(2)
                println(
                    "Closed diagnostic graph: roots=21, cold=$coldTime, warm=$warmTime, initialCompiles=21, unchangedCompiles=0, editedClosureCompiles=2"
                )
            }
    }

    @Test
    fun `concurrent pull consumers do not cancel each other and cancellation owns only its request`() {
        CompilerTestSupport.configure()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val file =
            directory.resolve("Concurrent.x").toFile().apply { writeText("module Concurrent {}") }
        val adapter =
            XdkAdapter(
                { source, repository, errors ->
                    EmbeddingSupport.instance().compileModule(source, repository, errors)
                },
                { sources, repository, errors ->
                    started.countDown()
                    check(release.await(20, SECONDS))
                    EmbeddingSupport.instance().compileModule(sources, repository, errors)
                },
                { _, _, _, _, _ -> error("No cursor analysis") },
            )
        try {
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Concurrent", file.toURI().toString()))
            )
            val first = adapter.workspaceDiagnosticsAsync()
            assertThat(started.await(10, SECONDS)).isTrue()
            val canceled = adapter.workspaceDiagnosticsAsync()
            val second = adapter.workspaceDiagnosticsAsync()
            assertThat(canceled.cancel(false)).isTrue()
            release.countDown()
            assertThat(first.get(20, SECONDS)).allMatch { it.success }
            assertThat(second.get(20, SECONDS)).isEqualTo(first.join())
            assertThat(canceled.isCancelled).isTrue()
        } finally {
            release.countDown()
            adapter.close()
        }
    }
}
