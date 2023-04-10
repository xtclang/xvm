package org.xvm

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskAction

fun Project.getBooleanProperty(name: String, defaultValue: Boolean = false): Boolean {
    return if (project.hasProperty(name)) project.property(name).toString().toBoolean() else defaultValue
}
open class GitCleanTask : Exec() {
    @TaskAction
    fun gitClean() {

    }
}