package org.xtclang.idea.playbook.probe

import com.google.gson.Gson
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

/** Bounded to one scenario's lifetime; observe real refresh requests without replacing handlers. */
class RefreshRequests private constructor(
    private val project: Project,
    private val owner: LanguageServerWrapper,
) : LanguageServerLifecycleListener {
    private val requests = ConcurrentLinkedQueue<String>()

    override fun handleLSPMessage(
        message: Message,
        consumer: MessageConsumer,
        wrapper: LanguageServerWrapper,
    ) {
        if (
            wrapper === owner &&
            message is RequestMessage &&
            message.method.startsWith("workspace/") &&
            message.method.endsWith("/refresh")
        ) {
            requests.add(message.method)
        }
    }

    fun values(): List<String> = requests.toList()

    fun negotiated(): List<String> {
        // Test-only observation of the actual handshake, not a second capability builder.
        // Fail on an upstream representation change instead of silently skipping providers.
        val initialization =
            LanguageServerWrapper::class.java.getDeclaredField("initParams").run {
                isAccessible = true
                get(owner) as InitializeParams
            }
        // Read the handshake's data: the driver and installed plugin can expose different
        // LSP4J API versions, especially for newer optional capabilities such as folding refresh.
        val workspace = Gson().toJsonTree(requireNotNull(initialization.capabilities.workspace)).asJsonObject
        return mapOf(
            "diagnostics" to "diagnostic",
            "semanticTokens" to "semanticTokens",
            "inlayHint" to "inlayHint",
            "codeLens" to "codeLens",
            "foldingRange" to "foldingRange",
        ).mapNotNull { (capability, method) ->
            "workspace/$method/refresh".takeIf {
                workspace.getAsJsonObject(capability)?.get("refreshSupport")?.asBoolean == true
            }
        }
    }

    fun clear() = requests.clear()

    fun tabLimit(value: Int): Int {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val settings = UISettings.getInstance()
        val previous = settings.editorTabLimit
        settings.editorTabLimit = value
        return previous
    }

    /** Exercise the real handlers while the write lock keeps their NBRA work queued. */
    fun burst(): CompletableFuture<Void> {
        ApplicationManager.getApplication().assertIsDispatchThread()
        check(owner.openedDocuments.size >= 16) { "Refresh regression needs many connected documents" }
        val client =
            LanguageServerWrapper::class.java.getDeclaredField("languageClient").run {
                isAccessible = true
                get(owner) as LanguageClientImpl
            }
        return WriteCommandAction.writeCommandAction(project).compute<CompletableFuture<Void>, RuntimeException> {
            CompletableFuture.allOf(
                *buildList {
                    repeat(64) {
                        add(client.refreshCodeLenses())
                        add(client.refreshInlayHints())
                        add(client.refreshSemanticTokens())
                    }
                }.toTypedArray(),
            )
        }
    }

    override fun handleStatusChanged(wrapper: LanguageServerWrapper) = Unit

    override fun handleError(
        wrapper: LanguageServerWrapper,
        error: Throwable,
    ) = Unit

    override fun dispose() {
        LanguageServerLifecycleManager
            .getInstance(project)
            .removeLanguageServerLifecycleListener(this)
    }

    companion object {
        @JvmStatic
        fun listen(project: Project): RefreshRequests =
            RefreshRequests(project, LanguageServiceAccessor.getInstance(project).startedServers.single()).also {
                LanguageServerLifecycleManager
                    .getInstance(project)
                    .addLanguageServerLifecycleListener(it)
            }
    }
}
