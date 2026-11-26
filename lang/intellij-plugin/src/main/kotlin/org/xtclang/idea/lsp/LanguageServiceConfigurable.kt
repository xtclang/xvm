package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.xvm.lsp.protocol.XtcServerProtocol
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextArea

/** Edits application defaults for new projects and projects that inherit language-service settings. */
class LanguageServiceApplicationConfigurable : LanguageServiceConfigurable(null)

/** Edits project language-service overrides, with an explicit option to inherit application defaults. */
class LanguageServiceProjectConfigurable(
    project: Project,
) : LanguageServiceConfigurable(project)

/** Only the dialog draft is mutable; applying publishes a validated immutable value. */
open class LanguageServiceConfigurable(
    private val project: Project?,
) : Configurable {
    private val inherit =
        JBCheckBox("Use application language-service defaults").apply {
            name = "xtc.service.inherit"
        }
    private val synchronization =
        JComboBox(arrayOf("full", "incremental")).apply { name = "xtc.service.sync" }
    private val adapter =
        JComboBox(LanguageAdapter.entries.toTypedArray()).apply {
            name = "xtc.service.adapter"
            toolTipText = "Compiler provides semantic analysis; Tree-sitter provides syntax-based features."
        }
    private val saving = JComboBox(arrayOf("editor", "server")).apply { name = "xtc.service.save" }
    private val hints = JBCheckBox("Show Ecstasy inlay hints").apply { name = "xtc.service.hints" }
    private val references = JBCheckBox("Show Ecstasy reference counts").apply { name = "xtc.service.references" }
    private val report =
        JTextArea(14, 70).apply {
            isEditable = false
            name = "xtc.service.effective"
        }
    private val reportRevision = AtomicLong()
    private var original: JsonObject? = null
    private var initial = LanguageServiceConfiguration()

    override fun getDisplayName(): String = if (project == null) "Language Service Defaults" else "Language Service"

    override fun createComponent(): JComponent {
        inherit.addItemListener { updateEnabled() }
        reset()
        return panel {
            if (project != null) {
                row { cell(inherit).comment("Project overrides are stored with LSP4IJ.", MAX_LINE_LENGTH_WORD_WRAP) }
            }
            row("Language adapter:") {
                cell(adapter)
            }.rowComment(
                "Restarts the server automatically.",
                MAX_LINE_LENGTH_WORD_WRAP,
            )
            row("Text synchronization:") {
                cell(synchronization)
            }.rowComment(
                "Restarts automatically. Full is the default. Incremental sends changed text; it does not enable " +
                    "incremental compilation.",
                MAX_LINE_LENGTH_WORD_WRAP,
            )
            row("Save formatting owner:") {
                cell(saving)
            }.rowComment(
                "Restarts automatically. The server formats on save while Tools → Actions on Save doesn't reformat " +
                    "Ecstasy files.",
                MAX_LINE_LENGTH_WORD_WRAP,
            )
            row { cell(hints).comment("Native IDE inlay controls still apply.", MAX_LINE_LENGTH_WORD_WRAP) }
            row { cell(references).comment("Applies immediately; Run lenses remain available.", MAX_LINE_LENGTH_WORD_WRAP) }
            row {
                comment(
                    "Indentation and the compiler wrapping margin are configured under Editor → Code Style → Ecstasy. " +
                        "Compiler paths are on Ecstasy › Compiler. Trace controls remain in Language Servers; JVM and log " +
                        "limits are on Ecstasy › Server Runtime and Logs.",
                    MAX_LINE_LENGTH_WORD_WRAP,
                )
            }
            row { cell(JBScrollPane(report)).align(Align.FILL) }.resizableRow()
            row { button("Refresh effective configuration") { refreshReport() } }
        }
    }

    private fun refreshReport() {
        val revision = reportRevision.incrementAndGet()
        val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
        val savedPreferences = LanguageServiceSettings.effective(project)
        val savedRuntime = ServerRuntimeSettings.getInstance().state
        val prefix =
            "Configured preferences (" +
                (
                    if (project == null) {
                        "application"
                    } else if (original == null) {
                        "inherited application defaults"
                    } else {
                        "project overrides with application defaults"
                    }
                ) +
                "):\n" +
                gson.toJson(savedPreferences) +
                "\n\nSaved machine runtime (applies after explicit restart):\n" +
                gson.toJson(savedRuntime) + "\n\n"
        report.text = prefix + "Open an Ecstasy source file to start its language service."
        val owner = project ?: return
        val wrapper =
            LanguageServiceAccessor.getInstance(owner).startedServers.firstOrNull {
                it.serverDefinition.id == CompilerSettings.SERVER_ID
            } ?: return
        val connection = wrapper.languageServer
        wrapper.initializedServer
            .thenCompose { (it as XtcServerProtocol).languageServiceStatus() }
            .whenComplete { status, failure ->
                ApplicationManager.getApplication().invokeLater {
                    if (!owner.isDisposed && reportRevision.get() == revision &&
                        LanguageServiceSettings.effective(owner) == savedPreferences &&
                        ServerRuntimeSettings.getInstance().state == savedRuntime &&
                        wrapper in LanguageServiceAccessor.getInstance(owner).startedServers &&
                        wrapper.languageServer === connection
                    ) {
                        report.text =
                            prefix +
                            if (failure == null) {
                                "Effective running service:\n" + gson.toJson(status)
                            } else {
                                "Service status unavailable: ${failure.message}. See the Language Servers log."
                            }
                    }
                }
            }
    }

    override fun disposeUIResources() {
        reportRevision.incrementAndGet()
    }

    private fun updateEnabled() {
        val editable = project == null || !inherit.isSelected
        adapter.isEnabled = editable
        synchronization.isEnabled = editable
        saving.isEnabled = editable
        hints.isEnabled = editable
        references.isEnabled = editable
    }

    private fun draft() =
        LanguageServiceConfiguration(
            synchronization.selectedItem as String,
            saving.selectedItem as String,
            hints.isSelected,
            references.isSelected,
            (adapter.selectedItem as LanguageAdapter).setting,
        )

    override fun isModified(): Boolean =
        (project != null && inherit.isSelected != (original == null)) ||
            ((project == null || !inherit.isSelected) && draft() != initial)

    override fun reset() {
        original = LanguageServiceConfiguration.section(LanguageServiceSettings.content(project))
        initial = LanguageServiceSettings.effective(project)
        adapter.selectedItem = LanguageAdapter.fromSetting(initial.adapter)
        inherit.isSelected = original == null
        synchronization.selectedItem = initial.textSynchronization
        saving.selectedItem = initial.saveFormatting
        hints.isSelected = initial.inlayHints
        references.isSelected = initial.referenceCodeLens
        updateEnabled()
        refreshReport()
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
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(
                failure.message ?: "Invalid Ecstasy language-service settings",
                failure,
                ConfigurationException.getDefaultTitle(),
            )
        }
    }
}
