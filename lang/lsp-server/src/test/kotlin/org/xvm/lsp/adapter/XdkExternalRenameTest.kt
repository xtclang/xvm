package org.xvm.lsp.adapter

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkRenameScope
import org.xvm.lsp.adapter.xdk.XdkSourceModule

class XdkExternalRenameTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `explicit roots include closed external consumers and report the precise scope`() {
        val library = source("workspace/Library.x", LIBRARY)
        val consumer = source("external/Consumer.x", CONSUMER)
        val omitted = source("unknown/Other.x", CONSUMER.replace("Consumer", "Other"))
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(library.parent))
            adapter.replaceSourceModules(graph(library, consumer))
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).isEmpty()
            val proposal =
                requireNotNull(
                    adapter
                        .renameProposalAsync(
                            library.toURI().toString(),
                            0,
                            LIBRARY.indexOf("input"),
                            "value",
                        )
                        .get(30, SECONDS)
                )
            val scope = requireNotNull(proposal.scope)
            assertThat(scope.boundary).isEqualTo(XdkRenameScope.Boundary.CONFIGURED_GRAPH)
            assertThat(scope.modules.map { it.name }).containsExactly("Library", "Consumer")
            assertThat(scope.sourceUris)
                .containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
            assertThat(proposal.edit.changes.keys)
                .containsExactlyInAnyOrder(library.toURI().toString(), consumer.toURI().toString())
            assertThat(apply(CONSUMER, proposal.edit, consumer))
                .isEqualTo(CONSUMER.replace("input", "value"))
            assertThat(omitted.readText()).isEqualTo(CONSUMER.replace("Consumer", "Other"))
            assertThat(library.readText()).isEqualTo(LIBRARY)
            assertThat(consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    @Test
    fun `external unsaved consumers participate in proof and input revision`() {
        val library = source("workspace/Library.x", LIBRARY)
        val consumer = source("external/Consumer.x", CONSUMER)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(library.parent))
            adapter.replaceSourceModules(graph(library, consumer))
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).isEmpty()
            val before =
                requireNotNull(
                    adapter
                        .renameProposalAsync(
                            library.toURI().toString(),
                            0,
                            LIBRARY.indexOf("input"),
                            "value",
                        )
                        .get(30, SECONDS)
                )
            val overlay =
                CONSUMER.replace("box.pick(input = 1)", "box.pick(input = 1) + box.pick(input = 2)")
            assertThat(adapter.compile(consumer.toURI().toString(), overlay).diagnostics).isEmpty()
            val after =
                requireNotNull(
                    adapter
                        .renameProposalAsync(
                            library.toURI().toString(),
                            0,
                            LIBRARY.indexOf("input"),
                            "value",
                        )
                        .get(30, SECONDS)
                )
            assertThat(requireNotNull(after.scope).revision)
                .isNotEqualTo(requireNotNull(before.scope).revision)
            assertThat(apply(overlay, after.edit, consumer))
                .isEqualTo(overlay.replace("input", "value"))
            assertThat(consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    @Test
    fun `missing registered external consumer cannot silently narrow proof`() {
        val library = source("workspace/Library.x", LIBRARY)
        val missing = directory.toRealPath().resolve("missing/Consumer.x").toFile()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(graph(library, missing))
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).isEmpty()
            assertThat(
                    adapter.rename(library.toURI().toString(), 0, LIBRARY.indexOf("input"), "value")
                )
                .isNull()
        }
    }

    @Test
    fun `external disk changes during proof invalidate the proposal`() {
        val library = source("workspace/Library.x", LIBRARY)
        val consumer = source("external/Consumer.x", CONSUMER)
        CompilerTestSupport.configure()
        val query =
            XdkProjectQueries(
                XdkProject(graph(library, consumer)),
                emptyMap(),
                XdkDependencies(emptyList()),
                { sources, repository, errors ->
                    // Captured source inputs have already been fixed before the first compiler
                    // call.
                    consumer.writeText("\n$CONSUMER")
                    EmbeddingSupport.instance().compileModule(sources, repository, errors)
                },
                { false },
            )
        assertThat(
                query.renameProposal(
                    library.toURI().toString(),
                    0,
                    LIBRARY.indexOf("input"),
                    "value",
                )
            )
            .isNull()
        assertThat(library.readText()).isEqualTo(LIBRARY)
    }

    @Test
    fun `automatic discovery receipt does not claim external consumers`() {
        val library = source("workspace/Library.x", LIBRARY)
        val consumer = source("external/Consumer.x", CONSUMER)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(library.parent))
            assertThat(adapter.compile(library.toURI().toString(), LIBRARY).diagnostics).isEmpty()
            val proposal =
                requireNotNull(
                    adapter
                        .renameProposalAsync(
                            library.toURI().toString(),
                            0,
                            LIBRARY.indexOf("input"),
                            "value",
                        )
                        .get(30, SECONDS)
                )
            assertThat(requireNotNull(proposal.scope).boundary)
                .isEqualTo(XdkRenameScope.Boundary.DISCOVERED_GRAPH)
            assertThat(requireNotNull(proposal.scope).sourceUris)
                .containsExactly(library.toURI().toString())
            assertThat(proposal.edit.changes.keys).containsExactly(library.toURI().toString())
            assertThat(consumer.readText()).isEqualTo(CONSUMER)
        }
    }

    private fun source(
        path: String,
        text: String,
    ): File =
        directory.toRealPath().resolve(path).toFile().apply {
            parentFile.mkdirs()
            writeText(text)
        }

    private fun graph(
        library: File,
        consumer: File,
    ): List<XdkSourceModule> =
        listOf(
            XdkSourceModule("Library", library.toURI().toString()),
            XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
        )

    private fun apply(
        text: String,
        edit: WorkspaceEdit,
        file: File,
    ): String =
        edit.changes
            .getValue(file.toURI().toString())
            .sortedByDescending { it.range.start.column }
            .fold(text) { value, change ->
                value.replaceRange(
                    change.range.start.column,
                    change.range.end.column,
                    change.newText,
                )
            }

    private companion object {
        const val LIBRARY = "module Library { class Box { Int pick(Int input) = input; } }"
        const val CONSUMER =
            "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(input = 1); }"
    }
}
