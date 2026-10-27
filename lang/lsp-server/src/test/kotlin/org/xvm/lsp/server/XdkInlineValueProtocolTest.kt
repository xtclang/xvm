package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlineValueContext
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.InlineValueWorkspaceCapabilities
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Checks `textDocument/inlineValue` over the protocol: the stopped method's parameters and locals as lookups by
 * name, which the editor makes in the stopped frame, and `workspace/inlineValue/refresh` once a compile ends, for a
 * client that takes it.
 */
class XdkInlineValueProtocolTest {
    @TempDir lateinit var root: Path
    private val source =
        """
        module Values {
            Int count = 0;
            Int add(Int n) {
                Int before = count;
                count = before + n;
                return count;
            }
        }
        """.trimIndent()

    @Test
    fun `inline values are lookups of the stopped method's variables, refreshed once a compile ends`() {
        CompilerTestSupport.configure()
        val uri =
            root
                .resolve("Values.x")
                .toFile()
                .canonicalFile
                .apply { writeText(source) }
                .toURI()
                .toString()
        val client = mock(LanguageClient::class.java)
        `when`(client.refreshInlineValues()).thenReturn(CompletableFuture.completedFuture(null))
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            val capabilities =
                server
                    .initialize(
                        InitializeParams().apply {
                            workspaceFolders = listOf(WorkspaceFolder(root.toUri().toString(), "Values"))
                            this.capabilities =
                                ClientCapabilities().apply {
                                    workspace = WorkspaceClientCapabilities().apply { inlineValue = InlineValueWorkspaceCapabilities(true) }
                                }
                        },
                    ).get(30, SECONDS)
                    .capabilities
            assertThat(capabilities.inlineValueProvider?.left).isTrue()
            server.initialized(InitializedParams())
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))

            // stopped on line 4, `count = before + n;`: the parameter and the local, not the field
            val stopped = Position(4, 8)
            val values =
                server.textDocumentService
                    .inlineValue(
                        InlineValueParams(
                            TextDocumentIdentifier(uri),
                            Range(Position(0, 0), Position(8, 0)),
                            InlineValueContext(1, Range(stopped, stopped)),
                        ),
                    ).get(30, SECONDS)
            assertThat(values).allSatisfy {
                assertThat(it.isInlineValueVariableLookup).isTrue()
                assertThat(it.inlineValueVariableLookup.isCaseSensitiveLookup).isTrue()
            }
            assertThat(values.map { it.inlineValueVariableLookup.let { lookup -> lookup.variableName to lookup.range.start.line } })
                .containsExactly("n" to 2, "before" to 3, "before" to 4, "n" to 4)
            verify(client, timeout(10_000).atLeastOnce()).refreshInlineValues()
        }
    }
}
