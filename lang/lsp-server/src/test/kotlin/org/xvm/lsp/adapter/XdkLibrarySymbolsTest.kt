package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.matchRank
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.awaited
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.server.XtcLanguageServer
import org.xvm.lsp.server.editorInitializeParams
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Go to Symbol in Workspace finds the libraries' declarations after the workspace's own: the bundled libraries' and the
 * attached sources' types, methods and properties, where Go to Definition opens them, at most a cap of them.
 */
class XdkLibrarySymbolsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a library type and a library member are found by name, each at its name in its library's source`() {
        CompilerTestSupport.configure()
        XdkAdapter().use { adapter ->
            val type = adapter.findLibrarySymbols("StringBuffer").first()
            assertThat(listOf(type.name, type.kind)).containsExactly("StringBuffer", SymbolKind.CLASS)
            assertThat(type.location.uri).endsWith("/ecstasy/text/StringBuffer.x")
            assertThat(named(type)).isEqualTo("StringBuffer")
            val member = adapter.findLibrarySymbols("getOrDefault").single()
            assertThat(listOf(member.name, member.kind)).containsExactly("getOrDefault", SymbolKind.METHOD)
            assertThat(member.location.uri).endsWith("/ecstasy/maps/Map.x")
            assertThat(named(member)).isEqualTo("getOrDefault")
            // a read-only library document, as Go to Definition's
            assertThat(adapter.readOnlyDocument(member.location.uri)).isNotNull()
        }
    }

    @Test
    fun `a query that most declarations match lists the cap of them, the closest first, and a letter lists none`() {
        CompilerTestSupport.configure()
        XdkAdapter().use { adapter ->
            val found = adapter.findLibrarySymbols("to")
            assertThat(found).hasSize(XdkLibrarySources.SYMBOL_LIMIT)
            // those named to first, then those whose names start with it, then the rest
            assertThat(found.map { matchRank(it.name, "to") }).isSorted()
            assertThat(adapter.findLibrarySymbols("t")).isEmpty()
            assertThat(adapter.findLibrarySymbols("")).isEmpty()
        }
    }

    @Test
    fun `an attached dependency's declaration is found, in its read-only snapshot, before the bundled libraries'`() {
        CompilerTestSupport.configure()
        val text = "module Shelving { static Int toShelf(Int n) = n; }"
        val sources = Files.createDirectories(directory.resolve("sources"))
        Files.writeString(sources.resolve("Shelving.x"), text)
        val errors = ErrorList()
        val compiled = EmbeddingSupport.instance().compileModule(Source(text, "Shelving.x"), null, errors)
        assertThat(compiled.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val attached = XdkDependency.fromBinary(compiled.toDependency().bytes()).withSources(listOf(sources.toFile()))
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(attached))
            val found = adapter.findLibrarySymbols("toShelf")
            assertThat(found.map { it.name to it.kind }).containsExactly("toShelf" to SymbolKind.METHOD)
            val uri = found.single().location.uri
            assertThat(uri).isIn(attached.documents.keys)
            assertThat(adapter.readOnlyDocument(uri)?.text).isEqualTo(text)
            // the attached library's first, as the workspace's dependency, then the bundled libraries'
            assertThat(adapter.findLibrarySymbols("to").first().name).isEqualTo("toShelf")
        }
    }

    @Test
    fun `the workspace's own match is listed before a library's`() {
        CompilerTestSupport.configure()
        val app = directory.resolve("App.x").toFile().apply { writeText("module App { static Int getOrDefault(Int value) = value; }") }
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server.initialize(editorInitializeParams()).awaited()
            server.replaceCompilerSourceModules(listOf(XdkSourceModule("App", app.toURI().toString())))
            val found = server.workspaceService.symbol(WorkspaceSymbolParams("getOrDefault")).awaited()
            val uris = if (found.isLeft) found.left.map { it.location.uri } else found.right.map { it.location.map({ it.uri }, { it.uri }) }
            assertThat(uris).hasSize(2)
            assertThat(Path.of(URI(uris[0])).toRealPath()).isEqualTo(app.toPath().toRealPath())
            assertThat(uris[1]).endsWith("/ecstasy/maps/Map.x")
        }
    }

    /** The text at [symbol]'s range, in its document on disk. */
    private fun named(symbol: SymbolInfo): String {
        val location = symbol.location
        val line = Files.readAllLines(Path.of(URI(location.uri)))[location.startLine]
        return line.substring(location.startColumn, location.endColumn)
    }
}
