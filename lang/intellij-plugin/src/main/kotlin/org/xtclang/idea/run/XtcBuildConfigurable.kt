package org.xtclang.idea.run

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel

/**
 * Settings | Languages & Frameworks | Ecstasy | Build and Run: what Build, Rebuild and Ecstasy run configurations use. The
 * embedded compiler is shown unavailable while the project's own modules replace bundled libraries.
 */
class XtcBuildConfigurable(
    private val project: Project,
) : BoundConfigurable("Build and Run") {
    override fun createPanel(): DialogPanel {
        val settings = XtcBuildSettings.getInstance(project)
        val replaced = settings.replacedLibraries
        return panel {
            buttonsGroup("Build and run using:") {
                XtcBuildMode.entries.forEach { mode ->
                    row {
                        val choice = radioButton(mode.label, mode)
                        if (!XtcBuildSettings.available(mode, replaced)) {
                            choice.enabled(false).comment(XtcBuildSettings.unavailable(replaced))
                        }
                    }
                }
            }.bind({ settings.mode }, { settings.mode = it })
            row {
                comment(
                    "The embedded compiler, the default, builds with the language server's compiler and runs in its " +
                        "execution worker, without Gradle, also when the project has a Gradle build. Run configurations " +
                        "set to the project default follow this choice.",
                )
            }
        }
    }
}
