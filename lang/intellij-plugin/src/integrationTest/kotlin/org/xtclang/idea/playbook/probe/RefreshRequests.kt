package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import java.util.concurrent.ConcurrentLinkedQueue

/** Bounded to one scenario's lifetime; observe real refresh requests without replacing handlers. */
class RefreshRequests private constructor(
    private val project: Project,
) : LanguageServerLifecycleListener {
    private val requests = ConcurrentLinkedQueue<String>()

    override fun handleLSPMessage(
        message: Message,
        consumer: MessageConsumer,
        wrapper: LanguageServerWrapper,
    ) {
        if (
            message is RequestMessage &&
            message.method.startsWith("workspace/") &&
            message.method.endsWith("/refresh")
        ) {
            requests.add(message.method)
        }
    }

    fun values(): List<String> = requests.toList()

    fun clear() = requests.clear()

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
            RefreshRequests(project).also {
                LanguageServerLifecycleManager
                    .getInstance(project)
                    .addLanguageServerLifecycleListener(it)
            }
    }
}
