package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

class ColorValueProtocolTest {
    @Test
    fun `color provider requires opt in and disabled requests report method not found`() {
        CompilerTestSupport.configure()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            assertThat(
                server
                    .initialize(InitializeParams())
                    .get(10, SECONDS)
                    .capabilities.colorProvider,
            ).isNull()
            assertThatThrownBy { server.textDocumentService.documentColor(DocumentColorParams(DOCUMENT)).get(10, SECONDS) }
                .hasCauseInstanceOf(ResponseErrorException::class.java)
                .satisfies({ failure ->
                    assertThat(
                        (failure.cause as ResponseErrorException).responseError.code,
                    ).isEqualTo(ResponseErrorCode.MethodNotFound.value)
                })
        }
    }

    @Test
    fun `picker range refers to the current document and replacement compiles to the requested color`() {
        withServer { server ->
            val service = server.textDocumentService
            val before = service.documentColor(DocumentColorParams(DOCUMENT)).get(30, SECONDS).single()
            assertThat(before.color).isEqualTo(Color(1.0, 128 / 255.0, 0.0, 1.0))
            val chosen = Color(0.0, 1.0, 0.0, 64 / 255.0)
            val presentation = service.colorPresentation(ColorPresentationParams(DOCUMENT, chosen, before.range)).get(10, SECONDS).single()
            assertThat(presentation.additionalTextEdits).isNullOrEmpty()
            assertThat(presentation.textEdit.range).isEqualTo(before.range)
            assertThat(presentation.textEdit.newText).isEqualTo("new Rgba(0, 255, 0, alpha = 64)")
            val changed = SOURCE.replace("new Rgba(255, 128, 0)", presentation.textEdit.newText)
            service.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(DOCUMENT.uri, 2),
                    listOf(TextDocumentContentChangeEvent(changed)),
                ),
            )
            val after = service.documentColor(DocumentColorParams(DOCUMENT)).get(30, SECONDS).single()
            assertThat(after.color).isEqualTo(chosen)
            // The old range no longer covers the whole constructor.
            assertThat(service.colorPresentation(ColorPresentationParams(DOCUMENT, chosen, before.range)).get(10, SECONDS)).isEmpty()
            service.didClose(DidCloseTextDocumentParams(DOCUMENT))
            assertThat(service.documentColor(DocumentColorParams(DOCUMENT)).get(10, SECONDS)).isEmpty()
            assertThat(service.colorPresentation(ColorPresentationParams(DOCUMENT, chosen, after.range)).get(10, SECONDS)).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY])
    fun `invalid picker channels return invalid params without disturbing the document`(red: Double) {
        withServer { server ->
            val service = server.textDocumentService
            val before = service.documentColor(DocumentColorParams(DOCUMENT)).get(30, SECONDS).single()
            assertThatThrownBy {
                service.colorPresentation(ColorPresentationParams(DOCUMENT, Color(red, 0.0, 0.0, 1.0), before.range)).get(10, SECONDS)
            }.hasCauseInstanceOf(ResponseErrorException::class.java)
                .satisfies({ failure ->
                    assertThat(
                        (failure.cause as ResponseErrorException).responseError.code,
                    ).isEqualTo(ResponseErrorCode.InvalidParams.value)
                })
            assertThat(service.documentColor(DocumentColorParams(DOCUMENT)).get(10, SECONDS)).containsExactly(before)
        }
    }

    @Test
    fun `reversed presentation ranges return invalid params`() {
        withServer { server ->
            val request = ColorPresentationParams(DOCUMENT, Color(0.0, 0.0, 0.0, 1.0), Range(Position(3, 2), Position(2, 2)))
            assertThatThrownBy { server.textDocumentService.colorPresentation(request).get(30, SECONDS) }
                .hasCauseInstanceOf(ResponseErrorException::class.java)
                .satisfies({ failure ->
                    assertThat(
                        (failure.cause as ResponseErrorException).responseError.code,
                    ).isEqualTo(ResponseErrorCode.InvalidParams.value)
                })
        }
    }

    private fun withServer(test: (XtcLanguageServer) -> Unit) {
        CompilerTestSupport.configure()
        XtcLanguageServer(XdkAdapter(colorPrototype = true)).use { server ->
            server.connect(mock(LanguageClient::class.java))
            assertThat(
                server
                    .initialize(InitializeParams())
                    .get(10, SECONDS)
                    .capabilities.colorProvider.left,
            ).isTrue()
            server.initialized(InitializedParams())
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(DOCUMENT.uri, "xtc", 1, SOURCE)))
            test(server)
        }
    }

    private companion object {
        val DOCUMENT = TextDocumentIdentifier("file:///ColorPrototype.x")
        val SOURCE =
            """
            module ColorPrototype {
                const Rgba(UInt8 red, UInt8 green, UInt8 blue, UInt8 alpha = 255) {}
                Rgba sample() = new Rgba(255, 128, 0);
            }
            """.trimIndent()
    }
}
