package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkFoldingTest {
    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n"])
    fun `method folds stop before their closing brace and following same-line declarations`(newline: String) {
        listOf("Int value", "Int", "Int value, Str").forEach { parameters ->
            val closingLine = " /* 😀 */ } Int later=1; }"
            val text = listOf("module Folds {", " void damaged($parameters) {", " Int hidden=1;", closingLine).joinToString(newline)
            XdkAdapter().use { adapter ->
                val result = adapter.compile(URI, text)
                assertThat(result.success).describedAs(parameters).isEqualTo(parameters == "Int value")
                val folds = adapter.getFoldingRanges(URI)
                val method = folds.single { it.startLine == 1 }
                assertThat(method.endLine).isEqualTo(3)
                assertThat(method.startCharacter).isNull()
                assertThat(method.endCharacter).isEqualTo(closingLine.indexOf('}'))
                val module = folds.single { it.startLine == 0 }
                assertThat(module.endCharacter).isEqualTo(closingLine.lastIndexOf('}'))
            }
        }
    }

    private companion object {
        const val URI = "file:///Folds.x"
    }
}
