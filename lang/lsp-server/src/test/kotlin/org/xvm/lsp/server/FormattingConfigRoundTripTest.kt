package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.xvm.lsp.adapter.XtcFormattingConfig
import org.xvm.lsp.adapter.treesitter.TreeSitterAdapter
import java.util.concurrent.CompletableFuture

/**
 * Round-trip test for formatting config flow:
 *
 * ```
 * IntelliJ Code Style → XtcLanguageClient.configuration()
 *     → workspace/configuration response
 *         → XtcLanguageServer.requestFormattingConfig()
 *             → editorFormattingConfig stored
 *                 → TreeSitterAdapter.onTypeFormatting() uses config
 *                     → indentation reflects custom settings
 * ```
 *
 * This test creates a real [XtcLanguageServer] with [TreeSitterAdapter], wires a mock
 * [LanguageClient] that responds to `workspace/configuration` with custom indent settings,
 * and verifies that `textDocument/onTypeFormatting` produces indentation matching the
 * client-provided config rather than the XTC defaults.
 */
@DisplayName("FormattingConfigRoundTrip")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormattingConfigRoundTripTest {
    private var adapter: TreeSitterAdapter? = null

    @BeforeAll
    fun setUpAdapter() {
        adapter = runCatching { TreeSitterAdapter() }.getOrNull()
    }

    @AfterAll
    fun tearDown() {
        adapter?.close()
    }

    @BeforeEach
    fun assumeTreeSitter() {
        Assumptions.assumeTrue(adapter != null, "Tree-sitter native library not available")
        // Reset adapter state between tests — the adapter is shared (PER_CLASS lifecycle)
        adapter?.editorFormattingConfig = null
    }

    /**
     * Create a mock [LanguageClient] that responds to `workspace/configuration` requests
     * with the given formatting settings map.
     */
    private fun mockClientWithConfig(config: Map<String, Any>): LanguageClient {
        val client = mock(LanguageClient::class.java)
        `when`(client.configuration(org.mockito.ArgumentMatchers.any(ConfigurationParams::class.java)))
            .thenReturn(CompletableFuture.completedFuture(listOf(config)))
        return client
    }

    /**
     * Create a mock client that returns null/empty for `workspace/configuration`,
     * simulating a client that doesn't support the XTC config section.
     */
    private fun mockClientWithoutConfig(): LanguageClient {
        val client = mock(LanguageClient::class.java)
        `when`(client.configuration(org.mockito.ArgumentMatchers.any(ConfigurationParams::class.java)))
            .thenReturn(CompletableFuture.completedFuture(listOf(null)))
        return client
    }

    /**
     * Full server lifecycle: initialize → initialized → didOpen → onTypeFormatting.
     * Returns the indent size from the first TextEdit, or -1 if no edits.
     */
    private fun formatWithServer(
        server: XtcLanguageServer,
        source: String,
        line: Int,
        column: Int,
        ch: String,
    ): Int {
        val uri = "file:///roundtrip-${System.nanoTime()}.x"

        // Open the document
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)),
        )

        // Request on-type formatting
        val params =
            DocumentOnTypeFormattingParams().apply {
                textDocument = TextDocumentIdentifier(uri)
                position = Position(line, column)
                this.ch = ch
                options = FormattingOptions(4, true)
            }
        val edits = server.textDocumentService.onTypeFormatting(params).get()
        return if (edits.isNullOrEmpty()) -1 else edits.first().newText.length
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Nested
    @DisplayName("workspace/configuration round-trip")
    inner class ConfigurationRoundTrip {
        @Test
        @DisplayName("server requests config from client during initialized()")
        fun serverRequestsConfigDuringInitialized() {
            val customConfig =
                mapOf(
                    "indentSize" to 2,
                    "continuationIndentSize" to 4,
                    "insertSpaces" to true,
                    "maxLineWidth" to 80,
                )
            val client = mockClientWithConfig(customConfig)
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()

            // Trigger the initialized callback — this sends workspace/configuration
            server.initialized(InitializedParams())

            // Verify the server called workspace/configuration on the client
            val captor = ArgumentCaptor.forClass(ConfigurationParams::class.java)
            verify(client).configuration(captor.capture())

            // Verify it requested the "xtc.formatting" section
            assertThat(captor.value.items).hasSize(1)
            assertThat(captor.value.items[0].section).isEqualTo("xtc.formatting")
        }

        @Test
        @DisplayName("server stores editor config after receiving response")
        fun serverStoresEditorConfig() {
            val customConfig =
                mapOf(
                    "indentSize" to 2,
                    "continuationIndentSize" to 4,
                    "insertSpaces" to true,
                    "maxLineWidth" to 80,
                )
            val client = mockClientWithConfig(customConfig)
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            // Allow async config request to complete
            Thread.sleep(100)

            assertThat(server.editorFormattingConfig).isNotNull()
            assertThat(server.editorFormattingConfig!!.indentSize).isEqualTo(2)
            assertThat(server.editorFormattingConfig!!.continuationIndentSize).isEqualTo(4)
            assertThat(server.editorFormattingConfig!!.maxLineWidth).isEqualTo(80)
        }

        @Test
        @DisplayName("server uses default config when client returns null")
        fun serverUsesDefaultWhenClientReturnsNull() {
            val client = mockClientWithoutConfig()
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            Thread.sleep(100)

            // No editor config stored — formatting will use LSP options or defaults
            assertThat(server.editorFormattingConfig).isNull()
        }
    }

    @Nested
    @DisplayName("formatting uses editor config")
    inner class FormattingUsesConfig {
        @Test
        @DisplayName("2-space indent config produces 2-space indentation")
        fun twoSpaceIndent() {
            val customConfig =
                mapOf(
                    "indentSize" to 2,
                    "continuationIndentSize" to 4,
                    "insertSpaces" to true,
                    "maxLineWidth" to 80,
                )
            val client = mockClientWithConfig(customConfig)
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            // Wait for async config to be stored
            Thread.sleep(100)

            // After "module myapp {", Enter → new line should indent by 2 (custom config)
            val source = "module myapp {\n}"
            val indent = formatWithServer(server, source, line = 1, column = 0, ch = "\n")
            assertThat(indent)
                .describedAs("indent after '{' should use 2-space config from editor")
                .isEqualTo(2)
        }

        @Test
        @DisplayName("default 4-space indent when no editor config")
        fun defaultFourSpaceIndent() {
            val client = mockClientWithoutConfig()
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            Thread.sleep(100)

            // Without editor config, falls back to LSP FormattingOptions (tabSize=4)
            val source = "module myapp {\n}"
            val indent = formatWithServer(server, source, line = 1, column = 0, ch = "\n")
            assertThat(indent)
                .describedAs("indent after '{' should use default 4-space from LSP options")
                .isEqualTo(4)
        }

        @Test
        @DisplayName("nested indentation respects custom config")
        fun nestedIndentRespectsConfig() {
            val customConfig =
                mapOf(
                    "indentSize" to 3,
                    "continuationIndentSize" to 6,
                    "insertSpaces" to true,
                    "maxLineWidth" to 100,
                )
            val client = mockClientWithConfig(customConfig)
            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            Thread.sleep(100)

            // Nested class inside module with correct 3-space indentation.
            // The tree-sitter adapter reads the parent line's indentation from source,
            // so the source must already use 3-space indent for the adapter to add correctly.
            val source = "module myapp {\n   class Person {\n\n   }\n}"
            val indent = formatWithServer(server, source, line = 2, column = 0, ch = "\n")
            assertThat(indent)
                .describedAs("nested indent should be parent indent (3) + config indent (3)")
                .isEqualTo(6)
        }

        @Test
        @DisplayName("didChangeConfiguration triggers config refresh")
        fun didChangeConfigurationRefreshes() {
            // Use an answer that returns different configs on successive calls
            val configs =
                listOf(
                    mapOf(
                        "indentSize" to 2,
                        "continuationIndentSize" to 4,
                        "insertSpaces" to true,
                        "maxLineWidth" to 80,
                    ),
                    mapOf(
                        "indentSize" to 6,
                        "continuationIndentSize" to 12,
                        "insertSpaces" to true,
                        "maxLineWidth" to 100,
                    ),
                )
            var callCount = 0
            val client = mock(LanguageClient::class.java)
            `when`(client.configuration(org.mockito.ArgumentMatchers.any(ConfigurationParams::class.java)))
                .thenAnswer {
                    val idx = callCount.coerceAtMost(configs.size - 1)
                    callCount++
                    CompletableFuture.completedFuture(listOf(configs[idx]))
                }

            val server = XtcLanguageServer(adapter!!)
            server.connect(client)
            server.initialize(InitializeParams()).get()
            server.initialized(InitializedParams())

            Thread.sleep(100)
            assertThat(server.editorFormattingConfig).isNotNull()
            assertThat(server.editorFormattingConfig!!.indentSize).isEqualTo(2)

            // Send didChangeConfiguration — server re-requests config, gets second answer
            server.workspaceService.didChangeConfiguration(
                org.eclipse.lsp4j.DidChangeConfigurationParams(com.google.gson.JsonObject()),
            )

            Thread.sleep(100)

            // Verify the server re-requested and stored the updated config
            assertThat(callCount).describedAs("configuration() should have been called twice").isEqualTo(2)
            assertThat(server.editorFormattingConfig!!.indentSize).isEqualTo(6)
            assertThat(server.editorFormattingConfig!!.continuationIndentSize).isEqualTo(12)
        }
    }

    @Nested
    @DisplayName("XtcFormattingConfig resolution order")
    inner class ResolutionOrder {
        @Test
        @DisplayName("editor config takes precedence over LSP FormattingOptions")
        fun editorConfigTakesPrecedence() {
            val editorConfig = XtcFormattingConfig(indentSize = 3, continuationIndentSize = 6)
            val lspOptions =
                org.xvm.lsp.adapter.XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                )

            val resolved = XtcFormattingConfig.resolve("file:///test.x", lspOptions, editorConfig)

            assertThat(resolved.indentSize)
                .describedAs("editor config (3) should win over LSP options (4)")
                .isEqualTo(3)
            assertThat(resolved.continuationIndentSize).isEqualTo(6)
        }

        @Test
        @DisplayName("LSP FormattingOptions used when no editor config")
        fun lspOptionsUsedWhenNoEditorConfig() {
            val lspOptions =
                org.xvm.lsp.adapter.XtcCompilerAdapter.FormattingOptions(
                    tabSize = 2,
                    insertSpaces = true,
                )

            val resolved = XtcFormattingConfig.resolve("file:///test.x", lspOptions, null)

            assertThat(resolved.indentSize)
                .describedAs("LSP tabSize should be used when no editor config")
                .isEqualTo(2)
        }

        @Test
        @DisplayName("defaults used when LSP options specify tabs")
        fun defaultsUsedForTabs() {
            val lspOptions =
                org.xvm.lsp.adapter.XtcCompilerAdapter.FormattingOptions(
                    tabSize = 8,
                    insertSpaces = false,
                )

            val resolved = XtcFormattingConfig.resolve("file:///test.x", lspOptions, null)

            // When insertSpaces is false, XTC falls back to its own default indent size
            assertThat(resolved.indentSize)
                .describedAs("XTC default indent (4) should be used when tabs are specified")
                .isEqualTo(4)
            assertThat(resolved.insertSpaces).isFalse()
        }
    }
}
