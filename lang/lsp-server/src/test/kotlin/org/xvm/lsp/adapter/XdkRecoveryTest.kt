package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.tool.ModuleInfo
import java.nio.file.Path

class XdkRecoveryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `recovered syntax is available without an assembled or validated module`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result =
            EmbeddingSupport.instance().compileModule(
                Source("module Editing { void run() { console.; } Int after = 1; }", URI),
                null,
                errors,
            )
        assertThat(errors.errors).isNotEmpty()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(result.file()).isNull()
        assertThat(result.pool()).isNull()
        assertThat(result.sourceTrees()).hasSize(1)
        assertThat(
            result
                .sourceTrees()
                .single()
                .source.fileName,
        ).isEqualTo(URI)
        val semantics = result.semanticSnapshot()
        assertThat(semantics.status).isEqualTo(SemanticModel.Status.UNAVAILABLE)
        assertThat(semantics.symbols).isEmpty()
        assertThat(semantics.occurrences).isEmpty()
        assertThat(semantics.types).isEmpty()
    }

    @Test
    fun `unfinished bodies retain current outline folding and UTF-16 selection without stale semantics`() {
        for (newline in listOf("\n", "\r\n")) {
            XdkAdapter().use { adapter ->
                val valid = "module Editing { Int old = 1; Int read() { return old; } }"
                assertThat(adapter.compile(URI, valid).success).isTrue()
                assertThat(adapter.findDefinition(URI, 0, valid.lastIndexOf("old"))).isNotNull()
                val text = "module Editing {$newline    void current() {$newline        class Local {} /* café 😀 */ console."
                val result = adapter.compile(URI, text)
                assertThat(result.success).isFalse()
                assertThat(result.diagnostics.map { it.code }).doesNotContain("EMB-5")
                assertThat(adapter.findWorkspaceSymbols("old")).isEmpty()
                assertThat(adapter.findWorkspaceSymbols("current")).hasSize(1)
                assertThat(adapter.findWorkspaceSymbols("Local")).hasSize(1)
                assertThat(adapter.getFoldingRanges(URI)).anySatisfy {
                    assertThat(it.startLine).isEqualTo(1)
                    assertThat(it.endLine).isEqualTo(2)
                }
                val cursor = Position(2, text.lines().last().length)
                val selection = adapter.getSelectionRanges(URI, listOf(cursor)).single()
                assertThat(selection.range.end).isEqualTo(cursor)
                assertThat(selection.range.start.line).isLessThan(cursor.line)
                assertThat(adapter.findDefinition(URI, 1, 10)).isNull()
                assertThat(adapter.findReferences(URI, 1, 10, true)).isEmpty()
                assertThat(adapter.prepareTypeHierarchy(URI, 0, 8)).isEmpty()
                assertThat(adapter.compile(URI, valid).success).isTrue()
                assertThat(adapter.findWorkspaceSymbols("current")).isEmpty()
                assertThat(adapter.findDefinition(URI, 0, valid.lastIndexOf("old"))).isNotNull()
                adapter.closeDocument(URI)
                assertThat(adapter.findWorkspaceSymbols("")).isEmpty()
            }
        }
    }

    @Test
    fun `a broken member retains independent syntax for siblings without linking partial trees`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Project.x").toFile()
        root.writeText("module Project { class Base {} }")
        val member = directory.resolve("Project/Child.x").toFile()
        member.parentFile.mkdirs()
        member.writeText("class Child extends Base { void changing() { this.")
        val sibling = directory.resolve("Project/Sibling.x").toFile()
        sibling.writeText("class Sibling { Int value = 1; }")
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val input = ModuleInfo(root, false)
        val compilation = EmbeddingSupport.instance().compileModule(input, null, errors)
        assertThat(compilation.parsed()).isNull()
        assertThat(compilation.file()).isNull()
        assertThat(compilation.sourceTrees().map { it.source.fileName }).containsExactlyInAnyOrder(root.path, member.path, sibling.path)
        assertThat(input.parsedSources).containsExactlyElementsOf(compilation.sourceTrees())

        XdkAdapter().use { adapter ->
            val uri = root.toURI().toString()
            val result = adapter.compile(uri, root.readText())
            assertThat(result.diagnostics).isNotEmpty().allSatisfy { assertThat(it.location.uri).isEqualTo(member.toURI().toString()) }
            assertThat(
                adapter
                    .findWorkspaceSymbols("Base")
                    .single()
                    .location.uri,
            ).isEqualTo(uri)
            assertThat(
                adapter
                    .findWorkspaceSymbols("changing")
                    .single()
                    .location.uri,
            ).isEqualTo(member.toURI().toString())
            assertThat(
                adapter
                    .findWorkspaceSymbols("Sibling")
                    .single()
                    .location.uri,
            ).isEqualTo(sibling.toURI().toString())
            assertThat(adapter.findDefinition(member.toURI().toString(), 0, member.readText().indexOf("Base"))).isNull()
            val rootSymbols = adapter.getCachedResult(uri)!!.symbols
            assertThat(rootSymbols.single().children.map { it.name }).containsExactly("Base")
        }
    }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
