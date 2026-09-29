package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.tool.ModuleInfo
import java.nio.file.Path

class CompilerDeclarationAnalysisTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `fresh declaration analysis does not validate bodies or masquerade as compilation`() {
        CompilerTestSupport.configure()
        val text = "module Headers { String broken() = 1; }"
        val errors = ErrorList()
        val headers = EmbeddingSupport.instance().analyzeDeclarations(Source(text, "Headers.x"), null, errors)
        assertThat(headers).isPresent()
        assertThat(errors.hasSeriousErrors()).isFalse()
        val normal = ErrorList()
        assertThat(EmbeddingSupport.instance().compileModule(Source(text, "Headers.x"), null, normal).succeeded()).isFalse()
        assertThat(normal.hasSeriousErrors()).isTrue()
    }

    @Test
    fun `declaration and syntax failures prevent exposing a queryable graph`() {
        CompilerTestSupport.configure()
        listOf("module Headers { Missing field; }", "module Headers {").forEach { text ->
            val errors = ErrorList()
            assertThat(EmbeddingSupport.instance().analyzeDeclarations(Source(text, "Headers.x"), null, errors)).isEmpty()
            assertThat(errors.hasSeriousErrors()).isTrue()
        }
    }

    @Test
    fun `declaration analysis obeys cancellation before parsing`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val cancelled = ErrorListener.cancellable(errors) { true }
        assertThat(EmbeddingSupport.instance().analyzeDeclarations(Source("broken", "Headers.x"), null, cancelled)).isEmpty()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `module tree declaration analysis retains member source identity`() {
        CompilerTestSupport.configure()
        val root = directory.resolve("Headers.x").toFile().apply { writeText("module Headers {}") }
        val member =
            directory.resolve("Headers/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box { String broken() = 1; }")
            }
        val errors = ErrorList()
        val headers = EmbeddingSupport.instance().analyzeDeclarations(ModuleInfo(root, false), null, errors).orElseThrow()
        assertThat(errors.hasSeriousErrors()).isFalse()
        assertThat(headers.sourceTrees().map { it.source.fileName }).contains(member.canonicalPath)
    }
}
