package org.xvm

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.ByteArrayOutputStream

/**
 * Superclass for build state cleanups. This can be used for full rebuilds, to remove all stuff
 * that's not under git source control (but excluding the .idea configuration and projects), or
 * to specifically remove all IDE config as well, e.g. to do a full clean rebuild. This can be good
 * if the IDE configurations created when importing the project do something that is not intentional,
 * and fix and rebuild with a new configuration.
 *
 * TODO: We currently only support IntelliJ file patterns for IDE configs.
 */
abstract class XvmClean : DefaultTask() {
    @Input
    @set:Option(
            option = "delete",
            description = "Pass this flag to really delete all known IDE state")
    var delete: Boolean = false // Not a dry run if true. Default is false.

    init {
        group = "other"
    }

    fun start(): String {
        logger.lifecycle("[$name] Running task from ${project.projectDir}...")
        if (delete) {
            logger.lifecycle("[$name]    NOTE: This is NOT a dry run. Files will be deleted.")
        } else {
            logger.lifecycle("[$name]    NOTE: This is a dry run. To actually delete files, use the --delete option.")
        }
        return name
    }

    fun end() {
        logger.lifecycle("[$name] Finished.")
    }
}

open class CleanIdeTask : XvmClean() {
    init {
        description = "Task that cleans all IDE state and config from the current project directory."
    }

    @TaskAction
    fun cleanIdeConfig() {
        val taskName = start()
        val ideFiles = project.fileTree(project.projectDir).matching {
            include(
                    ".idea",
                    "**.iml",
                    "**.ipr",
                    "**.iws")
        }

        ideFiles.visit {
            if (delete) {
                println("[$taskName]    Deleting '$path'")
                // TODO: Check that we get the correct match now before enabling delete
                //project.delete(path)
            } else {
                println("[$taskName]    Would delete '$path' ...")
            }
        }

        end()
    }
}

open class CleanGitTask : XvmClean() {
    init {
        description = "Task that cleans all files not under source control from the current project directory (except for contents if .gitignore)."
    }

    @TaskAction
    fun cleanGit() {
        val taskName = start()
        val out = ByteArrayOutputStream()
        project.exec {
            workingDir = project.projectDir
            standardOutput = out
            commandLine(listOf(
                    "git", "clean",
                    if (delete) "-fxd" else "-nfxd",
                    "-e", ".idea",
                    "-e", "*.iml",
                    "-e", "*.ipr",
                    "-e", "*.iws"))
        }
        out.toString().lines().forEach { line ->
            println("[$taskName]    $line")
        }
        end()
    }
}

/**
 * Helpers to access and parse boolean properties in a project
 */
fun Project.getBooleanProperty(name: String, default: Boolean = false): Boolean {
    return if (project.hasProperty(name)) project.property(name).toString().toBoolean() else default
}

fun Project.setBooleanProperty(name: String, default: Boolean = true): Boolean {
    project.setProperty(name, default)
    assert(project.hasProperty(name))
    return project.getBooleanProperty(name)
}
