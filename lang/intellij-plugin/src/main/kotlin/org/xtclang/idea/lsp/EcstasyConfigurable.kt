package org.xtclang.idea.lsp

import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import java.awt.event.ActionEvent

/**
 * Settings | Languages & Frameworks | Ecstasy: the node that holds every settings page of the plugin, the project's
 * and the application's, and links each of them. A pointer to its Build and Run page also stands under Build,
 * Execution, Deployment, beside the IDE's other build tools.
 */
class EcstasyConfigurable : BoundConfigurable("Ecstasy") {
    override fun createPanel(): DialogPanel =
        panel {
            row { text("The settings of the Ecstasy language server, its compiler and its builds and runs:") }
            PAGES.forEach { page -> row { link(page.name) { open(it, page.id) }.comment(page.about) } }
        }

    /** A page under this node: its configurable id, its name and what it sets. */
    internal data class Page(
        val id: String,
        val name: String,
        val about: String,
    )

    companion object {
        internal val PAGES =
            listOf(
                Page(
                    "xtc.service",
                    "Language Service",
                    "This project's language adapter, text synchronization, formatting on save, inlay hints and reference counts.",
                ),
                Page("xtc.service.defaults", "Language Service Defaults", "The same, for projects that use the application's defaults."),
                Page("xtc.compiler", "Compiler", "This project's source modules and their dependencies, or its imported Gradle model."),
                Page(
                    "xtc.runtime",
                    "Server Runtime and Logs",
                    "The server's memory policy, JVM options, libraries and logs, on this machine.",
                ),
                Page("xtc.build", "Build and Run", "Whether Build, Rebuild and Run use the embedded compiler or Gradle."),
            )

        /** Selects the page with the configurable [id] in the Settings dialog that holds [event]'s source. */
        internal fun open(
            event: ActionEvent,
            id: String,
        ) {
            val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(event.source as Component)) ?: return
            settings.find(id)?.let(settings::select)
        }
    }
}
