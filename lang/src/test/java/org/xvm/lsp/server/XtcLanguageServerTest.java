package org.xvm.lsp.server;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xvm.lsp.adapter.MockXtcCompilerAdapter;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("XtcLanguageServer")
class XtcLanguageServerTest {

    private XtcLanguageServer server;
    private LanguageClient mockClient;

    @BeforeEach
    void setUp() {
        server = new XtcLanguageServer(new MockXtcCompilerAdapter());
        mockClient = mock(LanguageClient.class);
        server.connect(mockClient);
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("should return server capabilities")
        void shouldReturnServerCapabilities() throws Exception {
            final InitializeParams params = new InitializeParams();

            final CompletableFuture<InitializeResult> future = server.initialize(params);
            final InitializeResult result = future.get();

            assertThat(result.getCapabilities()).isNotNull();
            assertThat(result.getCapabilities().getHoverProvider().getLeft()).isTrue();
            assertThat(result.getCapabilities().getCompletionProvider()).isNotNull();
            assertThat(result.getCapabilities().getDefinitionProvider().getLeft()).isTrue();
            assertThat(result.getCapabilities().getReferencesProvider().getLeft()).isTrue();
            assertThat(result.getCapabilities().getDocumentSymbolProvider().getLeft()).isTrue();
        }
    }

    @Nested
    @DisplayName("TextDocumentService")
    class TextDocumentServiceTests {

        @Test
        @DisplayName("didOpen should compile and publish diagnostics")
        void didOpenShouldCompileAndPublishDiagnostics() {
            final String uri = "file:///test.x";
            final String content = """
                    module myapp {
                        // ERROR: test error
                    }
                    """;

            final DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "xtc", 1, content));

            server.getTextDocumentService().didOpen(params);

            final ArgumentCaptor<PublishDiagnosticsParams> captor =
                    ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
            verify(mockClient).publishDiagnostics(captor.capture());

            final PublishDiagnosticsParams published = captor.getValue();
            assertThat(published.getUri()).isEqualTo(uri);
            assertThat(published.getDiagnostics())
                    .hasSize(1)
                    .allMatch(d -> d.getMessage().contains("test error"));
        }

        @Test
        @DisplayName("didOpen with valid code should have no diagnostics")
        void didOpenWithValidCodeShouldHaveNoDiagnostics() {
            final String uri = "file:///test.x";
            final String content = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            final DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "xtc", 1, content));

            server.getTextDocumentService().didOpen(params);

            final ArgumentCaptor<PublishDiagnosticsParams> captor =
                    ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
            verify(mockClient).publishDiagnostics(captor.capture());

            assertThat(captor.getValue().getDiagnostics()).isEmpty();
        }

        @Test
        @DisplayName("hover should return markdown content")
        void hoverShouldReturnMarkdownContent() throws Exception {
            // First, open the document
            final String uri = "file:///test.x";
            final String content = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "xtc", 1, content)));

            // Now request hover
            final HoverParams params = new HoverParams(
                    new TextDocumentIdentifier(uri),
                    new Position(1, 10));

            final CompletableFuture<Hover> future =
                    server.getTextDocumentService().hover(params);
            final Hover hover = future.get();

            assertThat(hover).isNotNull();
            assertThat(hover.getContents().getRight().getValue())
                    .contains("class Person");
        }

        @Test
        @DisplayName("completion should return items")
        void completionShouldReturnItems() throws Exception {
            final String uri = "file:///test.x";
            final String content = """
                    module myapp {
                    }
                    """;

            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "xtc", 1, content)));

            final CompletionParams params = new CompletionParams(
                    new TextDocumentIdentifier(uri),
                    new Position(1, 0));

            final var future = server.getTextDocumentService().completion(params);
            final var result = future.get();

            // Result is Either<List, CompletionList>
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft())
                    .isNotEmpty()
                    .anyMatch(item -> item.getLabel().equals("class"))
                    .anyMatch(item -> item.getLabel().equals("String"));
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("should complete without error")
        void shouldCompleteWithoutError() throws Exception {
            server.initialize(new InitializeParams()).get();

            final CompletableFuture<Object> future = server.shutdown();

            assertThat(future.get()).isNull();
        }
    }
}
