package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class AdapterBackendTest {
    @Test
    fun `startup selection overrides the bundled default and rejects invalid overrides`() {
        assertThat(AdapterBackend.resolve("compiler", "treesitter")).isEqualTo(AdapterBackend.TREE_SITTER)
        assertThat(AdapterBackend.resolve("treesitter", "compiler")).isEqualTo(AdapterBackend.COMPILER)
        assertThat(AdapterBackend.resolve("mock", null)).isEqualTo(AdapterBackend.MOCK)
        assertThat(AdapterBackend.resolve(null, null)).isEqualTo(AdapterBackend.COMPILER)
        assertThatIllegalArgumentException().isThrownBy { AdapterBackend.resolve("compiler", "typo") }
    }

    @Test
    fun `compiler is the default`() {
        assertThat(AdapterBackend.fromSetting()).isEqualTo(AdapterBackend.COMPILER)
        assertThat(AdapterBackend.fromSetting(null)).isEqualTo(AdapterBackend.COMPILER)
        listOf("treesitter", "tree-sitter", "TreeSitter").forEach {
            assertThat(AdapterBackend.fromSetting(it)).isEqualTo(AdapterBackend.TREE_SITTER)
        }
    }

    @Test
    fun `explicit adapter names and aliases remain supported`() {
        listOf("compiler", "xtc", "full").forEach {
            assertThat(AdapterBackend.fromSetting(it)).isEqualTo(AdapterBackend.COMPILER)
        }
        assertThat(AdapterBackend.fromSetting("mock")).isEqualTo(AdapterBackend.MOCK)
    }

    @Test
    fun `unknown settings cannot silently select mock`() {
        listOf("xdk", "compielr", "").forEach {
            assertThatIllegalArgumentException()
                .isThrownBy { AdapterBackend.fromSetting(it) }
                .withMessageContaining("Unknown lsp.adapter")
        }
    }
}
