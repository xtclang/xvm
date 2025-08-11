/**
 * Comprehensive Docker build script for XVM project.
 * Supports multi-platform builds, manifest creation, registry management, and caching.
 * All git commands and configuration creation deferred to execution phase only.
 */

import java.net.URLEncoder
import java.time.Instant

plugins {
    // NOTE: We add base here to get lifecycle tasks.
    base
    id("org.xtclang.build.xdk.versioning")
}



// Build configuration data class
data class BuildConfig(
    val version: String,
    val branch: String, 
    val commit: String,
    val baseImage: String,
    val branchTagName: String,
    val buildArgs: Map<String, String>,
    val metadataLabels: Map<String, String>,
    val isCI: Boolean
) {
    
    // Computed properties to avoid branchy logic
    val isMasterBranch = branch == "master"
    val tagPrefix = if (isMasterBranch) "latest" else branchTagName
    val versionTags = if (isMasterBranch) listOf(version) else emptyList()
    
    fun tagsForArch(arch: String): List<String> {
        return (listOf("${tagPrefix}-$arch") + versionTags.map { "${it}-$arch" } + listOf("${commit}-$arch"))
            .map { "${baseImage}:${it}" }
    }
    
    fun multiPlatformTags(): List<String> {
        return (listOf(tagPrefix) + versionTags + listOf(commit))
            .map { "${baseImage}:${it}" }
    }
    
    fun cacheArgs(arch: String? = null) = if (isCI) {
        val scope = arch?.let { ",scope=$it" } ?: ""
        listOf("--cache-from", "type=gha$scope", "--cache-to", "type=gha,mode=max$scope")
    } else {
        val archSuffix = arch?.let { "-$it" } ?: ""
        val cacheDir = File(System.getProperty("user.home"), ".cache/docker-buildx$archSuffix")
        cacheDir.mkdirs()
        listOf("--cache-from", "type=local,src=${cacheDir.absolutePath}", "--cache-to", "type=local,dest=${cacheDir.absolutePath},mode=max")
    }
}

// Factory function that can be called from doFirst blocks safely
fun createBuildConfig(): BuildConfig {
    val startTime = System.currentTimeMillis()
    logger.lifecycle("🔍 [DOCKER-DEBUG] createBuildConfig() called - starting configuration")
    
    val version = project.version.toString()
    
    // Get branch - prefer GH_BRANCH, fallback to git command only if needed
    val branch = System.getenv("GH_BRANCH") ?: try {
        providers.exec {
            commandLine("git", "branch", "--show-current")
            workingDir = project.rootDir
        }.standardOutput.asText.get().trim().ifBlank { "unknown" }
    } catch (e: Exception) {
        logger.warn("Failed to get git branch: ${e.message}")
        "unknown"
    }
    
    // Get commit - prefer GH_COMMIT, fallback to GitHub API to get remote commit
    // Always ensure we have a resolved commit (never empty for tagging consistency)  
    val commit = System.getenv("GH_COMMIT")?.takeIf { it.isNotBlank() } ?: try {
        // Query GitHub API to get the latest commit on the remote branch
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val apiUrl = "https://api.github.com/repos/xtclang/xvm/commits/$encodedBranch"
        val curlResult = providers.exec {
            commandLine("curl", "-fsSL", apiUrl)
            workingDir = project.rootDir
        }.standardOutput.asText.get()
        
        // Extract SHA from JSON response (simple regex extraction)
        val shaRegex = """"sha":\s*"([a-f0-9]{40})"""".toRegex()
        val matchResult = shaRegex.find(curlResult)
        matchResult?.groupValues?.get(1) ?: "unknown"
    } catch (e: Exception) {
        logger.warn("Failed to get remote commit from GitHub API: ${e.message}")
        "unknown"
    }
    
    val baseImage = "ghcr.io/xtclang/xvm"
    val branchTagName = branch.substringAfterLast("/").replace(Regex("[^a-zA-Z0-9._-]"), "_")
    
    // Only build args that actually affect the build process
    val buildArgs = mapOf(
        "GH_BRANCH" to branch,
        "GH_COMMIT" to commit
    )
    
    // Metadata that will be added as Docker labels (doesn't invalidate cache)
    val metadataLabels = mapOf(
        "org.opencontainers.image.created" to Instant.now().toString(),
        "org.opencontainers.image.revision" to commit,
        "org.opencontainers.image.version" to version,  
        "org.opencontainers.image.source" to "https://github.com/xtclang/xvm/tree/$branch"
    )
    val isCI = System.getenv("CI") == "true"
    
    val duration = System.currentTimeMillis() - startTime
    logger.lifecycle("🔍 [DOCKER-DEBUG] createBuildConfig() completed in ${duration}ms total")
    
    return BuildConfig(version, branch, commit, baseImage, branchTagName, buildArgs, metadataLabels, isCI)
}

// Helper function to show comprehensive build lifecycle information
fun logLifecycleInfo(arch: String, config: BuildConfig) {
    val buildTime = Instant.now()
    val hostArch = System.getProperty("os.arch")
    val hostOS = System.getProperty("os.name")
    val buildMode = if (config.isCI) "CI/CD Pipeline" else "Local Development"
    val buildMethod = if (config.isCI) "Pre-built artifacts (fast)" else "Tarball source (ultra-fast)"
    val modeDetails = if (config.isCI) {
        "⚡ CI Mode: Using pre-built XDK artifacts from build-verify job"
    } else {
        "⚡ Local Mode: GitHub tarball download + source build (no git operations)"
    }
    
    logger.lifecycle("""

🚀 ═══════════════════════════════════════════════════════════════════════
🚀 DOCKER BUILD LIFECYCLE - ${arch.uppercase()} Container
🚀 ═══════════════════════════════════════════════════════════════════════
📍 Source Information:
   • Branch: ${config.branch}
   • Commit: ${config.commit} (${config.commit.take(8)})
   • Version: ${config.version}
   • Repository: https://github.com/xtclang/xvm

🏗️ Build Environment:
   • Build Type: $buildMode
   • Build Time: $buildTime
   • Host OS: $hostOS
   • Host Architecture: $hostArch
   • Target Platform: linux/$arch
   • Project Root: ${project.rootDir}

🐳 Docker Configuration:
   • Build Method: $buildMethod
   • Base Image: ${config.baseImage}
   • Gradle Version: ${gradle.gradleVersion}
   • Java Version: ${System.getProperty("java.version")}

$modeDetails
🚀 ═══════════════════════════════════════════════════════════════════════
""".trimIndent())
}

// Common Docker command builder
fun buildDockerCommand(
    platforms: List<String>,
    config: BuildConfig, 
    cacheArgs: List<String>,
    tags: List<String>,
    action: String // "load" or "push"
): List<String> {
    // Use plain progress for better cache visibility, can be overridden with DOCKER_BUILDX_PROGRESS
    val progressMode = System.getenv("DOCKER_BUILDX_PROGRESS") ?: "plain"
    
    return listOf("docker", "buildx", "build", "--platform", platforms.joinToString(",")) +
           listOf("--progress=$progressMode") +
           config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
           config.metadataLabels.flatMap { listOf("--label", "${it.key}=${it.value}") } +
           cacheArgs +
           tags.flatMap { listOf("--tag", it) } +
           listOf("--$action", ".")
}

// Common Docker task execution logic
fun executeDockerTask(arch: String, platforms: List<String>, config: BuildConfig, action: String) {
    logLifecycleInfo(arch, config)
    
    val tags = if (platforms.size == 1) config.tagsForArch(arch) else config.multiPlatformTags()
    val cacheArgs = if (platforms.size == 1) config.cacheArgs(arch) else config.cacheArgs()
    
    val cmd = buildDockerCommand(platforms, config, cacheArgs, tags, action)
    
    logger.lifecycle("🔍 [DOCKER-DEBUG] Executing: ${cmd.joinToString(" ")}")
    logger.lifecycle("🔍 [DOCKER-DEBUG] Tags: ${tags.joinToString(", ")}")
    logger.lifecycle("🔍 [DOCKER-DEBUG] Build args: ${config.buildArgs}")
    logger.lifecycle("🔍 [DOCKER-DEBUG] Cache args: $cacheArgs")
    
    ProcessBuilder(cmd)
        .directory(projectDir)
        .inheritIO()
        .start()
        .waitFor()
        .let { exitCode ->
            if (exitCode != 0) {
                throw GradleException("Docker command failed with exit code: $exitCode")
            }
        }
}

// Simplified platform-specific build task factory
fun createPlatformBuildTask(arch: String, platform: String) = tasks.registering {
    group = "docker"
    description = "Build Docker image for $arch ($platform)"
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting $arch build task execution")
        
        // Cross-platform build protection
        val hostArch = System.getProperty("os.arch").let { osArch ->
            when (osArch) {
                "x86_64", "amd64" -> "amd64"
                "aarch64", "arm64" -> "arm64"
                else -> osArch
            }
        }
        
        val allowEmulation = project.findProperty("docker_emulated_build")?.toString()?.toBoolean() ?: false
        
        if (arch != hostArch && !allowEmulation) {
            logger.warn("")
            logger.warn("⚠️  SKIPPING CROSS-PLATFORM BUILD")
            logger.warn("⚠️  Task: $name")
            logger.warn("⚠️  Requested: $arch ($platform)")
            logger.warn("⚠️  Host Architecture: $hostArch") 
            logger.warn("⚠️  Reason: Cross-platform Docker builds are slow and emulated")
            logger.warn("⚠️  To override: -Pdocker_emulated_build=true")
            logger.warn("⚠️  Recommended: Use docker:build$hostArch or docker:buildMultiPlatform")
            logger.warn("")
            return@doLast
        }
        
        val config = createBuildConfig()
        executeDockerTask(arch, listOf(platform), config, "load")
    }
}

// Simplified platform-specific push task factory  
fun createPlatformPushTask(arch: String, buildTask: TaskProvider<Task>) = tasks.registering {
    group = "docker"
    description = "Push $arch Docker image to registry"
    dependsOn(buildTask)
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting $arch push task execution")
        val config = createBuildConfig()
        val tags = config.tagsForArch(arch)
        
        // Push all tags for this architecture
        tags.forEach { tag ->
            val pushCmd = listOf("docker", "push", tag)
            logger.lifecycle("🔍 [DOCKER-DEBUG] Executing: ${pushCmd.joinToString(" ")}")
            
            ProcessBuilder(pushCmd)
                .inheritIO()
                .start()
                .waitFor()
                .let { exitCode ->
                    if (exitCode != 0) {
                        throw GradleException("Docker push failed with exit code: $exitCode")
                    }
                }
        }
    }
}

//
// CORE BUILD TASKS
//

val buildAmd64 by createPlatformBuildTask("amd64", "linux/amd64")
val buildArm64 by createPlatformBuildTask("arm64", "linux/arm64")

// Docker functionality test for current platform (runs after build)
val testDockerFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality on current platform (fast cached version)"
    
    // Depend on the current platform's build
    val hostArch = System.getProperty("os.arch").let { osArch ->
        when (osArch) {
            "x86_64", "amd64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            else -> osArch
        }
    }
    
    dependsOn(if (hostArch == "amd64") buildAmd64 else buildArm64)
    
    doLast {
        // Run the comprehensive functionality test
        project.tasks.getByName("testDockerImageFunctionality").actions.forEach { action ->
            action.execute(project.tasks.getByName("testDockerImageFunctionality"))
        }
    }
}

val buildMultiPlatform by tasks.registering {
    group = "docker"
    description = "Build multi-platform Docker images locally"
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform build task execution")
        val config = createBuildConfig()
        executeDockerTask("multi-platform", listOf("linux/amd64", "linux/arm64"), config, "load")
    }
}

// Integrate with standard Gradle lifecycle
val assemble by tasks.existing {
    dependsOn(buildMultiPlatform)
    description = "Build multi-platform Docker images"
}

//
// CORE PUSH TASKS
//

val pushAmd64 by createPlatformPushTask("amd64", buildAmd64)
val pushArm64 by createPlatformPushTask("arm64", buildArm64)

val push by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform push task execution")
        val config = createBuildConfig()
        executeDockerTask("multi-platform", listOf("linux/amd64", "linux/arm64"), config, "push")
    }
}

// Main push task for multi-platform Docker images

//
// MANIFEST TASKS
//

val createManifest by tasks.registering {
    group = "docker"
    description = "Create multi-platform manifest (builds both architectures locally)"
    dependsOn(buildAmd64, buildArm64)
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting manifest creation task execution")
        val config = createBuildConfig()
        
        logger.lifecycle("✅ Built both architecture images locally:")
        logger.lifecycle("  📦 ${config.baseImage}:${config.tagPrefix}-amd64")
        logger.lifecycle("  📦 ${config.baseImage}:${config.tagPrefix}-arm64")
        logger.lifecycle("💡 To create registry manifest, push images first then use pushManifest task")
    }
}

val pushManifest by tasks.registering(Exec::class) {
    group = "docker"
    description = "Push multi-platform manifest"
    dependsOn(createManifest)
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting push manifest task execution")
        val config = createBuildConfig()
        commandLine("docker", "manifest", "push", "${config.baseImage}:${config.tagPrefix}")
    }
    
    workingDir = projectDir
}

//
// MANAGEMENT TASKS  
//

val listImages by tasks.registering {
    group = "docker"
    description = "List all Docker images with detailed tags, labels, and metadata"
    
    doLast {
        println("🐳 Docker Images Summary")
        println("=".repeat(50))
        
        val packages = try {
            providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name")
            }.standardOutput.asText.get().trim().split("\n").map { it.removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            println("💡 Run: gh auth refresh --hostname github.com --scopes read:packages")
            return@doLast
        }
        
        println("Found ${packages.size} container packages in registry")
        
        packages.forEach { pkg ->
            println("\n📦 Package: $pkg")
            println("   Registry: ghcr.io/xtclang/$pkg")
            
            val versions = try {
                val result = providers.exec {
                    commandLine("gh", "api", "orgs/xtclang/packages/container/$pkg/versions", 
                               "--jq", ".[] | {id: .id, tags: .metadata.container.tags, created: .created_at, size: .size}")
                }
                result.standardOutput.asText.get().trim().split("\n")
            } catch (e: Exception) {
                val errorOutput = try {
                    providers.exec {
                        commandLine("gh", "api", "orgs/xtclang/packages/container/$pkg/versions")
                        isIgnoreExitValue = true
                    }.standardError.asText.get()
                } catch (ex: Exception) {
                    "Unable to get detailed error"
                }
                println("  ❌ Could not get versions for $pkg")
                println("     Error: ${e.message}")
                println("     Details: $errorOutput")
                return@forEach
            }
            
            println("   Total versions: ${versions.size}")
            
            // Parse and calculate total size
            var totalSize = 0L
            var sizedVersionsCount = 0
            
            val parsedVersions = versions.mapNotNull { versionJson ->
                try {
                    val created = versionJson.substringAfter("\"created\":\"").substringBefore("\"")
                    val tagsSection = versionJson.substringAfter("\"tags\":[").substringBefore("]")
                    val tags = if (tagsSection.isBlank()) emptyList() else {
                        tagsSection.split(",").map { it.trim().removeSurrounding("\"") }
                    }
                    
                    // Extract size (might be null)
                    val sizeStr = versionJson.substringAfter("\"size\":").substringBefore("}")
                        .replace("null", "0").trim()
                    val size = try {
                        sizeStr.toLong()
                    } catch (e: Exception) {
                        0L
                    }
                    
                    if (size > 0) {
                        totalSize += size
                        sizedVersionsCount++
                    }
                    
                    Triple(created, tags, size)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (sizedVersionsCount > 0) {
                val totalSizeMB = totalSize / (1024 * 1024)
                println("   💾 Total size: ${totalSizeMB} MB across $sizedVersionsCount versioned images")
            }
            
            println("   📋 Recent versions with tags and sizes:")
            
            parsedVersions.take(10).forEachIndexed { i, (created, tags, size) ->
                val sizeStr = if (size > 0) {
                    val sizeMB = size / (1024 * 1024)
                    " (${sizeMB} MB)"
                } else {
                    " (size unknown)"
                }
                println("     ${i+1}. [${created}] tags: $tags$sizeStr")
            }
            
            if (parsedVersions.size > 10) {
                println("     ... and ${parsedVersions.size - 10} more versions")
            }
        }
    }
}

// Data class for image version information
data class ImageVersion(val id: String, val created: String, val tags: List<String>, val isMasterImage: Boolean, val json: String)

val cleanupVersions by tasks.registering {
    group = "docker"
    description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"
    // Don't depend on listImages to avoid caching - always fetch fresh data
    
    doLast {
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val keepCount = project.findProperty("keepCount")?.toString()?.toIntOrNull() ?: 10
        val packageName = "xvm"
        val isDryRun = project.findProperty("dryRun")?.toString()?.toBoolean() ?: false
        val isForced = project.findProperty("force")?.toString()?.toBoolean() ?: false
        
        if (isDryRun) {
            logger.lifecycle("🔍 DRY RUN MODE - No actual deletions will be performed")
            logger.lifecycle("")
        }
        
        // Get all versions sorted by creation date (newest first)  
        val versions = try {
            val output = providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages/container/$packageName/versions", 
                           "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags}")
            }.standardOutput.asText.get().trim()
            
            if (output.isEmpty()) {
                logger.lifecycle("❌ No versions found for package: $packageName")
                return@doLast
            }
            
            output.split("\n")
        } catch (e: Exception) {
            logger.error("❌ Error getting versions: ${e.message}")
            logger.warn("💡 Run: gh auth refresh --hostname github.com --scopes read:packages,delete:packages")
            return@doLast
        }
        
        // Parse versions and identify master/official images
        val parsedVersions = versions.mapNotNull { versionJson ->
            try {
                val id = versionJson.substringAfter("\"id\":").substringBefore(",").trim()
                val created = versionJson.substringAfter("\"created\":\"").substringBefore("\"")
                val tagsSection = versionJson.substringAfter("\"tags\":[").substringBefore("]")
                val tags = if (tagsSection.isBlank()) emptyList() else {
                    tagsSection.split(",").map { it.trim().removeSurrounding("\"") }
                }
                
                // Identify master/official images: those with "latest", version numbers, or "master" tags
                val isMasterImage = tags.any { tag -> 
                    tag == "latest" || 
                    tag == "master" ||
                    tag.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) || // version pattern
                    tag.matches(Regex("latest-\\w+")) // latest-amd64, latest-arm64
                }
                
                ImageVersion(id, created, tags, isMasterImage, versionJson)
            } catch (e: Exception) {
                logger.warn("⚠️  Could not parse version: $versionJson")
                null
            }
        }.sortedByDescending { it.created } // Sort by creation date (newest first)
        
        logger.lifecycle("📦 Package: $packageName")
        logger.lifecycle("📊 Total versions: ${parsedVersions.size}")
        
        // Separate master images from branch images
        val masterImages = parsedVersions.filter { it.isMasterImage }
        val branchImages = parsedVersions.filter { !it.isMasterImage }
        
        logger.lifecycle("🎯 Master/Official images: ${masterImages.size}")
        logger.lifecycle("🌿 Branch images: ${branchImages.size}")
        logger.lifecycle("")
        
        // Determine what to keep and delete
        // RULE 1: Always keep at least one master image (the most recent)
        val masterToKeep = if (masterImages.isNotEmpty()) listOf(masterImages.first()) else emptyList()
        
        // RULE 2: Keep up to keepCount total recent images, but prioritize protecting master images
        val allToKeep = mutableSetOf<ImageVersion>()
        allToKeep.addAll(masterToKeep) // Always keep most recent master
        
        // Fill remaining slots with most recent images (master + branch)
        val remaining = parsedVersions.take(keepCount)
        allToKeep.addAll(remaining)
        
        val finalKeep = allToKeep.toList().sortedByDescending { it.created }
        val toDelete = parsedVersions.filter { it !in finalKeep }
        
        // Additional safety check: never delete ALL master images
        val masterInDeleteList = toDelete.filter { it.isMasterImage }
        val masterInKeepList = finalKeep.filter { it.isMasterImage }
        
        if (masterInKeepList.isEmpty() && masterImages.isNotEmpty()) {
            logger.error("❌ SAFETY CHECK FAILED: Would delete all master images!")
            logger.error("❌ This is not allowed. At least one master image must remain.")
            return@doLast
        }
        
        logger.lifecycle("✅ Keeping these ${finalKeep.size} versions:")
        finalKeep.forEachIndexed { i, version ->
            val typeMarker = if (version.isMasterImage) "🏷️ MASTER" else "🌿 BRANCH"
            logger.lifecycle("  ${i+1}. $typeMarker [${version.created}] tags: ${version.tags}")
        }
        logger.lifecycle("")
        
        if (toDelete.isEmpty()) {
            logger.lifecycle("✅ No cleanup needed - already at optimal state")
            return@doLast
        }
        
        logger.lifecycle("🗑️  Would delete these ${toDelete.size} versions:")
        toDelete.forEachIndexed { i, version ->
            val typeMarker = if (version.isMasterImage) "🏷️ MASTER" else "🌿 BRANCH"
            logger.lifecycle("  ${i+1}. $typeMarker [${version.created}] tags: ${version.tags}")
        }
        logger.lifecycle("")
        
        // Safety summary
        logger.lifecycle("🛡️  Safety Summary:")
        logger.lifecycle("  📦 Master images to keep: ${masterInKeepList.size}")
        logger.lifecycle("  🗑️  Master images to delete: ${masterInDeleteList.size}")
        logger.lifecycle("  🌿 Branch images to delete: ${toDelete.size - masterInDeleteList.size}")
        logger.lifecycle("")
        
        if (isDryRun) {
            logger.lifecycle("🔍 DRY RUN COMPLETE - No changes made")
            logger.lifecycle("💡 Remove -PdryRun to perform actual cleanup")
            return@doLast
        }
        
        // Confirmation logic
        val needsConfirmation = System.getenv("CI") != "true" && !isForced
        
        if (needsConfirmation) {
            logger.warn("⚠️  This will permanently delete ${toDelete.size} package versions!")
            logger.lifecycle("")
            logger.lifecycle("📋 Deletion Plan:")
            toDelete.forEachIndexed { i, version ->
                val typeMarker = if (version.isMasterImage) "🏷️ MASTER" else "🌿 BRANCH" 
                logger.lifecycle("  ${i+1}. DELETE $typeMarker [${version.created}] tags: ${version.tags}")
            }
            logger.lifecycle("")
            logger.lifecycle("❓ Do you want to proceed with deleting these ${toDelete.size} versions?")
            logger.lifecycle("💡 Options:")
            logger.lifecycle("   • Add -Pforce=true to skip this prompt")
            logger.lifecycle("   • Add -PdryRun=true to preview without changes")
            logger.lifecycle("   • Press Ctrl+C to cancel")
            logger.lifecycle("")
            
            print("Type 'yes' to confirm deletion: ")
            val response = readLine()?.trim()?.lowercase()
            
            if (response != "yes") {
                logger.error("❌ Cancelled - user did not confirm deletion")
                return@doLast
            }
            
            logger.lifecycle("✅ User confirmed deletion, proceeding...")
        } else if (isForced) {
            logger.lifecycle("⚡ FORCED MODE - Skipping confirmation prompt")
        } else {
            logger.lifecycle("🤖 CI MODE - Proceeding automatically")
        }
        
        // Delete old versions
        var deletedCount = 0
        var failedCount = 0
        
        toDelete.forEach { version ->
            try {
                providers.exec {
                    commandLine("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName/versions/${version.id}")
                }
                
                deletedCount++
                val typeMarker = if (version.isMasterImage) "🏷️ MASTER" else "🌿 BRANCH"
                logger.lifecycle("✅ Deleted $typeMarker version ID: ${version.id} (tags: ${version.tags})")
                
            } catch (e: Exception) {
                failedCount++
                logger.error("❌ Failed to delete version ${version.id}: ${e.message}")
            }
        }
        
        logger.lifecycle("")
        logger.lifecycle("🔍 Verifying deletions by fetching fresh data from registry...")
        
        // Fetch fresh data to verify deletions
        val verificationVersions = try {
            val output = providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages/container/$packageName/versions", 
                           "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags}")
            }.standardOutput.asText.get().trim()
            
            if (output.isEmpty()) {
                emptyList()
            } else {
                output.split("\n")
            }
        } catch (e: Exception) {
            logger.warn("⚠️  Could not verify deletions: ${e.message}")
            emptyList()
        }
        
        val remainingIds = verificationVersions.mapNotNull { versionJson ->
            try {
                versionJson.substringAfter("\"id\":").substringBefore(",").trim()
            } catch (e: Exception) {
                null
            }
        }.toSet()
        
        // Check which deletions actually took effect
        val actuallyDeleted = toDelete.filter { it.id !in remainingIds }
        val stillPresent = toDelete.filter { it.id in remainingIds }
        
        logger.lifecycle("🎯 Cleanup Summary:")
        logger.lifecycle("  ✅ Successfully deleted: ${actuallyDeleted.size} versions")  
        logger.lifecycle("  ❌ Failed deletions: ${stillPresent.size} versions")
        logger.lifecycle("  📦 Total remaining in registry: ${verificationVersions.size} versions")
        logger.lifecycle("  🛡️  Master images protected: ${masterInKeepList.size}")
        
        if (stillPresent.isNotEmpty()) {
            logger.warn("⚠️  These versions are still present after deletion attempt:")
            stillPresent.forEach { version ->
                val typeMarker = if (version.isMasterImage) "🏷️ MASTER" else "🌿 BRANCH"
                logger.warn("    $typeMarker ID: ${version.id} (tags: ${version.tags})")
            }
            logger.warn("💡 GitHub API may have delays, or there were permission issues")
        }
        
        if (actuallyDeleted.size == toDelete.size) {
            logger.lifecycle("🎉 All requested deletions completed successfully!")
        }
    }
}


// Docker build test with local artifacts
val testDockerWithLocalArtifacts by tasks.registering {
    group = "docker"
    description = "Test Docker build using local XDK distribution as artifacts"
    
    doLast {
        logger.lifecycle("🧪 Testing Docker build with local artifacts...")
        
        // First ensure we have the XDK built with distribution ZIP
        logger.lifecycle("📋 Building XDK platform-agnostic distribution ZIP...")
        providers.exec {
            commandLine("../gradlew", "xdk:distZip")
            workingDir = projectDir
        }
        
        // Find the built distribution ZIP
        val xdkBuildDir = file("../xdk/build")
        if (!xdkBuildDir.exists()) {
            throw GradleException("XDK build directory not found after build.")
        }
        
        val distZipFile = fileTree("../xdk/build/distributions") {
            include("xdk-*.zip")
            exclude("*-linux_*.zip")  // Use platform-agnostic ZIP
            exclude("*-macos_*.zip")  // Use platform-agnostic ZIP
            exclude("*-windows_*.zip")  // Use platform-agnostic ZIP
        }.singleFile
        
        logger.lifecycle("📋 Using distribution ZIP: ${distZipFile.name}")
        
        // Copy the distribution ZIP to the Docker build context  
        copy {
            from(distZipFile)
            into(".")
            rename { "ci-dist.zip" }
        }
        
        logger.lifecycle("📋 Copied distribution ZIP to Docker context: ci-dist.zip")
        
        // Get host architecture for native build
        val hostArch = System.getProperty("os.arch").let { osArch ->
            when (osArch) {
                "x86_64", "amd64" -> "amd64"
                "aarch64", "arm64" -> "arm64"
                else -> osArch
            }
        }
        
        val platform = "linux/$hostArch"
        val testTag = "test-xvm-artifacts:latest"
        
        logger.lifecycle("🐳 Building Docker image with pre-built artifacts...")
        logger.lifecycle("  Platform: $platform")
        logger.lifecycle("  Tag: $testTag")
        
        val config = createBuildConfig()
        
        // Build Docker image with artifacts
        val cmd = listOf(
            "docker", "buildx", "build",
            "--platform", platform,
            "--progress=plain",
            "--build-arg", "USE_PREBUILT_ARTIFACTS=true",
            "--build-arg", "GH_BRANCH=${config.branch}",
            "--build-arg", "GH_COMMIT=${config.commit}",
            "--tag", testTag,
            "--load",
            "."
        )
        
        logger.lifecycle("🔍 Executing: ${cmd.joinToString(" ")}")
        
        val result = ProcessBuilder(cmd)
            .directory(projectDir)
            .inheritIO()
            .start()
            .waitFor()
            
        if (result != 0) {
            throw GradleException("Docker build with artifacts failed with exit code: $result")
        }
        
        logger.lifecycle("✅ Docker build with local artifacts succeeded!")
        
        // Test the built image
        logger.lifecycle("🧪 Testing built Docker image...")
        
        val testCommands = listOf(
            listOf("docker", "run", "--rm", testTag, "xec", "--version"),
            listOf("docker", "run", "--rm", testTag, "xcc", "--version"),
            listOf("docker", "run", "--rm", testTag, "cat", "/opt/xdk/xvm.json")
        )
        
        testCommands.forEach { testCmd ->
            logger.lifecycle("  Testing: ${testCmd.drop(3).joinToString(" ")}")
            val testResult = ProcessBuilder(testCmd)
                .start()
                .waitFor()
            if (testResult != 0) {
                logger.warn("  ⚠️ Test command failed: ${testCmd.joinToString(" ")}")
            } else {
                logger.lifecycle("  ✅ Test passed")
            }
        }
        
        logger.lifecycle("🎉 Docker artifacts test completed!")
        logger.lifecycle("💡 Image tagged as: $testTag")
        logger.lifecycle("💡 Clean up with: docker image rm $testTag")
    }
}

// Extension function to calculate directory size
fun File.directorySize(): Long {
    return if (isDirectory) {
        listFiles()?.sumOf { it.directorySize() } ?: 0L
    } else {
        length()
    }
}

// Docker image functional test - tests the pushed image functionality
val testDockerImageFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality - verifies that the pushed Docker image works correctly"
    
    // Get host architecture to determine which image to test
    val hostArch = System.getProperty("os.arch").let { osArch ->
        when (osArch) {
            "x86_64", "amd64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            else -> osArch
        }
    }
    
    // Only dependency: ensure the Docker image exists (was built)
    dependsOn(if (hostArch == "amd64") buildAmd64 else buildArm64)
    
    // Input: DockerTest.x program (copied into image during build)
    inputs.file("test/DockerTest.x")
    
    // Input: Dockerfile and build scripts that affect the image
    inputs.file("Dockerfile")
    inputs.dir("scripts")
    
    // Output: Test result marker file for up-to-date checking
    val testResultFile = layout.buildDirectory.file("docker-test-results.txt")
    outputs.file(testResultFile)
    
    doLast {
        logger.lifecycle("🧪 Testing Docker image functionality...")
        
        // Get the built image tag from the build configuration
        val config = createBuildConfig()
        val imageTag = config.tagsForArch(hostArch).first()
        
        logger.lifecycle("🐳 Testing Docker image: $imageTag")
        logger.lifecycle("  Platform: linux/$hostArch")
        logger.lifecycle("  Build task: ${if (hostArch == "amd64") "buildAmd64" else "buildArm64"}")
        
        // Define test scenarios that verify the Docker image functionality
        val testScenarios = listOf(
            // Basic launcher tests
            "xec launcher version" to listOf("xec", "--version"),
            "xcc launcher version" to listOf("xcc", "--version"),
            "launcher help functionality" to listOf("xec", "--help"),
            "container environment" to listOf("uname", "-m"),
            
            // Program compilation and execution tests (using the DockerTest.x copied into image)
            "compile and run with no arguments" to listOf("xec", "/opt/xdk/test/DockerTest.x"),
            "compile and run with single argument" to listOf("xec", "/opt/xdk/test/DockerTest.x", "hello"),
            "compile and run with multiple arguments" to listOf("xec", "/opt/xdk/test/DockerTest.x", "arg1", "arg with spaces", "arg3")
        )
        
        // Run functional tests against the built Docker image
        testScenarios.forEach { pair ->
            val (testName, testCmd) = pair
            logger.lifecycle("🧪 Testing: $testName")
            logger.lifecycle("  Command: ${testCmd.joinToString(" ")}")
            
            val dockerCmd = mutableListOf<String>().apply {
                addAll(listOf("docker", "run", "--rm", imageTag))
                addAll(testCmd)
            }
            val testResult = ProcessBuilder(dockerCmd.toList())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val output = testResult.inputStream.bufferedReader().readText()
            val errors = testResult.errorStream.bufferedReader().readText()
            val exitCode = testResult.waitFor()
            
            if (exitCode == 0) {
                logger.lifecycle("  ✅ $testName - PASSED")
                if (output.isNotEmpty()) {
                    logger.lifecycle("    Output: ${output.trim().take(100)}")
                }
                
                // Additional validation for version tests
                if (testName.contains("version") && !output.contains("xdk version")) {
                    logger.error("  ❌ $testName - FAILED (version output missing)")
                    throw GradleException("Docker functional test failed: $testName - no version info")
                }
                
                // Additional validation for help test
                if (testName.contains("help") && !output.contains("Ecstasy runner")) {
                    logger.error("  ❌ $testName - FAILED (help output missing)")
                    throw GradleException("Docker functional test failed: $testName - no help info")
                }
                
                // Additional validation for DockerTest program execution
                when (testName) {
                    "compile and run with no arguments" -> {
                        if (!output.contains("DockerTest invoked with 0 arguments.")) {
                            logger.error("  ❌ $testName - FAILED (no arguments output missing)")
                            throw GradleException("Docker functional test failed: $testName - wrong output")
                        }
                    }
                    "compile and run with single argument" -> {
                        if (!output.contains("DockerTest invoked with 1 arguments:") || !output.contains("[1]=\"hello\"")) {
                            logger.error("  ❌ $testName - FAILED (single argument output missing)")
                            throw GradleException("Docker functional test failed: $testName - wrong output")
                        }
                    }
                    "compile and run with multiple arguments" -> {
                        if (!output.contains("DockerTest invoked with 3 arguments:") || 
                            !output.contains("[1]=\"arg1\"") ||
                            !output.contains("[2]=\"arg with spaces\"") || 
                            !output.contains("[3]=\"arg3\"")) {
                            logger.error("  ❌ $testName - FAILED (multiple arguments output missing)")
                            throw GradleException("Docker functional test failed: $testName - wrong output")
                        }
                    }
                }
            } else {
                logger.error("  ❌ $testName - FAILED (exit code: $exitCode)")
                if (errors.isNotEmpty()) {
                    logger.error("    Error: ${errors.trim()}")
                }
                if (output.isNotEmpty()) {
                    logger.error("    Output: ${output.trim()}")
                }
                throw GradleException("Docker functional test failed: $testName")
            }
        }
        
        logger.lifecycle("🎉 All Docker functional tests passed!")
        logger.lifecycle("💡 Tested image: $imageTag")
        
        // Write test completion marker with timestamp
        testResultFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(buildString {
                appendLine("Docker functionality tests passed at ${Instant.now()}")
                appendLine("Image tested: $imageTag")
                appendLine("Platform: linux/$hostArch")
                appendLine("Host architecture: $hostArch")
            })
        }
    }
}
