import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

// Cross-platform build check utility
fun checkCrossPlatformBuild(targetArch: String): Boolean {
    val hostArch = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> "unknown"
    }
    val allowEmulation = System.getProperty("org.xtclang.docker.allowEmulation", "false").toBoolean()
    
    if (targetArch != hostArch && !allowEmulation) {
        return false
    }
    return true
}

abstract class DockerTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations
    
    @get:InputFile
    abstract val gitInfoFile: RegularFileProperty
    
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
    
    @TaskAction
    fun buildDockerImage() {
        // Architecture check at execution time
        val archCheck = architectureCheck.orNull
        if (archCheck != null && archCheck.isNotEmpty() && !checkCrossPlatformBuild(archCheck)) {
            throw GradleException("Cannot build $archCheck on this architecture")
        }
        
        val gitInfo = gitInfoFile.get().asFile
        val config = createDockerConfigFromGitInfo(gitInfo)
        val distZip = distZipUrl.orNull
        
        // Log configuration
        if (distZip != null) {
            logger.info("Using snapshot distribution: $distZip")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        
        // Compute tags at execution time based on platforms
        val computedTags = when {
            platforms.get().size == 1 && platforms.get()[0] == "linux/amd64" -> config.tagsForArch("amd64")
            platforms.get().size == 1 && platforms.get()[0] == "linux/arm64" -> config.tagsForArch("arm64") 
            else -> config.multiPlatformTags()
        }
        
        val platformArg = platforms.get().joinToString(",")
        val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
                  listOf("--progress=${System.getenv("DOCKER_BUILDX_PROGRESS") ?: "plain"}") +
                  config.buildArgs(distZip, jdkVersion.get()).flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  config.cacheArgs(if (platforms.get().size == 1) platforms.get()[0].substringAfter("/") else null) +
                  computedTags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
                  listOf("--${action.get()}", ".")
        
        logger.info("Docker: ${cmd.joinToString(" ")}")
        
        execOperations.exec {
            commandLine(cmd)
        }
    }
    
    private fun createDockerConfigFromGitInfo(gitInfoFile: File): DockerConfig {
        val props = java.util.Properties()
        gitInfoFile.inputStream().use { props.load(it) }
        
        return DockerConfig(
            props.getProperty("version"),
            props.getProperty("git.branch"),
            props.getProperty("git.commit")
        )
    }
}

// Docker configuration data class
data class DockerConfig(
    val version: String,
    val branch: String,
    val commit: String,
    val isCI: Boolean = System.getenv("CI") == "true"
) {
    val baseImage = "ghcr.io/xtclang/xvm"
    val isMaster = branch == "master"
    val tagPrefix = if (isMaster) "latest" else branch.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val versionTags = if (isMaster) listOf(version) else emptyList()
    
    fun tagsForArch(arch: String) = (listOf("${tagPrefix}-$arch") + versionTags.map { "${it}-$arch" } + listOf("${commit}-$arch"))
    fun multiPlatformTags() = listOf(tagPrefix) + versionTags + listOf(commit)
    
    fun buildArgs(distZipUrl: String? = null, jdkVersion: Int) = mapOf(
        "GH_BRANCH" to branch,
        "GH_COMMIT" to commit,
        "JAVA_VERSION" to jdkVersion.toString()
    ).let { baseArgs ->
        if (distZipUrl != null) baseArgs + ("DIST_ZIP_URL" to distZipUrl) else baseArgs
    }
    
    fun metadataLabels() = mapOf(
        "org.opencontainers.image.created" to Instant.now().toString(),
        "org.opencontainers.image.revision" to commit,
        "org.opencontainers.image.version" to version,
        "org.opencontainers.image.source" to "https://github.com/xtclang/xvm/tree/$branch"
    )
    
    fun cacheArgs(arch: String? = null): List<String> {
        return if (isCI) {
            val scope = arch?.let { ",scope=$it" } ?: ""
            listOf("--cache-from", "type=gha$scope", "--cache-to", "type=gha,mode=max$scope")
        } else {
            val cacheDir = File(System.getProperty("user.home"), ".cache/docker-buildx${arch?.let { "-$it" } ?: ""}")
            cacheDir.mkdirs()
            listOf("--cache-from", "type=local,src=${cacheDir.absolutePath}", "--cache-to", "type=local,dest=${cacheDir.absolutePath},mode=max")
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
            } else {
                throw GradleException("Failed to fetch package versions: $output", e)
            }
        }
    }
}