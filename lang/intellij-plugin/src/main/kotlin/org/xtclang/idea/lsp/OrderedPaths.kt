package org.xtclang.idea.lsp

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField

/** EDT-owned settings draft. Order is visible and empty is distinct from inherited defaults. */
internal class OrderedPaths(
    project: Project,
    title: String,
    directoriesOnly: Boolean,
) : JPanel(BorderLayout(0, 4)) {
    private val pathModel = DefaultListModel<String>()
    private val list =
        JBList(pathModel).apply {
            name = title
            visibleRowCount = 5
        }
    private val input = JTextField(28).apply { name = "$title path" }
    val paths: List<String> get() = (0 until pathModel.size()).map(pathModel::get)

    init {
        name = title
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                add(input)
                add(
                    JButton("Add path").apply {
                        addActionListener {
                            if (input.text.isNotBlank()) {
                                pathModel.addElement(input.text.trim())
                                input.text =
                                    ""
                            }
                        }
                    },
                )
                add(
                    JButton("Choose…").apply {
                        addActionListener {
                            val descriptor =
                                if (directoriesOnly) {
                                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                } else {
                                    FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                                }
                            FileChooser
                                .chooseFile(
                                    descriptor,
                                    project,
                                    null,
                                )?.let { pathModel.addElement(Path.of(it.path).toUri().toString()) }
                        }
                    },
                )
                add(JButton("Remove").apply { addActionListener { list.selectedIndices.sortedDescending().forEach(pathModel::remove) } })
                listOf("Up" to -1, "Down" to 1).forEach { (label, offset) ->
                    add(
                        JButton(label).apply {
                            addActionListener {
                                val from = list.selectedIndex
                                val to = from + offset
                                if (from >= 0 &&
                                    to in 0 until pathModel.size()
                                ) {
                                    val value = pathModel.remove(from)
                                    pathModel.add(to, value)
                                    list.selectedIndex = to
                                }
                            }
                        },
                    )
                }
            },
            BorderLayout.SOUTH,
        )
    }

    fun editable(value: Boolean) {
        fun update(component: Component) {
            component.isEnabled = value
            if (component is Container) component.components.forEach(::update)
        }
        update(this)
    }

    fun reset(paths: List<String>) {
        pathModel.clear()
        paths.forEach(pathModel::addElement)
    }
}
