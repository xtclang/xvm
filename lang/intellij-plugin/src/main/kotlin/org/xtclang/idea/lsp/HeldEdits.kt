package org.xtclang.idea.lsp

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP73 — 0.21.0 sends an edit once IntelliJ commits its document, and of its requests only on-type
// formatting sends the edits it holds first (#1729), so a request right after typing can reach the server before the
// typed text. Our callers send them first; they can go once a release has our #1738, where a request waits so.

/**
 * Sends the Ecstasy language server the edits of [file] that LSP4IJ still holds, and returns once they are sent, as a
 * request that follows them then reads the text that the editor has. A failed edit fails nothing: the request then goes
 * as it would have. Called under a read action, which the wait checks for cancellation.
 */
internal fun sendHeldEdits(
    project: Project,
    file: VirtualFile,
) {
    val sent =
        LanguageServiceAccessor
            .getInstance(project)
            .startedServers
            .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
            .mapNotNull { server -> server.getOpenedDocument(server.toUri(file))?.synchronizer }
            .map { synchronizer -> synchronizer.didOpenFuture.thenCompose { synchronizer.flushPendingChanges() } }
    if (sent.isNotEmpty()) {
        ProgressIndicatorUtils.awaitWithCheckCanceled(CompletableFuture.allOf(*sent.toTypedArray()).handle { _, _ -> null })
    }
}
