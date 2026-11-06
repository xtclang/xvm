package org.xtclang.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.xtclang.idea.XtcIconProvider
import javax.swing.Icon

/**
 * Run configuration type for Ecstasy applications.
 *
 * `getId()` and `XtcConfigurationFactory.getId()` are internal identifiers persisted in
 * workspace.xml across sessions — they MUST remain stable ("XtcRunConfiguration",
 * "XtcConfigurationFactory") even though everything user-facing now reads "Ecstasy".
 */
class XtcRunConfigurationType : ConfigurationType {
    override fun getDisplayName() = "Ecstasy Application"

    override fun getConfigurationTypeDescription() = "Run an Ecstasy application"

    override fun getIcon(): Icon = XtcIconProvider.xtcIcon ?: com.intellij.icons.AllIcons.FileTypes.Any_type

    override fun getId() = "XtcRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(XtcConfigurationFactory(this))

    companion object {
        /**
         * The registered type. RunManager lists a type's configurations by the type's identity, so a configuration
         * made from another instance's factory is not among them, and a producer never finds it again.
         */
        val instance: XtcRunConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(XtcRunConfigurationType::class.java)
    }
}

/** Factory for creating Ecstasy run configurations. */
class XtcConfigurationFactory(
    type: ConfigurationType,
) : ConfigurationFactory(type) {
    override fun getId() = "XtcConfigurationFactory"

    override fun getName() = "Ecstasy Application"

    override fun createTemplateConfiguration(project: Project): RunConfiguration = XtcRunConfiguration(project, this, "Ecstasy Application")
}
