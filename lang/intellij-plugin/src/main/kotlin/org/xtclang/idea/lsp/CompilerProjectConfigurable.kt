package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings.LanguageServerDefinitionSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/** Project-local source roots, backed by the same LSP4IJ settings used by rename and Undo. */
class CompilerProjectConfigurable(private val project: Project) : Configurable {
    private val discovery = JBCheckBox("Discover source modules automatically")
    private val rows =
        DefaultTableModel(
            arrayOf(
                "Module",
                "Root URI or relative path",
                "Dependencies (comma separated)",
                "Resource roots (JSON array; blank = automatic)",
            ),
            0,
        )
    private val table = JBTable(rows)
    // Settings dialogs have an explicit reset/apply lifecycle. This snapshot prevents a stale
    // dialog from overwriting a graph changed by rename or another settings editor.
    private var original: List<SourceModuleConfiguration>? = null

    override fun getDisplayName(): String = "Ecstasy Compiler"

    override fun createComponent(): JComponent {
        val add =
            JButton("Add module").apply {
                addActionListener { rows.addRow(arrayOf("", "", "", "")) }
            }
        val remove =
            JButton("Remove module").apply {
                addActionListener { table.selectedRows.sortedDescending().forEach(rows::removeRow) }
            }
        fun updateEnabled() {
            table.isEnabled = !discovery.isSelected
            add.isEnabled = !discovery.isSelected
            remove.isEnabled = !discovery.isSelected
        }
        discovery.addItemListener { updateEnabled() }
        reset()
        updateEnabled()
        return JPanel(BorderLayout(0, 8)).apply {
            add(
                JPanel(BorderLayout()).apply {
                    add(discovery, BorderLayout.NORTH)
                    add(
                        JBLabel(
                            "Roots are relative to this project. Apply updates the running server."
                        ),
                        BorderLayout.SOUTH,
                    )
                },
                BorderLayout.NORTH,
            )
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                    add(add)
                    add(remove)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun modules(): List<SourceModuleConfiguration>? =
        if (discovery.isSelected) null
        else
            (0 until rows.rowCount).map { row ->
                fun cell(column: Int) = rows.getValueAt(row, column)?.toString().orEmpty().trim()
                SourceModuleConfiguration(
                    cell(0),
                    cell(1),
                    cell(2).split(',').map(String::trim).filter(String::isNotEmpty),
                    cell(3).takeIf(String::isNotBlank)?.let { value ->
                        val paths = runCatching {
                            JsonParser.parseString(value)
                        }
                            .getOrElse {
                                throw IllegalArgumentException(
                                    "Resource roots must be a JSON array of paths",
                                    it,
                                )
                            }
                        require(paths.isJsonArray) {
                            "Resource roots must be a JSON array of paths"
                        }
                        paths.asJsonArray.map { path ->
                            require(path.isJsonPrimitive && path.asJsonPrimitive.isString) {
                                "Resource roots must contain only paths"
                            }
                            path.asString
                        }
                    },
                )
            }

    override fun isModified(): Boolean =
        table.isEditing || runCatching { modules() != original }.getOrDefault(true)

    override fun reset() {
        table.cellEditor?.cancelCellEditing()
        original = SourceGraphConfiguration.read(CompilerSettings.content(project))
        discovery.isSelected = original == null
        table.isEnabled = !discovery.isSelected
        rows.rowCount = 0
        original.orEmpty().forEach {
            rows.addRow(
                arrayOf(
                    it.name,
                    it.uri,
                    it.dependencies.joinToString(", "),
                    it.resourceRoots?.let { paths -> Gson().toJson(paths) }.orEmpty(),
                )
            )
        }
    }

    override fun apply() {
        table.cellEditor?.stopCellEditing()
        try {
            val content = CompilerSettings.content(project)
            require(SourceGraphConfiguration.read(content) == original) {
                "Compiler source graph changed while this dialog was open. Reset before applying."
            }
            val next = modules()
            val replacement =
                SourceGraphConfiguration.configure(
                    content,
                    next,
                    Path.of(requireNotNull(project.basePath)).toUri(),
                )
            val settings =
                CompilerSettings.store(project)
                    .getLanguageServerSettings(CompilerSettings.SERVER_ID)
            val copy =
                settings?.let(XmlSerializerUtil::createCopy) ?: LanguageServerDefinitionSettings()
            copy.configurationContent = replacement
            ProjectLanguageServerSettings.getInstance(project)
                .updateSettings(CompilerSettings.SERVER_ID, copy)
            original = next
        } catch (failure: IllegalArgumentException) {
            throw ConfigurationException(failure.message ?: "Invalid compiler source graph")
        }
    }
}
