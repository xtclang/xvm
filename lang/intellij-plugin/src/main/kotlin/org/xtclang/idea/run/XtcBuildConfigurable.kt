package org.xtclang.idea.run

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel

/** Settings | Build, Execution, Deployment | Ecstasy: what Build, Rebuild and Ecstasy run configurations use. */
class XtcBuildConfigurable(
    private val project: Project,
) : BoundConfigurable("Ecstasy") {
    override fun createPanel(): DialogPanel {
        val settings = XtcBuildSettings.getInstance(project)
        return panel {
            row("Build and run using:") {
                comboBox(XtcBuildMode.entries.toList())
                    .bindItem({ settings.mode }, { settings.mode = it ?: XtcBuildMode.EMBEDDED })
                    .comment(
                        "The embedded compiler, the default, builds with the language server's compiler and runs in its " +
                            "execution worker, without Gradle, also when the project has a Gradle build. Run configurations " +
                            "set to the project default follow this choice.",
                    )
            }
        }
    }
}
