package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

@Remote("org.xtclang.idea.playbook.probe.LanguageServicePage", plugin = "org.xtclang.playbook.probe")
interface LanguageServicePage {
    fun exercise(project: Project)
    fun content(project: Project): String?
    fun restore(project: Project, content: String?)
    fun transport(project: Project, value: String)
    fun saveFormatting(project: Project, enabled: Boolean): Boolean
    fun indent(project: Project, value: Int): Int
}
