package org.xvm.lsp.server

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ServerCapabilities
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.AdapterCapability
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.mock.MockAdapter
import org.xvm.lsp.adapter.xdk.XdkAdapter

/** Every adapter flag has exactly one advertised provider; absent flags cannot leak a provider. */
class CapabilityContractTest {
    @ParameterizedTest
    @EnumSource(AdapterCapability::class)
    fun `a single adapter feature advertises only its provider`(feature: AdapterCapability) {
        val adapter =
            object : Adapter by MockAdapter() {
                override val capabilities = setOf(feature)
            }
        XtcLanguageServer(adapter).use { server ->
            val capabilities = server.initialize(editorInitializeParams()).join().capabilities
            assertThat(providers(capabilities)).containsExactly(provider(feature))
            assertThat(capabilities.experimental).isNull()
        }
    }

    @Test
    fun `an adapter without features advertises only synchronization and position encoding`() {
        val adapter =
            object : Adapter by MockAdapter() {
                override val capabilities = emptySet<AdapterCapability>()
            }
        XtcLanguageServer(adapter).use { server ->
            val capabilities = server.initialize(editorInitializeParams()).join().capabilities
            assertThat(Gson().toJsonTree(capabilities).asJsonObject.keySet())
                .containsExactlyInAnyOrder("textDocumentSync", "positionEncoding")
        }
    }

    @Test
    fun `compiler capability inventory has no unsupported standard or experimental providers`() {
        CompilerTestSupport.configure()
        val adapter = XdkAdapter()
        XtcLanguageServer(adapter).use { server ->
            val capabilities = server.initialize(editorInitializeParams()).join().capabilities
            // This client has not negotiated versioned edits or pull diagnostics.
            assertThat(providers(capabilities))
                .containsExactlyInAnyOrderElementsOf(
                    (adapter.capabilities - AdapterCapability.RENAME).map(::provider),
                )
            assertThat(capabilities.experimental).isEqualTo(mapOf("xtcRenameProposal" to 1, "xtcFileMoveProposal" to 1))
            assertThat(capabilities.workspace.workspaceFolders.supported).isTrue()
            assertThat(capabilities.workspace.fileOperations).hasAllNullFieldsOrProperties()
            assertThat(capabilities.notebookDocumentSync).isNull()
        }
    }

    private fun providers(capabilities: ServerCapabilities) =
        Gson()
            .toJsonTree(capabilities)
            .asJsonObject
            .keySet()
            .filter { it.endsWith("Provider") }

    private fun provider(feature: AdapterCapability): String =
        when (feature) {
            AdapterCapability.HOVER -> "hoverProvider"
            AdapterCapability.COMPLETION -> "completionProvider"
            AdapterCapability.INLINE_COMPLETION -> "inlineCompletionProvider"
            AdapterCapability.DEFINITION -> "definitionProvider"
            AdapterCapability.DECLARATION -> "declarationProvider"
            AdapterCapability.REFERENCES -> "referencesProvider"
            AdapterCapability.DOCUMENT_SYMBOL -> "documentSymbolProvider"
            AdapterCapability.DOCUMENT_HIGHLIGHT -> "documentHighlightProvider"
            AdapterCapability.SELECTION_RANGE -> "selectionRangeProvider"
            AdapterCapability.FOLDING_RANGE -> "foldingRangeProvider"
            AdapterCapability.RENAME -> "renameProvider"
            AdapterCapability.CODE_ACTION -> "codeActionProvider"
            AdapterCapability.FORMATTING -> "documentFormattingProvider"
            AdapterCapability.RANGE_FORMATTING -> "documentRangeFormattingProvider"
            AdapterCapability.ON_TYPE_FORMATTING -> "documentOnTypeFormattingProvider"
            AdapterCapability.DOCUMENT_LINK -> "documentLinkProvider"
            AdapterCapability.DOCUMENT_COLOR -> "colorProvider"
            AdapterCapability.SIGNATURE_HELP -> "signatureHelpProvider"
            AdapterCapability.SEMANTIC_TOKENS -> "semanticTokensProvider"
            AdapterCapability.WORKSPACE_SYMBOL -> "workspaceSymbolProvider"
            AdapterCapability.CODE_LENS -> "codeLensProvider"
            AdapterCapability.LINKED_EDITING -> "linkedEditingRangeProvider"
            AdapterCapability.TYPE_HIERARCHY -> "typeHierarchyProvider"
            AdapterCapability.TYPE_DEFINITION -> "typeDefinitionProvider"
            AdapterCapability.IMPLEMENTATION -> "implementationProvider"
            AdapterCapability.MONIKER -> "monikerProvider"
            AdapterCapability.CALL_HIERARCHY -> "callHierarchyProvider"
            AdapterCapability.INLAY_HINT -> "inlayHintProvider"
        }
}
