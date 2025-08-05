plugins {
    id("org.xtclang.build.xdk.versioning")
}

// Git helpers to avoid repetition
fun gitBranch() = providers.exec { commandLine("git", "branch", "--show-current") }.standardOutput.asText.get().trim()
fun gitCommit() = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()

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

// Common build configuration factory
fun createBuildConfig(project: Project): BuildConfig {
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
    
    return BuildConfig(version, branch, commit, baseImage, branchTagName, buildArgs, metadataLabels, isCI)
}

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

// Helper function to create platform-specific Docker build tasks
fun createPlatformBuildTask(arch: String, platform: String) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for $arch ($platform)"
    workingDir = projectDir
    
    val config = createBuildConfig(project)
    val tags = config.tagsForArch(arch)
    
    val cmd = listOf("docker", "buildx", "build", "--platform", platform) +
              config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
              config.metadataLabels.flatMap { listOf("--label", "${it.key}=${it.value}") } +
              config.cacheArgs(arch) +
              tags.flatMap { listOf("--tag", it) } +
              listOf("--load", ".")
    
    commandLine(cmd)
    
    doFirst {
        logger.lifecycle("Executing command line: ${cmd.joinToString(" ")}")
        logger.info("Building Docker image for $arch...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
        logger.info("Base image: ${config.baseImage}")
    }
}

// Helper function to create platform-specific push tasks
fun createPlatformPushTask(arch: String, buildTask: TaskProvider<Exec>) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Push $arch Docker image to registry"
    dependsOn(buildTask)
    
    doFirst {
        val config = createBuildConfig(project)
        val imageTag = "${config.baseImage}:latest-$arch"
        
        loggedCommandLine("docker", "push", imageTag)
        
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
    
    doFirst {
        val config = createBuildConfig(project)
        val tags = config.multiPlatformTags()
        val cacheArgs = config.cacheArgs()
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building multi-platform Docker images...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
    }
    
    workingDir = projectDir
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
    
    doFirst {
        val config = createBuildConfig(project)
        val tags = config.multiPlatformTags()
        val cacheArgs = config.cacheArgs()
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  config.buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--push", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building and pushing multi-platform Docker images...")
        logger.info("Branch: ${config.branch}, Commit: ${config.commit}")
        logger.info("Environment: ${if (config.isCI) "CI" else "Local"}")
    }
    
    workingDir = projectDir
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
        val config = createBuildConfig(project)
        
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
        val config = createBuildConfig(project)
        commandLine("docker", "manifest", "push", "${config.baseImage}:latest")
    }
    
    workingDir = projectDir
}

//
// MANAGEMENT TASKS
//

val listImages by tasks.registering {
    group = "docker"
    description = "List all Docker images with their tags and manifest information"
    
    doLast {
        println("🐳 Docker Images Summary")
        println("=".repeat(50))
        
        val packages = try {
            providers.exec {
                commandLine("gh", "api", "orgs/xtclang/packages?package_type=container", "--jq", ".[].name")
            }.standardOutput.asText.get().trim().split("\n").map { it.removeSurrounding("\"") }
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            println("💡 Run: gh auth refresh --hostname github.com --scopes read:packages")
            return@doLast
        }
        
        packages.forEach { pkg ->
            println("\n📦 $pkg")
            
            val versions = try {
                providers.exec {
                    commandLine("gh", "api", "orgs/xtclang/packages/container/$pkg/versions", 
                               "--jq", ".[] | {tags: .metadata.container.tags, created: .created_at}")
                }.standardOutput.asText.get().trim().split("\n")
            } catch (e: Exception) {
                println("  ❌ Could not get versions: ${e.message}")
                return@forEach
            }
            
            versions.forEachIndexed { i, version ->
                println("  ${i+1}. $version")
            }
        }
        
        if (!packages.contains("xvm")) return@doLast
        
        println("\n🔍 Full Manifest for xvm:latest")
        try {
            val manifest = providers.exec {
                commandLine("docker", "manifest", "inspect", "ghcr.io/xtclang/xvm:latest")
            }.standardOutput.asText.get()
            
            println("Raw manifest JSON:")
            println(manifest)
            
        } catch (e: Exception) {
            println("  ❌ Could not inspect manifest: ${e.message}")
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

val prunePackages by tasks.registering {
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