package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class LanguageServiceApplicationConfigurable : LanguageServiceConfigurable(null)

class LanguageServiceProjectConfigurable(project: Project) : LanguageServiceConfigurable(project)

/** Only the dialog draft is mutable; applying publishes a validated immutable value. */
open class LanguageServiceConfigurable(private val project: Project?) : Configurable {
    private val inherit =
        JBCheckBox("Use application language-service defaults").apply {
            name = "xtc.service.inherit"
        }
    private val synchronization =
        JComboBox(arrayOf("full", "incremental")).apply { name = "xtc.service.sync" }
    private val saving = JComboBox(arrayOf("editor", "server")).apply { name = "xtc.service.save" }
    private val hints = JBCheckBox("Show Ecstasy inlay hints").apply { name = "xtc.service.hints" }
    private val report =
        JTextArea(14, 70).apply {
            isEditable = false
            name = "xtc.service.effective"
        }
    private val reportRevision = AtomicLong()
    private var original: JsonObject? = null
    private var initial = LanguageServiceConfiguration()

    override fun getDisplayName(): String =
        if (project == null) "Ecstasy Language Service Defaults" else "Ecstasy Language Service"

    override fun createComponent(): JComponent {
        inherit.addItemListener { updateEnabled() }
        reset()
        refreshReport()
        return JPanel(BorderLayout(0, 12)).apply {
            add(
                JPanel(GridLayout(0, 2, 8, 8)).apply {
                    if (project != null) {
                        add(inherit)
                        add(JBLabel("Project overrides are stored with LSP4IJ."))
                    }
                    add(JBLabel("Text synchronization (restart required)"))
                    add(synchronization)
                    add(JBLabel("Save formatting owner (restart required)"))
                    add(saving)
                    add(hints)
                    add(JBLabel("Native IDE inlay controls still apply."))
                },
                BorderLayout.NORTH,
            )
            add(
                JBLabel(
                    "<html>Full is the default. Incremental sends changed text; it does not enable incremental compilation.<br>" +
                        "Server save edits are unavailable in LSP4IJ. Use Tools → Actions on Save → Reformat code.<br>" +
                        "Indentation is configured under Editor → Code Style → Ecstasy. Line wrapping is not implemented.<br>" +
                        "Compiler paths remain under Ecstasy Compiler. Trace and runtime controls remain in Language Servers.</html>"
                ),
                BorderLayout.CENTER,
            )
            add(
                JPanel(BorderLayout()).apply {
                    add(JBScrollPane(report), BorderLayout.CENTER)
                    add(
                        JButton("Refresh effective configuration").apply {
                            addActionListener { refreshReport() }
                        },
                        BorderLayout.SOUTH,
                    )
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun refreshReport() {
        val revision = reportRevision.incrementAndGet()
        val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        val prefix =
            "Configured preferences (" +
                (if (project == null) "application"
                else if (original == null) "inherited application defaults"
                else "project overrides with application defaults") +
                "):\n" +
                gson.toJson(LanguageServiceSettings.effective(project)) +
                "\n\n"
        report.text = prefix + "Open an Ecstasy source file to start its language service."
        val owner = project ?: return
        val wrapper =
            LanguageServiceAccessor.getInstance(owner).startedServers.firstOrNull {
                it.serverDefinition.id == CompilerSettings.SERVER_ID
            } ?: return
        wrapper.initializedServer
            .thenCompose { (it as XtcLanguageServer).languageServiceStatus() }
            .whenComplete { status, failure ->
                ApplicationManager.getApplication().invokeLater {
                    if (!owner.isDisposed && reportRevision.get() == revision) {
                        report.text =
                            prefix +
                                if (failure == null)
                                    "Effective running service:\n" + gson.toJson(status)
                                else
                                    "Service status unavailable: ${failure.message}. See the Language Servers log."
                    }
                }
            }
    }

    override fun disposeUIResources() {
        reportRevision.incrementAndGet()
    }

    private fun updateEnabled() {
        val editable = project == null || !inherit.isSelected
        synchronization.isEnabled = editable
        // TODO LSP4IJ: enable server save formatting when native willSaveWaitUntil is implemented.
        saving.isEnabled = false
        saving.toolTipText =
            "LSP4IJ does not implement server save edits. Use native Actions on Save."
        hints.isEnabled = editable
    }

    private fun draft() =
        LanguageServiceConfiguration(
            synchronization.selectedItem as String,
            saving.selectedItem as String,
            hints.isSelected,
        )

    override fun isModified(): Boolean =
        (project != null && inherit.isSelected != (original == null)) ||
            ((project == null || !inherit.isSelected) && draft() != initial)

    override fun reset() {
        original = LanguageServiceConfiguration.section(LanguageServiceSettings.content(project))
        initial = LanguageServiceSettings.effective(project)
        inherit.isSelected = original == null
        synchronization.selectedItem = initial.textSynchronization
        saving.selectedItem = initial.saveFormatting
        hints.isSelected = initial.inlayHints
        updateEnabled()
    }

    override fun apply() {
        try {
            val replacement = draft().takeUnless { project != null && inherit.isSelected }
            val content =
                LanguageServiceConfiguration.replace(
                    LanguageServiceSettings.content(project),
                    original,
                    replacement,
                )
            LanguageServiceSettings.install(project, content)
            reset()
            refreshReport()
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(
                failure.message ?: "Invalid Ecstasy language-service settings"
            )
        }
    }
}
