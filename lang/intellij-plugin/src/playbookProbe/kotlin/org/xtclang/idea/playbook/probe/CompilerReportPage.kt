package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.xtclang.idea.lsp.CompilerProjectConfigurable
import org.xtclang.idea.lsp.SourceModuleConfiguration
import java.awt.Component
import java.awt.Container
import java.util.concurrent.CompletableFuture
import javax.swing.JList
import javax.swing.JTextArea

/**
 * Real settings UI with a controlled asynchronous data source; all state is confined to the EDT.
 */
class CompilerReportPage private constructor(
    private val project: Project,
) {
    private val requests = mutableListOf<CompletableFuture<List<SourceModuleConfiguration>>>()
    private val page =
        CompilerProjectConfigurable(project) {
            CompletableFuture<List<SourceModuleConfiguration>>().also(requests::add)
        }
    private val component = page.createComponent()

    init {
        descendants(component)
            .filterIsInstance<JList<*>>()
            .single { it.name == "xtc.compiler.navigation" }
            .setSelectedValue("Build import", true)
    }

    fun reset() = page.reset()

    fun text(): String =
        descendants(component)
            .filterIsInstance<JTextArea>()
            .single { it.name == "xtc.compiler.details" }
            .text

    fun complete(
        index: Int,
        name: String,
    ): CompletableFuture<Void> {
        ApplicationManager.getApplication().assertIsDispatchThread()
        requests[index].complete(
            listOf(SourceModuleConfiguration(name, "file:///$name.x", emptyList())),
        )
        // Run after the page's invokeLater publication, so a stale-reply assertion cannot pass
        // merely because the callback has not run yet.
        return CompletableFuture<Void>().also { barrier ->
            ApplicationManager.getApplication().invokeLater { barrier.complete(null) }
        }
    }

    fun dispose() = page.disposeUIResources()

    fun completeAndRestart(
        index: Int,
        name: String,
    ): CompletableFuture<Void> {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val barrier = complete(index, name)
        // Retire the actual connection before the queued publication can run on the EDT.
        LanguageServiceAccessor
            .getInstance(project)
            .startedServers
            .single()
            .restart()
        return barrier
    }

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
        }

    companion object {
        @JvmStatic
        fun open(project: Project): CompilerReportPage {
            ApplicationManager.getApplication().assertIsDispatchThread()
            return CompilerReportPage(project)
        }
    }
}
