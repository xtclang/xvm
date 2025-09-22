import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Git convention plugin that provides standard git information resolution for all projects.
 *
 * This plugin creates a single standard task `resolveGitInfo` that all projects can depend on
 * to get git branch, commit, dirty status, and other git metadata in a configuration cache
 * compatible way.
 */

abstract class ResolveGitInfoTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations

    @get:Input
    abstract val branchEnv: Property<String>

    @get:Input
    abstract val commitEnv: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val ciFlag: Property<String>

    @get:InputFile
    @get:Optional
    abstract val gitHeadFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    abstract val gitDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun resolveGitInfo() {
        logger.info(">>> RESOLVING GIT INFORMATION")

        val branch = branchEnv.orNull?.takeIf { it.isNotEmpty() } ?: run {
            try {
                val output = ByteArrayOutputStream()
                execOps.exec {
                    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
                    standardOutput = output
                }
                output.toString().trim().ifBlank { "master" }
            } catch (e: Exception) {
                logger.warn("Could not get git branch: ${e.message}")
                "master"
            }
        }

        val commit = commitEnv.orNull?.takeIf { it.isNotEmpty() } ?: run {
            try {
                val output = ByteArrayOutputStream()
                execOps.exec {
                    commandLine("git", "rev-parse", "HEAD")
                    standardOutput = output
                }
                output.toString().trim()
            } catch (e: Exception) {
                logger.warn("Could not get git commit: ${e.message}")
                "unknown"
            }
        }

        // Check if git working directory is dirty
        val isDirty = try {
            execOps.exec {
                commandLine("git", "diff", "--quiet")
            }
            false // if git diff --quiet succeeds, working directory is clean
        } catch (_: Exception) {
            true // if git diff --quiet fails, working directory is dirty
        }

        // Comprehensive git info for all consumers
        val gitInfo = mapOf(
            // Core git info
            "git.branch" to branch,
            "git.commit" to commit,
            "git.commit.id" to commit, // alias for compatibility
            "git.dirty" to isDirty.toString(),
            "git.status" to if (isDirty) "detached-head" else "clean",

            // Build info
            "git.build.version" to version.get(),
            "version" to version.get(),

            // Docker-specific derived info
            "docker.baseImage" to "ghcr.io/xtclang/xvm",
            "docker.isMaster" to (branch == "master").toString(),
            "docker.tagPrefix" to if (branch == "master") "latest" else branch.replace(Regex("[^a-zA-Z0-9._-]"), "_"),
            "docker.isCI" to (ciFlag.get() == "true").toString()
        )

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(gitInfo.map { "${it.key}=${it.value}" }.joinToString("\n"))
        }

        logger.info("Git info resolved: branch=$branch, commit=${commit.take(8)}, dirty=$isDirty, auto-tracking enabled")
    }
}

// Create the standard git info resolution task that all projects can use
val resolveGitInfo by tasks.registering(ResolveGitInfoTask::class) {
    group = "build"
    description = "Resolve git information (branch, commit, dirty status) in configuration cache compatible way"

    branchEnv.set(providers.environmentVariable("GH_BRANCH").orElse(""))
    commitEnv.set(providers.environmentVariable("GH_COMMIT").orElse(""))
    version.set(provider { project.version.toString() })
    ciFlag.set(providers.environmentVariable("CI").orElse(""))
    outputFile.set(layout.buildDirectory.file("git-info.properties"))

    // Monitor git repository state for proper up-to-date checking
    val gitDir = project.rootProject.layout.projectDirectory.dir(".git")
    if (gitDir.asFile.exists()) {
        gitDirectory.set(gitDir)
        val headFile = gitDir.file("HEAD")
        if (headFile.asFile.exists()) {
            gitHeadFile.set(headFile)
        }
    }
}



// Task registration
val resolveGitHubPackages by tasks.registering(ResolveGitHubPackagesTask::class) {
    group = "github"
    description = "Resolve GitHub packages for the organization"
}

val deleteGitHubPackages by tasks.registering(DeleteGitHubPackagesTask::class) {
    group = "github"
    description = "Delete GitHub packages or specific versions"
}

val ensureGitTags by tasks.registering(GitTaggingTask::class) {
    group = "git"
    description = "Ensure current commit is tagged with the current version"
    version.set(providers.provider { project.version.toString() })
}