package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import java.util.concurrent.ConcurrentLinkedQueue

/** Observe only the requested token; verbose console buffers can roll over during a long run. */
class PartialResults private constructor(
    private val project: Project,
    private val token: String,
) : LanguageServerLifecycleListener {
    private val batches = ConcurrentLinkedQueue<String>()

    override fun handleLSPMessage(
        message: Message,
        consumer: MessageConsumer,
        wrapper: LanguageServerWrapper,
    ) {
        if (message !is NotificationMessage || message.method != "$/progress") return
        val params = JSONUtils.getLsp4jGson().toJsonTree(message.params).asJsonObject
        if (params["token"]?.asString == token) batches.add(params["value"].toString())
    }

    fun values(): List<String> = batches.toList()

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
        fun listen(
            project: Project,
            token: String,
        ): PartialResults =
            PartialResults(project, token).also {
                LanguageServerLifecycleManager
                    .getInstance(project)
                    .addLanguageServerLifecycleListener(it)
            }
    }
}
