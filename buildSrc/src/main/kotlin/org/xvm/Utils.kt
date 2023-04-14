package org.xvm

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Use this task to delete all files not under source control in the repository. Gradle clean should
 * usually do the trick, but be aware that it doesn't necessarily delete everything. It just tries
 * to clean up dependencies for a cached rebuild, so that configuration is rebuilt from scratch.
 *
 * NOTE: This is _not_ the same thing as running "git clean". Intuitively, developers tend to think
 * about "./gradlew clean" as a "make clean" standard POSIX build task, that usually maps to removing
 * all generated files. In order to get the same functionality, which typically should never be
 * needed in best practice Gradle projects, use the "gitClean" task below.
 *
 * The default behavior of this task is that is does a dry run only, logging which files it wants to
 * delete, but does not actually remove anything. To override this, the property "gitCleanDryRun"
 * must be set to "false", e.g. "./gradlew gitClean -PgitCleanDryRun=false"
 */
abstract class CheckStuff : DefaultTask() {
    @get:Input
    @set:Option(option = "dryRun", description = "Should dry run mode be enabled (default: true)?")
    var dryRun: Boolean = true

    @get:Input
    @set:Option(option = "cleanIdeState", description = "Should IDE configuration be deleted as well (default: false)?")
    var cleanIdeState: Boolean = false

    init {
        group = "other"
        description = "Runs git clean, recursively from the repo root. Default is dry run."
    }

    private fun deleteIdeState() {
        // get IDE extensions for files, kill them off it gitClean did not catch them
        // get IDE config dir(s) and delete them
        project.fileTree(project.projectDir).matching {
            // TODO add file patterns for other known IDEs.
            include(
                ".idea",
                "**/*.iml",
                "**/*.ipr",
                "**/*.iws")
        }.visit {
            println("FileVisitDetails: $this")
        }
    }

    @TaskAction
    fun gitClean() {
        project.exec {
            logger.lifecycle("Running $name task (dry run: $dryRun, clean_ide_state: $cleanIdeState)...");
            if (dryRun) {
                logger.warn("WARNING: gitClean is in dry run mode. To explicitly run gitClean, set the dryRun option to 'false'.")
            }

            var argList = listOf("git", "clean", if (dryRun) "-nfxd" else "-fxd")
            if (cleanIdeState) {
                deleteIdeState()
            } else {
                argList += listOfNotNull("-e", ".idea")
            }
            commandLine(argList)
        }
    }
}
