package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkLexical

class XdkLexicalTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r"])
    @Timeout(10)
    fun `large lexical projections preserve UTF-16 spans without rescanning per token`(
        newline: String
    ) {
        val lines = (0 until 2000).map { "String text$it = \"😀\"; // line $it" }
        val source = lines.joinToString(newline, postfix = newline)
        assertThat(XdkLexical.mayUseResources(source)).isFalse()
        val tokens = XdkLexical.tokens(source)
        assertThat(tokens).hasSize(lines.size * 2)
        lines.forEachIndexed { line, text ->
            assertThat(tokens[line * 2].take(3)).containsExactly(line, text.indexOf('"'), 4)
            assertThat(tokens[line * 2 + 1].take(3))
                .containsExactly(line, text.indexOf("//"), text.length - text.indexOf("//"))
        }
    }
}
