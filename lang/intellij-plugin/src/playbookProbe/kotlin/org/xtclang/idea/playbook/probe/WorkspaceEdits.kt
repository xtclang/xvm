package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import java.util.concurrent.CompletableFuture

/** Exercise the installed client's workspace/applyEdit handler, LSP4IJ's, without adding a server test RPC. */
object WorkspaceEdits {
    @JvmStatic
    fun apply(
        project: Project,
        json: String,
    ): CompletableFuture<ApplyWorkspaceEditResponse> {
        val wrapper = LanguageServiceAccessor.getInstance(project).startedServers.single()
        // the wrapper has no public accessor for its client
        val client =
            LanguageServerWrapper::class.java.getDeclaredField("languageClient").run {
                isAccessible = true
                get(wrapper) as LanguageClientImpl
            }
        return client.applyEdit(JSONUtils.getLsp4jGson().fromJson(json, ApplyWorkspaceEditParams::class.java))
    }
}
