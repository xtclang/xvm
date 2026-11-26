package org.xtclang.idea.lsp

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
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
    private val libraries =
        JBCheckBox("Compile an XDK library as a whole when one of its sources is opened").apply {
            name = "xtc.lsp.compileLibraries"
            toolTipText =
                "Go to Definition, hover and references then work for every name in an XDK source, also in method " +
                "bodies. The compile takes about 10 seconds when a library's first source opens, and some hundred MB " +
                "while one stays open; it needs a server heap of at least 1 GB (-Xmx1G). Off: an XDK source resolves " +
                "only its own declarations, imports and type names."
        }
    private val replacement =
        JBCheckBox("Use the workspace's own modules in place of the bundled XDK libraries of their names").apply {
            name = "xtc.lsp.replaceLibraries"
            toolTipText =
                "As in the XVM repository: a workspace module named as an XDK library, such as json.xtclang.org, " +
                "replaces the bundled one, so code is checked against the workspace's version, and builds and runs " +
                "use Gradle. Off: the bundled libraries stay, and such modules are not compiled."
        }
    private val policy = ComboBox(MemoryPolicy.entries.toTypedArray()).apply { name = "xtc.runtime.memoryPolicy" }
    private val maxHeap =
        JBTextField(12).apply {
            name = "xtc.runtime.maxHeap"
            emptyText.text = "the JVM's own"
            toolTipText = "With the Default policy: the server's maximum heap, such as 4G. Empty: a quarter of this machine's memory."
        }
    private var original = ServerRuntimeSettings.Options()

    override fun getDisplayName(): String = "Server Runtime and Logs"

    override fun createComponent(): JComponent =
        panel {
            row {
                comment(
                    "Machine-local settings; uses IntelliJ's bundled Java runtime. Apply saves them for the next server start.",
                    MAX_LINE_LENGTH_WORD_WRAP,
                )
            }
            row {
                button("Restart running Ecstasy servers with saved settings") {
                    ProjectManager
                        .getInstance()
                        .openProjects
                        .filterNot { it.isDisposed }
                        .forEach(::restartEcstasyServers)
                }.applyToComponent { name = "xtc.runtime.restart" }
            }
            row {
                cell(JBScrollPane(options))
                    .align(Align.FILL)
                    .comment(
                        "One JVM option per line: -Xms/-Xmx/-Xss sizes, -XX:MaxMetaspaceSize, -XX:ReservedCodeCacheSize, " +
                            "-XX:ActiveProcessorCount, or -XX:+UseG1GC/UseSerialGC/UseParallelGC/UseZGC. Sizes use K, M or G. " +
                            "An -Xmx or a collector here overrides the memory policy below. No shell quoting, agents, " +
                            "classpath or system-property overrides.",
                        MAX_LINE_LENGTH_WORD_WRAP,
                    )
            }.resizableRow()
            row("Memory policy:") {
                cell(policy)
            }.rowComment(
                "Default: the maximum heap below, or the JVM's own, a quarter of this machine's memory. Adaptive: " +
                    "starts small, grows as the work needs and gives memory back, using at most 3/4 of this machine's.",
                MAX_LINE_LENGTH_WORD_WRAP,
            )
            row("Maximum heap, with the Default policy:") { cell(maxHeap) }
            row("Log history (days):") { cell(history) }
            row("Each log file (MB):") { cell(fileSize) }
            row("Rolled archive cap per stream (MB):") { cell(totalSize) }
            row("Retired process sessions to keep:") { cell(sessions) }
            row { cell(libraries).comment("Navigation in XDK sources, also in method bodies.", MAX_LINE_LENGTH_WORD_WRAP) }
            row {
                cell(
                    replacement,
                ).comment("For working on the XDK's own libraries, as in the XVM repository.", MAX_LINE_LENGTH_WORD_WRAP)
            }
            row {
                comment(
                    "Active processes are protected. Old sessions are pruned on server start. " +
                        "Tools → Export Ecstasy Server Logs saves bounded recent logs and status.",
                    MAX_LINE_LENGTH_WORD_WRAP,
                )
            }
        }.also { reset() }

    private fun draft() =
        original.copy(
            vmOptions = options.text,
            logHistoryDays = history.value as Int,
            logMaxFileMb = fileSize.value as Int,
            logTotalSizeMb = totalSize.value as Int,
            logRetainedSessions = sessions.value as Int,
            compileLibraries = libraries.isSelected,
            replaceLibraries = replacement.isSelected,
            memoryPolicy = policy.selectedItem as MemoryPolicy,
            maxHeap = maxHeap.text.trim(),
        )

    override fun isModified(): Boolean = draft() != original

    override fun reset() {
        original = ServerRuntimeSettings.getInstance().state
        options.text = original.vmOptions
        history.value = original.logHistoryDays
        fileSize.value = original.logMaxFileMb
        totalSize.value = original.logTotalSizeMb
        sessions.value = original.logRetainedSessions
        libraries.isSelected = original.compileLibraries
        replacement.isSelected = original.replaceLibraries
        policy.selectedItem = original.memoryPolicy
        maxHeap.text = original.maxHeap
    }

    override fun apply() {
        try {
            ServerRuntimeSettings.getInstance().install(original, draft())
            reset()
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(
                failure.message ?: "Invalid Ecstasy JVM options",
                failure,
                ConfigurationException.getDefaultTitle(),
            )
        }
    }
}
