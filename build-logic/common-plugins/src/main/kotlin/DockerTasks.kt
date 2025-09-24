import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File

// Cross-platform build check utility - configuration cache compatible version  
fun checkCrossPlatformBuild(project: org.gradle.api.Project, targetArch: String): Boolean {
    val hostArch = when (project.providers.systemProperty("os.arch").get()) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> "unknown"
    }
    val allowEmulation = project.providers.systemProperty("org.xtclang.docker.allowEmulation").getOrElse("false").toBoolean()
    
    if (targetArch != hostArch && !allowEmulation) {
        return false
    }
    return true
}

abstract class DockerTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations
    
    // Git info no longer needed for Docker builds
    
    @get:Input
    abstract val platforms: ListProperty<String>
    
    @get:Input
    abstract val action: Property<String>
    
    @get:Input
    @get:Optional
    abstract val distZipUrl: Property<String>
    
    @get:Input
    abstract val jdkVersion: Property<Int>
    
    @get:Input
    @get:Optional
    abstract val architectureCheck: Property<String>
    
    @get:Input
    abstract val tags: ListProperty<String>
    
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

    @get:InputDirectory
    abstract val dockerDir: DirectoryProperty
    
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
            // Simplified Docker command construction
            val platformArg = platforms.get().joinToString(",")
            val baseImage = "ghcr.io/xtclang/xvm"
            val tag = "latest" // Simplified - just use latest tag

            val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
                      listOf("--progress=${dockerProgress.get()}") +
                      listOf("--build-arg", "JAVA_VERSION=${jdkVersion.get()}") +
                      listOf("--build-arg", "DIST_ZIP_URL=xdk-dist.zip") +
                      listOf("--tag", "$baseImage:$tag") +
                      listOf("--${action.get()}", ".")

            logger.info("Executing Docker command: ${cmd.joinToString(" ")}")

            execOperations.exec {
                commandLine(cmd)
                workingDir(dockerDir.get().asFile)
            }
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
