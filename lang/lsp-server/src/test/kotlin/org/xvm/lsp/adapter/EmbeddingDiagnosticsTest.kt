package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.FileRepository
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.Source
import org.xvm.tool.ModuleInfo
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class EmbeddingDiagnosticsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a non-module root reports its source diagnostic instead of an internal failure`(fileInput: Boolean) {
        CompilerTestSupport.configure()
        val text = "class NotModule {}"
        val file = directory.resolve("NotModule.x").toFile().canonicalFile
        val errors = ErrorList()
        val support = EmbeddingSupport.instance()
        val result =
            if (fileInput) {
                file.writeText(text)
                support.compileModule(ModuleInfo(file, false), null, errors)
            } else {
                support.compileModule(Source(text, file.path), null, errors)
            }
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code })
            .containsExactly(EmbeddingSupport.ERR_MODULE_SOURCE)
        val site = errors.errors.single().site() as ErrorListener.Site.In
        assertThat(site.source().fileName).isEqualTo(file.path)
        assertThat(site.source().toString(site.lPosStart(), site.lPosEnd())).isEqualTo(text)
    }

    @ParameterizedTest
    @ValueSource(strings = ["module FileAudit {}", "module FileAudit { void broken( { }"])
    fun `discovery with an unreadable adjacent binary preserves source compilation diagnostics`(text: String) {
        CompilerTestSupport.configure()
        val file = directory.resolve("FileAudit.x").toFile().canonicalFile
        file.writeText(text)
        directory.resolve("FileAudit.xtc").toFile().writeText("corrupt")
        val sources = ModuleInfo(file, false)
        assertThat(sources.qualifiedModuleName).isEqualTo("FileAudit")
        assertThat(sources.moduleVersion).isNull()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(sources, null, errors)
        val valid = text == "module FileAudit {}"
        assertThat(result.succeeded()).isEqualTo(valid)
        assertThat(errors.hasSeriousErrors()).isEqualTo(!valid)
        assertThat(errors.errors).noneMatch { it.code == "EMB-5" }
        errors.errors.forEach {
            assertThat(it.code).startsWith("PARSER-")
            assertThat((it.site() as ErrorListener.Site.In).source().fileName).isEqualTo(file.path)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `an incompatible candidate does not poison fallback or ordinary source diagnostics`(singleFile: Boolean) {
        CompilerTestSupport.configure()
        val file = directory.resolve("Old.xtc").toFile()
        FileStructure("Old").writeTo(file)
        val bytes = file.readBytes()
        ByteBuffer.wrap(bytes).putInt(2 * Int.SIZE_BYTES, FileStructure.getToolMinorVersion() - 1)
        Files.write(file.toPath(), bytes)
        val repository: ModuleRepository =
            if (singleFile) FileRepository(file, true) else DirRepository(directory.toFile(), true)
        val support = EmbeddingSupport.instance()

        for (text in listOf("module Test {}", "module Test { void broken( { }")) {
            val errors = ErrorList()
            val result = support.compileModule(Source(text), repository, errors)
            assertThat(result.succeeded()).isEqualTo(text == "module Test {}")
            assertThat(repository.readFailures).containsKey(file)
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }

        val errors = ErrorList()
        val result =
            support.compileModule(
                Source("module Missing { package lib import Unavailable; }"),
                repository,
                errors,
            )
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code }).contains("COMPILER-24", "EMB-5")
        assertThat(errors.errors.filter { it.code == "EMB-5" }.map { it.message })
            .anyMatch { it.contains(file.path) && it.contains("Unsupported .xtc version") }
    }

    @Test
    fun `cancellation stops a valid token stream before parsing the rest of the document`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        var reads = 0
        val text =
            "module Cancelled { " + (1..4000).joinToString(" ") { "Int value$it = $it;" } + " }"
        val source =
            object : Source(text) {
                override fun next(): Char {
                    reads++
                    return super.next()
                }
            }
        val result =
            EmbeddingSupport
                .instance()
                .compileModule(source, null, ErrorListener.cancellable(errors) { reads >= 80 })
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(reads).isBetween(80, 120)
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `unexpected compiler failures are not hidden by an earlier source error`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            object : Source("module Broken {}", "file:///Broken.x") {
                override fun next(): Char {
                    errors.error(
                        "PARSER-03",
                        ErrorListener.`in`(this, 0, 1),
                        "injected source error",
                    )
                    throw IllegalStateException("injected compiler failure")
                }
            }
        val result = EmbeddingSupport.instance().compileModule(source, null, errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors.map { it.code }).contains("PARSER-03", "EMB-5")
    }

    @Test
    fun `cancellation before compilation does not read source or invent a diagnostic`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            object : Source("module Unneeded {}") {
                override fun next(): Char = error("cancelled compilation must not read source")
            }
        val result =
            EmbeddingSupport
                .instance()
                .compileModule(source, null, ErrorListener.cancellable(errors) { true })
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `an empty editor buffer reports a source problem without an internal failure`() {
        CompilerTestSupport.configure()
        for (text in listOf("", "// no module yet")) {
            val errors = ErrorList()
            val result =
                EmbeddingSupport
                    .instance()
                    .compileModule(Source(text, "file:///Empty.x"), null, errors)
            assertThat(result.succeeded()).isFalse()
            assertThat(errors.hasSeriousErrors()).isTrue()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }
    }
}
