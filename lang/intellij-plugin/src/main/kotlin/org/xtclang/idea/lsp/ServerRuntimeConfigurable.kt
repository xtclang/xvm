package org.xtclang.idea.lsp

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/** Editing a draft never restarts an active compiler. Restart is an explicit separate action. */
class ServerRuntimeConfigurable : Configurable {
    private val options = JTextArea(8, 65).apply { name = "xtc.runtime.vmOptions" }
    private var original = ServerRuntimeSettings.Options()

    override fun getDisplayName(): String = "Ecstasy Server Runtime"

    override fun createComponent(): JComponent =
        JPanel(BorderLayout(0, 8)).apply {
            add(
                JBLabel(
                    "<html>Machine-local settings; uses IntelliJ's bundled Java runtime. Apply saves for the next server start.<br>One JVM option per line: -Xms/-Xmx/-Xss sizes, -XX:MaxMetaspaceSize, -XX:ReservedCodeCacheSize,<br>-XX:ActiveProcessorCount, or -XX:+UseG1GC/UseSerialGC/UseParallelGC/UseZGC. Sizes use K, M or G.<br>Example: -Xmx2G. No shell quoting, agents, classpath or system-property overrides.</html>",
                ),
                BorderLayout.NORTH,
            )
            add(JBScrollPane(options), BorderLayout.CENTER)
            add(
                JButton("Restart running Ecstasy servers with saved settings").apply {
                    addActionListener {
                        ServerRuntimeSettings.getInstance().state.arguments()
                        ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }.forEach { project ->
                            LanguageServiceAccessor
                                .getInstance(project)
                                .startedServers
                                .filter {
                                    it.serverDefinition.id ==
                                        CompilerSettings.SERVER_ID
                                }.forEach { it.restart() }
                        }
                    }
                },
                BorderLayout.SOUTH,
            )
            reset()
        }

    override fun isModified(): Boolean = options.text != original.vmOptions

    override fun reset() {
        original = ServerRuntimeSettings.getInstance().state
        options.text = original.vmOptions
    }

    override fun apply() {
        try {
            ServerRuntimeSettings.getInstance().install(original, original.copy(vmOptions = options.text))
            reset()
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(failure.message ?: "Invalid Ecstasy JVM options")
        }
    }
}
