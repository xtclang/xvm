package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.xvm.lsp.adapter.MockXtcCompilerAdapter

@DisplayName("XtcLanguageServer")
class XtcLanguageServerTest {

    private lateinit var server: XtcLanguageServer
    private lateinit var mockClient: LanguageClient

    @BeforeEach
    fun setUp() {
        server = XtcLanguageServer(MockXtcCompilerAdapter())
        mockClient = mock(LanguageClient::class.java)
        server.connect(mockClient)
    }

    @Nested
    @DisplayName("initialize()")
    inner class InitializeTests {

        @Test
        @DisplayName("should return server capabilities")
        fun shouldReturnServerCapabilities() {
            val params = InitializeParams()

            val future = server.initialize(params)
            val result = future.get()

            assertThat(result.capabilities).isNotNull()
            assertThat(result.capabilities.hoverProvider.left).isTrue()
            assertThat(result.capabilities.completionProvider).isNotNull()
            assertThat(result.capabilities.definitionProvider.left).isTrue()
            assertThat(result.capabilities.referencesProvider.left).isTrue()
            assertThat(result.capabilities.documentSymbolProvider.left).isTrue()
        }
    }

    @Nested
    @DisplayName("TextDocumentService")
    inner class TextDocumentServiceTests {

        @Test
        @DisplayName("didOpen should compile and publish diagnostics")
        fun didOpenShouldCompileAndPublishDiagnostics() {
            val uri = "file:///test.x"
            val content = """
                module myapp {
                    // ERROR: test error
                }
            """.trimIndent()

            val params = DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))

            server.textDocumentService.didOpen(params)

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient).publishDiagnostics(captor.capture())

            val published = captor.value
            assertThat(published.uri).isEqualTo(uri)
            assertThat(published.diagnostics)
                .hasSize(1)
                .allMatch { it.message.contains("test error") }
        }

        @Test
        @DisplayName("didOpen with valid code should have no diagnostics")
        fun didOpenWithValidCodeShouldHaveNoDiagnostics() {
            val uri = "file:///test.x"
            val content = """
                module myapp {
                    class Person {
                    }
                }
            """.trimIndent()

            val params = DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))

            server.textDocumentService.didOpen(params)

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient).publishDiagnostics(captor.capture())

            assertThat(captor.value.diagnostics).isEmpty()
        }

        @Test
        @DisplayName("hover should return markdown content")
        fun hoverShouldReturnMarkdownContent() {
            // First, open the document
            val uri = "file:///test.x"
            val content = """
                module myapp {
                    class Person {
                    }
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))
            )

            // Now request hover
            val params = HoverParams(TextDocumentIdentifier(uri), Position(1, 10))

            val future = server.textDocumentService.hover(params)
            val hover = future.get()

            assertThat(hover).isNotNull()
            assertThat(hover.contents.right.value).contains("class Person")
        }

        @Test
        @DisplayName("completion should return items")
        fun completionShouldReturnItems() {
            val uri = "file:///test.x"
            val content = """
                module myapp {
                }
            """.trimIndent()

            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, content))
            )

            val params = CompletionParams(TextDocumentIdentifier(uri), Position(1, 0))

            val future = server.textDocumentService.completion(params)
            val result = future.get()

            // Result is Either<List, CompletionList>
            assertThat(result.isLeft).isTrue()
            assertThat(result.left)
                .isNotEmpty()
                .anyMatch { it.label == "class" }
                .anyMatch { it.label == "String" }
        }
    }

    @Nested
    @DisplayName("shutdown()")
    inner class ShutdownTests {

        @Test
        @DisplayName("should complete without error")
        fun shouldCompleteWithoutError() {
            server.initialize(InitializeParams()).get()

            val future = server.shutdown()

            assertThat(future.get()).isNull()
        }
    }
}
