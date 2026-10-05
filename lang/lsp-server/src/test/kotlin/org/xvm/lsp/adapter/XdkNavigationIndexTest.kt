package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkNavigationIndex
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

class XdkNavigationIndexTest {
    @TempDir lateinit var directory: Path

    private val compiled = ConcurrentHashMap<String, AtomicInteger>()

    @Test
    fun `navigation reuses 32 independent roots and rebuilds only an edited dependency closure`() {
        val roots = (0 until 32).map { source("Node$it", "module Node$it { class Box {} }") }
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
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(32)
            val coldTime = cold.elapsedNow()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(33)
            val warm = TimeSource.Monotonic.markNow()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(32)
            val warmTime = warm.elapsedNow()
            assertThat(compiled.values.sumOf { it.get() }).isEqualTo(33)
            val hierarchy = adapter.prepareTypeHierarchy(roots.first().uri, 0, 21).single()
            assertThat(adapter.getSubtypes(hierarchy).single().uri).isEqualTo(consumer.uri)

            source("Node0", "module Node0 { class Box { Int added = 1; } }")
            assertThat(adapter.findWorkspaceSymbols("added")).hasSize(1)
            assertThat(compiled.getValue("Node0").get()).isEqualTo(2)
            assertThat(compiled.getValue("Consumer").get()).isEqualTo(2)
            assertThat(compiled.filterKeys { it != "Node0" && it != "Consumer" }.values).allMatch { it.get() == 1 }
            assertThat(adapter.getSubtypes(hierarchy)).isEmpty()

            // Broken independent roots allow partial navigation but never complete references.
            source("Node31", "module Node31 { Missing broken; }")
            assertThat(adapter.findWorkspaceSymbols("Child")).hasSize(1)
            assertThat(adapter.findReferences(roots.first().uri, 0, 21, true)).isEmpty()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(31)
            assertThat(compiled.getValue("Node31").get()).isEqualTo(2)
            source("Node31", "module Node31 { class Box {} }")
            assertThat(adapter.findReferences(roots.first().uri, 0, 21, true)).hasSize(2)
            assertThat(compiled.getValue("Node31").get()).isEqualTo(3)

            adapter.replaceSourceModules(graph.filter { it.name != "Node31" })
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(31)
            adapter.replaceSourceModules(graph)
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(32)
            assertThat(compiled.getValue("Node31").get()).isEqualTo(4)
            println(
                "Navigation graph: roots=33, cold=$coldTime, warm=$warmTime, initialCompiles=33, unchangedCompiles=0, editedClosureCompiles=2",
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
    fun `retirement and close reject publication from an older index generation`() {
        val index = XdkNavigationIndex()
        val beforeEdit = index.snapshot()
        index.retire(emptySet())
        assertThat(index.publish(beforeEdit, emptyMap())).isFalse()
        val beforeClose = index.snapshot()
        index.clear()
        assertThat(index.publish(beforeClose, emptyMap())).isFalse()
        assertThat(index.publish(index.snapshot(), emptyMap())).isTrue()
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
