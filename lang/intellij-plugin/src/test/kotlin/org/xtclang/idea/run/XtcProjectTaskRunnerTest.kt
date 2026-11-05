package org.xtclang.idea.run

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.Module
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.ProjectTaskRunner
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor

/** Which builder Build Project in embedded mode reaches, with the plugins the IDE has loaded, Gradle's among them. */
class XtcProjectTaskRunnerTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `embedded builds are asked before Gradle's runner, which takes every module of an imported Gradle project`() {
        // Build Project gives each module to the first runner that takes it; Gradle's takes all of a Gradle project's,
        // its main and test source sets', so ours after it never ran and Gradle built classes and testClasses
        val runners = ProjectTaskRunner.EP_NAME.extensionList.map { it.javaClass.name }
        assertThat(runners).contains(GRADLE_RUNNER, XtcProjectTaskRunner::class.java.name)
        assertThat(runners.indexOf(XtcProjectTaskRunner::class.java.name)).isLessThan(runners.indexOf(GRADLE_RUNNER))
    }

    @Test
    fun `in a Gradle project, a module of Ecstasy sources builds embedded, and one with Java sources too with Gradle`() {
        runInEdtAndWait {
            val fixture = editor.fixture
            gradleModule(fixture.module) {
                fixture.addFileToProject("app/App.x", "module App {}")
                IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
                assertThat(builder(fixture.module)).isEqualTo(XtcProjectTaskRunner::class.java.name)

                // the embedded build compiles no Java, so Gradle builds the module, its Ecstasy sources with the XTC plugin
                fixture.addFileToProject("app/Main.java", "class Main {}")
                IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
                assertThat(builder(fixture.module)).isEqualTo(GRADLE_RUNNER)
            }
        }
    }

    /** The runner that Build Project gives the module's build to: the first, in their order, that takes it. */
    private fun builder(module: Module): String {
        val task = ProjectTaskManager.getInstance(module.project).createModulesBuildTask(module, true, false, false)
        return ProjectTaskRunner.EP_NAME.extensionList
            .first { it.canRun(module.project, task, ProjectTaskContext()) }
            .javaClass.name
    }

    /** Runs [test] with [module] a module of a Gradle build whose builds Gradle runs, as IntelliJ's import makes it. */
    private fun gradleModule(
        module: Module,
        test: () -> Unit,
    ) {
        val root = "/gradle-build"
        val settings = GradleSettings.getInstance(module.project)
        settings.linkProject(
            GradleProjectSettings().apply {
                externalProjectPath = root
                delegatedBuild = true
            },
        )
        WriteAction.run<RuntimeException> {
            ExternalSystemModulePropertyManager.getInstance(module).setExternalOptions(
                GradleConstants.SYSTEM_ID,
                ModuleData(":", GradleConstants.SYSTEM_ID, "JAVA_MODULE", module.name, root, root),
                ProjectData(GradleConstants.SYSTEM_ID, module.name, root, root),
            )
        }
        try {
            test()
        } finally {
            WriteAction.run<RuntimeException> { ExternalSystemModulePropertyManager.getInstance(module).unlinkExternalOptions() }
            settings.unlinkExternalProject(root)
        }
    }

    private companion object {
        const val GRADLE_RUNNER = "org.jetbrains.plugins.gradle.execution.build.GradleProjectTaskRunner"
    }
}
