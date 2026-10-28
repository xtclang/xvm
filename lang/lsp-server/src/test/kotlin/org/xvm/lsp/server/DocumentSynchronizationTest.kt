package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentRangesFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSaveReason
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.xvm.lsp.adapter.FormattingOptions as AdapterFormattingOptions
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange
import org.xvm.lsp.adapter.TextEdit as AdapterTextEdit

class DocumentSynchronizationTest {
    private val uri = "file:///Sync.x"
    private val id = TextDocumentIdentifier(uri)
    private val save = WillSaveTextDocumentParams(id, TextDocumentSaveReason.Manual)

    private fun params(
        incremental: Boolean = false,
        format: Boolean = false,
    ) = InitializeParams().apply {
        initializationOptions =
            mapOf(
                "xtcDocumentSync" to
                    mapOf("incremental" to incremental, "formatOnSave" to format),
            )
        capabilities =
            ClientCapabilities().apply {
                textDocument =
                    TextDocumentClientCapabilities().apply {
                        synchronization = SynchronizationCapabilities(true, true, true)
                    }
            }
    }

    @Test
    fun `sequential patches use UTF16 and preserve all line endings`() {
        val original = "a😀b\r\nsecond\rlast\n"
        val updated =
            DocumentText(original)
                .change(
                    listOf(
                        patch(0, 1, 0, 3, "XY"),
                        patch(0, 3, 1, 3, "!\r\nQ"),
                        patch(2, 0, 2, 4, "end"),
                    ),
                    true,
                )
        assertThat(updated).isEqualTo("aXY!\r\nQond\rend\n")
        assertThat(
            DocumentText(updated)
                .change(
                    listOf(TextDocumentContentChangeEvent("reset"), patch(0, 5, 0, 5, "!")),
                    true,
                ),
        ).isEqualTo("reset!")
        assertThat(DocumentText("abc").offset(Position(0, 99))).isEqualTo(3)
    }

    @Test
    fun `invalid patches and overlapping formatter edits fail safely`() {
        val text = DocumentText("a😀b\r\nline")
        listOf(
            patch(0, 2, 0, 3, ""),
            patch(2, 0, 2, 0, ""),
            patch(1, 2, 0, 0, ""),
            patch(0, -1, 0, 0, ""),
        ).forEach { change ->
            assertThatThrownBy { text.change(listOf(change), true) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { text.change(listOf(patch(0, 0, 0, 0, "x")), false) }
            .hasMessageContaining("not negotiated")
        val edit = TextEdit(Range(Position(0, 0), Position(0, 3)), "x")
        assertThat(text.nonOverlapping(listOf(edit, edit))).containsExactly(edit)
        assertThatThrownBy {
            text.nonOverlapping(
                listOf(edit, TextEdit(Range(Position(0, 1), Position(0, 4)), "y")),
            )
        }.hasMessageContaining("overlapping")
    }

    @Test
    fun `save edits do not wait for compilation or mutate the server document`() {
        val compilation = CompletableFuture<CompilationResult>()
        val adapter =
            object : Adapter by MockAdapter() {
                override fun compileAsync(
                    uri: String,
                    content: String,
                ) = compilation

                override fun formatDocument(
                    uri: String,
                    content: String,
                    options: AdapterFormattingOptions,
                ) = listOf(
                    AdapterTextEdit(
                        AdapterRange(AdapterPosition(0, 0), AdapterPosition(0, 0)),
                        content,
                    ),
                )
            }
        XtcLanguageServer(adapter).use { server ->
            server.connect(mock(LanguageClient::class.java))
            val caps =
                server
                    .initialize(params(incremental = true, format = true))
                    .get(10, SECONDS)
                    .capabilities
            assertThat(caps.textDocumentSync.right.change)
                .isEqualTo(TextDocumentSyncKind.Incremental)
            assertThat(caps.textDocumentSync.right.willSaveWaitUntil).isTrue()
            val documents = server.textDocumentService
            documents.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, "a😀b\r\nline")),
            )
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 2),
                    listOf(patch(0, 1, 0, 3, "XY")),
                ),
            )
            documents.willSave(save)
            assertThat(
                documents
                    .willSaveWaitUntil(save)
                    .get(10, SECONDS)
                    .single()
                    .newText,
            ).isEqualTo("aXYb\r\nline")
            assertThat(compilation.isDone).isFalse()
            // A malformed batch must not publish even its valid prefix or advance the version.
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 3),
                    listOf(patch(0, 0, 0, 1, "Z"), patch(9, 0, 9, 0, "!")),
                ),
            )
            assertThat(
                documents
                    .willSaveWaitUntil(save)
                    .get(10, SECONDS)
                    .single()
                    .newText,
            ).isEqualTo("aXYb\r\nline")
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 3),
                    listOf(patch(0, 0, 0, 1, "Q")),
                ),
            )
            assertThat(
                documents
                    .willSaveWaitUntil(save)
                    .get(10, SECONDS)
                    .single()
                    .newText,
            ).isEqualTo("QXYb\r\nline")
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 2),
                    listOf(TextDocumentContentChangeEvent("old")),
                ),
            )
            assertThat(
                documents
                    .willSaveWaitUntil(save)
                    .get(10, SECONDS)
                    .single()
                    .newText,
            ).isEqualTo("QXYb\r\nline")
        }
    }

    @Test
    fun `full sync still refuses patches and save formatting is opt in`() {
        CompilerTestSupport.configure()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            val caps = server.initialize(params()).get(10, SECONDS).capabilities
            assertThat(caps.textDocumentSync.right.change).isEqualTo(TextDocumentSyncKind.Full)
            assertThat(caps.documentRangeFormattingProvider.right.rangesSupport).isTrue()
            val documents = server.textDocumentService
            val source = "module Sync {\r\nInt value = 1;\r\nString text = \"😀\";\r\n}"
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(uri, 2),
                    listOf(patch(0, 0, 0, 1, "!")),
                ),
            )
            assertThat(documents.willSaveWaitUntil(save).get(10, SECONDS)).isEmpty()
            val edits =
                documents
                    .rangesFormatting(
                        DocumentRangesFormattingParams(
                            id,
                            FormattingOptions(4, true),
                            listOf(
                                Range(Position(2, 0), Position(3, 0)),
                                Range(Position(1, 0), Position(2, 0)),
                                Range(Position(1, 0), Position(3, 0)),
                            ),
                        ),
                    ).get(30, SECONDS)
            assertThat(edits).hasSize(2)
            assertThat(edits.map { it.range.start.line }).containsExactly(1, 2)
            assertThat(edits.map { it.newText }).containsOnly("    ")
            assertThat(
                documents
                    .rangesFormatting(
                        DocumentRangesFormattingParams(
                            id,
                            FormattingOptions(4, true),
                            emptyList(),
                        ),
                    ).get(10, SECONDS),
            ).isEmpty()
            assertThatThrownBy {
                documents
                    .rangesFormatting(
                        DocumentRangesFormattingParams(
                            id,
                            FormattingOptions(4, true),
                            listOf(Range(Position(10, 0), Position(10, 1))),
                        ),
                    ).get(10, SECONDS)
            }.hasMessageContaining("Invalid text position")
        }
    }

    @Test
    fun `invalid initialization and unnegotiated save requests are rejected`() {
        XtcLanguageServer(MockAdapter()).use { server ->
            assertThatThrownBy {
                server
                    .initialize(
                        InitializeParams().apply {
                            initializationOptions =
                                mapOf("xtcDocumentSync" to mapOf("incremental" to "yes"))
                        },
                    ).get(10, SECONDS)
            }.hasMessageContaining("must be a boolean")
            server.initialize(InitializeParams()).get(10, SECONDS)
            assertThatThrownBy {
                server.textDocumentService.willSaveWaitUntil(save).get(10, SECONDS)
            }.hasMessageContaining("not negotiated")
        }
    }

    private fun patch(
        line: Int,
        column: Int,
        endLine: Int,
        endColumn: Int,
        text: String,
    ) = TextDocumentContentChangeEvent().apply {
        range = Range(Position(line, column), Position(endLine, endColumn))
        this.text = text
    }
}
