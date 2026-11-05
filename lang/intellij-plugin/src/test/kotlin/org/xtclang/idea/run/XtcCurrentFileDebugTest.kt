package org.xtclang.idea.run

import com.intellij.execution.actions.ExecutorAction
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import java.nio.file.Files
import java.nio.file.Path

/**
 * The run widget's Run and Debug with "Current File" selected, on a module file: what IntelliJ finds for the file,
 * and whether it can launch it under the debugger, as its Debug button would.
 */
class XtcCurrentFileDebugTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @TempDir lateinit var directory: Path

    @Test
    fun `Debug of Current File on a module file finds an Ecstasy configuration that the debugger launches`() {
        // a file on disk, as the IDE's are: the light fixture's own files are in memory, where a path cannot be had
        val source = Files.writeString(directory.resolve("cardGame.x"), "module cardGame {\n    void run() {}\n}\n")
        runInEdtAndWait {
            val project = editor.fixture.project
            val virtual = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source))
            val file = requireNotNull(PsiManager.getInstance(project).findFile(virtual))

            // what the widget's buttons find for Current File: the producers' configurations from the file's context
            val settings = ExecutorAction.getRunConfigsForCurrentFile(file, true).single { it.configuration is XtcRunConfiguration }
            val configuration = settings.configuration as XtcRunConfiguration
            assertThat(configuration.moduleName).isEqualTo("cardGame")
            assertThat(configuration.moduleUri).isEqualTo(source.toUri().toString())
            configuration.checkConfiguration()

            // each button keeps a configuration only when a runner runs it with that button's executor
            assertThat(ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configuration)).isNotNull
            assertThat(ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration)).isNotNull

            // and the Debug executor's launch has a state to start
            val debug = DefaultDebugExecutor.getDebugExecutorInstance()
            val environment = ExecutionEnvironmentBuilder.create(debug, settings).build()
            assertThat(configuration.getState(debug, environment)).isNotNull
        }
    }
}
