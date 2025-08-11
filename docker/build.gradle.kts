/**
 * Comprehensive Docker build script for XVM project.
 * Supports multi-platform builds, manifest creation, registry management, and caching.
 * All git commands and configuration creation deferred to execution phase only.
 */

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
        val encodedBranch = java.net.URLEncoder.encode(branch, "UTF-8")
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
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val keepCount = 5
        val packageName = "xvm"
        
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
        
        logger.lifecycle("📦 Package: $packageName")
        logger.lifecycle("📊 Total versions: ${versions.size}")
        logger.lifecycle("🎯 Keeping: $keepCount most recent")
        logger.lifecycle("🗑️  Deleting: ${maxOf(0, versions.size - keepCount)} old versions")
        logger.lifecycle("")
        
        if (versions.size <= keepCount) {
            logger.lifecycle("✅ No cleanup needed - already at or below limit")
            return@doLast
        }
        
        // Show what we're keeping
        logger.lifecycle("✅ Keeping these versions:")
        versions.take(keepCount).forEachIndexed { i, version ->
            logger.lifecycle("  ${i+1}. $version")
        }
        logger.lifecycle("")
        
        // Show what we're deleting
        val toDelete = versions.drop(keepCount)
        logger.lifecycle("🗑️  Deleting these ${toDelete.size} versions:")
        toDelete.forEachIndexed { i, version ->
            logger.lifecycle("  ${i+1}. $version")
        }
        logger.lifecycle("")
        
        // Ask for confirmation (only in interactive mode)
        if (System.getenv("CI") != "true") {
            logger.warn("⚠️  This will permanently delete ${toDelete.size} package versions!")
            logger.warn("💡 Add -Pconfirm=true to proceed, or run from CI")
            
            if (project.findProperty("confirm") != "true") {
                logger.error("❌ Cancelled - add -Pconfirm=true to actually delete")
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
                logger.lifecycle("✅ Deleted version ID: $id")
                
            } catch (e: Exception) {
                failedCount++
                logger.error("❌ Failed to delete version: ${e.message}")
            }
        }
        
        logger.lifecycle("")
        logger.lifecycle("🎯 Cleanup Summary:")
        logger.lifecycle("  ✅ Deleted: $deletedCount versions")  
        logger.lifecycle("  ❌ Failed: $failedCount versions")
        logger.lifecycle("  📦 Remaining: $keepCount versions")
        
        if (failedCount > 0) {
            logger.warn("💡 Some deletions failed - check GitHub CLI authentication and permissions")
        }
    }
}

val pruneImages by tasks.registering {
    group = "docker"
    description = "Delete non-master Docker packages from GitHub Container Registry"
    
    doLast {
        logger.lifecycle("🧹 Pruning Non-Master Docker Packages")
        logger.lifecycle("=".repeat(50))
        
        val packagesToDelete = listOf<String>(
            // Add branch-specific packages here as needed  
            // e.g., "xvm-feature-branch", "xvm-experimental"
        )
        
        if (packagesToDelete.isEmpty()) {
            logger.lifecycle("✅ No packages configured for deletion")
            logger.warn("💡 Add package names to the packagesToDelete list in build.gradle.kts")
            return@doLast
        }
        
        packagesToDelete.forEach { packageName ->
            logger.lifecycle("🗑️  Attempting to delete package: $packageName")
            try {
                providers.exec {
                    commandLine("gh", "api", "-X", "DELETE", "orgs/xtclang/packages/container/$packageName")
                }
                logger.lifecycle("✅ Successfully deleted package: $packageName")
            } catch (e: Exception) {
                logger.error("❌ Failed to delete $packageName: ${e.message}")
            }
        }
        
        logger.lifecycle("💡 View remaining packages: https://github.com/orgs/xtclang/packages")
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
        exec {
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

// Docker image functional test task with proper Gradle dependencies
val testDockerImageFunctionality by tasks.registering {
    group = "docker"
    description = "Test Docker image functionality with XVM sample programs on current platform"
    
    // Get host architecture for dependencies
    val hostArch = System.getProperty("os.arch").let { osArch ->
        when (osArch) {
            "x86_64", "amd64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            else -> osArch
        }
    }
    
    // Depend on Docker image build for current platform (ensure Docker image exists)
    dependsOn(if (hostArch == "amd64") buildAmd64 else buildArm64)
    
    // Input: XDK distribution ZIP that the Docker build needs
    inputs.files(fileTree("../xdk/build/distributions") {
        include("xdk-*.zip")
        exclude("*-linux_*.zip")  // Use platform-agnostic ZIP
        exclude("*-macos_*.zip")
        exclude("*-windows_*.zip")
    }).optional(true)  // Optional because we may need to build it first
    
    // Input: DockerTest.x program that gets copied into the image
    inputs.file("test/DockerTest.x")
    
    // Input: Dockerfile and build scripts that affect the image
    inputs.file("Dockerfile")
    inputs.dir("scripts")
    
    // Output: Test result marker file for up-to-date checking
    val testResultFile = layout.buildDirectory.file("docker-test-results.txt")
    outputs.file(testResultFile)
    
    doLast {
        logger.lifecycle("🧪 Testing Docker image functionality...")
        
        val platform = "linux/$hostArch"
        val testTag = "test-xvm-functional:latest"
        
        logger.lifecycle("🐳 Using Docker image built by platform build task...")
        logger.lifecycle("  Platform: $platform")
        logger.lifecycle("  Build task: ${if (hostArch == "amd64") "buildAmd64" else "buildArm64"}")
        logger.lifecycle("  Test tag: $testTag")
        
        // The Docker image was already built by the platform build task we depend on
        // We just need to copy the distribution file and re-tag the image for testing
        
        // Ensure XDK distribution exists, building it through proper Gradle execution if needed
        val xdkBuildDir = file("../xdk/build/distributions")
        val existingDistFiles = fileTree(xdkBuildDir) {
            include("xdk-*.zip")
            exclude("*-linux_*.zip")
            exclude("*-macos_*.zip") 
            exclude("*-windows_*.zip")
        }.files
        
        val distZipFile = if (existingDistFiles.isNotEmpty()) {
            // Use existing distribution
            val file = existingDistFiles.first()
            logger.lifecycle("📦 Found existing XDK distribution: ${file.name}")
            file
        } else {
            // Build XDK distribution through proper Gradle execution
            logger.lifecycle("📋 XDK distribution not found, building it...")
            
            exec {
                commandLine("../gradlew", ":xdk:distZip")
                workingDir = projectDir
            }
            
            val newDistFiles = fileTree(xdkBuildDir) {
                include("xdk-*.zip")
                exclude("*-linux_*.zip")
                exclude("*-macos_*.zip") 
                exclude("*-windows_*.zip")
            }.files
            
            if (newDistFiles.isEmpty()) {
                throw GradleException("Failed to build XDK distribution")
            }
            
            val file = newDistFiles.first()
            logger.lifecycle("📦 Built XDK distribution: ${file.name}")
            file
        }
        
        // Copy distribution ZIP to Docker build context if not already there
        val dockerContextZip = file("ci-dist.zip")
        if (!dockerContextZip.exists() || dockerContextZip.lastModified() < distZipFile.lastModified()) {
            copy {
                from(distZipFile)
                into(".")
                rename { "ci-dist.zip" }
            }
            logger.lifecycle("📋 Updated distribution ZIP in Docker context")
        }
        
        // Build Docker image with proper dependencies satisfied
        val config = createBuildConfig()
        val cmd = listOf(
            "docker", "buildx", "build",
            "--platform", platform,
            "--progress=plain",
            "--build-arg", "GH_BRANCH=${config.branch}",
            "--build-arg", "GH_COMMIT=${config.commit}",
            "--build-arg", "USE_PREBUILT_ARTIFACTS=true",
            "--tag", testTag,
            "--load",
            "."
        )
        
        logger.lifecycle("🔍 Building Docker image with dependencies satisfied: ${cmd.joinToString(" ")}")
        
        val buildResult = ProcessBuilder(cmd)
            .directory(projectDir)
            .inheritIO()
            .start()
            .waitFor()
            
        if (buildResult != 0) {
            throw GradleException("Docker build failed with exit code: $buildResult")
        }
        
        logger.lifecycle("✅ Docker image built successfully with proper dependencies!")
        
        // Define test scenarios that match the original CI functionality
        val testScenarios = listOf(
            // Basic launcher tests
            "xec launcher version" to listOf("xec", "--version"),
            "xcc launcher version" to listOf("xcc", "--version"),
            "launcher help functionality" to listOf("xec", "--help"),
            "native launcher binary compatibility" to listOf("uname", "-m"),
            
            // Program compilation and execution tests (like original EchoTest)
            "compile and run with no arguments" to listOf("xec", "/opt/xdk/test/DockerTest.x"),
            "compile and run with single argument" to listOf("xec", "/opt/xdk/test/DockerTest.x", "hello"),
            "compile and run with multiple arguments" to listOf("xec", "/opt/xdk/test/DockerTest.x", "arg1", "arg with spaces", "arg3")
        )
        
        // Run functional tests
        testScenarios.forEach { (testName, testCmd) ->
            logger.lifecycle("🧪 Testing: $testName")
            logger.lifecycle("  Command: ${testCmd.joinToString(" ")}")
            
            val dockerCmd = listOf("docker", "run", "--rm", testTag) + testCmd
            val testResult = ProcessBuilder(dockerCmd)
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
                
                // Additional validation for DockerTest program execution (matching original EchoTest behavior)
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
        logger.lifecycle("💡 Image tagged as: $testTag")
        
        // Write test completion marker with timestamp and dependencies info
        testResultFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(buildString {
                appendLine("Docker functionality tests passed at ${java.time.Instant.now()}")
                appendLine("Dependencies satisfied:")
                appendLine("- XDK distribution: ${distZipFile.name}")
                appendLine("- Docker build task: ${if (hostArch == "amd64") "buildAmd64" else "buildArm64"}")
                appendLine("- Platform: $platform")
                appendLine("- Host architecture: $hostArch")
            })
        }
    }
}