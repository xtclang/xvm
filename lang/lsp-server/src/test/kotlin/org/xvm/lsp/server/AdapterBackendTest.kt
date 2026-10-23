package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.xvm.lsp.adapter.mock.MockAdapter

/**
 * Checks bundled defaults, startup overrides and supported adapter aliases. Unknown values must fail
 * explicitly rather than silently selecting a mock or different backend.
 */
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

    @Test
    fun `only a failed Tree-sitter start warns that the Mock adapter answers instead`() {
        assertThat(fallbackWarning(AdapterBackend.TREE_SITTER, AdapterBackend.MOCK)).contains("Mock adapter")
        assertThat(fallbackWarning(AdapterBackend.MOCK, AdapterBackend.MOCK)).isNull()
        assertThat(fallbackWarning(AdapterBackend.TREE_SITTER, AdapterBackend.TREE_SITTER)).isNull()
        assertThat(fallbackWarning(AdapterBackend.COMPILER, AdapterBackend.COMPILER)).isNull()
    }

    @Test
    fun `the startup warning reaches the user once the handshake completes`() {
        val warning = fallbackWarning(AdapterBackend.TREE_SITTER, AdapterBackend.MOCK)!!
        XtcLanguageServer(MockAdapter(), startupWarning = warning).use { server ->
            val client = mock(LanguageClient::class.java)
            server.connect(client)
            server.initialize(InitializeParams()).join()
            server.initialized(InitializedParams())
            verify(client).showMessage(MessageParams(MessageType.Warning, warning))
            verifyNoMoreInteractions(client)
        }
    }
}
