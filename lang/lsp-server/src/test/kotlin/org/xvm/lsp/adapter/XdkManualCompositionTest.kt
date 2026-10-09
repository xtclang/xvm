package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkManualCompositionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `manual covariant mixin redirects navigate to written source methods`() {
        val text = manualCompositionFixture("mixinTests")
        val uri = source("mixinTests", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val root = text.positionOf("Root self();", "self")
            val base = text.positionOf("Base self()", "self")
            val implementations = adapter.findImplementation(uri, root.line, root.column)
            assertThat(implementations.map { Position(it.startLine, it.startColumn) }).contains(base)
            implementations.forEach { target ->
                assertThat(target.uri).isEqualTo(uri)
                assertThat(text.lines()[target.startLine].substring(target.startColumn, target.endColumn)).isEqualTo("self")
            }
            assertThat(adapter.getCachedResult(uri)!!.diagnostics).isEmpty()
        }
    }

    @Test
    fun `manual interface delegation never invents a runtime receiver implementation`() {
        val text = manualCompositionFixture("delegationTests")
        val uri = source("delegationTests", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("String showText();", "showText")
            val targets = adapter.findImplementation(uri, at.line, at.column)
            assertThat(targets.map { Position(it.startLine, it.startColumn) })
                .containsExactlyInAnyOrder(
                    text.positionOf("@Override String showText() = name;", "showText"),
                    text.positionOf("@Override String showText() = super()", "showText"),
                )
            targets.forEach { target ->
                val line = text.lines()[target.startLine]
                assertThat(line.substring(target.startColumn, target.endColumn)).isEqualTo("showText")
                assertThat(line).doesNotContain("delegates")
            }
        }
    }

    private fun source(
        name: String,
        text: String,
    ): String =
        directory
            .resolve("$name.x")
            .toFile()
            .also { it.writeText(text) }
            .canonicalFile
            .toURI()
            .toString()
}
