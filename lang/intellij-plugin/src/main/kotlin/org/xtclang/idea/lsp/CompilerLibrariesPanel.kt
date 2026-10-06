package org.xtclang.idea.lsp

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

internal class CompilerLibrariesPanel(
    private val project: Project,
) : JPanel(BorderLayout(0, 8)) {
    private val inherited = JBCheckBox("Inherit binary libraries from Gradle", true)
    private val binaries = OrderedPaths(project, "Binary library paths", false)
    private val rows = DefaultTableModel(arrayOf("Binary module name", "Attached source directory (ordered)"), 0)
    private val attachments = JBTable(rows).apply { name = "Library source attachments" }
    val editing: Boolean get() = attachments.isEditing

    init {
        inherited.addItemListener { binaries.editable(!inherited.isSelected) }
        binaries.editable(false)
        add(
            JPanel(BorderLayout(0, 4)).apply {
                add(
                    JBLabel("Bundled XDK: always available, read-only. Attach sources for navigation; they are not compiler dependencies."),
                    BorderLayout.NORTH,
                )
                add(inherited, BorderLayout.SOUTH)
            },
            BorderLayout.NORTH,
        )
        add(
            JPanel(BorderLayout(0, 8)).apply {
                add(binaries, BorderLayout.NORTH)
                add(JBScrollPane(attachments), BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                add(JButton("Add attachment").apply { addActionListener { rows.addRow(arrayOf("", "")) } })
                add(
                    JButton("Choose source directory").apply {
                        addActionListener {
                            val row = attachments.selectedRow
                            if (row >= 0) {
                                FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null)?.let {
                                    rows.setValueAt(Path.of(it.path).toUri().toString(), row, 1)
                                }
                            }
                        }
                    },
                )
                add(
                    JButton(
                        "Remove attachment",
                    ).apply { addActionListener { attachments.selectedRows.sortedDescending().forEach(rows::removeRow) } },
                )
                listOf("Move attachment up" to -1, "Move attachment down" to 1).forEach { (label, offset) ->
                    add(
                        JButton(label).apply {
                            addActionListener {
                                val from = attachments.selectedRow
                                val to = from + offset
                                if (from >= 0 &&
                                    to in 0 until rows.rowCount
                                ) {
                                    rows.moveRow(from, from, to)
                                    attachments.setRowSelectionInterval(to, to)
                                }
                            }
                        },
                    )
                }
            },
            BorderLayout.SOUTH,
        )
    }

    fun options(): LibraryOptions =
        LibraryOptions(
            if (inherited.isSelected) null else binaries.paths,
            (0 until rows.rowCount)
                .map { row -> rows.getValueAt(row, 0).toString().trim() to rows.getValueAt(row, 1).toString().trim() }
                .groupBy({ it.first }, { it.second })
                .map { (module, roots) -> LibraryAttachment(module, roots) },
        )

    fun stopEditing() {
        attachments.cellEditor?.stopCellEditing()
    }

    fun reset(options: LibraryOptions) {
        attachments.cellEditor?.cancelCellEditing()
        inherited.isSelected = options.modulePath == null
        binaries.reset(options.modulePath.orEmpty())
        rows.rowCount = 0
        options.sourceAttachments.forEach { attachment -> attachment.roots.forEach { rows.addRow(arrayOf(attachment.module, it)) } }
    }
}
