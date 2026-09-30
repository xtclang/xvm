package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.xtclang.idea.lsp.XtcClientFeatures

/** Exercise the installed client's application entry point without adding a server test RPC. */
object WorkspaceEdits {
    @JvmStatic
    fun apply(project: Project, json: String): CompletableFuture<ApplyWorkspaceEditResponse> {
        val features =
            LanguageServiceAccessor.getInstance(project).startedServers.single().clientFeatures
                as XtcClientFeatures
        return features.applyEdit(
            JSONUtils.getLsp4jGson().fromJson(json, ApplyWorkspaceEditParams::class.java)
        )
    }
}
