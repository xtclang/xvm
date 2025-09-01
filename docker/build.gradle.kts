/**
 * Docker build and management for XVM project.
 * Supports multi-platform builds, registry management, and cleanup.
 */

import XdkDistribution.Companion.normalizeArchitecture
import java.io.File
import java.time.Instant

plugins {
    base
    id("org.xtclang.build.xdk.versioning")
}

private val semanticVersion: SemanticVersion by extra
private val javaVersion = getXdkPropertyInt("org.xtclang.java.jdk")

// Docker configuration
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
    fun buildArgs(distZipUrl: String? = null, javaVersion: Int) = mapOf(
        "GH_BRANCH" to branch,
        "GH_COMMIT" to commit,
        "JAVA_VERSION" to javaVersion.toString()
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

fun createDockerConfig(): DockerConfig {
    val version = semanticVersion.artifactVersion
    val branch = System.getenv("GH_BRANCH") ?: try {
        val result = spawn("git", "branch", "--show-current", throwOnError = false, logger = logger)
        if (result.isSuccessful()) result.output.ifBlank { "master" } else "master"
    } catch (_: Exception) { "master" }
    
    val commit = System.getenv("GH_COMMIT") ?: try {
        val result = spawn("git", "rev-parse", "HEAD", throwOnError = false, logger = logger)
        if (result.isSuccessful()) result.output else "unknown"
    } catch (_: Exception) { "unknown" }
    
    return DockerConfig(version, branch, commit)
}


fun execDockerCommand(cmd: List<String>) {
    logger.info("Docker: ${cmd.joinToString(" ")}")
    
    // Use ProcessBuilder for live output streaming
    val builder = ProcessBuilder(cmd).redirectErrorStream(true)
    builder.directory(projectDir)
    
    val process = builder.start()
    
    // Stream output in real-time
    process.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            logger.info(line)
        }
    }
    
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw IllegalStateException("Docker command failed with exit code: $exitCode")
    }
}

fun buildDockerImage(config: DockerConfig, platforms: List<String>, tags: List<String>, action: String, distZipUrl: String? = null, javaVersion: Int) {
    val platformArg = platforms.joinToString(",")
    val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
              listOf("--progress=${System.getenv("DOCKER_BUILDX_PROGRESS") ?: "plain"}") +
              config.buildArgs(distZipUrl, javaVersion).flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
              config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
              config.cacheArgs(if (platforms.size == 1) platforms[0].substringAfter("/") else null) +
              tags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
              listOf("--$action", ".")
    execDockerCommand(cmd)
}

fun checkCrossPlatformBuild(targetArch: String, taskName: String): Boolean {
    val hostArch = normalizeArchitecture(System.getProperty("os.arch"))
    val allowEmulation = getXdkPropertyBoolean("org.xtclang.docker.allowEmulation", false)
    
    if (targetArch != hostArch && !allowEmulation) {
        throw GradleException("Cannot build for $targetArch on $hostArch architecture. " +
            "Set org.xtclang.docker.allowEmulation=true in xdk.properties or use " +
            "-Porg.xtclang.docker.allowEmulation=true to enable emulation")
    }
    return true
}

// Build tasks
val buildAmd64 by tasks.registering {
    group = "docker"
    description = "Build Docker image for AMD64 (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        if (!checkCrossPlatformBuild("amd64", name)) return@doLast
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/amd64"), config.tagsForArch("amd64"), "load", distZipUrl, javaVersion)
    }
}

val buildArm64 by tasks.registering {
    group = "docker"
    description = "Build Docker image for ARM64 (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        if (!checkCrossPlatformBuild("arm64", name)) return@doLast
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/arm64"), config.tagsForArch("arm64"), "load", distZipUrl, javaVersion)
    }
}

val buildAll by tasks.registering {
    group = "docker"
    description = "Build multi-platform Docker images (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/amd64", "linux/arm64"), config.multiPlatformTags(), "load", distZipUrl, javaVersion)
    }
}

val pushAmd64 by tasks.registering {
    group = "docker"
    description = "Push AMD64 Docker image to GitHub Container Registry (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        if (!checkCrossPlatformBuild("amd64", name)) return@doLast
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/amd64"), config.tagsForArch("amd64"), "push", distZipUrl, javaVersion)
    }
}

val pushArm64 by tasks.registering {
    group = "docker"
    description = "Push ARM64 Docker image to GitHub Container Registry (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        if (!checkCrossPlatformBuild("arm64", name)) return@doLast
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/arm64"), config.tagsForArch("arm64"), "push", distZipUrl, javaVersion)
    }
}

val pushAll by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
    doLast {
        val config = createDockerConfig()
        val distZipUrl = System.getenv("DIST_ZIP_URL")
        if (distZipUrl != null) {
            logger.info("Using snapshot distribution: $distZipUrl")
        } else {
            logger.info("Using source build with branch: ${config.branch}, commit: ${config.commit}")
        }
        buildDockerImage(config, listOf("linux/amd64", "linux/arm64"), config.multiPlatformTags(), "push", distZipUrl, javaVersion)
    }
}

val createManifest by tasks.registering {
    group = "docker"
    description = "Create multi-platform manifest"
    dependsOn(pushAmd64, pushArm64)
    doLast {
        val config = createDockerConfig()
        val multiTags = config.multiPlatformTags()
        
        multiTags.forEach { tag ->
            val manifestTag = "${config.baseImage}:$tag"
            logger.info("Creating manifest for: $manifestTag")
            
            val createCmd = listOf("docker", "manifest", "create", manifestTag,
                                 "${config.baseImage}:$tag-amd64",
                                 "${config.baseImage}:$tag-arm64")
            execDockerCommand(createCmd)
            
            val pushCmd = listOf("docker", "manifest", "push", manifestTag)
            execDockerCommand(pushCmd)
            
            logger.info("✅ Created and pushed manifest: $manifestTag")
        }
    }
}

// Common function to test Docker image functionality
fun testDockerImage(imageTag: String, logger: Logger) {
    logger.info("🧪 Testing Docker image: $imageTag")
    logger.info("This tests compilation and execution of XTC programs with different parameters")
    
    // Basic launcher tests
    logger.info("🔧 Testing launchers...")
    val basicTests = listOf(
        "xec --version" to listOf("xec", "--version"),
        "xcc --version" to listOf("xcc", "--version")
    )
    
    basicTests.forEach { (testName, testCmd) ->
        val dockerCmd = listOf("docker", "run", "--rm", imageTag) + testCmd
        val result = spawn(dockerCmd.first(), *dockerCmd.drop(1).toTypedArray(), throwOnError = false, logger = logger)
        
        if (result.isSuccessful()) {
            logger.info("✅ $testName")
        } else {
            throw GradleException("❌ $testName failed (exit ${result.exitValue}): ${result.output}")
        }
    }
    
    // Verify launcher type (script vs binary)
    logger.info("🔍 Verifying Docker image uses script launchers...")
    val launcherContentCmd = listOf("docker", "run", "--rm", imageTag, "head", "-5", "/opt/xdk/bin/xcc")
    val launcherResult = spawn(launcherContentCmd.first(), *launcherContentCmd.drop(1).toTypedArray(), throwOnError = false, logger = logger)
    
    if (launcherResult.isSuccessful()) {
        val content = launcherResult.output
        logger.info("📋 Launcher content preview: $content")
        
        if (content.contains("#!/bin/sh") || content.contains("#!/bin/bash") || content.contains("exec") && content.contains("java")) {
            logger.info("✅ Docker image is using script launchers (as expected)")
        } else {
            throw GradleException("❌ Launcher doesn't appear to be a shell script - this indicates the distribution changes didn't work. Content: $content")
        }
    } else {
        throw GradleException("❌ Failed to check launcher type: ${launcherResult.output}")
    }
    
    // Verify script content has XTC module paths
    logger.info("🔍 Verifying script launchers contain XTC module paths...")
    val scriptContentCmd = listOf("docker", "run", "--rm", imageTag, "head", "-50", "/opt/xdk/bin/xcc")
    val scriptResult = spawn(scriptContentCmd.first(), *scriptContentCmd.drop(1).toTypedArray(), throwOnError = false, logger = logger)
    
    if (scriptResult.isSuccessful()) {
        val scriptContent = scriptResult.output
        val hasXdkHome = scriptContent.contains("XDK_HOME") || scriptContent.contains("APP_HOME")
        val hasLibPaths = scriptContent.contains("-L") && scriptContent.contains("lib")
        val hasTurtle = scriptContent.contains("javatools_turtle.xtc")
        val hasBridge = scriptContent.contains("javatools_bridge.xtc")
        
        if (hasXdkHome && hasLibPaths && hasTurtle && hasBridge) {
            logger.info("✅ Script launchers contain expected XTC module paths")
        } else {
            logger.warn("⚠️  Script launcher missing some expected paths:")
            logger.warn("  XDK_HOME/APP_HOME: $hasXdkHome")
            logger.warn("  Library paths (-L): $hasLibPaths") 
            logger.warn("  javatools_turtle.xtc: $hasTurtle")
            logger.warn("  javatools_bridge.xtc: $hasBridge")
            logger.info("📋 Script content: $scriptContent")
        }
    } else {
        throw GradleException("❌ Failed to check script content: ${scriptResult.output}")
    }
    
    // Functional tests
    logger.info("🧪 Testing XTC program execution...")
    val functionalTests = listOf(
        "DockerTest (no args)" to listOf("xec", "/opt/xdk/test/DockerTest.x"),
        "DockerTest (with args)" to listOf("xec", "/opt/xdk/test/DockerTest.x", "hello", "world")
    )
    
    functionalTests.forEach { (testName, testCmd) ->
        val dockerCmd = listOf("docker", "run", "--rm", imageTag) + testCmd
        val result = spawn(dockerCmd.first(), *dockerCmd.drop(1).toTypedArray(), throwOnError = false, logger = logger)
        
        if (result.isSuccessful()) {
            logger.info("✅ $testName")
        } else {
            throw GradleException("❌ $testName failed (exit ${result.exitValue}): ${result.output}")
        }
    }
}

val testImageFunctionalityAmd64 by tasks.registering {
    group = "docker"
    description = "Test AMD64 Docker image functionality"
    dependsOn(buildAmd64)
    
    inputs.file("test/DockerTest.x")
    inputs.file("Dockerfile")
    outputs.file(layout.buildDirectory.file("docker-test-amd64-results.txt"))
    
    doLast {
        val config = createDockerConfig()
        val imageTag = "${config.baseImage}:${config.tagPrefix}-amd64"
        testDockerImage(imageTag, logger)
        outputs.files.singleFile.writeText("AMD64 Docker tests passed at ${Instant.now()}")
    }
}

val testImageFunctionalityArm64 by tasks.registering {
    group = "docker"
    description = "Test ARM64 Docker image functionality"
    dependsOn(buildArm64)
    
    inputs.file("test/DockerTest.x")
    inputs.file("Dockerfile")
    outputs.file(layout.buildDirectory.file("docker-test-arm64-results.txt"))
    
    doLast {
        val config = createDockerConfig()
        val imageTag = "${config.baseImage}:${config.tagPrefix}-arm64"
        testDockerImage(imageTag, logger)
        outputs.files.singleFile.writeText("ARM64 Docker tests passed at ${Instant.now()}")
    }
}

val testImageFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality for all platforms"
    dependsOn(testImageFunctionalityAmd64, testImageFunctionalityArm64)
    
    outputs.file(layout.buildDirectory.file("docker-test-results.txt"))
    
    doLast {
        outputs.files.singleFile.writeText("All platform Docker tests completed at ${Instant.now()}")
    }
}

// Lifecycle integration
// Docker build removed from main lifecycle - run manually with ./gradlew :docker:buildAll
// tasks.assemble { dependsOn(buildAll) }

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


val listImages by tasks.registering {
    group = "docker"
    description = "List Docker images in registry (use -PshowSizes=true to calculate actual sizes)"
    
    val showSizes = project.findProperty("showSizes")?.toString()?.toBoolean() ?: false
    
    doLast {
        logger.lifecycle("🐳 Docker Images Summary")
        logger.lifecycle("=".repeat(50))
        
        val githubToken = getGitHubToken()
        val env = if (githubToken != null) mapOf("GITHUB_TOKEN" to githubToken) else emptyMap()
        val result = spawn("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name", 
                          env = env, throwOnError = false, logger = logger)
        
        if (!result.isSuccessful()) {
            logger.lifecycle("❌ Error fetching packages")
            return@doLast
        }
        
        val packages = result.output.trim().split("\n").map { it.removeSurrounding("\"") }.filter { it.isNotEmpty() }
        logger.lifecycle("Found ${packages.size} container packages in registry")
        
        packages.forEach { pkg ->
            logger.lifecycle("\n📦 Package: $pkg")
            logger.lifecycle("   Registry: ghcr.io/xtclang/$pkg")
            
            val versions = fetchPackageVersions(pkg, githubToken)
            if (versions.isEmpty()) {
                logger.lifecycle("  ❌ Could not get versions for $pkg")
                return@forEach
            }
            
            val parsedVersions = versions.mapNotNull { parseImageVersion(it) }
            logger.lifecycle("   Total versions: ${parsedVersions.size}")
            logger.lifecycle("   📋 Recent versions:")
            
            parsedVersions.take(20).forEachIndexed { i, version ->
                val sizeInfo = if (showSizes && version.tags.isNotEmpty()) {
                    val actualSize = getDockerImageSize("ghcr.io/xtclang/$pkg:${version.tags.first()}")
                    if (actualSize > 0) " (${actualSize.formatBytes()})" else ""
                } else ""
                
                val tagInfo = if (version.tags.isEmpty()) " [UNTAGGED/DANGLING]" else " tags: ${version.tags}"
                logger.lifecycle("     ${i+1}. [${version.created}]$tagInfo$sizeInfo")
            }
            
            if (parsedVersions.size > 20) {
                logger.lifecycle("     ... and ${parsedVersions.size - 20} more versions")
            }
        }
    }
}

val cleanImages by tasks.registering {
    group = "docker"
    description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"
    
    val keepCount = project.findProperty("keepCount")?.toString()?.toIntOrNull() ?: 10
    val isDryRun = project.findProperty("dryRun")?.toString()?.toBoolean() ?: false
    val isForced = project.findProperty("force")?.toString()?.toBoolean() ?: false
    
    doLast {
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val packageName = "xvm"
        if (isDryRun) logger.lifecycle("🔍 DRY RUN MODE")
        
        val githubToken = getGitHubToken()
        val versions = fetchPackageVersions(packageName, githubToken)
        if (versions.isEmpty()) {
            logger.lifecycle("❌ No versions found")
            return@doLast
        }
        
        val parsedVersions = versions.mapNotNull { parseImageVersion(it) }.sortedByDescending { it.created }
        val masterImages = parsedVersions.filter { it.isMasterImage }
        val masterToKeep = if (masterImages.isNotEmpty()) listOf(masterImages.first()) else emptyList()
        
        val allToKeep = mutableSetOf<ImageVersion>().apply {
            addAll(masterToKeep)
            addAll(parsedVersions.take(keepCount))
        }.sortedByDescending { it.created }
        
        val toDelete = parsedVersions.filter { it !in allToKeep }
        val masterInKeep = allToKeep.filter { it.isMasterImage }
        
        if (masterInKeep.isEmpty() && masterImages.isNotEmpty()) {
            logger.lifecycle("❌ SAFETY CHECK FAILED: Would delete all master images!")
            return@doLast
        }
        
        logger.lifecycle("📦 Package: $packageName (${parsedVersions.size} total)")
        logger.lifecycle("✅ Keeping: ${allToKeep.size} versions (${masterInKeep.size} master)")
        logger.lifecycle("🗑️  Deleting: ${toDelete.size} versions")
        
        if (toDelete.isEmpty()) {
            logger.lifecycle("✅ No cleanup needed")
            return@doLast
        }
        
        if (isDryRun) {
            logger.lifecycle("🔍 DRY RUN COMPLETE")
            return@doLast
        }
        
        val needsConfirmation = !(System.getenv("CI") == "true") && !isForced
        if (needsConfirmation) {
            logger.lifecycle("❓ Delete ${toDelete.size} versions? (Type 'yes' to confirm)")
            val response = readlnOrNull()?.trim()?.lowercase()
            if (response != "yes") {
                logger.lifecycle("❌ Cancelled")
                return@doLast
            }
        }
        
        // Execute deletions
        var deleted = 0
        val failures = mutableListOf<String>()
        
        toDelete.forEach { version ->
            val env = if (githubToken != null) mapOf("GITHUB_TOKEN" to githubToken) else emptyMap()
            val result = spawn("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName/versions/${version.id}",
                              env = env, throwOnError = false, logger = logger)
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
            
            val remainingVersions = fetchPackageVersions(packageName, githubToken)
            val remainingIds = remainingVersions.mapNotNull { parseImageVersion(it)?.id }.toSet()
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
}
