package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.xmlb.XmlSerializerUtil
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings.LanguageServerDefinitionSettings
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.net.URI
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.table.DefaultTableModel

/** Project-local source roots, backed by the same LSP4IJ settings used by rename and Undo. */
class CompilerProjectConfigurable(private val project: Project) : Configurable {
    private val discovery = JBCheckBox("Use Gradle model or automatic source discovery")
    private val effective = JTextArea(8, 60).apply { isEditable = false }
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
            add(
                JPanel(BorderLayout(0, 8)).apply {
                    add(JBScrollPane(table), BorderLayout.CENTER)
                    add(JBScrollPane(effective), BorderLayout.SOUTH)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                    add(add)
                    add(remove)
                    add(
                        JButton("Choose module root").apply {
                            addActionListener {
                                val row = table.selectedRow
                                if (row >= 0 && !discovery.isSelected) {
                                    FileChooser.chooseFile(
                                            FileChooserDescriptorFactory.createSingleFileDescriptor(
                                                "x"
                                            ),
                                            project,
                                            null,
                                        )
                                        ?.let {
                                            rows.setValueAt(
                                                Path.of(it.path).toUri().toString(),
                                                row,
                                                1,
                                            )
                                        }
                                }
                            }
                        }
                    )
                    add(
                        JButton("Add resource directory").apply {
                            addActionListener {
                                val row = table.selectedRow
                                if (row >= 0 && !discovery.isSelected) {
                                    FileChooser.chooseFile(
                                            FileChooserDescriptorFactory
                                                .createSingleFolderDescriptor(),
                                            project,
                                            null,
                                        )
                                        ?.let { directory ->
                                            val current = runCatching {
                                                modules()?.get(row)?.resourceRoots.orEmpty()
                                            }
                                                .getOrNull()
                                            if (current != null)
                                                rows.setValueAt(
                                                    Gson()
                                                        .toJson(
                                                            (current +
                                                                    Path.of(directory.path)
                                                                        .toUri()
                                                                        .toString())
                                                                .distinct()
                                                        ),
                                                    row,
                                                    3,
                                                )
                                        }
                                }
                            }
                        }
                    )
                    add(
                        JButton("Refresh Gradle model").apply {
                            addActionListener { refreshBuild(false) }
                        }
                    )
                    add(
                        JButton("Prepare generated resources").apply {
                            addActionListener { refreshBuild(true) }
                        }
                    )
                    add(
                        JButton("Reset to build model").apply {
                            addActionListener { discovery.isSelected = true }
                        }
                    )
                    add(
                        JButton("Open build file").apply {
                            addActionListener {
                                val selected = table.selectedRow
                                val modules = runCatching { modules() }.getOrNull().orEmpty()
                                val model = runCatching {
                                    CompilerBuildModel.read(project)
                                }
                                    .getOrNull()
                                val entries =
                                    model
                                        ?.get("sourceSets")
                                        ?.asJsonArray
                                        ?.map { it.asJsonObject }
                                        .orEmpty()
                                val root = modules.getOrNull(selected)?.uri
                                val entry =
                                    entries.firstOrNull { item ->
                                        item["sourceFiles"].asJsonArray.any { it.asString == root }
                                    } ?: entries.firstOrNull()
                                entry?.get("buildFile")?.asString?.let { uri ->
                                    LocalFileSystem.getInstance()
                                        .refreshAndFindFileByNioFile(Path.of(URI(uri)))
                                        ?.let {
                                            FileEditorManager.getInstance(project)
                                                .openFile(it, true)
                                        }
                                }
                            }
                        }
                    )
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun refreshBuild(prepare: Boolean) {
        effective.text = "Reading evaluated Gradle inputs…"
        CompilerBuildModel.refresh(project, prepare) { failure ->
            if (failure != null) effective.text = failure else refreshEffectivePaths()
        }
    }

    private fun refreshEffectivePaths() {
        val description = runCatching {
            CompilerBuildModel.describe(project)
        }
            .getOrElse { it.message.orEmpty() }
        effective.text = description
        LanguageServiceAccessor.getInstance(project)
            .startedServers
            .filter { it.serverDefinition.id == CompilerSettings.SERVER_ID }
            .forEach { wrapper ->
                wrapper.initializedServer.thenAccept { server ->
                    (server as? XtcLanguageServer)?.compilerSourceModules()?.thenAccept { modules ->
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed)
                                effective.text =
                                    description +
                                        "\n\nEffective compiler modules:\n" +
                                        modules.joinToString("\n") {
                                            "${it.name}: ${it.uri}\n  Resources: ${it.resourceRoots.orEmpty().joinToString()}\n  Dependencies: ${it.dependencies.joinToString()}"
                                        }
                        }
                    }
                }
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
        refreshEffectivePaths()
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
                    LanguageServiceSettings.content(project) ?: content,
                    next,
                    Path.of(requireNotNull(project.basePath)).toUri(),
                )
            val settings =
                ProjectLanguageServerSettings.getInstance(project)
                    .getLanguageServerSettings(CompilerSettings.SERVER_ID)
                    ?: CompilerSettings.store(project)
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
