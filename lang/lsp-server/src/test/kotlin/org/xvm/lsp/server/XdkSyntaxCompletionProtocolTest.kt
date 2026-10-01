package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemInsertTextModeSupportCapabilities
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.util.concurrent.TimeUnit.SECONDS

class XdkSyntaxCompletionProtocolTest {
    @Test
    fun `missing declaration names use a plain empty edit at the original Unicode position`() {
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server.initialize(params(true)).get(30, SECONDS)
            val line = "    /* 😀 */ String "
            val text = "module Editing {\r\n$line= \"\";\r\n}"
            val service = server.textDocumentService
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, text)))
            val position = Position(1, line.length)
            val item =
                service
                    .completion(CompletionParams(TextDocumentIdentifier(URI), position))
                    .get(30, SECONDS)
                    .left
                    .single()
            assertThat(item.label).isEqualTo("string")
            assertThat(item.insertTextFormat).isEqualTo(InsertTextFormat.PlainText)
            assertThat(item.textEdit.left.range.start).isEqualTo(position)
            assertThat(item.textEdit.left.range.end).isEqualTo(position)
            assertThat(item.textEdit.left.newText).isEqualTo("string")
            assertThat(item.additionalTextEdits).isEmpty()
        }
    }

    @Test
    fun `only snippet capable clients receive placeholders and indentation modes are negotiated`() {
        listOf(false, true).forEach { snippets ->
            XtcLanguageServer(XdkAdapter()).use { server ->
                server.connect(mock(LanguageClient::class.java))
                server.initialize(params(snippets)).get(30, SECONDS)
                val text = "module Editing {\n    void run() {\n        if\n    }\n}"
                val service = server.textDocumentService
                service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, text)))
                val items =
                    service
                        .completion(CompletionParams(TextDocumentIdentifier(URI), Position(2, 10)))
                        .get(30, SECONDS)
                        .left
                val item = items.single { it.label == "if block" }
                assertThat(item.insertTextFormat)
                    .isEqualTo(if (snippets) InsertTextFormat.Snippet else InsertTextFormat.PlainText)
                assertThat(item.insertTextMode).isEqualTo(if (snippets) InsertTextMode.AsIs else null)
                assertThat(item.textEdit.left.newText).isEqualTo(item.insertText)
                assertThat(item.textEdit.left.range.start).isEqualTo(Position(2, 8))
                assertThat(item.textEdit.left.range.end).isEqualTo(Position(2, 10))
                assertThat(item.insertText).contains("\n            ", "\n        }")
                if (snippets) {
                    assertThat(item.insertText).contains("${'$'}{1:True}", "${'$'}0")
                } else {
                    assertThat(item.insertText).doesNotContain("$").contains("if (True)")
                }
                assertThat(items.single { it.label == "if" }.insertTextFormat).isEqualTo(InsertTextFormat.PlainText)
            }
        }
    }

    @Test
    fun `clients that only adjust indentation receive relative template bodies`() {
        val initialize =
            params(false).apply {
                capabilities.textDocument.completion.insertTextMode = InsertTextMode.AdjustIndentation
            }
        val policy = ClientPresentation.read(initialize)
        assertThat(policy.completionTemplate("if (True) {\r\n            \r\n        }"))
            .isEqualTo("if (True) {\r\n    \r\n}")
        assertThat(policy.completionTemplate("return value;")).isEqualTo("return value;")
        assertThat(ClientPresentation.read(params(true)).completionTemplate("if (True) {\n            \n        }"))
            .isEqualTo("if (True) {\n            \n        }")
    }

    private fun params(snippets: Boolean) =
        InitializeParams().apply {
            capabilities =
                ClientCapabilities().apply {
                    textDocument =
                        TextDocumentClientCapabilities().apply {
                            completion =
                                CompletionCapabilities().apply {
                                    completionItem =
                                        CompletionItemCapabilities().apply {
                                            snippetSupport = snippets
                                            if (snippets) {
                                                insertTextModeSupport =
                                                    CompletionItemInsertTextModeSupportCapabilities(
                                                        listOf(InsertTextMode.AsIs),
                                                    )
                                            }
                                        }
                                }
                        }
                }
        }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
