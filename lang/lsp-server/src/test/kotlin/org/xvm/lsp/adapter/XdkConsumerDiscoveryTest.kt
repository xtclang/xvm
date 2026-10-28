package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkConsumerDiscovery
import org.xvm.lsp.adapter.xdk.XdkRenameScope
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Consumer discovery finds the modules under chosen folders that import the graph, directly or through each
 * other, and reports name collisions instead of guessing. It installs nothing; the host adds what it wants.
 */
class XdkConsumerDiscoveryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `direct and indirect importers are found while unrelated modules and collisions are kept apart`() {
        val library = source("workspace/Library.x", LIBRARY)
        source("external/Consumer.x", CONSUMER)
        source("external/deeper/Indirect.x", "module Indirect { package consumer import Consumer; }")
        source("external/Unrelated.x", "module Unrelated {}")
        source("external/copy/Library.x", "module Library { package consumer import Consumer; }")
        source("external/a/Twice.x", "module Twice { package lib import Library; }")
        source("external/b/Twice.x", "module Twice { package lib import Library; }")
        val graph = listOf(XdkSourceModule("Library", library.toURI().toString()))

        val result = XdkConsumerDiscovery.discover(listOf(file("external")), graph, emptyMap()) { false }

        assertThat(result.modules.map { it.name to it.dependencies }).containsExactly(
            "Consumer" to setOf("Library"),
            "Indirect" to setOf("Consumer"),
        )
        assertThat(result.conflicts.map { it.name }).containsExactly("Library", "Twice", "Twice")
    }

    @Test
    fun `an unsaved buffer decides what a module imports and graph roots under the folder are not reported`() {
        val library = source("workspace/Library.x", LIBRARY)
        val unrelated = source("workspace/Unrelated.x", "module Unrelated {}")
        val graph = listOf(XdkSourceModule("Library", library.toURI().toString()))
        val overlays = mapOf(unrelated.toURI().toString() to "module Unrelated { package lib import Library; }")

        val result = XdkConsumerDiscovery.discover(listOf(file("workspace")), graph, overlays) { false }

        assertThat(result.modules.map { it.name }).containsExactly("Unrelated")
        assertThat(result.conflicts).isEmpty()
    }

    @Test
    fun `discovered consumers added to the explicit graph are renamed with their library`() {
        CompilerTestSupport.configure()
        val library = source("workspace/Library.x", LIBRARY)
        val consumer = source("external/Consumer.x", CONSUMER)
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library.toURI().toString())))
            val found = adapter.discoverConsumers(listOf(file("external")))
            assertThat(found.modules.map { it.name }).containsExactly("Consumer")

            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library.toURI().toString())) + found.modules)
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).isEmpty()
            val proposal =
                requireNotNull(
                    adapter.renameProposalAsync(library.toURI().toString(), 0, LIBRARY.indexOf("pick"), "choose").get(30, SECONDS),
                )
            assertThat(requireNotNull(proposal.scope).boundary).isEqualTo(XdkRenameScope.Boundary.CONFIGURED_GRAPH)
            assertThat(proposal.edit.changes.keys).containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
            assertThat(consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    private fun file(path: String): File = directory.toRealPath().resolve(path).toFile()

    private fun source(
        path: String,
        text: String,
    ): File =
        file(path).apply {
            parentFile.mkdirs()
            writeText(text)
        }

    private companion object {
        const val LIBRARY = "module Library { class Box { Int pick(Int input) = input; } }"
        const val CONSUMER = "module Consumer { package lib import Library; Int use(lib.Box box) = box.pick(1); }"
    }
}
