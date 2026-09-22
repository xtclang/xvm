package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport.Compilation
import org.xvm.asm.ErrorListener
import org.xvm.asm.FileStructure
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.Location
import java.util.concurrent.TimeUnit.SECONDS

class XdkDiagnosticTest {
    @Test
    fun `foreign source locations retain their identity`() {
        for (name in listOf("file:///Other.x", "untitled:Other.x")) {
            XdkAdapter { _, errs ->
                val foreign = Source("\n  broken", name)
                reportAtEnd(foreign, errs)
            }.use { adapter ->
                val diagnostic = adapter.compile(URI, "module Current {}").diagnostics.single()
                assertThat(diagnostic.location).isEqualTo(Location(name, 1, 8, 1, 8))
            }
        }
    }

    @Test
    fun `relative and unnamed foreign sources do not borrow current document positions`() {
        for (name in listOf(null, "Other.x")) {
            XdkAdapter { _, errs ->
                reportAtEnd(if (name == null) Source("\n  broken") else Source("\n  broken", name), errs)
            }.use { adapter ->
                val diagnostic = adapter.compile(URI, "module Current {}").diagnostics.single()
                assertThat(diagnostic.location).isEqualTo(Location(URI, 0, 0, 0, 0))
                assertThat(diagnostic.message).startsWith("In ${name ?: "an unidentified source"}:")
            }
        }
    }

    @Test
    fun `source offsets preserve UTF-16 columns CRLF and end of file`() {
        for (newline in listOf("\n", "\r\n")) {
            val text = "// first$newline// café 😀"
            XdkAdapter(::reportAtEnd).use { adapter ->
                val diagnostic = adapter.compile(URI, text).diagnostics.single()
                assertThat(diagnostic.location).isEqualTo(Location(URI, 1, "// café 😀".length, 1, "// café 😀".length))
            }
        }
    }

    @Test
    fun `an unexpected failure is not mislabeled as unavailable configuration`() {
        XdkAdapter { _, _ -> throw IllegalStateException("unexpected compiler failure") }.use { adapter ->
            assertThatThrownBy { adapter.compileAsync(URI, "module Current {}").get(10, SECONDS) }
                .hasCauseInstanceOf(IllegalStateException::class.java)
                .hasRootCauseMessage("unexpected compiler failure")
            assertThat(adapter.getCachedResult(URI)).isNull()
        }
    }

    @Test
    fun `actual compiler positions account for supplementary characters and line endings`() {
        CompilerTestSupport.configure()
        for (newline in listOf("\n", "\r\n")) {
            val line = "    Int read() { /* café 😀 */ return missing; }"
            val text = "module Unicode {$newline$line$newline}"
            XdkAdapter().use { adapter ->
                val diagnostics = adapter.compile(URI, text).diagnostics
                val start = line.indexOf("missing")
                assertThat(diagnostics.map { it.location }).contains(Location(URI, 1, start, 1, start + "missing".length))
                assertThat(diagnostics.map { it.code }).doesNotContain("EMB-5")
            }
        }
    }

    @Test
    fun `incomplete edits clear obsolete navigation and recover after correction`() {
        CompilerTestSupport.configure()
        XdkAdapter().use { adapter ->
            val valid = "module Editing { Int run() { return 1; } }"
            for (incomplete in listOf("", "module Editing {", "module Editing { void run() { console.", "module Editing { String s = \"")) {
                assertThat(adapter.compile(URI, valid).success).isTrue()
                val broken = adapter.compile(URI, incomplete)
                assertThat(broken.success).isFalse()
                assertThat(broken.diagnostics).isNotEmpty()
                assertThat(broken.diagnostics.map { it.code }).doesNotContain("EMB-5")
                assertThat(adapter.findWorkspaceSymbols("run")).isEmpty()
                val repaired = adapter.compile(URI, valid)
                assertThat(repaired.diagnostics).isEmpty()
                assertThat(adapter.findWorkspaceSymbols("run")).isNotEmpty()
            }
        }
    }

    private fun reportAtEnd(
        source: Source,
        errs: ErrorListener,
    ): Compilation {
        while (source.hasNext()) source.next()
        errs.error("PARSER-03", ErrorListener.`in`(source, source.position, source.position), "identifier")
        return Compilation.forFile(FileStructure("Fixture"))
    }

    private companion object {
        const val URI = "file:///Current.x"
    }
}
