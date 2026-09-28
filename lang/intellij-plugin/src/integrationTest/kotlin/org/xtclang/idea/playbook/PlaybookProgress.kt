package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

@Remote("org.xtclang.idea.playbook.probe.PlaybookProgress", plugin = "org.xtclang.playbook.probe")
internal interface PlaybookProgress {
    fun install(project: Project)

    fun update(
        project: Project,
        text: String,
    )

    fun focus(
        project: Project,
        restoring: Boolean,
    )

    fun text(project: Project): String
}
