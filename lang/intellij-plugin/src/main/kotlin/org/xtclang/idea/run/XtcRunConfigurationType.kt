package org.xtclang.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.xtclang.idea.XtcIconProvider
import javax.swing.Icon

/**
 * Run configuration type for XTC applications.
 */
class XtcRunConfigurationType : ConfigurationType {
    override fun getDisplayName() = "XTC Application"

    override fun getConfigurationTypeDescription() = "Run an XTC application"

    override fun getIcon(): Icon = XtcIconProvider.XTC_ICON ?: com.intellij.icons.AllIcons.FileTypes.Any_type

    override fun getId() = "XtcRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(XtcConfigurationFactory(this))
}

/**
 * Factory for creating XTC run configurations.
 */
class XtcConfigurationFactory(
    type: ConfigurationType,
) : ConfigurationFactory(type) {
    override fun getId() = "XtcConfigurationFactory"

    override fun getName() = "XTC Application"

    override fun createTemplateConfiguration(project: Project): RunConfiguration = XtcRunConfiguration(project, this, "XTC Application")
}
