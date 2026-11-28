package org.xtclang.idea.playbook.probe

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * IntelliJ's own import of the Gradle build at a project's root, as Load Gradle Project does: the import makes the
 * project's modules Gradle's, and Build Project then runs their Gradle tasks.
 */
object GradleImports {
    /** The roots whose import IntelliJ has applied to their projects since [link] asked for it. */
    private val applied = ConcurrentHashMap.newKeySet<Path>()

    /**
     * Links the build at [project]'s root, unless IntelliJ has, and imports it. Its Gradle JVM is the IDE's own: the
     * project has no SDK, which IntelliJ's default for a linked build would take.
     */
    @JvmStatic
    fun link(project: Project) {
        val root = requireNotNull(project.basePath)
        applied.remove(Path.of(root))
        project.messageBus.connect(project).subscribe(
            ProjectDataImportListener.TOPIC,
            object : ProjectDataImportListener {
                override fun onImportFinished(projectPath: String?) {
                    projectPath?.let(Path::of)?.takeIf { it == Path.of(root) }?.let(applied::add)
                }
            },
        )
        val settings = GradleSettings.getInstance(project)
        if (settings.getLinkedProjectSettings(root) == null) {
            settings.linkProject(
                GradleProjectSettings().apply {
                    externalProjectPath = root
                    gradleJvm = ExternalSystemJdkUtil.USE_INTERNAL_JAVA
                },
            )
        }
        ExternalSystemUtil.refreshProject(
            root,
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC),
        )
    }

    /**
     * Whether IntelliJ has imported [project]'s Gradle build, as [link] asked: the import's data has been applied, and the
     * project's modules, which it made, are all Gradle's.
     */
    @JvmStatic
    fun imported(project: Project): Boolean =
        Path.of(requireNotNull(project.basePath)) in applied &&
            ModuleManager.getInstance(project).modules.let { modules ->
                modules.isNotEmpty() && modules.all { ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it) }
            }
}
