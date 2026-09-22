package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class AdapterBackendTest {
    @Test
    fun `tree-sitter remains the default`() {
        assertThat(AdapterBackend.fromSetting()).isEqualTo(AdapterBackend.TREE_SITTER)
        assertThat(AdapterBackend.fromSetting(null)).isEqualTo(AdapterBackend.TREE_SITTER)
        listOf("treesitter", "tree-sitter", "TreeSitter").forEach {
            assertThat(AdapterBackend.fromSetting(it)).isEqualTo(AdapterBackend.TREE_SITTER)
        }
    }

    @Test
    fun `compiler and mock require explicit selection`() {
        listOf("compiler", "xtc", "full").forEach {
            assertThat(AdapterBackend.fromSetting(it)).isEqualTo(AdapterBackend.COMPILER)
        }
        assertThat(AdapterBackend.fromSetting("mock")).isEqualTo(AdapterBackend.MOCK)
    }

    @Test
    fun `unknown settings cannot silently select mock`() {
        listOf("xdk", "compielr", "").forEach {
            assertThatIllegalArgumentException().isThrownBy { AdapterBackend.fromSetting(it) }.withMessageContaining("Unknown lsp.adapter")
        }
    }
}
