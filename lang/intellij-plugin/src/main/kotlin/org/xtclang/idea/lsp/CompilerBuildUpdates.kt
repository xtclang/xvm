package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
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
        internal fun affectsModel(
            root: Path,
            changed: Path,
        ): Boolean =
            listOf(CompilerBuildModel.PATH, CompilerWorkspaceModels.PATH).any {
                root.resolve(it).normalize().startsWith(changed.normalize())
            }
    }
}
