package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkNavigationIndex
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkWorkspaceNavigation
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

class XdkNavigationIndexTest {
    @TempDir lateinit var directory: Path

    private val compiled = ConcurrentHashMap<String, AtomicInteger>()

    @ParameterizedTest
    @ValueSource(ints = [32, 128])
    fun `navigation reuses independent roots and rebuilds only an edited dependency closure`(count: Int) {
        val last = "Node${count - 1}"
        val roots = (0 until count).map { source("Node$it", "module Node$it { class Box {} }") }
        val consumer =
            source(
                "Consumer",
                """
                module Consumer {
                    package lib import Node0;
                    class Child extends lib.Box {}
                }
                """.trimIndent(),
                setOf("Node0"),
            )
        val graph = roots + consumer
        adapter().use { adapter ->
            adapter.replaceSourceModules(graph)
            val cold = TimeSource.Monotonic.markNow()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(count)
            val coldTime = cold.elapsedNow()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(count + 1)
            val warm = TimeSource.Monotonic.markNow()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(count)
            val warmTime = warm.elapsedNow()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(count + 1)
            val hierarchy = adapter.prepareTypeHierarchy(roots.first().uri, 0, 21).single()
            assertThat(adapter.getSubtypes(hierarchy).single().uri).isEqualTo(consumer.uri)

            source("Node0", "module Node0 { class Box { Int added = 1; } }")
            assertThat(adapter.findWorkspaceSymbols("added")).hasSize(1)
            assertThat(compiled.getValue("Node0").get()).isEqualTo(2)
            assertThat(compiled.getValue("Consumer").get()).isEqualTo(2)
            assertThat(compiled.filterKeys { it != "Node0" && it != "Consumer" }.values).allMatch { it.get() == 1 }
            assertThat(adapter.getSubtypes(hierarchy)).isEmpty()

            // Broken independent roots allow partial navigation but never complete references.
            source(last, "module $last { Missing broken; }")
            assertThat(adapter.findWorkspaceSymbols("Child")).hasSize(1)
            assertThat(adapter.findReferences(roots.first().uri, 0, 21, true)).isEmpty()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(count - 1)
            assertThat(compiled.getValue(last).get()).isEqualTo(2)
            source(last, "module $last { class Box {} }")
            assertThat(adapter.findReferences(roots.first().uri, 0, 21, true)).hasSize(2)
            assertThat(compiled.getValue(last).get()).isEqualTo(3)

            adapter.replaceSourceModules(graph.filter { it.name != last })
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(count - 1)
            adapter.replaceSourceModules(graph)
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(count)
            assertThat(compiled.getValue(last).get()).isEqualTo(4)
            println(
                "Navigation graph: roots=${count + 1}, cold=$coldTime, warm=$warmTime, initialCompiles=${count + 1}, unchangedCompiles=0, editedClosureCompiles=2",
            )
        }
    }

    @Test
    fun `first graph navigation reuses editor builds and unsaved source inputs`() {
        val text = "module Library { class Box {} }"
        val library = source("Library", text)
        val consumerText = "module Consumer { package lib import Library; class Child extends lib.Box {} }"
        val consumer = source("Consumer", consumerText, setOf("Library"))
        val independent = source("Independent", "module Independent { class Unrelated {} }")
        adapter().use { adapter ->
            adapter.replaceSourceModules(listOf(library, consumer, independent))
            assertThat(adapter.compile(consumer.uri, consumerText).diagnostics).isEmpty()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(2)
            assertThat(adapter.findReferences(library.uri, 0, text.indexOf("Box"), true)).hasSize(2)
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(3)
            val changed = "module Library { class Box { Int added = 1; } }"
            assertThat(adapter.compile(library.uri, changed).diagnostics).isEmpty()
            assertThat(adapter.findWorkspaceSymbols("added")).hasSize(1)
            assertThat(compiled.getValue("Library").get()).isEqualTo(2)
            assertThat(compiled.getValue("Consumer").get()).isEqualTo(2)
            assertThat(compiled.getValue("Independent").get()).isEqualTo(1)
            assertThat(directory.resolve("Library.x").toFile().readText()).isEqualTo(text)
        }
    }

    @Test
    fun `graph diagnostics seed navigation including failed roots without duplicate compilation`() {
        val library = source("Library", "module Library { class Box {} }")
        val consumer =
            source("Consumer", "module Consumer { package lib import Library; class Child extends lib.Box {} }", setOf("Library"))
        val broken = source("Broken", "module Broken { Missing value; }")
        adapter().use { adapter ->
            adapter.replaceSourceModules(listOf(library, consumer, broken))
            assertThat(adapter.workspaceDiagnosticsAsync().join()).hasSize(3).anyMatch { !it.success }
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(3)
            assertThat(adapter.findWorkspaceSymbols("Child")).hasSize(1)
            assertThat(adapter.findReferences(library.uri, 0, 23, true)).isEmpty()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(3)
            source("Broken", "module Broken {}")
            assertThat(adapter.workspaceDiagnosticsAsync().join()).allMatch { it.success }
            assertThat(adapter.findReferences(library.uri, 0, 23, true)).hasSize(2)
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(4)
        }
    }

    @Test
    fun `resource replacement with unchanged timestamp invalidates only dependent navigation`() {
        val resources = directory.resolve("assets").toFile().apply { mkdirs() }
        val resource = resources.resolve("data.txt").apply { writeText("first") }
        val library =
            source("Library", "module Library { static String text() = $./data.txt; }").let {
                XdkSourceModule(it.name, it.uri, resourceRoots = listOf(resources.toURI().toString()))
            }
        val consumer = source("Consumer", "module Consumer { package lib import Library; String run() = lib.text(); }", setOf("Library"))
        val independent = source("Independent", "module Independent { class Unrelated {} }")
        adapter().use { adapter ->
            adapter.replaceSourceModules(listOf(library, consumer, independent))
            assertThat(adapter.findWorkspaceSymbols("run")).hasSize(1)
            val stamp = Files.getLastModifiedTime(resource.toPath())
            resource.writeText("other")
            Files.setLastModifiedTime(resource.toPath(), stamp)
            assertThat(adapter.findWorkspaceSymbols("run")).hasSize(1)
            assertThat(compiled.getValue("Library").get()).isEqualTo(2)
            assertThat(compiled.getValue("Consumer").get()).isEqualTo(2)
            assertThat(compiled.getValue("Independent").get()).isEqualTo(1)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `retained graph navigation releases all compilation pools and ASTs`(diagnosticsFirst: Boolean) {
        CompilerTestSupport.configure()
        val observed = mutableListOf<WeakReference<Any>>()
        val roots = (0 until 32).map { source("Node$it", "module Node$it { class Box {} }") }
        XdkAdapter(
            { source, repository, errors -> EmbeddingSupport.instance().compileModule(source, repository, errors) },
            { sources, repository, errors ->
                assertThat(repository?.moduleNames).isEmpty()
                if (observed.size == 48) {
                    System.gc()
                    assertThat(observed.count { it.get() != null })
                        .describedAs("earlier compiler attempts retained while the graph is still compiling")
                        .isZero()
                }
                EmbeddingSupport.instance().compileModule(sources, repository, errors).also { result ->
                    observed += WeakReference(result)
                    result.pool()?.let { observed += WeakReference(it) }
                    result.sourceTrees().forEach { observed += WeakReference(it) }
                }
            },
            { _, _, _, _, _ -> error("No cursor analysis") },
        ).use { adapter ->
            adapter.replaceSourceModules(roots)
            if (diagnosticsFirst) assertThat(adapter.workspaceDiagnosticsAsync().join()).allMatch { it.success }
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(32)
            assertThat(observed).hasSizeGreaterThanOrEqualTo(96)
            await().atMost(Duration.ofSeconds(20)).untilAsserted {
                System.gc()
                assertThat(observed.count { it.get() != null }).describedAs("compiler objects retained by detached index").isZero()
            }
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(32)
            assertThat(observed).hasSize(96)
        }
    }

    @Test
    fun `retirement and close reject publication from an older index generation`() {
        val index = XdkNavigationIndex()
        val navigation = mapOf("graph" to XdkWorkspaceNavigation(emptyMap(), "graph", true, emptyMap()))
        val beforeEdit = index.snapshot()
        index.retire(emptySet())
        assertThat(index.publish(beforeEdit, emptyMap(), navigation)).isFalse()
        val beforeClose = index.snapshot()
        index.clear()
        assertThat(index.publish(beforeClose, emptyMap(), navigation)).isFalse()
        assertThat(index.snapshot().navigation).isEmpty()
        assertThat(index.publish(index.snapshot(), emptyMap(), navigation)).isTrue()
        index.retire(emptySet())
        assertThat(index.snapshot().navigation).isEmpty()
    }

    private fun adapter(): XdkAdapter {
        CompilerTestSupport.configure()
        return XdkAdapter(
            { source, repository, errors -> EmbeddingSupport.instance().compileModule(source, repository, errors) },
            { sources, repository, errors ->
                compiled.computeIfAbsent(sources.sourceFile.nameWithoutExtension) { AtomicInteger() }.incrementAndGet()
                EmbeddingSupport.instance().compileModule(sources, repository, errors)
            },
            { _, _, _, _, _ -> error("Navigation must not invoke cursor analysis") },
        )
    }

    private fun source(
        name: String,
        text: String,
        dependencies: Set<String> = emptySet(),
    ): XdkSourceModule =
        directory.resolve("$name.x").toFile().let {
            it.writeText(text)
            XdkSourceModule(name, it.canonicalFile.toURI().toString(), dependencies)
        }
}
