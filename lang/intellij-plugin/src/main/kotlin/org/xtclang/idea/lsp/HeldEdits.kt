package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
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
 * request that follows them then reads the text that the editor has. With nothing held it sends nothing, and returns
 * at once unless an edit is still on its way. A failed edit fails nothing: the request then goes as it would have.
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
        awaitSent(CompletableFuture.allOf(*sent.toTypedArray()))
    }
}

/**
 * Waits until the edits that [sent] dispatches are sent, where a wait holds nothing up. In IntelliJ 2026.2 the callers
 * run on a pooled thread, in a read action that a write action cancels: completion's contributors in AsyncCompletion's
 * `tryReadOrCancel`, parameter info's `findElementForParameterInfo` and `updateParameterInfo` in non-blocking read
 * actions; and LSP4IJ's dispatcher, which sends the edits, needs neither the EDT nor a lock. On the EDT, or with write
 * access, as completion has when it starts in a write action, it returns at once: the edits are queued all the same,
 * though the request after them can then come first.
 */
internal fun awaitSent(sent: CompletableFuture<*>) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread || application.isWriteAccessAllowed) return
    ProgressIndicatorUtils.awaitWithCheckCanceled(sent.handle { _, _ -> null })
}
