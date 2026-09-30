package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.junit.jupiter.api.Test

class ClientCapabilitiesTest {
    @Test
    fun `do not advertise save hooks that LSP4IJ does not dispatch`() {
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
        assertThat(sync.willSave).isFalse()
        assertThat(sync.willSaveWaitUntil).isFalse()
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
