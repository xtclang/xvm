package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentContentParams
import org.eclipse.lsp4j.TextDocumentContentRefreshParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class LibraryContentProtocolTest {
    @Test
    fun `library content is negotiated per connection and cannot read unregistered or retired URIs`() {
        listOf(false, true).forEach { supported ->
            XtcLanguageServer(XdkAdapter()).use { server ->
                server.connect(mock(LanguageClient::class.java))
                val params =
                    editorInitializeParams().apply {
                        if (supported) capabilities.workspace.textDocumentContent = TextDocumentContentCapabilities()
                    }
                val capabilities = server.initialize(params).join().capabilities
                assertThat(capabilities.workspace.textDocumentContent != null).isEqualTo(supported)
                val target = definition(server)
                if (supported) {
                    assertThat(URI(target.uri).scheme).isEqualTo("ecstasy-library")
                    val content =
                        server.workspaceService
                            .textDocumentContent(TextDocumentContentParams(target.uri))
                            .join()
                            .text
                    assertThat(content.lines()[target.range.start.line]).contains("const String")
                    XtcLanguageServer(XdkAdapter()).use { other ->
                        other.initialize(params).join()
                        refused(other, target.uri)
                    }
                    refused(server, target.uri + "/../secret")
                    server.close()
                    refused(server, target.uri)
                } else {
                    assertThat(URI(target.uri).scheme).isEqualTo("file")
                    assertThat(Files.readString(Path.of(URI(target.uri)))).contains("const String")
                    refused(server, target.uri)
                }
                refused(server, "file:///etc/passwd")
            }
        }
        XtcLanguageServer(MockAdapter()).use { server ->
            val params = editorInitializeParams().apply { capabilities.workspace.textDocumentContent = TextDocumentContentCapabilities() }
            assertThat(
                server
                    .initialize(params)
                    .join()
                    .capabilities.workspace
                    ?.textDocumentContent,
            ).isNull()
        }
    }

    @Test
    fun `fetched library views refresh and untrusted client presentations never replace artifact content`() {
        val notifications = LinkedBlockingQueue<TextDocumentContentRefreshParams>()
        val reply = CompletableFuture<Void>()
        val client = mock(LanguageClient::class.java)
        doAnswer { invocation ->
            notifications.add(invocation.getArgument(0))
            reply
        }.`when`(client).refreshTextDocumentContent(any())
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            server
                .initialize(
                    editorInitializeParams().apply {
                        capabilities.workspace.textDocumentContent =
                            TextDocumentContentCapabilities()
                    },
                ).join()
            server.initialized(InitializedParams())
            val target = definition(server)
            server.readOnlyDocuments.refresh()
            assertThat(notifications).isEmpty() // Navigating alone does not fetch content.
            val workspace = server.workspaceService
            val params = TextDocumentContentParams(target.uri)
            val text = workspace.textDocumentContent(params).join().text
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(target.uri, "xtc", 1, text.replace("\n", "\r\n"))))
            val moniker = MonikerParams(TextDocumentIdentifier(target.uri), target.range.start)
            val original = documents.moniker(moniker).get(30, SECONDS)
            assertThat(original).hasSize(1)
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(target.uri, 2),
                    listOf(TextDocumentContentChangeEvent("module Forged {}")),
                ),
            )
            assertThat(documents.moniker(moniker).get(30, SECONDS)).isEmpty()
            assertThat(workspace.textDocumentContent(params).join().text).isEqualTo(text)
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(target.uri, 3),
                    listOf(TextDocumentContentChangeEvent(text.replace("const String", "const  String"))),
                ),
            )
            assertThat(documents.moniker(moniker).get(30, SECONDS)).isEmpty()
            documents.didChange(
                DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(target.uri, 4), listOf(TextDocumentContentChangeEvent(text))),
            )
            assertThat(documents.moniker(moniker).get(30, SECONDS)).isEqualTo(original)
            server.replaceCompilerDependencies(emptyList())
            assertThat(notifications.poll(10, SECONDS)?.uri).isEqualTo(target.uri)
            server.readOnlyDocuments.refresh()
            assertThat(notifications).isEmpty()
            assertThat(workspace.textDocumentContent(params).join().text).isEqualTo(text)
            val acknowledged = server.readOnlyDocuments.refresh()
            reply.complete(null)
            acknowledged.get(10, SECONDS)
            server.readOnlyDocuments.refresh()
            assertThat(notifications.poll(10, SECONDS)?.uri).isEqualTo(target.uri)
            server.close()
            server.readOnlyDocuments.refresh()
            assertThat(notifications).isEmpty()
        }
    }

    private fun definition(server: XtcLanguageServer): Location {
        val uri = "file:///App.x"
        val text = "module App { String text = \"value\"; }"
        server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
        return server.textDocumentService
            .definition(
                DefinitionParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("String"))),
            ).get(30, SECONDS)
            .left
            .single()
    }

    private fun refused(
        server: XtcLanguageServer,
        uri: String,
    ) {
        assertThatThrownBy { server.workspaceService.textDocumentContent(TextDocumentContentParams(uri)).join() }
            .hasCauseInstanceOf(ResponseErrorException::class.java)
    }
}
