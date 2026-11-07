package org.xtclang.idea.lsp

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SpinnerNumberModel

/** Editing a draft never restarts an active compiler. Restart is an explicit separate action. */
class ServerRuntimeConfigurable : Configurable {
    private val options = JTextArea(8, 65).apply { name = "xtc.runtime.vmOptions" }
    private val history = JSpinner(SpinnerNumberModel(7, 1, 90, 1)).apply { name = "xtc.logs.historyDays" }
    private val fileSize = JSpinner(SpinnerNumberModel(10, 1, 100, 1)).apply { name = "xtc.logs.maxFileMb" }
    private val totalSize = JSpinner(SpinnerNumberModel(50, 1, 1000, 1)).apply { name = "xtc.logs.totalSizeMb" }
    private val sessions = JSpinner(SpinnerNumberModel(5, 1, 20, 1)).apply { name = "xtc.logs.retainedSessions" }
    private var original = ServerRuntimeSettings.Options()

    override fun getDisplayName(): String = "Ecstasy Server Runtime and Logs"

    override fun createComponent(): JComponent =
        JPanel(BorderLayout(0, 8)).apply {
            add(
                JBLabel(
                    "<html>Machine-local settings; uses IntelliJ's bundled Java runtime. Apply saves for the next server start.<br>One JVM option per line: -Xms/-Xmx/-Xss sizes, -XX:MaxMetaspaceSize, -XX:ReservedCodeCacheSize,<br>-XX:ActiveProcessorCount, or -XX:+UseG1GC/UseSerialGC/UseParallelGC/UseZGC. Sizes use K, M or G.<br>Example: -Xmx2G. No shell quoting, agents, classpath or system-property overrides.</html>",
                ),
                BorderLayout.NORTH,
            )
            add(
                JPanel(BorderLayout(0, 8)).apply {
                    add(JBScrollPane(options), BorderLayout.CENTER)
                    add(
                        JPanel(GridLayout(0, 2, 8, 8)).apply {
                            add(JBLabel("Log history (days)"))
                            add(history)
                            add(JBLabel("Each log file (MB)"))
                            add(fileSize)
                            add(JBLabel("Rolled archive cap per stream (MB)"))
                            add(totalSize)
                            add(JBLabel("Retired process sessions to keep"))
                            add(sessions)
                            add(JBLabel("Active processes are protected. Old sessions are pruned on server start."))
                            add(JBLabel("Tools → Export Ecstasy Server Logs saves bounded recent logs and status."))
                        },
                        BorderLayout.SOUTH,
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                JButton("Restart running Ecstasy servers with saved settings").apply {
                    name = "xtc.runtime.restart"
                    addActionListener {
                        ServerRuntimeSettings.getInstance().launchArguments()
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

    private fun draft() =
        original.copy(
            vmOptions = options.text,
            logHistoryDays = history.value as Int,
            logMaxFileMb = fileSize.value as Int,
            logTotalSizeMb = totalSize.value as Int,
            logRetainedSessions = sessions.value as Int,
        )

    override fun isModified(): Boolean = draft() != original

    override fun reset() {
        original = ServerRuntimeSettings.getInstance().state
        options.text = original.vmOptions
        history.value = original.logHistoryDays
        fileSize.value = original.logMaxFileMb
        totalSize.value = original.logTotalSizeMb
        sessions.value = original.logRetainedSessions
    }

    override fun apply() {
        try {
            ServerRuntimeSettings.getInstance().install(original, draft())
            reset()
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(failure.message ?: "Invalid Ecstasy JVM options")
        }
    }
}
