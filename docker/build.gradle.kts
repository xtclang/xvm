/**
 * Docker build and management for XVM project.
 * Supports multi-platform builds, registry management, and cleanup.
 */

import java.net.URLEncoder
import java.time.Instant

plugins {
    base
    id("org.xtclang.build.xdk.versioning")
}

// Configuration and utility functions
data class BuildConfig(
    val version: String,
    val branch: String,
    val commit: String,
    val isCI: Boolean = System.getenv("CI") == "true"
) {
    val baseImage = "ghcr.io/xtclang/xvm"
    val isMaster = branch == "master" 
    val tagPrefix = if (isMaster) "latest" else branch.substringAfterLast("/").replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val versionTags = if (isMaster) listOf(version) else emptyList()
    
    fun tagsForArch(arch: String) = (listOf("${tagPrefix}-$arch") + versionTags.map { "${it}-$arch" } + listOf("${commit}-$arch"))
    fun multiPlatformTags() = listOf(tagPrefix) + versionTags + listOf(commit)
    
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
            val cacheDir = File(System.getProperty("user.home"), ".cache/docker-buildx${arch?.let { "-$it" } ?: ""}")
            cacheDir.mkdirs()
            listOf("--cache-from", "type=local,src=${cacheDir.absolutePath}", "--cache-to", "type=local,dest=${cacheDir.absolutePath},mode=max")
        }
    }
}

fun createBuildConfig(): BuildConfig {
    val version = project.version.toString()
    val branch = System.getenv("GH_BRANCH") ?: try {
        providers.exec {
            commandLine("git", "branch", "--show-current")
            workingDir = project.rootDir
        }.standardOutput.asText.get().trim().ifBlank { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
    
    val commit = System.getenv("GH_COMMIT")?.takeIf { it.isNotBlank() } ?: try {
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val apiUrl = "https://api.github.com/repos/xtclang/xvm/commits/$encodedBranch"
        val curlResult = providers.exec {
            commandLine("curl", "-fsSL", apiUrl)
            workingDir = project.rootDir
        }.standardOutput.asText.get()
        """"sha":\s*"([a-f0-9]{40})"""".toRegex().find(curlResult)?.groupValues?.get(1) ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
    
    return BuildConfig(version, branch, commit)
}

fun execDockerCommand(cmd: List<String>) {
    logger.info("Docker: ${cmd.joinToString(" ")}")
    ProcessBuilder(cmd)
        .directory(projectDir)
        .inheritIO()
        .start()
        .waitFor()
        .let { if (it != 0) throw GradleException("Docker command failed with exit code: $it") }
}

fun buildDockerImage(config: BuildConfig, platforms: List<String>, tags: List<String>, action: String) {
    val platformArg = platforms.joinToString(",")
    val cmd = listOf("docker", "buildx", "build", "--platform", platformArg) +
              listOf("--progress=${System.getenv("DOCKER_BUILDX_PROGRESS") ?: "plain"}") +
              config.buildArgs().flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
              config.metadataLabels().flatMap { listOf("--label", "${it.key}=${it.value}") } +
              config.cacheArgs(if (platforms.size == 1) platforms[0].substringAfter("/") else null) +
              tags.flatMap { listOf("--tag", "${config.baseImage}:${it}") } +
              listOf("--$action", ".")
    execDockerCommand(cmd)
}

fun getHostArch(): String = System.getProperty("os.arch").let { osArch ->
    when (osArch) {
        "x86_64", "amd64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> osArch
    }
}

fun checkCrossPlatformBuild(targetArch: String, taskName: String): Boolean {
    val hostArch = getHostArch()
    val allowEmulation = project.findProperty("docker_emulated_build")?.toString()?.toBoolean() ?: false
    
    if (targetArch != hostArch && !allowEmulation) {
        logger.warn("Skipping cross-platform build $taskName ($targetArch on $hostArch)")
        logger.warn("Use -Pdocker_emulated_build=true to enable emulation")
        return false
    }
    return true
}

// Build tasks
val buildAmd64 by tasks.registering {
    group = "docker"
    description = "Build Docker image for AMD64"
    
    doLast {
        if (!checkCrossPlatformBuild("amd64", name)) return@doLast
        val config = createBuildConfig()
        buildDockerImage(config, listOf("linux/amd64"), config.tagsForArch("amd64"), "load")
    }
}

val buildArm64 by tasks.registering {
    group = "docker"
    description = "Build Docker image for ARM64"
    
    doLast {
        if (!checkCrossPlatformBuild("arm64", name)) return@doLast
        val config = createBuildConfig()
        buildDockerImage(config, listOf("linux/arm64"), config.tagsForArch("arm64"), "load")
    }
}

val buildAll by tasks.registering {
    group = "docker"
    description = "Build multi-platform Docker images"
    
    doLast {
        val config = createBuildConfig()
        buildDockerImage(config, listOf("linux/amd64", "linux/arm64"), config.multiPlatformTags(), "load")
    }
}

val pushAmd64 by tasks.registering {
    group = "docker"
    description = "Push AMD64 Docker image"
    dependsOn(buildAmd64)
    
    doLast {
        val config = createBuildConfig()
        config.tagsForArch("amd64").forEach { tag ->
            execDockerCommand(listOf("docker", "push", "${config.baseImage}:$tag"))
        }
    }
}

val pushArm64 by tasks.registering {
    group = "docker" 
    description = "Push ARM64 Docker image"
    dependsOn(buildArm64)
    
    doLast {
        val config = createBuildConfig()
        config.tagsForArch("arm64").forEach { tag ->
            execDockerCommand(listOf("docker", "push", "${config.baseImage}:$tag"))
        }
    }
}

val pushAll by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images"
    
    doLast {
        val config = createBuildConfig()
        buildDockerImage(config, listOf("linux/amd64", "linux/arm64"), config.multiPlatformTags(), "push")
    }
}

val createManifest by tasks.registering {
    group = "docker"
    description = "Create multi-platform manifest"
    dependsOn(buildAmd64, buildArm64)
    
    doLast {
        val config = createBuildConfig()
        logger.info("Built images: ${config.baseImage}:${config.tagPrefix}-{amd64,arm64}")
    }
}

// Test functionality
val testImageFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality"
    
    val hostArch = getHostArch()
    dependsOn(if (hostArch == "amd64") buildAmd64 else buildArm64)
    
    inputs.file("test/DockerTest.x")
    inputs.file("Dockerfile")
    outputs.file(layout.buildDirectory.file("docker-test-results.txt"))
    
    doLast {
        val config = createBuildConfig()
        val imageTag = "${config.baseImage}:${config.tagPrefix}-$hostArch"
        
        logger.info("Testing Docker image: $imageTag")
        
        val testScenarios = listOf(
            "xec --version" to listOf("xec", "--version"),
            "xcc --version" to listOf("xcc", "--version"),
            "DockerTest (no args)" to listOf("xec", "/opt/xdk/test/DockerTest.x"),
            "DockerTest (with args)" to listOf("xec", "/opt/xdk/test/DockerTest.x", "hello", "world")
        )
        
        testScenarios.forEach { (testName, testCmd) ->
            val dockerCmd = listOf("docker", "run", "--rm", imageTag) + testCmd
            val result = ProcessBuilder(dockerCmd)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val exitCode = result.waitFor()
            result.inputStream.bufferedReader().readText()
            
            if (exitCode == 0) {
                logger.info("✅ $testName")
            } else {
                val errors = result.errorStream.bufferedReader().readText()
                throw GradleException("❌ $testName failed (exit $exitCode): $errors")
            }
        }
        
        outputs.files.singleFile.writeText("Docker tests passed at ${Instant.now()}")
        logger.info("All Docker functionality tests passed")
    }
}

// Lifecycle integration
tasks.assemble {
    dependsOn(buildAll)
}

// Registry management tasks
data class ImageVersion(val id: String, val created: String, val tags: List<String>, val isMasterImage: Boolean)

fun fetchPackageVersions(packageName: String): List<String> {
    return try {
        // Get GitHub token before exec block
        val githubToken = System.getenv("GITHUB_TOKEN")
        
        providers.exec {
            commandLine("gh", "api", "--paginate", "orgs/xtclang/packages/container/$packageName/versions",
                       "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags}")
            
            // Pass GitHub token to subprocess
            if (githubToken != null) {
                environment("GITHUB_TOKEN", githubToken)
            }
        }.standardOutput.asText.get().trim().split("\n").filter { it.isNotEmpty() }
    } catch (e: Exception) {
        logger.warn("Failed to fetch package versions: ${e.message}")
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
        
        val isMasterImage = tags.any { tag ->
            tag == "latest" || tag == "master" || tag.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) || tag.matches(Regex("latest-\\w+"))
        }
        
        ImageVersion(id, created, tags, isMasterImage)
    } catch (_: Exception) {
        null
    }
}

val listImages by tasks.registering {
    group = "docker"
    description = "List Docker images in registry"
    
    doLast {
        logger.lifecycle("🐳 Docker Images Summary")
        logger.lifecycle("=".repeat(50))
        
        val packages = try {
            providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name")
                
                // Ensure GitHub token is available to the subprocess
                val githubToken = System.getenv("GITHUB_TOKEN") 
                    ?: try { providers.exec { commandLine("gh", "auth", "token") }.standardOutput.asText.get().trim() } catch (e: Exception) { null }
                
                if (githubToken != null) {
                    environment("GITHUB_TOKEN", githubToken)
                }
            }.standardOutput.asText.get().trim().split("\n").map { it.removeSurrounding("\"") }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.lifecycle("❌ Error: ${e.message}")
            return@doLast
        }
        
        logger.lifecycle("Found ${packages.size} container packages in registry")
        
        packages.forEach { pkg ->
            logger.lifecycle("\n📦 Package: $pkg")
            logger.lifecycle("   Registry: ghcr.io/xtclang/$pkg")
            
            val versions = fetchPackageVersions(pkg)
            if (versions.isEmpty()) {
                logger.lifecycle("  ❌ Could not get versions for $pkg")
                return@forEach
            }
            
            val parsedVersions = versions.mapNotNull { parseImageVersion(it) }
            logger.lifecycle("   Total versions: ${parsedVersions.size}")
            logger.lifecycle("   📋 Recent versions:")
            
            parsedVersions.take(10).forEachIndexed { i, version ->
                logger.lifecycle("     ${i+1}. [${version.created}] tags: ${version.tags}")
            }
            
            if (parsedVersions.size > 10) {
                logger.lifecycle("     ... and ${parsedVersions.size - 10} more versions")
            }
        }
    }
}

val cleanImages by tasks.registering {
    group = "docker"
    description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"
    
    // Capture values during configuration phase to avoid deprecation warnings
    val capturedKeepCount = project.findProperty("keepCount")?.toString()?.toIntOrNull() ?: 10
    val capturedIsDryRun = project.findProperty("dryRun")?.toString()?.toBoolean() ?: false
    val capturedIsForced = project.findProperty("force")?.toString()?.toBoolean() ?: false
    
    doLast {
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val keepCount = capturedKeepCount
        val packageName = "xvm"
        val isDryRun = capturedIsDryRun
        
        val isForced = capturedIsForced
        
        if (isDryRun) logger.lifecycle("🔍 DRY RUN MODE")
        
        val versions = fetchPackageVersions(packageName)
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
        
        var deleted = 0
        val failures = mutableListOf<String>()
        
        // Get GitHub token once for all deletions
        val githubToken = System.getenv("GITHUB_TOKEN")
        
        toDelete.forEach { version ->
            try {
                providers.exec {
                    commandLine("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName/versions/${version.id}")
                    isIgnoreExitValue = false // Ensure we catch non-zero exit codes
                    
                    // Pass GitHub token to subprocess
                    if (githubToken != null) {
                        environment("GITHUB_TOKEN", githubToken)
                    }
                }
                
                // If we get here, the command succeeded
                deleted++
                logger.lifecycle("✅ Deleted version ${version.id} (tags: ${version.tags})")
                
            } catch (e: Exception) {
                val errorMsg = "Failed to delete version ${version.id} (tags: ${version.tags}): ${e.message}"
                logger.warn("❌ $errorMsg")
                failures.add(errorMsg)
            }
        }
        
        logger.lifecycle("🎯 Attempted deletions: $deleted/${toDelete.size} versions")
        
        // Log failures but don't fail immediately - let verification determine success
        if (failures.isNotEmpty()) {
            logger.lifecycle("⚠️  Some deletion commands failed:")
            failures.forEach { logger.lifecycle("   $it") }
            logger.lifecycle("🔍 Will verify actual deletions via API to determine success...")
        }
        
        // Verify deletions with retry logic for API delays
        logger.lifecycle("🔍 Verifying deletions...")
        var actuallyDeleted = 0
        val maxRetries = 3
        val retryDelayMs = 5000L // 5 seconds
        
        for (attempt in 1..maxRetries) {
            Thread.sleep(if (attempt > 1) retryDelayMs else 1000L) // Initial 1s delay, then 5s
            
            val remainingVersions = fetchPackageVersions(packageName)
            val remainingIds = remainingVersions.mapNotNull { parseImageVersion(it)?.id }.toSet()
            actuallyDeleted = toDelete.count { it.id !in remainingIds }
            
            logger.lifecycle("📊 Attempt $attempt: $actuallyDeleted/${toDelete.size} deletions confirmed")
            
            if (actuallyDeleted == toDelete.size) {
                logger.lifecycle("🎉 All deletions verified! Final count: ${remainingVersions.size} versions remaining")
                break
            }
            
            if (attempt < maxRetries) {
                logger.lifecycle("⏳ Waiting ${retryDelayMs/1000}s for API consistency...")
            }
        }
        
        if (actuallyDeleted != toDelete.size) {
            logger.warn("""
                
                ⚠️${"=".repeat(70)}
                ⚠️  DOCKER PACKAGE CLEANUP WARNING
                ⚠️${"=".repeat(70)}
                ⚠️  Package cleanup incomplete: $actuallyDeleted/${toDelete.size} deletions confirmed after $maxRetries attempts
                ⚠️
                ⚠️  Possible causes:
                ⚠️    • GitHub API delays (very common - may resolve on next build)
                ⚠️    • Permission issues (GITHUB_TOKEN lacks delete:packages scope)
                ⚠️    • Network/connectivity issues
                ⚠️    • Package registry temporarily unavailable
                ⚠️
                ⚠️  Impact: Old package versions were not cleaned up
                ⚠️  Action: Check package registry manually or retry next build
                ⚠️${"=".repeat(70)}
                
                ✅ Continuing build - package cleanup is not critical for build success
            """.trimIndent())
        }
    }
}

