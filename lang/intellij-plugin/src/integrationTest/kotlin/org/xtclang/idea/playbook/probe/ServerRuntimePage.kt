package org.xtclang.idea.playbook.probe

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import org.xtclang.idea.lsp.ExportServerLogsAction
import org.xtclang.idea.lsp.ServerRuntimeConfigurable
import java.awt.Component
import java.awt.Container
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.JButton
import javax.swing.JSpinner
import javax.swing.JTextArea

/** Exercises the installed application settings page, including validation and explicit restart. */
object ServerRuntimePage {
    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    @JvmStatic
    fun content(): String {
        val page = ServerRuntimeConfigurable()
        try {
            val controls = descendants(page.createComponent()).toList()
            return JsonObject()
                .apply {
                    addProperty("vmOptions", controls.filterIsInstance<JTextArea>().single().text)
                    controls.filterIsInstance<JSpinner>().forEach { addProperty(it.name, it.value as Int) }
                }.toString()
        } finally {
            page.disposeUIResources()
        }
    }

    @JvmStatic
    fun edit(
        json: String,
        action: String,
    ) {
        val before = content()
        val page = ServerRuntimeConfigurable()
        try {
            val controls = descendants(page.createComponent()).toList()
            val values = JsonParser.parseString(json).asJsonObject
            values["vmOptions"]?.let { controls.filterIsInstance<JTextArea>().single().text = it.asString }
            controls.filterIsInstance<JSpinner>().forEach { spinner ->
                values[spinner.name]?.let { spinner.value = it.asInt }
            }
            when (action) {
                "cancel" -> {
                    check(content() == before)
                }

                "reset" -> {
                    page.reset()
                    check(!page.isModified())
                    check(content() == before)
                }

                "invalid" -> {
                    check(runCatching { page.apply() }.exceptionOrNull() is ConfigurationException)
                    check(content() == before)
                }

                "apply" -> {
                    page.apply()
                    check(!page.isModified())
                }

                "restart" -> {
                    controls.filterIsInstance<JButton>().single { it.name == "xtc.runtime.restart" }.doClick()
                }

                else -> {
                    error("Unknown runtime page action: $action")
                }
            }
        } finally {
            page.disposeUIResources()
        }
    }

    @JvmStatic
    fun export(
        project: Project,
        destination: String,
    ) {
        // Driver calls this on a background thread. The bounded wait is for completion, not a delay.
        ExportServerLogsAction.export(project, Path.of(destination)).get(30, SECONDS)
    }
}
