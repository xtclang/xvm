package org.xvm.lsp.adapter

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.treesitter.SemanticTokenLegend

class XdkInitializerTest {
    @Test
    fun `folded initializer participates in hover navigation references tokens and complete rename`(
        @TempDir directory: Path
    ) {
        val source = "module Folded { Int value = 1; Int copy = value; Int run() = value; }"
        val path = directory.resolve("Folded.x")
        Files.writeString(path, source)
        val uri = path.toUri().toString()
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(XdkSourceModule("Folded", uri)))
            assertThat(adapter.compile(uri, source).success).isTrue()
            val offset = source.indexOf("value; Int run")
            assertThat(adapter.getHoverInfo(uri, 0, offset)).contains("Int")
            assertThat(adapter.findDefinition(uri, 0, offset)?.startColumn)
                .isEqualTo(source.indexOf("value"))
            assertThat(adapter.findReferences(uri, 0, offset, true)).hasSize(3)
            val tokens =
                requireNotNull(adapter.getSemanticTokens(uri))
                    .data
                    .chunked(5)
                    .runningFold(listOf(0, 0, 0, 0, 0)) { previous, delta ->
                        listOf(
                            previous[0] + delta[0],
                            if (delta[0] == 0) previous[1] + delta[1] else delta[1],
                        ) + delta.drop(2)
                    }
                    .drop(1)
            val reference = tokens.single { it[0] == 0 && it[1] == offset }
            assertThat(SemanticTokenLegend.tokenTypes[reference[3]]).isEqualTo("property")
            assertThat(adapter.prepareRename(uri, 0, offset)).isNotNull()
            val edits =
                requireNotNull(adapter.rename(uri, 0, offset, "number")).changes.getValue(uri)
            assertThat(edits).hasSize(3)
            val renamed =
                edits
                    .sortedByDescending { it.range.start.column }
                    .fold(source) { text, edit ->
                        text.replaceRange(
                            edit.range.start.column,
                            edit.range.end.column,
                            edit.newText,
                        )
                    }
            assertThat(renamed).doesNotContain("value").contains("copy = number")
            assertThat(adapter.compile(uri, renamed).success).isTrue()
            adapter.closeDocument(uri)
            assertThat(
                    adapter.compile(uri, source.replace("copy = value", "copy = missing")).success
                )
                .isFalse()
            assertThat(adapter.prepareRename(uri, 0, offset)).isNull()
        }
    }
}
