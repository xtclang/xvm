package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.util.concurrent.CompletableFuture

/** Shared preflight ownership for the file-tree Rename and Move actions. */
internal object XtcFileOperations {
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
                        error != null -> {
                            "$title failed: ${error.cause?.message ?: error.message}"
                        }

                        edit == null -> {
                            "The compiler cannot safely apply this operation. No changes were made."
                        }

                        !edit.apply() -> {
                            "Sources or paths changed; invoke the operation again."
                        }

                        else -> {
                            null
                        }
                    }
                if (failure == null) {
                    completed()
                } else {
                    Messages.showErrorDialog(project, failure, "Ecstasy Refactoring")
                }
            }
        }
    }
}
