package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

class XdkDeclarationServerTest {
    @Test
    fun `declaration is advertised only by the implementing adapter and preserves multiple wire targets`() {
        listOf(MockAdapter(), XdkAdapter()).forEach { adapter ->
            val server = XtcLanguageServer(adapter)
            server.connect(mock(LanguageClient::class.java))
            try {
                val capabilities =
                    server.initialize(InitializeParams()).get(20, SECONDS).capabilities
                if (adapter is XdkAdapter) {
                    assertThat(capabilities.declarationProvider.left).isTrue()
                    val uri = "file:///Declarations.x"
                    val text =
                        "module Declarations { interface A { Int read(); } interface B { Int read(); } " +
                            "class Both implements A, B { @Override Int read() = 1; } Int use(Both value) = value.read(); }"
                    val documents = server.textDocumentService
                    documents.didOpen(
                        DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, text)),
                    )
                    val targets =
                        documents
                            .declaration(
                                DeclarationParams(
                                    TextDocumentIdentifier(uri),
                                    Position(0, text.lastIndexOf("read")),
                                ),
                            ).get(30, SECONDS)
                            .left
                    assertThat(targets).hasSize(2)
                    assertThat(targets.map { it.range.start.character })
                        .containsExactly(
                            text.indexOf("read"),
                            text.indexOf(
                                "read",
                                text.indexOf("read") + 1,
                            ),
                        )
                } else {
                    assertThat(capabilities.declarationProvider).isNull()
                }
            } finally {
                server.shutdown().get(20, SECONDS)
            }
        }
    }
}
