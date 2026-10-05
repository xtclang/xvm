package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Files
import java.nio.file.Path

/** Consume atomic exports without rewriting user settings or restarting a server. */
class CompilerBuildUpdates : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val roots = CompilerWorkspaceModels.roots(project)
                    if (events.none { event -> roots.any { affectsModel(it, Path.of(event.path)) } }) return
                    ApplicationManager.getApplication().executeOnPooledThread {
                        if (!project.isDisposed) CompilerBuildModel.publish(project)
                    }
                }
            },
        )
    }

    companion object {
        internal fun affectsModel(root: Path, changed: Path): Boolean =
            listOf(CompilerBuildModel.PATH, CompilerWorkspaceModels.PATH).any {
                root.resolve(it).normalize().startsWith(changed.normalize())
            }
    }
}

/** Community Gradle sync has a public completion event; failed/cancelled syncs do not refresh. */
class CompilerGradleSync : ExternalSystemTaskNotificationListener {
    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        if (id.projectSystemId.id != "GRADLE" || id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        val project = id.findProject() ?: return
        val root = Path.of(projectPath)
        // Opt into automatic export after the first explicit import. Do not run arbitrary builds
        // merely because a folder is open, and do not interpret a Gradle script to guess its tasks.
        if (listOf(CompilerBuildModel.PATH, CompilerWorkspaceModels.PATH).none { Files.isRegularFile(root.resolve(it)) }) return
        CompilerBuildModel.refresh(project, false) { failure ->
            if (failure != null) logger<CompilerGradleSync>().warn(failure)
        }
    }
}
