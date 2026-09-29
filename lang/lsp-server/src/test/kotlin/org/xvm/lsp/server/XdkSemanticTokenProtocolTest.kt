package org.xvm.lsp.server

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokensCapabilities
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequestsFull
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkSemanticTokenProtocolTest {
    @Test
    fun `compiler token delta survives edits and resets on reopen with negotiated refresh`() {
        CompilerTestSupport.configure()
        XtcLanguageServer(XdkAdapter()).use { server ->
            val client = mock(LanguageClient::class.java)
            `when`(client.refreshSemanticTokens())
                .thenReturn(CompletableFuture.completedFuture(null))
            server.connect(client)
            val capabilities =
                server
                    .initialize(
                        InitializeParams().apply {
                            this.capabilities =
                                ClientCapabilities().apply {
                                    textDocument =
                                        TextDocumentClientCapabilities().apply {
                                            semanticTokens =
                                                SemanticTokensCapabilities().apply {
                                                    requests =
                                                        SemanticTokensClientCapabilitiesRequests(
                                                            SemanticTokensClientCapabilitiesRequestsFull(
                                                                true
                                                            ),
                                                            true,
                                                        )
                                                }
                                        }
                                    workspace =
                                        WorkspaceClientCapabilities().apply {
                                            semanticTokens =
                                                SemanticTokensWorkspaceCapabilities(true)
                                        }
                                }
                        }
                    )
                    .get()
                    .capabilities
            assertThat(capabilities.semanticTokensProvider.full.right.delta).isTrue()
            assertThat(capabilities.semanticTokensProvider.range.left).isTrue()
            val service = server.textDocumentService
            val document = TextDocumentIdentifier("file:///Tokens.x")
            val source = "module Tokens { Int value = 1; Int run() = value; }"
            service.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(document.uri, "xtc", 1, source))
            )
            val before = service.semanticTokensFull(SemanticTokensParams(document)).get(30, SECONDS)
            assertThat(before.data).isNotEmpty()
            val unchanged =
                service
                    .semanticTokensFullDelta(SemanticTokensDeltaParams(document, before.resultId))
                    .get(30, SECONDS)
            assertThat(unchanged.right.edits).isEmpty()
            service.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(document.uri, 2),
                    listOf(TextDocumentContentChangeEvent("\r\n$source")),
                )
            )
            val delta =
                service
                    .semanticTokensFullDelta(SemanticTokensDeltaParams(document, before.resultId))
                    .get(30, SECONDS)
                    .right
            assertThat(delta.edits).hasSize(1)
            assertThat(delta.edits.single().data).containsExactly(1)
            val range =
                service
                    .semanticTokensRange(
                        SemanticTokensRangeParams(document, Range(Position(1, 0), Position(2, 0)))
                    )
                    .get(30, SECONDS)
            assertThat(range.data)
                .isEqualTo(
                    service.semanticTokensFull(SemanticTokensParams(document)).get(30, SECONDS).data
                )
            service.didClose(DidCloseTextDocumentParams(document))
            service.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(document.uri, "xtc", 1, source))
            )
            assertThat(
                    service
                        .semanticTokensFullDelta(
                            SemanticTokensDeltaParams(document, before.resultId)
                        )
                        .get(30, SECONDS)
                        .isLeft
                )
                .isTrue()
            verify(client, timeout(5000).atLeastOnce()).refreshSemanticTokens()
        }
    }

    @Test
    fun `unnegotiated extension requests are refused without changing full tokens`() {
        CompilerTestSupport.configure()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            val capabilities = server.initialize(InitializeParams()).get().capabilities
            assertThat(capabilities.semanticTokensProvider.full.left).isTrue()
            assertThat(capabilities.semanticTokensProvider.range).isNull()
            assertThatThrownBy {
                    server.textDocumentService
                        .semanticTokensFullDelta(
                            SemanticTokensDeltaParams(
                                TextDocumentIdentifier("file:///Tokens.x"),
                                "old",
                            )
                        )
                        .get(10, SECONDS)
                }
                .hasMessageContaining("not negotiated")
        }
    }
}
