package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

@Remote(
    "org.xtclang.idea.playbook.probe.CompilerSettingsPage",
    plugin = "org.xtclang.playbook.probe",
)
interface CompilerSettingsPage {
    fun useBuildModel(project: Project): String

    fun refreshBuildModel(project: Project)

    fun installProjectGraph(project: Project)

    fun dismissExpectedConfigurationError(project: Project)

    fun resourceRootsRoundTrip(
        project: Project,
        roots: String,
    )

    fun content(project: Project): String?

    fun clearProjectGraph(project: Project)
}
