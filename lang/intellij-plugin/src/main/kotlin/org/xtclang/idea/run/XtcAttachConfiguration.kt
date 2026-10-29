package org.xtclang.idea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationBase
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import org.jdom.Element
import org.xtclang.idea.PluginPaths
import org.xtclang.idea.XtcIconProvider
import org.xtclang.idea.dap.XtcDebugAdapterFactory
import org.xtclang.idea.lsp.XtcLspConnectionProvider
import javax.swing.Icon

/**
 * Attaches the debugger to an Ecstasy program that runs outside the IDE under the launcher's `--debug`, as in
 * `xec --debug listen:127.0.0.1:5005,suspend,agent=<the language server's jar> App.x`: Debug starts a debug session in
 * the language server, which attaches to the program's debugger agent at [host] and [port] on this machine, with the
 * program's [token] if it was given one, and with [stopOnEntry], stops a program that waits for the debugger at its
 * first line. Stopping the session detaches, and the program runs on. It has no Run.
 */
class XtcAttachConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : DAPRunConfigurationBase<Any>(project, factory, name) {
    var host = DEFAULT_HOST
    var port = DEFAULT_PORT
    var token = ""
    var stopOnEntry = false // A program that waits for the debugger stops at its first line

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XtcAttachSettingsEditor()

    override fun checkConfiguration() {
        if (port !in 1..65535) throw RuntimeConfigurationError("The port must be between 1 and 65535")
    }

    override fun canRun(executorId: String): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID

    override fun getDebugAdapterServer(): DebugAdapterServerDefinition? =
        DebugAdapterManager.getInstance().getDebugAdapterServerById(XtcDebugAdapterFactory.SERVER_ID)

    /** The arguments of the session's `attach`, as the language server takes them. */
    fun dapParameters(): Map<String, Any> =
        buildMap {
            put("type", "xtc")
            put("request", "attach")
            put("host", host)
            put("port", port)
            token.takeIf { it.isNotEmpty() }?.let { put("token", it) }
            if (stopOnEntry) put("stopOnEntry", true)
        }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        host = element.getAttributeValue("host")?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        port = element.getAttributeValue("port")?.toIntOrNull() ?: DEFAULT_PORT
        token = element.getAttributeValue("token").orEmpty()
        stopOnEntry = element.getAttributeValue("stopOnEntry")?.toBoolean() ?: false
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute("host", host)
        element.setAttribute("port", port.toString())
        element.setAttribute("token", token)
        element.setAttribute("stopOnEntry", stopOnEntry.toString())
    }

    internal companion object {
        const val DEFAULT_HOST = "localhost"
        const val DEFAULT_PORT = 5005

        /**
         * The `--debug` option of `xec` and `xtc run` for a program that waits before its first line for an attach on
         * [port] of this machine, with this plugin's language server jar as its agent; `<jar>` stands in for a jar
         * that is not found.
         */
        fun debugOption(port: Int): String {
            val jar = runCatching { PluginPaths.findServerJar(XtcLspConnectionProvider.LSP_SERVER_JAR).toString() }.getOrDefault("<jar>")
            val value = "listen:127.0.0.1:$port,suspend,agent=$jar"
            return if (value.any(Char::isWhitespace)) "--debug \"$value\"" else "--debug $value"
        }
    }
}

/**
 * The type of [XtcAttachConfiguration]. `getId()` and the factory's are persisted in workspace.xml, so they must stay
 * as they are.
 */
class XtcAttachConfigurationType : ConfigurationType {
    override fun getDisplayName() = "Ecstasy Attach"

    override fun getConfigurationTypeDescription() = "Attach the debugger to an Ecstasy program run with --debug"

    override fun getIcon(): Icon = XtcIconProvider.XTC_ICON ?: com.intellij.icons.AllIcons.FileTypes.Any_type

    override fun getId() = "XtcAttachConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(XtcAttachConfigurationFactory(this))
}

class XtcAttachConfigurationFactory(
    type: ConfigurationType,
) : ConfigurationFactory(type) {
    override fun getId() = "XtcAttachConfigurationFactory"

    override fun getName() = "Ecstasy Attach"

    override fun createTemplateConfiguration(project: Project): RunConfiguration = XtcAttachConfiguration(project, this, "Ecstasy Attach")
}

/** The settings of an [XtcAttachConfiguration], with the option to run the program with. */
class XtcAttachSettingsEditor : SettingsEditor<XtcAttachConfiguration>() {
    private var host = XtcAttachConfiguration.DEFAULT_HOST
    private var port = XtcAttachConfiguration.DEFAULT_PORT
    private var token = ""
    private var stopOnEntry = false

    private val editorPanel by lazy {
        panel {
            row("Host:") {
                textField()
                    .bindText(::host)
                    .comment("This machine's name or loopback address, where the program listens")
            }
            row("Port:") {
                intTextField(1..65535)
                    .bindIntText(::port)
                    .comment("Run the program with ${XtcAttachConfiguration.debugOption(XtcAttachConfiguration.DEFAULT_PORT)}")
            }
            row("Token:") {
                textField()
                    .bindText(::token)
                    .comment("The token the program's --debug option gives, if it gives one")
            }
            row {
                checkBox("Stop at the program's first line")
                    .bindSelected(::stopOnEntry)
                    .comment("For a program that waits for the debugger, as the option's suspend has it")
            }
        }
    }

    override fun createEditor() = editorPanel

    override fun applyEditorTo(config: XtcAttachConfiguration) {
        editorPanel.apply()
        config.host = host
        config.port = port
        config.token = token
        config.stopOnEntry = stopOnEntry
    }

    override fun resetEditorFrom(config: XtcAttachConfiguration) {
        host = config.host
        port = config.port
        token = config.token
        stopOnEntry = config.stopOnEntry
        editorPanel.reset()
    }
}
