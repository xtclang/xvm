/**
 * Minimal Docker build script to avoid Windows CI configuration phase failures.
 * All git commands and configuration creation deferred to execution phase only.
 */

plugins {
    id("org.xtclang.build.xdk.versioning")
}

// NO configuration-time execution - everything deferred to task execution

val buildArm64 by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for arm64 (linux/arm64)"
    workingDir = projectDir
    
    doFirst {
        logger.lifecycle("🔍 [DOCKER-DEBUG] ARM64 build started")
        
        // Get environment variables or fallback to git commands
        val branch = System.getenv("GH_BRANCH") ?: 
                     System.getenv("GITHUB_HEAD_REF") ?: 
                     System.getenv("GITHUB_REF_NAME") ?: 
                     try {
                         providers.exec {
                             commandLine("git", "branch", "--show-current")
                             workingDir = project.rootDir
                         }.standardOutput.asText.get().trim()
                     } catch (e: Exception) {
                         logger.warn("Failed to get git branch: ${e.message}")
                         "unknown"
                     }
        
        val commit = System.getenv("GH_COMMIT") ?: 
                     System.getenv("GITHUB_SHA") ?: 
                     try {
                         providers.exec {
                             commandLine("git", "rev-parse", "HEAD")
                             workingDir = project.rootDir
                         }.standardOutput.asText.get().trim()
                     } catch (e: Exception) {
                         logger.warn("Failed to get git commit: ${e.message}")
                         "unknown"
                     }
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Branch: $branch, Commit: ${commit.take(8)}")
        
        val branchTag = branch.substringAfterLast("/").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val baseImage = "ghcr.io/xtclang/xvm"
        val version = project.version.toString()
        
        val tags = if (branch == "master") {
            listOf(
                "$baseImage:latest-arm64",
                "$baseImage:$version-arm64", 
                "$baseImage:$commit-arm64"
            )
        } else {
            listOf(
                "$baseImage:$branchTag-arm64",
                "$baseImage:$commit-arm64"
            )
        }
        
        val buildArgs = mapOf(
            "GH_BRANCH" to branch,
            "GH_COMMIT" to commit
        )
        
        val metadataLabels = mapOf(
            "org.opencontainers.image.created" to java.time.Instant.now().toString(),
            "org.opencontainers.image.revision" to commit,
            "org.opencontainers.image.version" to version,  
            "org.opencontainers.image.source" to "https://github.com/xtclang/xvm/tree/$branch"
        )
        
        val isCI = System.getenv("CI") == "true"
        val cacheArgs = if (isCI) {
            listOf("--cache-from", "type=gha,scope=arm64", "--cache-to", "type=gha,mode=max,scope=arm64")
        } else {
            val cacheDir = File(System.getProperty("user.home"), ".cache/docker-buildx-arm64")
            cacheDir.mkdirs()
            listOf("--cache-from", "type=local,src=${cacheDir.absolutePath}", "--cache-to", "type=local,dest=${cacheDir.absolutePath},mode=max")
        }
        
        val cmd = listOf("docker", "buildx", "build", "--platform", "linux/arm64") +
                  buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  metadataLabels.flatMap { listOf("--label", "${it.key}=${it.value}") } +
                  cacheArgs +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        commandLine(cmd)
        
        logger.lifecycle("🔍 [DOCKER-DEBUG] Executing: ${cmd.joinToString(" ")}")
        logger.lifecycle("🔍 [DOCKER-DEBUG] Tags: ${tags.joinToString(", ")}")
    }
}

val dockerBuild by tasks.registering {
    group = "docker"
    description = "Build Docker images (ARM64 only for now)"
    dependsOn(buildArm64)
}

// Simple task listing for debugging
val listTasks by tasks.registering {
    group = "docker"
    description = "List Docker tasks available"
    
    doLast {
        println("Available Docker tasks:")
        println("  buildArm64 - Build ARM64 Docker image")
        println("  dockerBuild - Alias for buildArm64")
        println("  listTasks - This task")
    }
}