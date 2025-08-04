plugins {
    base
}

// Git helpers to avoid repetition
fun gitBranch() = providers.exec { commandLine("git", "branch", "--show-current") }.standardOutput.asText.get().trim()
fun gitCommit() = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
fun gitCommitShort() = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()

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
        val commitShort = gitCommitShort()
        val baseImage = baseImageName(branch)
        
        val buildArgs = mapOf(
            "GH_BRANCH" to branch,
            "GH_COMMIT" to commit
        )
        
        val tags = listOf(
            "${baseImage}:latest-$arch",
            "${baseImage}:${version}-$arch", 
            "${baseImage}:${commitShort}-$arch"
        )
        
        val cmd = listOf("docker", "buildx", "build", "--platform", platform) +
                  buildArgs.flatMap { listOf("--build-arg", "${it.key}=${it.value}") } +
                  tags.flatMap { listOf("--tag", it) } +
                  listOf("--load", ".")
        
        loggedCommandLine(cmd)
        
        logger.info("Building Docker image for $platform platform...")
        logger.info("Branch: $branch, Commit: $commitShort")
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
        val commitShort = gitCommitShort()
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
            "${baseImage}:${commitShort}",
            "${baseImage}:${commit}"
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
        logger.info("Branch: $branch, Commit: $commitShort")
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
        val commitShort = gitCommitShort()
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
            "${baseImage}:${commitShort}",
            "${baseImage}:${commit}"
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
        logger.info("Branch: $branch, Commit: $commitShort")
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
        val commitShort = gitCommitShort()
        val baseImage = baseImageName(branch)
        
        val manifestTags = listOf(
            "latest" to version,
            version to version,
            commitShort to commitShort
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