package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemInsertTextModeSupportCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.junit.jupiter.api.Test

/**
 * Checks that the IntelliJ client advertises only capabilities its native integration can honor: snippet
 * indentation, save hooks and deferred resolution.
 */
class ClientCapabilitiesTest {
    @Test
    fun `advertise the indentation modes LSP4IJ declares, since its snippets honor AsIs`() {
        val completion =
            CompletionCapabilities().apply {
                completionItem =
                    CompletionItemCapabilities().apply {
                        snippetSupport = true
                        insertTextModeSupport = CompletionItemInsertTextModeSupportCapabilities(InsertTextMode.entries)
                    }
            }
        val params =
            InitializeParams().apply {
                capabilities =
                    ClientCapabilities().apply {
                        textDocument = TextDocumentClientCapabilities().apply { this.completion = completion }
                    }
            }
        XtcLanguageServerFactory().createClientFeatures().initializeParams(params)
        assertThat(completion.insertTextMode).isNull()
        assertThat(completion.completionItem.insertTextModeSupport.valueSet).containsExactlyElementsOf(InsertTextMode.entries)
        assertThat(completion.completionItem.snippetSupport).isTrue()
    }

    @Test
    fun `ask for quick fixes per diagnostic`() {
        assertThat(XtcLspConnectionProvider.LSP4IJ_CODE_ACTIONS)
            .isEqualTo(mapOf("xtcCodeActions" to mapOf("quickFixesPerDiagnostic" to true)))
    }

    @Test
    fun `name the settings page that adds consumers, for the server's message on modules outside the project`() {
        assertThat(XtcLspConnectionProvider.ADD_CONSUMERS)
            .isEqualTo(
                mapOf("xtcAddConsumers" to "use Add consumers from folders in Settings | Languages & Frameworks | Ecstasy | Compiler"),
            )
    }

    @Test
    fun `advertise the save hooks LSP4IJ dispatches`() {
        val sync =
            SynchronizationCapabilities().apply {
                willSave = true
                willSaveWaitUntil = true
                didSave = true
            }
        val params =
            InitializeParams().apply {
                capabilities =
                    ClientCapabilities().apply {
                        textDocument =
                            TextDocumentClientCapabilities().apply { synchronization = sync }
                    }
            }
        XtcLanguageServerFactory().createClientFeatures().initializeParams(params)
        assertThat(sync.willSave).isTrue()
        assertThat(sync.willSaveWaitUntil).isTrue()
        assertThat(sync.didSave).isTrue()
    }

    @Test
    fun `negotiate lazy action edits and completion documentation resolve`() {
        val actions = CodeActionResolveSupportCapabilities(listOf("edit", "command"))
        val completion = CompletionItemResolveSupportCapabilities(listOf("documentation"))
        val params =
            InitializeParams().apply {
                capabilities =
                    ClientCapabilities().apply {
                        textDocument =
                            TextDocumentClientCapabilities().apply {
                                codeAction =
                                    CodeActionCapabilities().apply { resolveSupport = actions }
                                this.completion =
                                    CompletionCapabilities().apply {
                                        completionItem =
                                            CompletionItemCapabilities().apply {
                                                resolveSupport = completion
                                            }
                                    }
                            }
                    }
            }
        XtcLanguageServerFactory().createClientFeatures().initializeParams(params)
        assertThat(actions.properties).containsExactly("edit", "command")
        assertThat(completion.properties).containsExactly("documentation")
    }

    @Test
    fun `accept clients without action resolve capabilities`() {
        val params = InitializeParams().apply { capabilities = ClientCapabilities() }
        XtcLanguageServerFactory().createClientFeatures().initializeParams(params)
        assertThat(params.capabilities.textDocument).isNull()
    }
}
