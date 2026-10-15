package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import java.io.File
import java.nio.file.Path

/**
 * A module whose dependency fails is not compiled, so it reports only why. Its own syntax still parses, so
 * the outline, folding and selection stay available while the dependency is broken; semantic views do not
 * guess.
 */
class XdkBlockedModuleTest {
    @TempDir lateinit var directory: Path

    private fun write(
        name: String,
        text: String,
    ): File =
        directory
            .toRealPath()
            .resolve(name)
            .toFile()
            .also { it.writeText(text) }

    private fun names(symbols: List<SymbolInfo>): List<String> = symbols.flatMap { listOf(it.name) + names(it.children) }

    @Test
    fun `a consumer of a failed dependency keeps syntax views and reports only the failure`() {
        CompilerTestSupport.configure()
        val library = write("Library.x", BROKEN_LIBRARY)
        val consumer = write("Consumer.x", CONSUMER)
        val uri = consumer.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", uri, setOf("Library")),
                ),
            )
            val result = adapter.compile(uri, CONSUMER)
            assertThat(result.success).isFalse()
            // the library publishes its own error; the consumer says only why it was not compiled
            assertThat(result.diagnostics.filter { it.location.uri == uri }.map { it.code })
                .containsExactly(Diagnostic.Codes.DEPENDENCY_FAILED)

            assertThat(names(adapter.getCachedResult(uri)!!.symbols)).contains("Consumer", "Box", "read")
            assertThat(adapter.getFoldingRanges(uri)).isNotEmpty()
            val read = CONSUMER.lines().indexOfFirst { "Int read()" in it }
            assertThat(adapter.getSelectionRanges(uri, listOf(Position(read, 12)))).isNotEmpty()
            assertThat(adapter.getHoverInfo(uri, read, 12)).isNull()

            // repairing the dependency restores the full analysis
            library.writeText(LIBRARY)
            adapter.didChangeWatchedFile(library.toURI().toString(), 2)
            assertThat(adapter.compile(uri, "\n$CONSUMER").success).isTrue()
        }
    }

    private companion object {
        const val LIBRARY = "module Library { static Int value() = 1; }"
        const val BROKEN_LIBRARY = "module Library { static Int value() = \"text\"; }"
        const val CONSUMER =
            """module Consumer {
    package lib import Library;

    class Box {
        Int read() {
            return lib.value();
        }
    }
}
"""
    }
}
