package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path

class XdkIndexLifecycleTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `joining graph views preserves document lambda import and expression facts`() {
        CompilerTestSupport.configure()
        val text =
            "module Joined { import ecstasy.maps.ListMap as MapImpl; " +
                "function Int(Int) create() { MapImpl<String, Int> map = new MapImpl(); return (Int input) -> input + map.size; } }"
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, "Joined.x"), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val before = result.semanticSnapshot()
        assertThat(before.lambdas).hasSize(1)
        assertThat(before.sourceLinks).hasSize(1)
        assertThat(before.imports).hasSize(1)
        val joined = SemanticModel.joined(listOf(before), emptyMap()).single()
        assertThat(joined.lambdas).isEqualTo(before.lambdas)
        assertThat(joined.sourceLinks).isEqualTo(before.sourceLinks)
        assertThat(joined.imports).isEqualTo(before.imports)
        assertThat(joined.expressions).isEqualTo(before.expressions)
        assertThat(joined.functionCalls).isEqualTo(before.functionCalls)
        assertThat(joined.occurrences).isEqualTo(before.occurrences)
        assertThat(joined.calls).isEqualTo(before.calls)
    }

    @Test
    fun `replacing binary indices source authority and graphs retires old hierarchy handles`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { class Box { Int pick(Int value) = value; } }"
        val first = source("first/Library.x", libraryText)
        val second = source("second/Library.x", libraryText)
        val sourceText = "module Library {\n    class Box { Int pick(Int value) = value + 1; }\n}"
        val editable = source("editable/Library.x", sourceText)
        val text =
            "module Consumer { package lib import Library; Int run(lib.Box box) = box.pick(1); " +
                "Int forward(lib.Box box) = run(box); }"
        val consumer = source("Consumer.x", text)
        val firstArtifact = artifact(libraryText, first)
        val secondArtifact = artifact(libraryText, second)
        val graph = XdkSourceModule("Consumer", consumer, setOf("Library"))
        val call = text.indexOf("pick")
        val run = text.indexOf("forward")
        XdkAdapter().use { adapter ->
            adapter.replaceDependencies(listOf(firstArtifact))
            adapter.replaceSourceModules(listOf(graph))
            assertThat(adapter.findDefinition(consumer, 0, call)?.uri).isEqualTo(first)
            val old = adapter.prepareCallHierarchy(consumer, 0, run).single()
            assertThat(
                adapter
                    .getOutgoingCalls(old)
                    .single()
                    .to.name,
            ).isEqualTo("run")

            adapter.replaceDependencies(listOf(secondArtifact))
            assertThat(adapter.getOutgoingCalls(old)).isEmpty()
            assertThat(adapter.findDefinition(consumer, 0, call)?.uri).isEqualTo(second)
            val indexed = adapter.prepareCallHierarchy(consumer, 0, run).single()
            assertThat(indexed.data).isNotEqualTo(old.data)
            assertThat(
                adapter
                    .getOutgoingCalls(indexed)
                    .single()
                    .to.name,
            ).isEqualTo("run")

            adapter.replaceDependencies(listOf(XdkDependency.fromBinary(secondArtifact.bytes())))
            assertThat(adapter.getOutgoingCalls(indexed)).isEmpty()
            assertThat(adapter.findDefinition(consumer, 0, call)).isNull()
            val binary = adapter.prepareCallHierarchy(consumer, 0, run).single()
            assertThat(
                adapter
                    .getOutgoingCalls(binary)
                    .single()
                    .to.name,
            ).isEqualTo("run")

            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", editable), graph))
            assertThat(adapter.getOutgoingCalls(binary)).isEmpty()
            assertThat(adapter.findDefinition(consumer, 0, call)?.uri).isEqualTo(editable)
            assertThat(adapter.findDefinition(consumer, 0, call)?.startLine).isEqualTo(1)
            val current = adapter.prepareCallHierarchy(consumer, 0, run).single()
            assertThat(
                adapter
                    .getOutgoingCalls(current)
                    .single()
                    .to.name,
            ).isEqualTo("run")

            adapter.replaceSourceModules(emptyList())
            assertThat(adapter.findWorkspaceSymbols("Box")).isEmpty()
            assertThat(adapter.getOutgoingCalls(current)).isEmpty()
        }
    }

    private fun source(
        path: String,
        text: String,
    ): String =
        directory.resolve(path).toFile().let {
            it.parentFile.mkdirs()
            it.writeText(text)
            it.canonicalFile.toURI().toString()
        }

    private fun artifact(
        text: String,
        uri: String,
    ): XdkDependency {
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, uri), null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.toDependency()
    }
}
