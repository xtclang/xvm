package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class XdkLiveWorkspaceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unsaved imports and new module buffers update the discovered graph and close restores disk`() {
        val library = source("Library", "module Library { static String answer()=\"ok\"; }")
        val original = "module App {}"
        val app = source("App", original)
        val changed = "module App { package lib import Library; String run()=lib.answer(); }"
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(app, original).success).isTrue()
            assertThat(adapter.compile(app, changed).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(app, 0, changed.indexOf("answer"))!!.uri).isEqualTo(library)
            val fresh =
                directory
                    .resolve("Fresh.x")
                    .toFile()
                    .canonicalFile
                    .toURI()
                    .toString()
            assertThat(adapter.compile(fresh, "module Fresh { class OnlyInBuffer {} }").success).isTrue()
            assertThat(adapter.findWorkspaceSymbols("OnlyInBuffer")).hasSize(1)
            adapter.closeDocument(fresh)
            assertThat(adapter.findWorkspaceSymbols("OnlyInBuffer")).isEmpty()
            adapter.closeDocument(app)
            assertThat(adapter.compile(app, original).success).isTrue()
            assertThat(directory.resolve("App.x").toFile().readText()).isEqualTo(original)
        }
    }

    @Test
    fun `folder changes update imports and an explicit graph keeps authority`() {
        val first = directory.resolve("first").toFile().also { it.mkdirs() }
        val second = directory.resolve("second").toFile().also { it.mkdirs() }
        val app = first.resolve("App.x").also { it.writeText("module App { package lib import Library; String run()=lib.answer(); }") }
        second.resolve("Library.x").writeText("module Library { static String answer()=\"ok\"; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(first.path))
            assertThat(adapter.compile(app.toURI().toString(), app.readText()).success).isFalse()
            adapter.changeWorkspaceFolders(listOf(second.toURI().toString()), emptyList())
            assertThat(adapter.compile(app.toURI().toString(), app.readText()).success).isTrue()
            adapter.changeWorkspaceFolders(emptyList(), listOf(second.toURI().toString()))
            assertThat(adapter.compile(app.toURI().toString(), app.readText()).success).isFalse()
            adapter.replaceSourceModules(emptyList())
            adapter.changeWorkspaceFolders(listOf(second.toURI().toString()), emptyList())
            assertThat(adapter.findWorkspaceSymbols("answer")).isEmpty()
            adapter.discoverSourceModules()
            assertThat(adapter.findWorkspaceSymbols("answer")).hasSize(1)
        }
    }

    @Test
    fun `detached graph results are reused and closed file edits invalidate the cache`() {
        CompilerTestSupport.configure()
        val text = "module Healthy { class Base {} class Child extends Base {} }"
        val root = source("Healthy", text)
        source("Neighbor", "module Neighbor {}")
        val attempts = AtomicInteger()
        XdkAdapter(
            { source, errors -> EmbeddingSupport.instance().compileModule(source, null, errors) },
            { sources, errors ->
                attempts.incrementAndGet()
                EmbeddingSupport.instance().compileModule(sources, null, errors)
            },
        ).use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val item = adapter.prepareTypeHierarchy(root, 0, text.indexOf("Base")).single()
            assertThat(attempts.get()).isEqualTo(2)
            assertThat(adapter.getSubtypes(item).single().name).isEqualTo("Child")
            assertThat(adapter.findWorkspaceSymbols("Child")).hasSize(1)
            assertThat(adapter.findReferences(root, 0, text.indexOf("Base"), true)).hasSize(2)
            assertThat(attempts.get()).isEqualTo(2)
            source("Neighbor", "module Neighbor { Missing broken; }")
            assertThat(adapter.getSubtypes(item)).isEmpty()
            val current = adapter.prepareTypeHierarchy(root, 0, text.indexOf("Base")).single()
            assertThat(adapter.getSubtypes(current).single().name).isEqualTo("Child")
            assertThat(adapter.findReferences(root, 0, text.indexOf("Base"), true)).isEmpty()
            assertThat(adapter.rename(root, 0, text.indexOf("Base"), "Renamed")).isNull()
            assertThat(adapter.getCachedResult(root)).isNull()
        }
    }

    @Test
    fun `invalid unsaved graphs clear facts report diagnostics and recover from another file`() {
        val app = source("App", "module App {}")
        val library = source("Library", "module Library { class Target {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(app, "module App {}").success).isTrue()
            assertThat(adapter.findWorkspaceSymbols("Target")).hasSize(1)
            assertThat(adapter.compile(app, "module Library {}").diagnostics).anyMatch { it.code == "SOURCE-GRAPH" }
            assertThat(adapter.findWorkspaceSymbols("Target")).isEmpty()
            // The rejected edited header remains in the catalog. Correct a different file.
            assertThat(adapter.compile(library, "module Renamed { class Target {} }").success).isTrue()
            assertThat(adapter.findWorkspaceSymbols("Target")).hasSize(1)
            assertThat(adapter.compile(app, "module Library { package other import Renamed; }").success).isTrue()
            assertThat(adapter.compile(library, "module Renamed { package other import Library; }").diagnostics)
                .anyMatch { it.code == "SOURCE-GRAPH" }
            adapter.closeDocument(library)
            assertThat(adapter.compile(app, "module App {}").success).isTrue()
            assertThat(adapter.findWorkspaceSymbols("Target")).hasSize(1)
        }
    }

    @Test
    fun `a missing source dependency cannot use an older host binary during partial navigation`() {
        CompilerTestSupport.configure()
        val library =
            EmbeddingSupport
                .instance()
                .compileModule(
                    Source("module Missing { class Base {} }"),
                    null,
                    ErrorList(),
                ).toDependency()
        val healthy = source("Healthy", "module Healthy { class Visible {} }")
        val consumer = source("Consumer", "module Consumer { package lib import Missing; class Hidden extends lib.Base {} }")
        val missing = directory.resolve("Missing.x").toUri().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(library))
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Healthy", healthy),
                    XdkSourceModule("Missing", missing),
                    XdkSourceModule("Consumer", consumer, setOf("Missing")),
                ),
            )
            assertThat(adapter.findWorkspaceSymbols("Visible")).hasSize(1)
            assertThat(adapter.findWorkspaceSymbols("Hidden")).isEmpty()
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory.resolve("$name.x").toFile().let {
            it.writeText(text)
            it.canonicalFile.toURI().toString()
        }
}
