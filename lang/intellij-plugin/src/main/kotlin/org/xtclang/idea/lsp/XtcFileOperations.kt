package org.xtclang.idea.lsp

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/** Shared preflight ownership for the file-tree Rename and Move actions. */
internal object XtcFileOperations {
    /** The plugin's notifications' group, which plugin.xml declares. */
    const val NOTIFICATIONS = "Ecstasy Language Server"

    fun isSourcePath(file: VirtualFile): Boolean =
        if (!file.isDirectory) {
            file.extension == "x"
        } else {
            file.children.any { !it.isDirectory && it.extension == "x" } ||
                generateSequence(file) { it.parent }
                    .any { it.parent?.findChild("${it.name}.x")?.isDirectory == false }
        }

    fun server(project: Project): LanguageServerWrapper? =
        LanguageServiceAccessor.getInstance(project).startedServers.singleOrNull {
            it.serverDefinition.id == CompilerSettings.SERVER_ID &&
                it.serverCapabilitiesSync
                    ?.workspace
                    ?.fileOperations
                    ?.willRename != null
        }

    fun submit(
        project: Project,
        title: String,
        future: CompletableFuture<XtcRenameEdit?>,
        completed: () -> Unit = {},
    ) {
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                ProgressIndicatorUtils.awaitWithCheckCanceled(future.handle { _, _ -> null })
            }

            override fun onCancel() {
                future.cancel(true)
            }
        }.queue()
        future.whenComplete { edit, error ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || future.isCancelled) return@invokeLater
                val failure =
                    when {
                        error != null -> fileOperationFailure(title, error) ?: return@invokeLater
                        edit == null -> "The compiler cannot safely apply this operation. No changes were made."
                        !edit.apply() -> "Sources or paths changed; invoke the operation again."
                        else -> null
                    }
                if (failure == null) {
                    completed()
                } else {
                    // the operation ran in the background, and its answer can come after other changes, an Undo's
                    // among them: a notification says so, where a modal dialog would hold the IDE until it is closed
                    Notification(NOTIFICATIONS, "Ecstasy Refactoring", failure, NotificationType.ERROR).notify(project)
                }
            }
        }
    }
}

/** What a file operation's notification says when a change of the project overtook the server's proof. */
internal const val FILE_OPERATION_OVERTAKEN = "the project changed while the language server proved it; invoke it again."

/**
 * What the notification of the file operation [title], a file's rename or a move, says when the server's answer failed with
 * [error]: nothing for a cancellation, as of the operation's progress; that a change of the project overtook the server's
 * proof, as its ContentModified says, so that the operation invoked again is proven of the project as it then is;
 * otherwise the server's reason, as that it can't prove the operation while a module does not compile.
 */
internal fun fileOperationFailure(
    title: String,
    error: Throwable,
): String? {
    val causes = generateSequence(error) { it.cause }
    return when {
        causes.any { it is CancellationException } -> {
            null
        }

        causes.any { it is ResponseErrorException && it.responseError.code == ResponseErrorCode.ContentModified.value } -> {
            "$title failed: $FILE_OPERATION_OVERTAKEN"
        }

        else -> {
            "$title failed: ${error.cause?.message ?: error.message}"
        }
    }
}
