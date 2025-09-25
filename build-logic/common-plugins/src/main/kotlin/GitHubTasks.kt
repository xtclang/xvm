import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

// GitHub Tasks - simplified to only include what's actually used
// Package management and publication listing tasks replaced by bin/list-publications.sh

abstract class GitTaggingTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val snapshotOnly: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    init {
        snapshotOnly.convention(false)
    }

    @TaskAction
    fun ensureTags() {
        val semanticVersionString = version.get()
        val isSnapshot = semanticVersionString.contains("SNAPSHOT")
        val isRelease = !isSnapshot
        val snapshotOnlyMode = snapshotOnly.get()

        if (snapshotOnlyMode && isRelease) {
            logger.warn("[git] ensureTags called with snapshotOnly=true. Skipping non-snapshot version: $semanticVersionString")
            return
        }

        val artifactBaseVersion = semanticVersionString.removeSuffix("-SNAPSHOT")
        val tagPrefix = if (isSnapshot) "snapshot/" else "release/"
        val localTag = "${tagPrefix}v$artifactBaseVersion"

        // Check if tag already exists
        val tagExists = try {
            execOps.exec {
                commandLine("git", "rev-parse", "--verify", "refs/tags/$localTag")
                isIgnoreExitValue = true
            }
            true
        } catch (e: Exception) {
            false
        }

        if (tagExists) {
            logger.lifecycle("[git] Tag $localTag already exists")

            if (isSnapshot) {
                // Delete existing snapshot tag
                logger.lifecycle("[git] Deleting existing snapshot tag $localTag")
                try {
                    execOps.exec {
                        commandLine("git", "tag", "-d", localTag)
                        isIgnoreExitValue = true
                    }
                } catch (e: Exception) {
                    logger.warn("[git] Failed to delete local tag: ${e.message}")
                }

                try {
                    execOps.exec {
                        commandLine("git", "push", "--delete", "origin", localTag)
                        isIgnoreExitValue = true
                    }
                } catch (e: Exception) {
                    logger.warn("[git] Failed to delete remote tag: ${e.message}")
                }
            } else {
                logger.warn("[git] Release version $semanticVersionString already tagged. No publication will be made.")
                return
            }
        }

        // Create new tag
        logger.lifecycle("[git] Creating tag $localTag")
        execOps.exec {
            commandLine("git", "tag", localTag)
        }

        // Push tag to remote
        logger.lifecycle("[git] Pushing tag $localTag to origin")
        execOps.exec {
            commandLine("git", "push", "origin", localTag)
        }

        // Write result to output file if specified
        outputFile.orNull?.asFile?.let { file ->
            file.parentFile.mkdirs()
            file.writeText("$localTag\n")
        }

        logger.lifecycle("[git] Successfully created and pushed tag: $localTag")
    }
}

// ResolveGitInfoTask and ShowGitInfoTask removed - replaced by Palantir gradle-git-version plugin