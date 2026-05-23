package org.xtclang.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.xtclang.idea.XtcIconProvider
import javax.swing.Icon

/**
 * Run configuration type for Ecstasy applications.
 *
 * `getId()` and `XtcConfigurationFactory.getId()` are internal identifiers
 * persisted in workspace.xml across sessions — they MUST remain stable
 * ("XtcRunConfiguration", "XtcConfigurationFactory") even though everything
 * user-facing now reads "Ecstasy".
 */
class XtcRunConfigurationType : ConfigurationType {
    override fun getDisplayName() = "Ecstasy Application"

    override fun getConfigurationTypeDescription() = "Run an Ecstasy application"

    override fun getIcon(): Icon = XtcIconProvider.XTC_ICON ?: com.intellij.icons.AllIcons.FileTypes.Any_type

    override fun getId() = "XtcRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(XtcConfigurationFactory(this))
}

/**
 * Factory for creating Ecstasy run configurations.
 */
class XtcConfigurationFactory(
    type: ConfigurationType,
) : ConfigurationFactory(type) {
    override fun getId() = "XtcConfigurationFactory"

    override fun getName() = "Ecstasy Application"

    override fun createTemplateConfiguration(project: Project): RunConfiguration = XtcRunConfiguration(project, this, "Ecstasy Application")
}
