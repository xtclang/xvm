/**
 * This is a convenience build that supports our CI / local Docker operations
 * as Gradle tasks. They can also be used to diagnose and delete old or broken
 * Docker images from the Github Container Registry.
 *
 * TODO: Note, this file is pretty long and has some redundance and boiler plate,
 *   but we will improve it over time, för example by finding a working Docker plugin
 *   for Gradle, and a Git plugin.
 */

plugins {
    id("org.xtclang.build.xdk.versioning")
}

// Git helpers as functions to defer execution completely until called
fun createGitBranchProvider() = providers.exec {
    commandLine("git", "branch", "--show-current")
    workingDir = project.rootDir
}.standardOutput.asText.map { it.trim().ifBlank { "unknown" } }

fun createGitCommitProvider() = providers.exec {
    commandLine("git", "rev-parse", "HEAD")  
    workingDir = project.rootDir
}.standardOutput.asText.map { it.trim().ifBlank { "unknown" } }

// Safe git functions that work in both configuration and execution phases
fun gitBranch(): String {
    // Try environment variables first (CI environments typically provide these)
    val ciBranch = System.getenv("GH_BRANCH") ?: System.getenv("GITHUB_HEAD_REF") ?: System.getenv("GITHUB_REF_NAME")
    if (!ciBranch.isNullOrBlank()) {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Using CI branch from env: '$ciBranch'")
        return ciBranch
    }
    
    // Fall back to git command only if needed
    try {
        val startTime = System.currentTimeMillis()
        logger.lifecycle("🔍 [DOCKER-DEBUG] gitBranch() falling back to git command")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Current working directory: ${project.rootDir}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Operating system: ${System.getProperty("os.name")}")
        
        val result = createGitBranchProvider().get()
        
        val duration = System.currentTimeMillis() - startTime
        logger.lifecycle("🔍 [DOCKER-DEBUG] gitBranch() completed in ${duration}ms, result: '$result'")
        return result
    } catch (e: Exception) {
        logger.error("❌ [DOCKER-DEBUG] gitBranch() FAILED: ${e.javaClass.simpleName}: ${e.message}")
        logger.error("❌ [DOCKER-DEBUG] Stack trace: ${e.stackTrace.take(5).joinToString("\n")}")
        return "unknown"
    }
}

fun gitCommit(): String {
    // Try environment variables first (CI environments typically provide these)
    val ciCommit = System.getenv("GH_COMMIT") ?: System.getenv("GITHUB_SHA")
    if (!ciCommit.isNullOrBlank()) {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Using CI commit from env: '${ciCommit.take(8)}'")
        return ciCommit
    }
    
    // Fall back to git command only if needed
    try {
        val startTime = System.currentTimeMillis()
        logger.lifecycle("🔍 [DOCKER-DEBUG] gitCommit() falling back to git command")
        
        val result = createGitCommitProvider().get()
        
        val duration = System.currentTimeMillis() - startTime
        logger.lifecycle("🔍 [DOCKER-DEBUG] gitCommit() completed in ${duration}ms, result: '${result.take(8)}'")
        return result
    } catch (e: Exception) {
        logger.error("❌ [DOCKER-DEBUG] gitCommit() FAILED: ${e.javaClass.simpleName}: ${e.message}")
        logger.error("❌ [DOCKER-DEBUG] Stack trace: ${e.stackTrace.take(5).joinToString("\n")}")
        return "unknown"
    }
}

// Extract branch tag from full branch name (everything after last slash, sanitized for Docker)
fun branchTag(branch: String): String {
    val tagName = branch.substringAfterLast("/")
    return tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

// Single image name for all branches
fun baseImageName() = "ghcr.io/xtclang/xvm"

// Command line execution with logging
fun Exec.loggedCommandLine(cmd: List<String>) {
    logger.lifecycle("Executing command line: ${cmd.joinToString(" ")}")
    commandLine(cmd)
}

fun Exec.loggedCommandLine(vararg cmd: String) = loggedCommandLine(cmd.toList())

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
    val branch = gitBranch()
    val commit = gitCommit()
    val baseImage = baseImageName()
    val branchTagName = branchTag(branch)
    
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

// Helper function to create platform-specific Docker build tasks
fun createPlatformBuildTask(arch: String, platform: String) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for $arch ($platform)"
    workingDir = projectDir
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting $arch build task execution")
        val config = createBuildConfig()
        val tags = config.tagsForArch(arch)
        
        val cmd = listOf("docker", "buildx", "build", "--platform", platform) +
                  config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  config.metadataLabels.flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  config.cacheArgs(arch) +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        commandLine(cmd)
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Executing command line: ${cmd.joinToString(" ")}")
        logger.info("Building Docker image for $arch...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
        logger.info("Base image: ${config.baseImage}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Tags: ${tags.joinToString(", ")}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Build args: ${config.buildArgs}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Cache args: ${config.cacheArgs(arch)}")
    }
}

// Helper function to create platform-specific push tasks
fun createPlatformPushTask(arch: String, buildTask: TaskProvider<Exec>) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Push $arch Docker image to registry"
    dependsOn(buildTask)
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting $arch push task execution")
        val config = createBuildConfig()
        val imageTag = "${config.baseImage}:latest-$arch"
        
        commandLine("docker", "push", imageTag)
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Executing: docker push $imageTag")
        logger.info("Pushing $arch Docker image...")
        logger.info("Image: $imageTag")
    }
}

//
// CORE BUILD TASKS
//

val buildAmd64 by createPlatformBuildTask("amd64", "linux/amd64")
val buildArm64 by createPlatformBuildTask("arm64", "linux/arm64")

val buildMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build multi-platform Docker images locally"
    workingDir = projectDir
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform build task execution")
        val config = createBuildConfig()
        val tags = config.multiPlatformTags()
        val cacheArgs = config.cacheArgs()
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        commandLine(cmd)
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Executing command line: ${cmd.joinToString(" ")}")
        logger.info("Building multi-platform Docker images...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Tags: ${tags.joinToString(", ")}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Build args: ${config.buildArgs}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Cache args: $cacheArgs")
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

val pushAmd64 by createPlatformPushTask("amd64", buildAmd64)
val pushArm64 by createPlatformPushTask("arm64", buildArm64)

val pushMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    workingDir = projectDir
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] Starting multi-platform push task execution")
        val config = createBuildConfig()
        val tags = config.multiPlatformTags()
        val cacheArgs = config.cacheArgs()
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--push", ".")
        
        commandLine(cmd)
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Executing command line: ${cmd.joinToString(" ")}")
        logger.info("Building and pushing multi-platform Docker images...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
        logger.info("Environment: ${if (config.isCI) "CI" else "Local"}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Tags: ${tags.joinToString(", ")}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Build args: ${config.buildArgs}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Cache args: $cacheArgs")
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
        
        if (!packages.contains("xvm")) {
            println("\n⚠️  Main 'xvm' package not found")
            return@doLast
        }
        
        println("\n🔍 Detailed Analysis for xvm Package")
        println("=".repeat(50))
        
        // Get manifest for latest tag
        try {
            val manifest = providers.exec {
                commandLine("docker", "manifest", "inspect", "ghcr.io/xtclang/xvm:latest")
            }.standardOutput.asText.get()
            
            println("📄 Multi-platform Manifest for xvm:latest:")
            println(manifest)
            
        } catch (e: Exception) {
            val errorOutput = try {
                providers.exec {
                    commandLine("docker", "manifest", "inspect", "ghcr.io/xtclang/xvm:latest")
                    isIgnoreExitValue = true
                }.standardError.asText.get()
            } catch (ex: Exception) {
                "Unable to get detailed error"
            }
            println("  ❌ Could not inspect manifest for xvm:latest")
            println("     Error: ${e.message}")
            println("     Details: $errorOutput")
        }
        
        // Get detailed image metadata for each architecture (only if they exist locally)
        println("\n🏷️  Local Image Analysis")
        val availableImages = try {
            providers.exec {
                commandLine("docker", "images", "ghcr.io/xtclang/xvm", "--format", "{{.Tag}}")
            }.standardOutput.asText.get().trim().split("\n")
                .filter { it.isNotEmpty() && it != "<none>" }
        } catch (e: Exception) {
            emptyList<String>()
        }
        
        if (availableImages.isEmpty()) {
            println("   ❌ No local images found for ghcr.io/xtclang/xvm")
            println("   💡 Try: docker pull ghcr.io/xtclang/xvm:latest")
            println("   💡 Or build locally: ./gradlew docker:buildMultiPlatform")
        } else {
            println("   Found ${availableImages.size} local images: ${availableImages.joinToString(", ")}")
        }
        
        availableImages.take(3).forEach { tag ->
            println("\n🏷️  Image Details for xvm:$tag")
            try {
                val imageInfo = providers.exec {
                    commandLine("docker", "image", "inspect", "ghcr.io/xtclang/xvm:$tag", 
                               "--format", "{{json .}}")
                }.standardOutput.asText.get()
                
                // Parse key metadata from JSON (simplified parsing)
                val lines = imageInfo.lines()
                val architecture = lines.find { it.contains("\"Architecture\"") }?.substringAfter("\":\"")?.substringBefore("\"")
                val size = lines.find { it.contains("\"Size\"") }?.substringAfter("\":\"")?.substringBefore("\"")
                val created = lines.find { it.contains("\"Created\"") }?.substringAfter("\":\"")?.substringBefore("\"")
                
                println("     Architecture: $architecture")
                println("     Size: $size bytes")
                println("     Created: $created")
                
                // Extract labels
                val labelsStart = imageInfo.indexOf("\"Labels\":")
                if (labelsStart != -1) {
                    val labelsSection = imageInfo.substring(labelsStart).substringBefore("}").substringAfter("{")
                    println("     🏷️  Labels:")
                    labelsSection.split(",").forEach { label ->
                        if (label.contains("\":\"")) {
                            val key = label.substringAfter("\"").substringBefore("\"")
                            val value = label.substringAfter("\":\"").substringBefore("\"")
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                println("       $key: $value")
                            }
                        }
                    }
                }
                
                // Extract environment variables
                val envStart = imageInfo.indexOf("\"Env\":")
                if (envStart != -1) {
                    val envSection = imageInfo.substring(envStart).substringBefore("]").substringAfter("[")
                    println("     🌍 Environment:")
                    envSection.split(",").forEach { env ->
                        val cleanEnv = env.trim().removeSurrounding("\"")
                        if (cleanEnv.contains("=") && !cleanEnv.startsWith("PATH=")) {
                            println("       $cleanEnv")
                        }
                    }
                }
                
            } catch (e: Exception) {
                val errorOutput = try {
                    providers.exec {
                        commandLine("docker", "image", "inspect", "ghcr.io/xtclang/xvm:$tag")
                        isIgnoreExitValue = true
                    }.standardError.asText.get()
                } catch (ex: Exception) {
                    "Unable to get detailed error"
                }
                println("     ❌ Could not inspect image: ghcr.io/xtclang/xvm:$tag")
                println("        Error: ${e.message}")
                println("        Details: $errorOutput")
                println("        💡 Try: docker pull ghcr.io/xtclang/xvm:$tag")
            }
        }
        
        println("\n📊 Summary")
        println("   Single package model: ghcr.io/xtclang/xvm")
        println("   Branch-based tagging for all builds")
        println("   Multi-platform manifests for AMD64 + ARM64")
        println("   Metadata includes: build date, commit SHA, version, source URL")
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