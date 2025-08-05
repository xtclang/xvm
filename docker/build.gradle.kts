plugins {
    base
}

// Git helpers to avoid repetition
fun gitBranch() = providers.exec { commandLine("git", "branch", "--show-current") }.standardOutput.asText.get().trim()
fun gitCommit() = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
fun gitCommitShort() = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()

// Image name logic
fun baseImageName(branch: String) = "ghcr.io/xtclang/xvm" + if (branch != "master") "-$branch" else ""

// Command line execution with logging
fun Exec.loggedCommandLine(cmd: List<String>) {
    logger.lifecycle("Executing command line: ${cmd.joinToString(" ")}")
    commandLine(cmd)
}

fun Exec.loggedCommandLine(vararg cmd: String) = loggedCommandLine(cmd.toList())

// Helper function to create platform-specific Docker build tasks
fun createDockerBuildTask(platform: String, arch: String) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for $platform platform"
    
    doFirst {
        val version = project.version.toString()
        val branch = gitBranch()
        val commit = gitCommit()
        val baseImage = baseImageName(branch)
        
        val buildArgs = mapOf(
            "GH_BRANCH" to branch,
            "GH_COMMIT" to commit
        )
        
        val tags = listOf(
            "${baseImage}:latest-$arch",
            "${baseImage}:${version}-$arch", 
            "${baseImage}:${commit}-$arch",
            "${baseImage}:${branch}-$arch"
        )
        
        val cmd = listOf("docker", "buildx", "build", "--platform", platform) +
                  buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building Docker image for $platform platform...")
        logger.info("Branch: $branch, Commit: $commit")
        logger.info("Base image: $baseImage")
    }
    
    workingDir = projectDir
}

// Platform-specific build tasks (useful for debugging)
val dockerBuildAmd64 by createDockerBuildTask("linux/amd64", "amd64")
val dockerBuildArm64 by createDockerBuildTask("linux/arm64", "arm64")

// Main build task - builds multi-platform locally
val dockerBuildMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build multi-platform Docker images locally (amd64 + arm64)"
    
    doFirst {
        val version = project.version.toString()
        val branch = gitBranch()
        val commit = gitCommit()
        val baseImage = baseImageName(branch)
        val buildDate = java.time.Instant.now().toString()
        
        val buildArgs = mapOf(
            "GH_BRANCH" to branch,
            "GH_COMMIT" to commit,
            "BUILD_DATE" to buildDate,
            "VCS_REF" to commit
        )
        
        val tags = listOf(
            "${baseImage}:latest",
            "${baseImage}:${version}",
            "${baseImage}:${commit}",
            "${baseImage}:${branch}"
        )
        
        java.io.File("/tmp/.buildx-cache").mkdirs()
        val cacheArgs = listOf("--cache-from", "type=local,src=/tmp/.buildx-cache", "--cache-to", "type=local,dest=/tmp/.buildx-cache,mode=max")
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building multi-platform Docker images locally...")
        logger.info("Branch: $branch, Commit: $commit")
        logger.info("Base image: $baseImage")
    }
    
    workingDir = projectDir
}

// Main push task - builds and pushes to registry
val dockerPushMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    
    doFirst {
        val version = project.version.toString()
        val branch = gitBranch()
        val commit = gitCommit()
        val baseImage = baseImageName(branch)
        val buildDate = java.time.Instant.now().toString()
        val isCI = System.getenv("CI") == "true"
        
        val buildArgs = mapOf(
            "GH_BRANCH" to branch,
            "GH_COMMIT" to commit,
            "BUILD_DATE" to buildDate,
            "VCS_REF" to commit
        )
        
        val tags = listOf(
            "${baseImage}:latest",
            "${baseImage}:${version}",
            "${baseImage}:${commit}",
            "${baseImage}:${branch}"
        )
        
        val cacheArgs = if (isCI) {
            listOf("--cache-from", "type=gha", "--cache-to", "type=gha,mode=max")
        } else {
            java.io.File("/tmp/.buildx-cache").mkdirs()
            listOf("--cache-from", "type=local,src=/tmp/.buildx-cache", "--cache-to", "type=local,dest=/tmp/.buildx-cache,mode=max")
        }
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/amd64,linux/arm64") +
                  buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--push", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building and pushing multi-platform Docker images to registry...")
        logger.info("Branch: $branch, Commit: $commit")
        logger.info("Base image: $baseImage")
        logger.info("Environment: ${if (isCI) "CI" else "Local"}")
    }
    
    workingDir = projectDir
}

// Helper function to create platform-specific Docker push tasks
fun createDockerPushTask(arch: String, buildTask: TaskProvider<Exec>) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Push $arch Docker image to GitHub Container Registry"
    dependsOn(buildTask)
    
    doFirst {
        val branch = gitBranch()
        val baseImage = baseImageName(branch)
        val imageTag = "${baseImage}:latest-$arch"
        
        loggedCommandLine("docker", "push", imageTag)
        
        logger.info("Pushing $arch Docker image to GitHub Container Registry...")
        logger.info("Branch: $branch, Image: $imageTag")
        logger.info("Make sure you're logged in with: docker login ghcr.io")
    }
}

val dockerPushAmd64 by createDockerPushTask("amd64", dockerBuildAmd64)
val dockerPushArm64 by createDockerPushTask("arm64", dockerBuildArm64)

val dockerPushAll by tasks.registering {
    group = "docker"
    description = "Push all platform-specific Docker images to GitHub Container Registry"
    
    dependsOn(dockerPushAmd64, dockerPushArm64)
}

// Simple task that builds both platforms individually (useful for local testing)
val dockerBuild by tasks.registering {
    group = "docker"
    description = "Build Docker images for both platforms individually (amd64 + arm64)"
    
    dependsOn(dockerBuildAmd64, dockerBuildArm64)
}

val dockerCreateManifest by tasks.registering(Exec::class) {
    group = "docker"
    description = "Create and push multi-platform Docker manifests (like CI workflow)"
    
    doFirst {
        val version = project.version.toString()
        val branch = gitBranch()
        val commit = gitCommit()
        val baseImage = baseImageName(branch)
        
        val manifestTags = listOf(
            "latest" to version,
            version to version,
            commit to commit,
            branch to branch
        )
        
        logger.info("Creating manifests for: $baseImage (version: $version)")
        
        fun loggedExec(vararg cmd: String) {
            logger.lifecycle("Executing command line: ${cmd.joinToString(" ")}")
            providers.exec { commandLine(*cmd) }
        }
        
        manifestTags.forEach { (tag, _) ->
            loggedExec("docker", "manifest", "create", "$baseImage:$tag", 
                       "$baseImage:$tag-amd64", "$baseImage:$tag-arm64")
            loggedExec("docker", "manifest", "push", "$baseImage:$tag")
        }
    }
    
    workingDir = projectDir
}

// Convenience aliases for common workflows
val dockerBuildPushAndManifest by tasks.registering {
    group = "docker"  
    description = "Build, push platform images, and create manifests (complete CI-like workflow)"
    
    dependsOn(dockerPushAll, dockerCreateManifest)
}

// List all Docker images with tags and manifest information  
val dockerListImages by tasks.registering {
    group = "docker"
    description = "List all Docker images with their tags, versions, and manifest information"
    
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

// Docker package retention/cleanup task
val dockerCleanupVersions by tasks.registering {
    group = "docker"
    description = "Clean up old Docker package versions (keep 5 most recent)"
    dependsOn(dockerListImages)
    
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

// Legacy package cleanup task (for non-master branches)
val dockerPrunePackages by tasks.registering {
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