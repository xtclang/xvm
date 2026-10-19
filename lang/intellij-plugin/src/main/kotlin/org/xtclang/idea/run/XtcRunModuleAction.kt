package org.xtclang.idea.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.commands.CommandExecutor
import org.xtclang.idea.XtcFileType

/**
 * Runs an Ecstasy module: from the Run lens (LSP command `xtc.runModule` with the source URI and module
 * name), or from an Ecstasy editor. Reuses the module's run configuration or creates a temporary one, as
 * Run Context Configuration does, so Stop, Rerun and the Run tool window behave natively.
 */
class XtcRunModuleAction :
    AnAction(),
    DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible =
            event.project != null &&
            (event.getData(CommandExecutor.LSP_COMMAND) != null || event.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == XtcFileType)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val command = event.getData(CommandExecutor.LSP_COMMAND)
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isInLocalFileSystem && it.fileType == XtcFileType }
        val uri = command?.getArgumentAt(0, String::class.java) ?: file?.toNioPath()?.toUri()?.toString()
        val moduleName =
            command?.getArgumentAt(1, String::class.java)
                ?: event.getData(CommonDataKeys.EDITOR)?.document?.let { declaredModule(it.text) }
                ?: file?.nameWithoutExtension
                ?: return
        // LSP commands may arrive off the event dispatch thread; run configurations start on it
        ApplicationManager.getApplication().invokeLater({ run(project, moduleName, uri) }, project.disposed)
    }

    companion object {
        private val MODULE = Regex("""^\s*module\s+([\w.]+)""", RegexOption.MULTILINE)

        internal fun declaredModule(text: CharSequence): String? = MODULE.find(text)?.groupValues?.get(1)

        fun run(
            project: Project,
            moduleName: String,
            uri: String?,
        ) {
            val runManager = RunManager.getInstance(project)
            val settings =
                runManager.allSettings.firstOrNull {
                    (it.configuration as? XtcRunConfiguration)?.let { config ->
                        config.moduleName == moduleName || (uri != null && config.moduleUri == uri)
                    } == true
                } ?: runManager
                    .createConfiguration(
                        "Run $moduleName",
                        ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java).configurationFactories.first(),
                    ).also {
                        (it.configuration as XtcRunConfiguration).apply {
                            this.moduleName = moduleName
                            moduleUri = uri.orEmpty()
                            runWith = XtcRunMode.PROJECT
                        }
                        runManager.setTemporaryConfiguration(it)
                    }
            // the Run lens names the module as it is now: a configuration found by its name keeps up with a root
            // file that was renamed or moved, and one found by its source with a module that was renamed
            (settings.configuration as? XtcRunConfiguration)?.let { config ->
                config.moduleName = moduleName
                if (uri != null) config.moduleUri = uri
            }
            runManager.selectedConfiguration = settings
            ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        }
    }
}
