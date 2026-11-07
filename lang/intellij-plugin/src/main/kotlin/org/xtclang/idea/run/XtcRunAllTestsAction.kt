package org.xtclang.idea.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory

/**
 * Run All Ecstasy Tests in the Project window's context menu, beside Gradle's "Tests in", on a folder whose tests are
 * Java or Kotlin ones too. The context menu offers only the configurations that rank first, and there Gradle's replaces
 * the one that [XtcAllTestsConfigurationProducer] makes. This runs that configuration: the project's one, or else a new
 * temporary one, as the context's Run does. Where the context offers it already, this is not shown.
 */
class XtcRunAllTestsAction :
    AnAction(),
    DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = offered(ConfigurationContext.getFromContext(event.dataContext, event.place))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val settings = configuration(project)
        RunManager.getInstance(project).selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    companion object {
        /**
         * Whether [context] is a folder that holds test modules, where the context's Run offers no Run All: what it
         * offers is the existing configuration that it finds, or else the ones that rank first.
         */
        fun offered(context: ConfigurationContext): Boolean {
            val directory = context.psiLocation as? PsiDirectory ?: return false
            if (!XtcAllTestsConfigurationProducer.holdsTests(directory.virtualFile, directory.project.guessProjectDir())) return false
            val shown =
                context.findExisting()?.let(::listOf) ?: context.configurationsFromContext.orEmpty().map { it.configurationSettings }
            return shown.none { (it.configuration as? XtcRunConfiguration)?.allTestModules == true }
        }

        /** The project's Run All Ecstasy Tests, which the context's Run finds too, or else a new temporary one. */
        fun configuration(project: Project): RunnerAndConfigurationSettings {
            val runs = RunManager.getInstance(project)
            val type = XtcRunConfigurationType.instance
            val existing =
                runs.getConfigurationSettingsList(type).firstOrNull {
                    (it.configuration as? XtcRunConfiguration)?.allTestModules ==
                        true
                }
            return existing
                ?: runs.createConfiguration(XtcAllTestsConfigurationProducer.NAME, type.configurationFactories.first()).also {
                    XtcAllTestsConfigurationProducer.runAll(it.configuration as XtcRunConfiguration)
                    runs.setTemporaryConfiguration(it)
                }
        }
    }
}
