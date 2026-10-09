package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.xvm.lsp.adapter.mock.MockAdapter
import java.util.concurrent.CompletableFuture

class StartupRegistrationTest {
    @Test
    fun `watcher requests wait for initialized and require negotiated dynamic registration`() {
        listOf(false, true).forEach { supported ->
            val client = mock(LanguageClient::class.java)
            `when`(client.registerCapability(any()))
                .thenReturn(CompletableFuture.completedFuture(null))
            XtcLanguageServer(MockAdapter()).use { server ->
                server.connect(client)
                val params =
                    InitializeParams().apply {
                        capabilities =
                            ClientCapabilities().apply {
                                workspace =
                                    WorkspaceClientCapabilities().apply {
                                        didChangeWatchedFiles =
                                            DidChangeWatchedFilesCapabilities(supported)
                                    }
                            }
                    }
                assertThat(server.initialize(params).join().capabilities).isNotNull()
                verify(client, never()).registerCapability(any(RegistrationParams::class.java))
                server.initialized(InitializedParams())
                server.initialized(InitializedParams())
                verify(client, times(if (supported) 1 else 0))
                    .registerCapability(any(RegistrationParams::class.java))
            }
        }
    }
}
