package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import java.nio.file.Files
import java.nio.file.Path

// What the plugin does with IntelliJ's Gradle plugin, registered by xtc-gradle.xml only where the IDE has it.

/** The roots of the Gradle builds that IntelliJ imported into a project. */
class GradleBuildRoots : BuildRoots {
    override fun roots(project: Project): List<String> =
        GradleSettings.getInstance(project).linkedProjectsSettings.map { it.externalProjectPath }
}

/** Publishes the build model again when a Gradle build is unlinked from a project, without the roots it had. */
class GradleBuildUnlinks : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect(project).subscribe(
            GradleSettingsListener.TOPIC,
            object : GradleSettingsListener {
                override fun onProjectsUnlinked(linkedProjectPaths: Set<String>) {
                    project.service<CompilerImportService>().rootsRemoved()
                    ApplicationManager.getApplication().executeOnPooledThread {
                        if (!project.isDisposed) CompilerBuildModel.publish(project)
                    }
                }
            },
        )
    }
}

/** Community Gradle sync has a public completion event; failed/cancelled syncs do not refresh. */
class CompilerGradleSync : ExternalSystemTaskNotificationListener {
    override fun onSuccess(
        projectPath: String,
        id: ExternalSystemTaskId,
    ) {
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
