/**
 * Docker build and management for XVM project.
 * Supports multi-platform builds, registry management, and cleanup.
 */

import XdkDistribution.Companion.normalizeArchitecture
import java.io.File
import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    base
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.git.properties)
}

// Docker configuration
data class DockerConfig(
    val version: String,
    val branch: String,
    val commit: String,
    val isCI: Boolean = providers.environmentVariable("CI").orElse("false").get() == "true"
) {
    val baseImage = "ghcr.io/xtclang/xvm"
    val isMaster = branch == "master"
    val tagPrefix = if (isMaster) "latest" else branch.substringAfterLast("/").replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val versionTags = if (isMaster) listOf(version) else emptyList()
    
    fun tagsForArch(arch: String) = (listOf("${tagPrefix}-$arch") + versionTags.map { "${it}-$arch" } + if (commit.isNotEmpty()) listOf("${commit}-$arch") else emptyList())
    fun multiPlatformTags() = listOf(tagPrefix) + versionTags + if (commit.isNotEmpty()) listOf(commit) else emptyList()
    fun buildArgs() = mapOf("GH_BRANCH" to branch, "GH_COMMIT" to commit)
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
            val cacheDir = File(providers.systemProperty("user.home").get(), ".cache/docker-buildx${arch?.let { "-$it" } ?: ""}")
            cacheDir.mkdirs()
            listOf("--cache-from", "type=local,src=${cacheDir.absolutePath}", "--cache-to", "type=local,dest=${cacheDir.absolutePath},mode=max")
        }
    }
}

// Get semantic version from versioning plugin
private val semanticVersion: SemanticVersion by extra
private val sharedLogger: Logger by extra

// Git properties configuration
gitProperties {
    // Let plugin handle git operations, with env var overrides
    val envBranch = providers.environmentVariable("GH_BRANCH").orNull
    val envCommit = providers.environmentVariable("GH_COMMIT").orNull
    
    if (envBranch != null) {
        customProperty("branch", envBranch)
    }
    if (envCommit != null) {
        customProperty("commit.id", envCommit)
    }
}

// Docker configuration using git properties plugin with env var fallbacks for CI
// Priority: 1) CI env vars (GH_BRANCH/GH_COMMIT), 2) git properties plugin, 3) direct git commands
val dockerBranch = providers.environmentVariable("GH_BRANCH")
    .orElse(providers.gradleProperty("git.branch"))
    .orElse(providers.exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
    }.standardOutput.asText.map { it.trim() })
    .orElse("master").get()

val dockerCommit = providers.environmentVariable("GH_COMMIT")
    .orElse(providers.gradleProperty("git.commit.id"))
    .orElse(providers.exec {
        commandLine("git", "rev-parse", "HEAD")  
    }.standardOutput.asText.map { it.trim() })
    .orElse("").get()

val dockerConfig = DockerConfig(
    version = semanticVersion.artifactVersion, // Use artifact version (e.g. "0.4.4-SNAPSHOT") 
    branch = dockerBranch,
    commit = dockerCommit
)

// Custom task type for Docker builds with proper configuration cache support
abstract class DockerBuildTask : DefaultTask() {
    @get:Input
    abstract val targetArchitecture: Property<String>
    
    @get:Input
    abstract val version: Property<String>
    
    @get:Input
    abstract val branch: Property<String>
    
    @get:Input
    abstract val commit: Property<String>
    
    @get:Input
    abstract val action: Property<String>
    
    @get:Input
    abstract val allowEmulation: Property<Boolean>
    
    @get:Internal
    abstract val workingDirectory: DirectoryProperty
    
    @TaskAction
    fun buildDockerImage() {
        val targetArch = targetArchitecture.get()
        val hostArch = normalizeArchitecture(providers.systemProperty("os.arch").get())
        
        // Check cross-platform build
        if (targetArch != hostArch && !allowEmulation.get()) {
            throw GradleException("❌ Cross-platform Docker build not allowed: Cannot build $targetArch on $hostArch without emulation. " +
                                 "Use -PdockerAllowEmulation=true to enable emulation (WARNING: This will be very slow on your machine!)")
        }
        
        // Create config and build Docker image
        val config = DockerConfig(version.get(), branch.get(), commit.get())
        val platforms = listOf("linux/$targetArch")
        val tags = config.tagsForArch(targetArch)
        
        val platformArg = platforms.joinToString(",")
        val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
                  listOf("--progress=${providers.environmentVariable("DOCKER_BUILDX_PROGRESS").orElse("plain").get()}") +
                  config.buildArgs().flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  config.cacheArgs(targetArch) +
                  tags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
                  listOf("--${action.get()}", ".")
        
        spawn(cmd.first(), *cmd.drop(1).toTypedArray(), workingDir = workingDirectory.get().asFile, throwOnError = true)
    }
}

// Custom task type for Docker manifest creation with proper inputs/outputs
abstract class DockerManifestTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>
    
    @get:Input
    abstract val branch: Property<String>
    
    @get:Input
    abstract val commit: Property<String>
    
    @get:Input
    abstract val baseImage: Property<String>
    
    @get:Input 
    abstract val architectures: ListProperty<String>
    
    @get:Internal
    abstract val workingDirectory: DirectoryProperty
    
    @get:OutputFile
    abstract val manifestMarker: RegularFileProperty
    
    @TaskAction
    fun createManifest() {
        val config = DockerConfig(version.get(), branch.get(), commit.get())
        val multiTags = config.multiPlatformTags()
        
        multiTags.forEach { tag: String ->
            val manifestTag = "${baseImage.get()}:$tag"
            logger.info("Creating manifest for: $manifestTag")
            
            val archTags = architectures.get().map { "${baseImage.get()}:$tag-$it" }
            val createCmd = listOf("docker", "manifest", "create", "--amend", manifestTag) + archTags
            spawn(createCmd.first(), *createCmd.drop(1).toTypedArray(), workingDir = workingDirectory.get().asFile, throwOnError = true)
            
            val pushCmd = listOf("docker", "manifest", "push", manifestTag)
            spawn(pushCmd.first(), *pushCmd.drop(1).toTypedArray(), workingDir = workingDirectory.get().asFile, throwOnError = true)
            
            logger.info("✅ Created and pushed manifest: $manifestTag")
        }
        
        // Write marker file to indicate task completion
        manifestMarker.get().asFile.writeText("Manifest created at ${Instant.now()}")
    }
}

// Custom task type for listing Docker images with configuration cache support
abstract class DockerListImagesTask : DefaultTask() {
    @get:Input
    abstract val showSizes: Property<Boolean>
    
    @get:Internal
    abstract val workingDirectory: DirectoryProperty
    
    @TaskAction
    fun listImages() {
        logger.lifecycle("🐳 Docker Images Summary")
        logger.lifecycle("=".repeat(50))
        
        val githubToken = getGitHubTokenInternal()
        val env = if (githubToken != null) mapOf("GITHUB_TOKEN" to githubToken) else emptyMap()
        val result = spawn("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name", 
                          env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
        
        if (!result.isSuccessful()) {
            logger.lifecycle("❌ Error fetching packages")
            return
        }
        
        val packages = result.output.trim().split("\n").map { it.removeSurrounding("\"") }.filter { it.isNotEmpty() }
        logger.lifecycle("Found ${packages.size} container packages in registry")
        
        packages.forEach { pkg ->
            logger.lifecycle("\n📦 Package: $pkg")
            logger.lifecycle("   Registry: ghcr.io/xtclang/$pkg")
            
            // Skip packages with invalid names (containing slashes that break API calls)
            if (pkg.contains("/")) {
                logger.lifecycle("  ⚠️ Skipping package with invalid name format: $pkg")
                return@forEach
            }
            
            val versions = fetchPackageVersionsInternal(pkg, githubToken)
            if (versions.isEmpty()) {
                logger.lifecycle("  ❌ Could not get versions for $pkg")
                return@forEach
            }
            
            val parsedVersions = versions.mapNotNull { parseImageVersionInternal(it) }
            logger.lifecycle("   Total versions: ${parsedVersions.size}")
            logger.lifecycle("   📋 Recent versions:")
            
            parsedVersions.take(20).forEachIndexed { i, version ->
                val sizeInfo = if (showSizes.get() && version.tags.isNotEmpty()) {
                    val actualSize = getDockerImageSizeInternal("ghcr.io/xtclang/$pkg:${version.tags.first()}")
                    if (actualSize > 0) " (${actualSize.formatBytesInternal()})" else ""
                } else ""
                
                val tagInfo = if (version.tags.isEmpty()) " [UNTAGGED/DANGLING]" else " tags: ${version.tags}"
                logger.lifecycle("     ${i+1}. [${version.created}]$tagInfo$sizeInfo")
            }
            
            if (parsedVersions.size > 20) {
                logger.lifecycle("     ... and ${parsedVersions.size - 20} more versions")
            }
        }
    }
    
    private fun getGitHubTokenInternal(): String? {
        return providers.environmentVariable("GITHUB_TOKEN").orNull ?: try {
            val result = spawn("gh", "auth", "token", throwOnError = false, workingDir = workingDirectory.get().asFile)
            if (result.isSuccessful()) result.output else null
        } catch (e: Exception) {
            logger.warn("Could not get GitHub token: ${e.message}")
            null
        }
    }
    
    private fun fetchPackageVersionsInternal(packageName: String, token: String?): List<String> {
        val env = if (token != null) mapOf("GITHUB_TOKEN" to token) else emptyMap()
        val result = spawn("gh", "api", "--paginate", "orgs/xtclang/packages/container/$packageName/versions",
                          "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags, size: .size}",
                          env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
        
        return if (result.isSuccessful()) {
            result.output.split("\n").filter { it.isNotEmpty() }
        } else {
            logger.warn("Failed to fetch package versions: ${result.output}")
            emptyList()
        }
    }
    
    private fun parseImageVersionInternal(versionJson: String): ImageVersion? {
        return try {
            val id = versionJson.substringAfter("\"id\":").substringBefore(",").trim()
            val created = versionJson.substringAfter("\"created\":\"").substringBefore("\"")
            val tagsSection = versionJson.substringAfter("\"tags\":[").substringBefore("]")
            val tags = if (tagsSection.isBlank()) emptyList() else {
                tagsSection.split(",").map { it.trim().removeSurrounding("\"") }
            }
            
            val sizeBytes = try {
                versionJson.substringAfter("\"size\":").substringBefore(",").substringBefore("}").trim().toLongOrNull() ?: 0L
            } catch (_: Exception) { 0L }
            
            val isMasterImage = tags.any { tag ->
                tag == "latest" || tag == "master" || tag.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) || tag.matches(Regex("latest-\\w+"))
            }
            
            ImageVersion(id, created, tags, isMasterImage, sizeBytes)
        } catch (_: Exception) {
            null
        }
    }
    
    private fun getDockerImageSizeInternal(imageRef: String): Long {
        return try {
            val result = spawn("docker", "manifest", "inspect", imageRef, throwOnError = false, workingDir = workingDirectory.get().asFile)
            if (!result.isSuccessful() || result.output.isBlank()) return 0L
            
            // Parse manifest to get config size + layer sizes
            val configSizeMatch = """"size":\s*(\d+)""".toRegex().find(result.output)
            val configSize = configSizeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            
            val layerSizes = """"size":\s*(\d+)""".toRegex()
                .findAll(result.output)
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .toList()
            
            configSize + layerSizes.sum()
        } catch (e: Exception) {
            logger.debug("Failed to get size for $imageRef: ${e.message}")
            0L
        }
    }
    
    private fun Long.formatBytesInternal(): String = when {
        this == 0L -> "unknown"
        this < 1024 -> "${this}B"
        this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
        this < 1024 * 1024 * 1024 -> "${"%.1f".format(this / (1024.0 * 1024.0))}MB"
        else -> "${"%.1f".format(this / (1024.0 * 1024.0 * 1024.0))}GB"
    }
}

// Custom task type for deleting broken Docker packages with configuration cache support
abstract class DockerDeleteBrokenPackagesTask : DefaultTask() {
    @get:Input
    abstract val dryRun: Property<Boolean>
    
    @get:Internal
    abstract val workingDirectory: DirectoryProperty
    
    @TaskAction
    fun deleteBrokenPackages() {
        logger.lifecycle("🧹 Delete Broken Docker Packages")
        logger.lifecycle("=".repeat(50))
        
        if (dryRun.get()) logger.lifecycle("🔍 DRY RUN MODE (use -PdryRun=false to actually delete)")
        
        val githubToken = getGitHubTokenInternal()
        val env = if (githubToken != null) mapOf("GITHUB_TOKEN" to githubToken) else emptyMap()
        val result = spawn("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name", 
                          env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
        
        if (!result.isSuccessful()) {
            logger.lifecycle("❌ Error fetching packages")
            return
        }
        
        val packages = result.output.trim().split("\n").map { it.removeSurrounding("\"") }.filter { it.isNotEmpty() }
        val brokenPackages = packages.filter { it.contains("/") }
        
        if (brokenPackages.isEmpty()) {
            logger.lifecycle("✅ No broken packages found")
            return
        }
        
        logger.lifecycle("Found ${brokenPackages.size} broken packages:")
        brokenPackages.forEach { pkg ->
            logger.lifecycle("  - $pkg")
        }
        
        if (dryRun.get()) {
            logger.lifecycle("🔍 DRY RUN: Would delete these packages")
            return
        }
        
        brokenPackages.forEach { pkg ->
            logger.lifecycle("\n🗑️ Deleting package: $pkg")
            val deleteResult = spawn("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/${pkg.replace("/", "%2F")}", 
                                   env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
            
            if (deleteResult.isSuccessful()) {
                logger.lifecycle("✅ Deleted: $pkg")
            } else {
                logger.lifecycle("❌ Failed to delete $pkg: ${deleteResult.output}")
            }
        }
    }
    
    private fun getGitHubTokenInternal(): String? {
        return providers.environmentVariable("GITHUB_TOKEN").orNull ?: try {
            val result = spawn("gh", "auth", "token", throwOnError = false, workingDir = workingDirectory.get().asFile)
            if (result.isSuccessful()) result.output else null
        } catch (e: Exception) {
            logger.warn("Could not get GitHub token: ${e.message}")
            null
        }
    }
}

// Custom task type for cleaning Docker images with configuration cache support
abstract class DockerCleanImagesTask : DefaultTask() {
    @get:Input
    abstract val keepCount: Property<Int>
    
    @get:Input
    abstract val dryRun: Property<Boolean>
    
    @get:Input
    abstract val forced: Property<Boolean>
    
    @get:Internal
    abstract val workingDirectory: DirectoryProperty
    
    @TaskAction
    fun cleanImages() {
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val packageName = "xvm"
        if (dryRun.get()) logger.lifecycle("🔍 DRY RUN MODE")
        
        val githubToken = getGitHubTokenInternal()
        val versions = fetchPackageVersionsInternal(packageName, githubToken)
        if (versions.isEmpty()) {
            logger.lifecycle("❌ No versions found")
            return
        }
        
        val parsedVersions = versions.mapNotNull { parseImageVersionInternal(it) }.sortedByDescending { it.created }
        val masterImages = parsedVersions.filter { it.isMasterImage }
        val masterToKeep = if (masterImages.isNotEmpty()) listOf(masterImages.first()) else emptyList()
        
        val allToKeep = mutableSetOf<ImageVersion>().apply {
            addAll(masterToKeep)
            addAll(parsedVersions.take(keepCount.get()))
        }.sortedByDescending { it.created }
        
        val toDelete = parsedVersions.filter { it !in allToKeep }
        val masterInKeep = allToKeep.filter { it.isMasterImage }
        
        if (masterInKeep.isEmpty() && masterImages.isNotEmpty()) {
            logger.lifecycle("❌ SAFETY CHECK FAILED: Would delete all master images!")
            return
        }
        
        logger.lifecycle("📦 Package: $packageName (${parsedVersions.size} total)")
        logger.lifecycle("✅ Keeping: ${allToKeep.size} versions (${masterInKeep.size} master)")
        logger.lifecycle("🗑️  Deleting: ${toDelete.size} versions")
        
        if (toDelete.isEmpty()) {
            logger.lifecycle("✅ No cleanup needed")
            return
        }
        
        if (dryRun.get()) {
            logger.lifecycle("🔍 DRY RUN COMPLETE")
            return
        }
        
        val needsConfirmation = !(providers.environmentVariable("CI").orElse("false").get() == "true") && !forced.get()
        if (needsConfirmation) {
            logger.lifecycle("❓ Delete ${toDelete.size} versions? (Type 'yes' to confirm)")
            val response = readlnOrNull()?.trim()?.lowercase()
            if (response != "yes") {
                logger.lifecycle("❌ Cancelled")
                return
            }
        }
        
        // Execute deletions
        var deleted = 0
        val failures = mutableListOf<String>()
        
        toDelete.forEach { version ->
            val env = if (githubToken != null) mapOf("GITHUB_TOKEN" to githubToken) else emptyMap()
            val result = spawn("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName/versions/${version.id}",
                              env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
            if (result.isSuccessful()) {
                deleted++
                logger.lifecycle("✅ Deleted version ${version.id} (tags: ${version.tags})")
            } else {
                val errorMsg = "Delete command failed with exit code ${result.exitValue}: ${result.output}"
                logger.warn("❌ $errorMsg")
                failures.add(errorMsg)
            }
        }
        
        logger.lifecycle("🎯 Attempted deletions: $deleted/${toDelete.size} versions")
        
        // Verify deletions
        logger.lifecycle("🔍 Verifying deletions...")
        var actuallyDeleted = 0
        val maxRetries = 3
        
        for (attempt in 1..maxRetries) {
            Thread.sleep(if (attempt > 1) 5000L else 1000L)
            
            val remainingVersions = fetchPackageVersionsInternal(packageName, githubToken)
            val remainingIds = remainingVersions.mapNotNull { parseImageVersionInternal(it)?.id }.toSet()
            actuallyDeleted = toDelete.count { it.id !in remainingIds }
            
            logger.lifecycle("📊 Attempt $attempt: $actuallyDeleted/${toDelete.size} deletions confirmed")
            
            if (actuallyDeleted == toDelete.size) {
                logger.lifecycle("🎉 All deletions verified! Final count: ${remainingVersions.size} versions remaining")
                break
            }
            
            if (attempt < maxRetries) {
                logger.lifecycle("⏳ Waiting 5s for API consistency...")
            }
        }
        
        if (actuallyDeleted != toDelete.size) {
            logger.warn("⚠️  Package cleanup incomplete: $actuallyDeleted/${toDelete.size} deletions confirmed after $maxRetries attempts")
            logger.warn("⚠️  This may be due to GitHub API delays, permission issues, or network problems")
            logger.warn("✅  Continuing build - package cleanup is not critical for build success")
        }
    }
    
    private fun getGitHubTokenInternal(): String? {
        return providers.environmentVariable("GITHUB_TOKEN").orNull ?: try {
            val result = spawn("gh", "auth", "token", throwOnError = false, workingDir = workingDirectory.get().asFile)
            if (result.isSuccessful()) result.output else null
        } catch (e: Exception) {
            logger.warn("Could not get GitHub token: ${e.message}")
            null
        }
    }
    
    private fun fetchPackageVersionsInternal(packageName: String, token: String?): List<String> {
        val env = if (token != null) mapOf("GITHUB_TOKEN" to token) else emptyMap()
        val result = spawn("gh", "api", "--paginate", "orgs/xtclang/packages/container/$packageName/versions",
                          "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags, size: .size}",
                          env = env, throwOnError = false, workingDir = workingDirectory.get().asFile)
        
        return if (result.isSuccessful()) {
            result.output.split("\n").filter { it.isNotEmpty() }
        } else {
            logger.warn("Failed to fetch package versions: ${result.output}")
            emptyList()
        }
    }
    
    private fun parseImageVersionInternal(versionJson: String): ImageVersion? {
        return try {
            val id = versionJson.substringAfter("\"id\":").substringBefore(",").trim()
            val created = versionJson.substringAfter("\"created\":\"").substringBefore("\"")
            val tagsSection = versionJson.substringAfter("\"tags\":[").substringBefore("]")
            val tags = if (tagsSection.isBlank()) emptyList() else {
                tagsSection.split(",").map { it.trim().removeSurrounding("\"") }
            }
            
            val sizeBytes = try {
                versionJson.substringAfter("\"size\":").substringBefore(",").substringBefore("}").trim().toLongOrNull() ?: 0L
            } catch (_: Exception) { 0L }
            
            val isMasterImage = tags.any { tag ->
                tag == "latest" || tag == "master" || tag.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) || tag.matches(Regex("latest-\\w+"))
            }
            
            ImageVersion(id, created, tags, isMasterImage, sizeBytes)
        } catch (_: Exception) {
            null
        }
    }
}
val allowEmulation = providers.gradleProperty("dockerAllowEmulation").map { it.toBoolean() }.orElse(false).get()
val workingDir = projectDir

// Helper to configure DockerBuildTask with common settings
fun TaskContainer.registerDockerBuild(taskName: String, arch: String, action: String = "load", configure: DockerBuildTask.() -> Unit = {}) = 
    register(taskName, DockerBuildTask::class) {
        group = "docker"
        targetArchitecture.set(arch)
        version.set(dockerConfig.version)
        branch.set(dockerConfig.branch)
        commit.set(dockerConfig.commit)
        this.action.set(action)
        allowEmulation.set(providers.gradleProperty("dockerAllowEmulation").map { it.toBoolean() }.orElse(false))
        workingDirectory.set(layout.projectDirectory.dir("."))
        configure()
    }


// Configuration-cache safe docker build logic 
// Takes all parameters instead of capturing script variables

// Build tasks
val buildAmd64 by tasks.registerDockerBuild("buildAmd64", "amd64") {
    description = "Build Docker image for AMD64"
}

val buildArm64 by tasks.registerDockerBuild("buildArm64", "arm64") {
    description = "Build Docker image for ARM64"
}

val buildAll by tasks.registering {
    group = "docker" 
    description = "Build multi-platform Docker images"
    
    val configVersion = dockerConfig.version
    val configBranch = dockerConfig.branch  
    val configCommit = dockerConfig.commit
    val buildWorkingDir = workingDir
    
    doLast {
        val config = DockerConfig(configVersion, configBranch, configCommit)
        val platforms = listOf("linux/amd64", "linux/arm64")
        val tags = config.multiPlatformTags()
        val action = "load"
        
        // Inline buildDockerImage logic
        val platformArg = platforms.joinToString(",")
        val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
                  listOf("--progress=${providers.environmentVariable("DOCKER_BUILDX_PROGRESS").orElse("plain").get()}") +
                  config.buildArgs().flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  config.cacheArgs(if (platforms.size == 1) platforms[0].substringAfter("/") else null) +
                  tags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
                  listOf("--$action", ".")
        
        // Remove logger parameter to fix configuration cache issue
        spawn(cmd.first(), *cmd.drop(1).toTypedArray(), workingDir = buildWorkingDir, throwOnError = true)
    }
}

val pushAmd64 by tasks.registerDockerBuild("pushAmd64", "amd64", "push") {
    description = "Push AMD64 Docker image to GitHub Container Registry"
}

val pushArm64 by tasks.registerDockerBuild("pushArm64", "arm64", "push") {
    description = "Push ARM64 Docker image to GitHub Container Registry"
}

val pushAll by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images"
    
    val version = dockerConfig.version
    val branch = dockerConfig.branch  
    val commit = dockerConfig.commit
    val buildWorkingDir = workingDir
    
    doLast {
        // Multi-platform build - similar to buildAll but with push action
        val config = DockerConfig(version, branch, commit)
        val platforms = listOf("linux/amd64", "linux/arm64")
        val tags = config.multiPlatformTags()
        val action = "push"
        
        val platformArg = platforms.joinToString(",")
        val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
                  listOf("--progress=${providers.environmentVariable("DOCKER_BUILDX_PROGRESS").orElse("plain").get()}") +
                  config.buildArgs().flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  config.cacheArgs(if (platforms.size == 1) platforms[0].substringAfter("/") else null) +
                  tags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
                  listOf("--$action", ".")
        
        spawn(cmd.first(), *cmd.drop(1).toTypedArray(), workingDir = buildWorkingDir, throwOnError = true)
    }
}

val createManifest by tasks.registering(DockerManifestTask::class) {
    group = "docker"
    description = "Create multi-platform manifest"
    dependsOn(pushAmd64, pushArm64)
    
    version.set(dockerConfig.version)
    branch.set(dockerConfig.branch)
    commit.set(dockerConfig.commit)
    baseImage.set(dockerConfig.baseImage)
    architectures.set(listOf("amd64", "arm64"))
    workingDirectory.set(layout.projectDirectory.dir("."))
    manifestMarker.set(layout.buildDirectory.file("docker-manifest-marker.txt"))
}

val testImageFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality"
    
    val hostArch = normalizeArchitecture(providers.systemProperty("os.arch").get())
    dependsOn(if (hostArch == "amd64") buildAmd64 else buildArm64)
    
    inputs.file("test/DockerTest.x")
    inputs.file("Dockerfile")
    outputs.file(layout.buildDirectory.file("docker-test-results.txt"))
    
    doLast {
        val imageTag = "${dockerConfig.baseImage}:${dockerConfig.tagPrefix}-$hostArch"
        
        logger.info("Testing Docker image: $imageTag")
        
        val testScenarios = listOf(
            "xec --version" to listOf("xec", "--version"),
            "xcc --version" to listOf("xcc", "--version"),
            "DockerTest (no args)" to listOf("xec", "/opt/xdk/test/DockerTest.x"),
            "DockerTest (with args)" to listOf("xec", "/opt/xdk/test/DockerTest.x", "hello", "world")
        )
        
        testScenarios.forEach { (testName, testCmd) ->
            val dockerCmd = listOf("docker", "run", "--rm", imageTag) + testCmd
            val result = spawn(dockerCmd.first(), *dockerCmd.drop(1).toTypedArray(), throwOnError = false, logger = logger)
            
            if (result.isSuccessful()) {
                logger.info("✅ $testName")
            } else {
                throw GradleException("❌ $testName failed (exit ${result.exitValue}): ${result.output}")
            }
        }
        
        outputs.files.singleFile.writeText("Docker tests passed at ${Instant.now()}")
    }
}

// Debug task to verify Docker configuration
val debugDockerConfig by tasks.registering {
    group = "docker"
    description = "Debug Docker configuration and tagging"
    
    doLast {
        println("=== Docker Configuration Debug ===")
        println("Current git branch: ${providers.exec { commandLine("git", "rev-parse", "--abbrev-ref", "HEAD") }.standardOutput.asText.orElse("unknown").get().trim()}")
        println("GH_BRANCH env: ${providers.environmentVariable("GH_BRANCH").orNull}")
        println("GH_COMMIT env: ${providers.environmentVariable("GH_COMMIT").orNull}")
        println("Detected branch: ${dockerConfig.branch}")
        println("Detected commit: ${dockerConfig.commit.take(8)}")
        println("Version: ${dockerConfig.version}")
        println("Is master: ${dockerConfig.isMaster}")
        println("Tag prefix: ${dockerConfig.tagPrefix}")
        println("Version tags: ${dockerConfig.versionTags}")
        println("ARM64 tags: ${dockerConfig.tagsForArch("arm64")}")
        println("Multi-platform tags: ${dockerConfig.multiPlatformTags()}")
    }
}

// Lifecycle integration
tasks.assemble { dependsOn(buildAll) }

// Registry management
data class ImageVersion(val id: String, val created: String, val tags: List<String>, val isMasterImage: Boolean, val sizeBytes: Long = 0L) {
    fun formattedSize(): String = when {
        sizeBytes == 0L -> "unknown"
        sizeBytes < 1024 -> "${sizeBytes}B"
        sizeBytes < 1024 * 1024 -> "${"%.1f".format(sizeBytes / 1024.0)}KB"
        sizeBytes < 1024 * 1024 * 1024 -> "${"%.1f".format(sizeBytes / (1024.0 * 1024.0))}MB"
        else -> "${"%.1f".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))}GB"
    }
}

fun Long.formatBytes(): String = when {
    this == 0L -> "unknown"
    this < 1024 -> "${this}B"
    this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
    this < 1024 * 1024 * 1024 -> "${"%.1f".format(this / (1024.0 * 1024.0))}MB"
    else -> "${"%.1f".format(this / (1024.0 * 1024.0 * 1024.0))}GB"
}

fun getGitHubToken(): String? {
    return System.getenv("GITHUB_TOKEN") ?: try {
        val result = spawn("gh", "auth", "token", throwOnError = false, logger = logger)
        if (result.isSuccessful()) result.output else null
    } catch (e: Exception) {
        logger.warn("Could not get GitHub token: ${e.message}")
        null
    }
}

fun fetchPackageVersions(packageName: String, token: String?): List<String> {
    val env = if (token != null) mapOf("GITHUB_TOKEN" to token) else emptyMap()
    val result = spawn("gh", "api", "--paginate", "orgs/xtclang/packages/container/$packageName/versions",
                      "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags, size: .size}",
                      env = env, throwOnError = false, logger = logger)
    
    return if (result.isSuccessful()) {
        result.output.split("\n").filter { it.isNotEmpty() }
    } else {
        logger.warn("Failed to fetch package versions: ${result.output}")
        emptyList()
    }
}

fun parseImageVersion(versionJson: String): ImageVersion? {
    return try {
        val id = versionJson.substringAfter("\"id\":").substringBefore(",").trim()
        val created = versionJson.substringAfter("\"created\":\"").substringBefore("\"")
        val tagsSection = versionJson.substringAfter("\"tags\":[").substringBefore("]")
        val tags = if (tagsSection.isBlank()) emptyList() else {
            tagsSection.split(",").map { it.trim().removeSurrounding("\"") }
        }
        
        val sizeBytes = try {
            versionJson.substringAfter("\"size\":").substringBefore(",").substringBefore("}").trim().toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        
        val isMasterImage = tags.any { tag ->
            tag == "latest" || tag == "master" || tag.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) || tag.matches(Regex("latest-\\w+"))
        }
        
        ImageVersion(id, created, tags, isMasterImage, sizeBytes)
    } catch (_: Exception) {
        null
    }
}

fun getDockerImageSize(imageRef: String): Long {
    return try {
        val result = spawn("docker", "manifest", "inspect", imageRef, throwOnError = false, logger = logger)
        if (!result.isSuccessful() || result.output.isBlank()) return 0L
        
        // Parse manifest to get config size + layer sizes
        val configSizeMatch = """"size":\s*(\d+)""".toRegex().find(result.output)
        val configSize = configSizeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        
        val layerSizes = """"size":\s*(\d+)""".toRegex()
            .findAll(result.output)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .toList()
        
        configSize + layerSizes.sum()
    } catch (e: Exception) {
        logger.debug("Failed to get size for $imageRef: ${e.message}")
        0L
    }
}


val listImages by tasks.registering(DockerListImagesTask::class) {
    group = "docker"
    description = "List Docker images in registry (use -PshowSizes=true to calculate actual sizes)"
    
    showSizes.set(providers.gradleProperty("showSizes").map { it.toBoolean() }.orElse(false))
    workingDirectory.set(layout.projectDirectory.dir("."))
}

val deleteBrokenPackages by tasks.registering(DockerDeleteBrokenPackagesTask::class) {
    group = "docker"
    description = "Delete packages with invalid names that break the API"
    
    dryRun.set(providers.gradleProperty("dryRun").map { it.toBoolean() }.orElse(true))
    workingDirectory.set(layout.projectDirectory.dir("."))
}

val cleanImages by tasks.registering(DockerCleanImagesTask::class) {
    group = "docker"
    description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"
    
    keepCount.set(providers.gradleProperty("keepCount").map { it.toIntOrNull() ?: 10 }.orElse(10))
    dryRun.set(providers.gradleProperty("dryRun").map { it.toBoolean() }.orElse(false))
    forced.set(providers.gradleProperty("force").map { it.toBoolean() }.orElse(false))
    workingDirectory.set(layout.projectDirectory.dir("."))
}
