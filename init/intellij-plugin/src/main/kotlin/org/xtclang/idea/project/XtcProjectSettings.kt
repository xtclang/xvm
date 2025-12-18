package org.xtclang.idea.project

/**
 * Settings for XTC project creation.
 */
data class XtcProjectSettings(
    val projectName: String = "",
    val projectType: ProjectType = ProjectType.APPLICATION,
    val multiModule: Boolean = false
) {
    enum class ProjectType(val cliValue: String, val displayName: String) {
        APPLICATION("application", "Application"),
        LIBRARY("library", "Library"),
        SERVICE("service", "Service");

        override fun toString() = displayName
    }
}
