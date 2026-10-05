package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter

/**
 * [TreeSitterAdapter] has no inferred-type hints. Compiler hint behavior is covered by
 * [XdkPresentationTest] and [XdkInferredPresentationTest], independently of this syntax adapter.
 */
@DisplayName("TreeSitterAdapter - Inlay Hints")
class InlayHintTest : TreeSitterTestBase() {
    @Test
    fun `syntax adapter neither advertises nor fabricates inferred type hints`() {
        val uri = freshUri()
        val source =
            """
            module myapp {
                void run() {
                    var count = 42;
                }
            }
            """.trimIndent()
        ts.compile(uri, source)

        assertThat(ts.capabilities).doesNotContain(AdapterCapability.INLAY_HINT)
        assertThat(ts.getInlayHints(uri, Range(Position(0, 0), Position(4, 1)))).isEmpty()
    }
}
