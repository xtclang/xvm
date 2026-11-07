package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.awaited
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Exercises connection-owned virtual library content. Only registered current artifact URIs may
 * be read; client-provided paths or presentations cannot replace authoritative source content.
 */
class LibraryContentProtocolTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `rename in a library method explains that navigation sources are read only`(virtual: Boolean) {
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    editorInitializeParams().apply {
                        if (virtual) capabilities.workspace.textDocumentContent = TextDocumentContentCapabilities()
                    },
                ).join()
            val uri = "file:///App.x"
            val text =
                "module App { package webcli import webcli.xtclang.org; " +
                    "void run() { webcli.Gateway.getPassword(); } }"
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
            val target =
                documents
                    .definition(
                        DefinitionParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("getPassword") + 5)),
                    ).awaited()
                    .left
                    .single()
            val content =
                if (virtual) {
                    server.workspaceService
                        .textDocumentContent(TextDocumentContentParams(target.uri))
                        .join()
                        .text
                } else {
                    Files.readString(Path.of(URI(target.uri)))
                }
            assertThat(content.lines()[target.range.start.line]).contains("getPassword(")
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(target.uri, "xtc", 1, content)))
            val middle = Position(target.range.start.line, target.range.start.character + 5)
            val document = TextDocumentIdentifier(target.uri)
            assertThatThrownBy { documents.prepareRename(PrepareRenameParams(document, middle)).awaited() }
                .hasCauseInstanceOf(ResponseErrorException::class.java)
                .hasRootCauseMessage(
                    "Cannot rename a symbol in read-only library source. Open the library's source project to refactor it.",
                )
            assertThat(documents.rename(RenameParams(document, middle, "readPassword")).awaited()).isNull()
        }
    }

    @Test
    fun `a library source that the debugger opens answers hover and definition, and refuses code actions and rename`() {
        // the read-only copy that a stack frame in the XDK names, which the client opens as a step goes into it, before
        // navigation or a compile has named any of its declarations; its compile on its own resolves none of them
        val copy = Path.of(requireNotNull(XdkLibrarySources.file("ecstasy.xtclang.org", "ecstasy/reflect/ClassTemplate.x")))
        val uri = copy.toUri().toString()
        val text = Files.readString(copy)
        val lines = text.lines()

        fun at(
            declaration: String,
            name: String,
        ) = lines.indexOfFirst { it.trimStart().startsWith(declaration) }.let { Position(it, lines[it].indexOf(name) + 2) }
        val type = at("interface ClassTemplate", "ClassTemplate")
        val method = at("conditional String pathWithin(", "pathWithin")
        // an undocumented method, where a project's own source offers to generate its doc comment
        val undocumented = at("Int estimateStringLength()", "estimateStringLength")
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server.initialize(editorInitializeParams()).join()
            server.initialized(InitializedParams())
            val documents = server.textDocumentService
            val document = TextDocumentIdentifier(uri)
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))

            fun hover(position: Position) =
                documents
                    .hover(HoverParams(document, position))
                    .awaited()
                    ?.contents
                    ?.right
                    ?.value
            assertThat(hover(type)).contains("interface ClassTemplate").contains("the compiled form of an Ecstasy module")
            assertThat(
                hover(method),
            ).contains("conditional String pathWithin()").contains("Determine the path that identifies this ClassTemplate")
            val definition = documents.definition(DefinitionParams(document, method)).awaited().left
            assertThat(definition).hasSize(1)
            assertThat(Path.of(URI(definition.single().uri))).isEqualTo(copy)
            assertThat(
                definition
                    .single()
                    .range.start.line,
            ).isEqualTo(method.line)

            assertThat(
                documents
                    .codeAction(
                        CodeActionParams(
                            document,
                            Range(undocumented, undocumented),
                            CodeActionContext(emptyList()).apply { triggerKind = CodeActionTriggerKind.Invoked },
                        ),
                    ).awaited(),
            ).isEmpty()
            assertThatThrownBy { documents.prepareRename(PrepareRenameParams(document, method)).awaited() }
                .hasCauseInstanceOf(ResponseErrorException::class.java)
                .hasRootCauseMessage(
                    "Cannot rename a symbol in read-only library source. Open the library's source project to refactor it.",
                )
            assertThat(documents.rename(RenameParams(document, method, "relativePath")).awaited()).isNull()
        }
    }

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
    fun `a dependency change fetches no library view again and untrusted client presentations never replace artifact content`() {
        val notifications = LinkedBlockingQueue<TextDocumentContentRefreshParams>()
        val client = mock(LanguageClient::class.java)
        doAnswer { invocation ->
            notifications.add(invocation.getArgument(0))
            CompletableFuture.completedFuture<Void>(null)
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
            val workspace = server.workspaceService
            val params = TextDocumentContentParams(target.uri)
            val text = workspace.textDocumentContent(params).join().text
            val documents = server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(target.uri, "xtc", 1, text.replace("\n", "\r\n"))))
            val moniker = MonikerParams(TextDocumentIdentifier(target.uri), target.range.start)
            val original = documents.moniker(moniker).awaited()
            assertThat(original).hasSize(1)
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(target.uri, 2),
                    listOf(TextDocumentContentChangeEvent("module Forged {}")),
                ),
            )
            assertThat(documents.moniker(moniker).awaited()).isEmpty()
            assertThat(workspace.textDocumentContent(params).join().text).isEqualTo(text)
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(target.uri, 3),
                    listOf(TextDocumentContentChangeEvent(text.replace("const String", "const  String"))),
                ),
            )
            assertThat(documents.moniker(moniker).awaited()).isEmpty()
            documents.didChange(
                DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(target.uri, 4), listOf(TextDocumentContentChangeEvent(text))),
            )
            assertThat(documents.moniker(moniker).awaited()).isEqualTo(original)
            // A library URI's text cannot change, so a dependency change asks the client to fetch no view again.
            server.replaceCompilerDependencies(emptyList())
            assertThat(notifications.poll(2, SECONDS)).isNull()
            assertThat(workspace.textDocumentContent(params).join().text).isEqualTo(text)
        }
    }

    private fun definition(server: XtcLanguageServer): Location {
        val uri = "file:///App.x"
        val text = "module App { String text = \"value\"; }"
        server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)))
        return server.textDocumentService
            .definition(
                DefinitionParams(TextDocumentIdentifier(uri), Position(0, text.indexOf("String"))),
            ).awaited()
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
