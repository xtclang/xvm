package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkAutoImports
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.awaited
import org.xvm.lsp.model.Diagnostic
import java.nio.file.Path

/**
 * A workspace whose own module has a bundled XDK library's name, which then replaces the bundled one, as the XVM
 * repository's replace them all ([org.xvm.lsp.adapter.xdk.XdkLibraryReplacement]): the server treats the library as
 * the workspace's sources, not as the bundle.
 */
class XdkReplacedLibraryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename of a workspace library's public declaration edits the modules that import it`() {
        val json = "module json.xtclang.org { class Answer {} }"
        val app = "module App { package json import json.xtclang.org; json.Answer? find() = Null; }"
        val jsonUri = source("json", json)
        val appUri = source("App", app)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule(JSON, jsonUri),
                    XdkSourceModule("App", appUri, setOf(JSON)),
                ),
            )
            assertThat(adapter.compile(jsonUri, json).diagnostics).isEmpty()
            // App compiles against the workspace's json, which has Answer, and not the bundled one
            assertThat(adapter.compile(appUri, app).diagnostics).isEmpty()
            val at = json.positionOf("class Answer", "Answer")
            val edit = requireNotNull(adapter.rename(jsonUri, at.line, at.column, "Reply")) { "No rename of the workspace's json" }
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(jsonUri, appUri)
            assertThat(apply(json, edit.changes.getValue(jsonUri))).isEqualTo(json.replace("Answer", "Reply"))
            assertThat(apply(app, edit.changes.getValue(appUri))).isEqualTo(app.replace("Answer", "Reply"))
        }
    }

    @Test
    fun `import candidates for a replaced library come from the workspace's compiled library, not the bundle`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance().compileModule(Source("module json.xtclang.org { class WorkspaceOnly {} }", "json.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        // the server compiles a workspace module of a library's name from source, and indexes what it can import
        val artifact = compilation.toDependency()
        assertThat(artifact.imports.map { it.path }).containsExactly("WorkspaceOnly")
        val facts = CompilerRenameFacts(emptyList(), emptyMap())
        val replaced = mapOf(JSON to artifact.imports)
        assertThat(XdkAutoImports.targets("WorkspaceOnly", facts, replaced).map { it.module }).containsExactly(JSON)
        // the bundled json's Parser is not offered once the workspace replaces json, and the bundled xml's still is
        assertThat(XdkAutoImports.targets("Parser", facts, replaced).map { it.module })
            .doesNotContain(JSON)
            .contains("xml.xtclang.org")
        assertThat(XdkAutoImports.targets("Parser", facts).map { it.module }).contains(JSON)
    }

    @Test
    fun `a workspace library with errors says that the server's compiler may be older than the repository's`() {
        source("json", "module json.xtclang.org { Missing value; }")
        source("xml", "module xml.xtclang.org {}")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val reports = adapter.workspaceDiagnosticsAsync().awaited().flatMap { it.diagnostics }

            fun codes(module: String) = reports.filter { it.location.uri.endsWith("/$module.x") }.map { it.code }
            assertThat(codes("json")).contains(Diagnostic.Codes.REPLACED_LIBRARY)
            assertThat(codes("xml")).doesNotContain(Diagnostic.Codes.REPLACED_LIBRARY)
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory
            .resolve("$name.x")
            .toFile()
            .apply { writeText(text) }
            .canonicalFile
            .toURI()
            .toString()

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }

    private companion object {
        const val JSON = "json.xtclang.org"
    }
}
