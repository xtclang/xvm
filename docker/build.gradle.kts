/**
 * Comprehensive Docker build script for XVM project.
 * Supports multi-platform builds, manifest creation, registry management, and caching.
 * All git commands and configuration creation deferred to execution phase only.
 */

plugins {
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
    
    fun tagsForArch(arch: String): List<String> {
        if (branch == "master") {
            return listOf(
                "${baseImage}:latest-$arch",
                "${baseImage}:${version}-$arch",
                "${baseImage}:${commit}-$arch"
            )
        }
        return listOf(
            "${baseImage}:${branchTagName}-$arch",
            "${baseImage}:${commit}-$arch"
        )
    }
    
    fun multiPlatformTags(): List<String> {
        if (branch == "master") {
            return listOf(
                "${baseImage}:latest",
                "${baseImage}:${version}",
                "${baseImage}:${commit}"
            )
        }
        return listOf(
            "${baseImage}:${branchTagName}",
            "${baseImage}:${commit}"
        )
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
    
    // Get commit - prefer GH_COMMIT, fallback to git command only if needed
    // Always ensure we have a resolved commit (never empty for tagging consistency)  
    val commit = System.getenv("GH_COMMIT")?.takeIf { it.isNotBlank() } ?: try {
        providers.exec {
            commandLine("git", "rev-parse", "HEAD")
            workingDir = project.rootDir
        }.standardOutput.asText.get().trim().ifBlank { "unknown" }
    } catch (e: Exception) {
        logger.warn("Failed to get git commit: ${e.message}")
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
        "org.opencontainers.image.created" to java.time.Instant.now().toString(),
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
    val buildTime = java.time.Instant.now()
    val hostArch = System.getProperty("os.arch")
    val hostOS = System.getProperty("os.name")
    
    logger.lifecycle("")
    logger.lifecycle("🚀 ═══════════════════════════════════════════════════════════════════════")
    logger.lifecycle("🚀 DOCKER BUILD LIFECYCLE - ${arch.uppercase()} Container")
    logger.lifecycle("🚀 ═══════════════════════════════════════════════════════════════════════")
    logger.lifecycle("📍 Source Information:")
    logger.lifecycle("   • Branch: ${config.branch}")
    logger.lifecycle("   • Commit: ${config.commit} (${config.commit.take(8)})")
    logger.lifecycle("   • Version: ${config.version}")
    logger.lifecycle("   • Repository: https://github.com/xtclang/xvm")
    logger.lifecycle("")
    logger.lifecycle("🏗️ Build Environment:")
    logger.lifecycle("   • Build Type: ${if (config.isCI) "CI/CD Pipeline" else "Local Development"}")
    logger.lifecycle("   • Build Time: $buildTime")
    logger.lifecycle("   • Host OS: $hostOS")
    logger.lifecycle("   • Host Architecture: $hostArch")
    logger.lifecycle("   • Target Platform: linux/$arch")
    logger.lifecycle("   • Project Root: ${project.rootDir}")
    logger.lifecycle("")
    logger.lifecycle("🐳 Docker Configuration:")
    logger.lifecycle("   • Build Method: ${if (config.isCI) "Pre-built artifacts (fast)" else "Tarball source (ultra-fast)"}")
    logger.lifecycle("   • Base Image: ${config.baseImage}")
    logger.lifecycle("   • Gradle Version: ${gradle.gradleVersion}")
    logger.lifecycle("   • Java Version: ${System.getProperty("java.version")}")
    logger.lifecycle("")
    if (config.isCI) {
        logger.lifecycle("⚡ CI Mode: Using pre-built XDK artifacts from build-verify job")
    } else {
        logger.lifecycle("⚡ Local Mode: GitHub tarball download + source build (no git operations)")
    }
    logger.lifecycle("🚀 ═══════════════════════════════════════════════════════════════════════")
    logger.lifecycle("")
}

// Common Docker command builder
fun buildDockerCommand(
    platforms: List<String>,
    config: BuildConfig, 
    cacheArgs: List<String>,
    tags: List<String>,
    action: String // "load" or "push"
): List<String> {
    return listOf("docker", "buildx", "build", "--platform", platforms.joinToString(",")) +
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
        .directory(File(System.getProperty("user.dir") + "/docker"))
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
        val config = createBuildConfig()
        executeDockerTask(arch, listOf(platform), config, "load")
    }
}

// Simplified platform-specific push task factory  
fun createPlatformPushTask(arch: String, platform: String, buildTask: TaskProvider<Task>) = tasks.registering {
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

val buildMultiPlatform by tasks.registering {
    group = "docker"
    description = "Build multi-platform Docker images locally"
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform build task execution")
        val config = createBuildConfig()
        executeDockerTask("multi-platform", listOf("linux/amd64", "linux/arm64"), config, "load")
    }
}

// Alias for most common build task
val dockerBuild by tasks.registering {
    group = "docker"
    description = "Build Docker images (alias for buildMultiPlatform)"
    dependsOn(buildMultiPlatform)
}

//
// CORE PUSH TASKS
//

val pushAmd64 by createPlatformPushTask("amd64", "linux/amd64", buildAmd64)
val pushArm64 by createPlatformPushTask("arm64", "linux/arm64", buildArm64)

val pushMultiPlatform by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    
    doLast {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform push task execution")
        val config = createBuildConfig()
        executeDockerTask("multi-platform", listOf("linux/amd64", "linux/arm64"), config, "push")
    }
}

// Alias for most common push task
val dockerPush by tasks.registering {
    group = "docker"
    description = "Build and push Docker images (alias for pushMultiPlatform)"
    dependsOn(pushMultiPlatform)
}

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
        
        println("✅ Built both architecture images locally:")
        if (config.branch == "master") {
            println("  📦 ${config.baseImage}:latest-amd64")
            println("  📦 ${config.baseImage}:latest-arm64")
            println("💡 To create registry manifest, push images first then use pushManifest task")
        } else {
            println("  📦 ${config.baseImage}:${config.branchTagName}-amd64")  
            println("  📦 ${config.baseImage}:${config.branchTagName}-arm64")
            println("💡 To create registry manifest, push images first then use pushManifest task")
        }
    }
}

val pushManifest by tasks.registering(Exec::class) {
    group = "docker"
    description = "Push multi-platform manifest"
    dependsOn(createManifest)
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting push manifest task execution")
        val config = createBuildConfig()
        commandLine("docker", "manifest", "push", "${config.baseImage}:latest")
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
            println("   📋 Recent versions with tags:")
            
            versions.take(10).forEachIndexed { i, version ->
                println("     ${i+1}. $version")
            }
            
            if (versions.size > 10) {
                println("     ... and ${versions.size - 10} more versions")
            }
        }
    }
}

val cleanupVersions by tasks.registering {
    group = "docker"
    description = "Clean up old Docker package versions (keep 5 most recent)"
    dependsOn(listImages)
    
    doLast {
        println("\n🧹 Docker Package Cleanup")
        println("=".repeat(50))
        
        val keepCount = 5
        val packageName = "xvm"
        
        // Get all versions sorted by creation date (newest first)
        val versions = try {
            val output = providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages/container/$packageName/versions", 
                           "--jq", ".[] | {id: .id, created: .created_at, tags: .metadata.container.tags}")
            }.standardOutput.asText.get().trim()
            
            if (output.isEmpty()) {
                println("❌ No versions found for package: $packageName")
                return@doLast
            }
            
            output.split("\n")
        } catch (e: Exception) {
            println("❌ Error getting versions: ${e.message}")
            println("💡 Run: gh auth refresh --hostname github.com --scopes read:packages,delete:packages")
            return@doLast
        }
        
        println("📦 Package: $packageName")
        println("📊 Total versions: ${versions.size}")
        println("🎯 Keeping: $keepCount most recent")
        println("🗑️  Deleting: ${maxOf(0, versions.size - keepCount)} old versions")
        println()
        
        if (versions.size <= keepCount) {
            println("✅ No cleanup needed - already at or below limit")
            return@doLast
        }
        
        // Show what we're keeping
        println("✅ Keeping these versions:")
        versions.take(keepCount).forEachIndexed { i, version ->
            println("  ${i+1}. $version")
        }
        println()
        
        // Show what we're deleting
        val toDelete = versions.drop(keepCount)
        println("🗑️  Deleting these ${toDelete.size} versions:")
        toDelete.forEachIndexed { i, version ->
            println("  ${i+1}. $version")
        }
        println()
        
        // Ask for confirmation (only in interactive mode)
        if (System.getenv("CI") != "true") {
            println("⚠️  This will permanently delete ${toDelete.size} package versions!")
            println("💡 Add -Pconfirm=true to proceed, or run from CI")
            
            if (project.findProperty("confirm") != "true") {
                println("❌ Cancelled - add -Pconfirm=true to actually delete")
                return@doLast
            }
        }
        
        // Delete old versions
        var deletedCount = 0
        var failedCount = 0
        
        toDelete.forEach { versionJson ->
            try {
                // Extract ID from JSON string (simple parsing)
                val id = versionJson.substringAfter("\"id\":").substringBefore(",").trim()
                
                providers.exec {
                    commandLine("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName/versions/$id")
                }
                
                deletedCount++
                println("✅ Deleted version ID: $id")
                
            } catch (e: Exception) {
                failedCount++
                println("❌ Failed to delete version: ${e.message}")
            }
        }
        
        println()
        println("🎯 Cleanup Summary:")
        println("  ✅ Deleted: $deletedCount versions")  
        println("  ❌ Failed: $failedCount versions")
        println("  📦 Remaining: $keepCount versions")
        
        if (failedCount > 0) {
            println("💡 Some deletions failed - check GitHub CLI authentication and permissions")
        }
    }
}

val pruneImages by tasks.registering {
    group = "docker"
    description = "Delete non-master Docker packages from GitHub Container Registry"
    
    doLast {
        println("🧹 Pruning Non-Master Docker Packages")
        println("=".repeat(50))
        
        val packagesToDelete = listOf<String>(
            // Add branch-specific packages here as needed  
            // e.g., "xvm-feature-branch", "xvm-experimental"
        )
        
        if (packagesToDelete.isEmpty()) {
            println("✅ No packages configured for deletion")
            println("💡 Add package names to the packagesToDelete list in build.gradle.kts")
            return@doLast
        }
        
        packagesToDelete.forEach { packageName ->
            println("🗑️  Attempting to delete package: $packageName")
            try {
                providers.exec {
                    commandLine("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName")
                }
                println("✅ Successfully deleted package: $packageName")
            } catch (e: Exception) {
                println("❌ Failed to delete $packageName: ${e.message}")
            }
        }
        
        println("💡 View remaining packages: https://github.com/orgs/xtclang/packages")
    }
}