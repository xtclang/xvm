package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkWorkspaceDiscovery
import java.nio.file.Path
import java.util.concurrent.CancellationException

/**
 * Exercises source-module discovery, import graphs and open-buffer precedence. Healthy modules remain
 * queryable beside damaged ones, generated sources are excluded and explicit configuration retains authority.
 */
class XdkWorkspaceDiscoveryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `discovery reads module imports ignores generated and bundled sources and honors buffers`() {
        val base = source("Base.x", "module Base { class Box {} }")
        val app = source("App.x", "module App { package lib import Base; }")
        source("App/Member.x", "class Member {}")
        source("build/Generated.x", "module Generated {}")
        source("Xml.x", "module xml.xtclang.org {}")
        source("Comment.x", "// module Fake {}\nclass Ordinary {}")
        val modules =
            XdkWorkspaceDiscovery.scan(listOf(directory.toFile()), emptyMap(), emptyList()) {
                false
            }
        assertThat(modules.map { it.name }).containsExactly("App", "Base")
        assertThat(modules.first().dependencies).containsExactly("Base")
        val edited =
            XdkWorkspaceDiscovery.scan(
                listOf(directory.toFile()),
                mapOf(app to "module App {}", base to "module Base { class Box {} }"),
                modules,
            ) {
                false
            }
        assertThat(edited.first().dependencies).isEmpty()
        assertThatThrownBy {
            XdkWorkspaceDiscovery.scan(listOf(directory.toFile()), emptyMap(), modules) { true }
        }.isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `unopened graph resolves imports and indexes independent healthy modules despite a broken neighbor`() {
        val base = source("Base.x", "module Base { static Int answer() = 42; }")
        val appText = "module App { package lib import Base; Int run() = lib.answer(); }"
        val app = source("App.x", appText)
        source("Broken.x", "module Broken { Missing value; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(
                adapter
                    .findWorkspaceSymbols("answer")
                    .single()
                    .location.uri,
            ).isEqualTo(base)
            assertThat(adapter.compile(app, appText).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(app, 0, appText.indexOf("answer"))!!.uri)
                .isEqualTo(base)
            val added = source("Added.x", "module Added { class AddedType {} }")
            adapter.refreshDiscoveredSources()
            assertThat(
                adapter
                    .findWorkspaceSymbols("AddedType")
                    .single()
                    .location.uri,
            ).isEqualTo(added)
            directory.resolve("Added.x").toFile().delete()
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("AddedType")).isEmpty()
        }
    }

    @Test
    fun `explicit graphs win and invalid replacements preserve automatic discovery`() {
        val base = source("Base.x", "module Base { class Box {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val module = XdkSourceModule("Base", base)
            assertThatThrownBy { adapter.replaceSourceModules(listOf(module, module)) }
                .isInstanceOf(IllegalArgumentException::class.java)
            val other = source("Other.x", "module Other { class OtherBox {} }")
            adapter.refreshDiscoveredSources()
            assertThat(
                adapter
                    .findWorkspaceSymbols("OtherBox")
                    .single()
                    .location.uri,
            ).isEqualTo(other)
            adapter.replaceSourceModules(emptyList())
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("Box")).isEmpty()
            adapter.discoverSourceModules()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(2)
        }
    }

    @Test
    fun `file events bring discovery to what a full walk finds, reading only the paths they name`() {
        source("Base.x", "module Base { class Box {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))

            fun graph() = adapter.sourceModuleInputs().map { Triple(it.name, it.uri, it.dependencies) }.toSet()

            /** Applies the events, then checks that a walk of the whole workspace finds nothing more. */
            fun applied(vararg uris: String): Map<String, Set<String>> {
                assertThat(adapter.applyFileEvents(uris.toList())).describedAs("installed").isNotNull()
                val incremental = graph()
                adapter.refreshDiscoveredSources()
                assertThat(graph()).describedAs("a full walk after %s", uris.toList()).isEqualTo(incremental)
                return incremental.associate { (name, _, dependencies) -> name to dependencies }
            }
            val app = source("App.x", "module App {}")
            assertThat(applied(app)).isEqualTo(mapOf("App" to emptySet<String>(), "Base" to emptySet()))

            // a watcher may report only the folder that holds a new file
            val members = source("App/lib.x", "package lib import Base;").substringBeforeLast("/lib.x")
            assertThat(applied(members)).containsEntry("App", setOf("Base"))
            directory.resolve("App/lib.x").toFile().delete()
            assertThat(applied("$members/lib.x")).containsEntry("App", emptySet<String>())

            // a new folder with a module and its members, then the folder moved and deleted
            source("Nested/Tool.x", "module Tool {}")
            source("Nested/Tool/base.x", "package base import Base;")
            val nested = directory.resolve("Nested").toFile()
            assertThat(applied(nested.toURI().toString())).containsEntry("Tool", setOf("Base"))
            val moved = directory.resolve("Moved").toFile()
            assertThat(nested.renameTo(moved)).isTrue()
            assertThat(applied(nested.toURI().toString(), moved.toURI().toString())).containsEntry("Tool", setOf("Base"))
            assertThat(graph().single { it.first == "Tool" }.second).startsWith(moved.canonicalFile.toURI().toString())
            moved.deleteRecursively()
            assertThat(applied(moved.toURI().toString())).doesNotContainKey("Tool")

            // generated sources stay out, as in a full walk
            val generated = source("build/Generated.x", "module Generated {}")
            assertThat(applied(generated, generated.substringBeforeLast("/Generated.x"))).doesNotContainKey("Generated")

            // an open buffer outlives its file
            adapter.updateDocument(app, "module App {}")
            directory.resolve("App.x").toFile().delete()
            assertThat(applied(app)).containsKey("App")
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory.resolve(name).toFile().let {
            it.parentFile.mkdirs()
            it.writeText(text)
            it.canonicalFile.toURI().toString()
        }
}
