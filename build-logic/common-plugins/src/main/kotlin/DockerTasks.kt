import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File

@CacheableTask
abstract class DockerTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val platforms: ListProperty<String>
    
    @get:Input
    abstract val action: Property<String>
    
    @get:Input
    @get:Optional
    abstract val distZipUrl: Property<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xdkDistributionFiles: ConfigurableFileCollection

    @get:Input
    abstract val jdkVersion: Property<Int>
    
    @get:Input
    @get:Optional
    abstract val architectureCheck: Property<String>
    
    @get:Input
    abstract val tags: ListProperty<String>

    @get:Input
    abstract val gitCommit: Property<String>

    @get:Input
    abstract val gitBranch: Property<String>

    @get:Input
    @get:Optional
    abstract val projectVersion: Property<String>
    
    @get:Input
    abstract val hostArch: Property<String>
    
    @get:Input
    abstract val allowEmulation: Property<Boolean>
    
    @get:Input
    abstract val dockerProgress: Property<String>
    
    @get:Input
    abstract val ciMode: Property<Boolean>
    
    @get:Input
    abstract val userHome: Property<String>

    @get:Input
    abstract val dockerCommand: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dockerDir: DirectoryProperty

    @get:OutputFile
    abstract val buildMarkerFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun buildDockerImage() {
        // Architecture check at execution time
        val archCheck = architectureCheck.orNull
        if (archCheck != null && archCheck.isNotEmpty()) {
            if (archCheck != hostArch.get() && !allowEmulation.get()) {
                throw GradleException("Cannot build $archCheck on this architecture")
            }
        }

        // Docker build simplified - no git info needed anymore
        val distZip = distZipUrl.orNull

        // Ensure we have a distribution ZIP
        if (distZip.isNullOrEmpty()) {
            throw GradleException("DIST_ZIP_URL is required but not provided. Run 'xdk:distZip' first or set DIST_ZIP_URL environment variable.")
        }

        logger.lifecycle("Using XDK distribution: $distZip")

        // Verify the distribution ZIP exists
        val distZipFile = File(distZip)
        if (!distZipFile.exists()) {
            throw GradleException("Distribution ZIP not found: $distZip")
        }

        // Copy the distribution ZIP to the Docker build context with the expected name
        val contextDistZip = File(dockerDir.get().asFile, "xdk-dist.zip")
        logger.info("Copying XDK distribution ZIP to Docker build context: ${distZipFile.absolutePath} -> ${contextDistZip.absolutePath}")
        distZipFile.copyTo(contextDistZip, overwrite = true)

        try {
            // Docker command construction with proper tag computation
            val platformArg = platforms.get().joinToString(",")
            val baseImage = "ghcr.io/xtclang/xvm"

            // Git info provided via Palantir plugin (local) or CI env vars (GH_COMMIT/GH_BRANCH)
            val commit = gitCommit.get()
            val branch = gitBranch.get()
            val version = projectVersion.get()

            // Sanitize branch name for Docker tags (same as CI)
            val branchTag = branch.replace(Regex(".*/"), "")
                                  .replace(Regex("[^a-zA-Z0-9._-]"), "_")

            // Determine if this is a CI build (load vs push action indicates local vs CI)
            val isLocalBuild = action.get() == "load"

            // Tag strategy (unified with CI - see .github/actions/compute-docker-tags):
            // - CI/Local master: latest, version, commit (full hash)
            // - CI/Local branch: branch-tag, commit (full hash)
            // - Local suffix: all tags get "-local" suffix for local builds
            val baseTags = if (branch == "master") {
                listOf("latest", version, commit)
            } else {
                listOf(branchTag, commit)
            }

            val computedTags = if (isLocalBuild) {
                baseTags.map { "$it-local" }
            } else {
                baseTags
            }

            logger.lifecycle("Computing Docker tags: branch=$branch, version=$version, commit=$commit")
            logger.lifecycle("Generated tags: ${computedTags.joinToString(", ")}")

            val dockerCmd = dockerCommand.get()
            val cmd = listOf(dockerCmd, "buildx", "build", "--platform", platformArg) +
                      listOf("--progress=${dockerProgress.get()}") +
                      listOf("--build-arg", "JAVA_VERSION=${jdkVersion.get()}") +
                      listOf("--build-arg", "DIST_ZIP_URL=xdk-dist.zip") +
                      computedTags.flatMap { tag -> listOf("--tag", "$baseImage:$tag") } +
                      listOf("--label", "org.opencontainers.image.revision=$commit") +
                      listOf("--label", "org.opencontainers.image.version=$version") +
                      listOf("--label", "org.opencontainers.image.source=https://github.com/xtclang/xvm/tree/$branch") +
                      listOf("--${action.get()}", ".")

            logger.info("Executing Docker command: ${cmd.joinToString(" ")}")

            execOperations.exec {
                commandLine(cmd)
                workingDir(dockerDir.get().asFile)
            }

            // Write marker file to track successful build
            val markerFile = buildMarkerFile.get().asFile
            markerFile.parentFile.mkdirs()
            markerFile.writeText("Docker image built successfully at ${java.time.Instant.now()}\nTags: ${computedTags.joinToString(", ")}\n")
        } finally {
            // Clean up the copied distribution ZIP
            if (contextDistZip.exists()) {
                contextDistZip.delete()
                logger.info("Cleaned up copied distribution ZIP: ${contextDistZip.absolutePath}")
            }
        }
    }
}

abstract class DockerCleanupTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations
    
    @get:Input
    abstract val keepCount: Property<Int>
    
    @get:Input
    abstract val dryRun: Property<Boolean>
    
    @get:Input
    abstract val forced: Property<Boolean>
    
    @get:Input
    abstract val packageName: Property<String>
    
    @TaskAction
    fun cleanupImages() {
        logger.lifecycle("Docker cleanup: keepCount=${keepCount.get()}, dryRun=${dryRun.get()}, packageName=${packageName.get()}")
        
        if (dryRun.get()) {
            logger.lifecycle("DRY RUN: Would clean up old Docker package versions")
            return
        }
        
        val result = ByteArrayOutputStream()
        try {
            execOps.exec {
                commandLine("gh", "api", "https://api.github.com/orgs/xtclang/packages/container/${packageName.get()}/versions")
                standardOutput = result
                errorOutput = result
            }
            logger.lifecycle("Successfully fetched package versions for cleanup")
        } catch (e: Exception) {
            val output = result.toString()
            if (forced.get()) {
                logger.warn("Failed to fetch package versions: $output")
                return
            }
            throw GradleException("Failed to fetch package versions: $output", e)
        }
    }
}
