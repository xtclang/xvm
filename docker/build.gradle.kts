plugins {
    base
}

// Helper function to create platform-specific Docker build tasks
fun createDockerBuildTask(platform: String, arch: String) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for $platform platform"
    
    val currentVersion = project.version.toString()
    
    // Get current branch name
    val branchName = providers.exec {
        commandLine("git", "branch", "--show-current")
    }.standardOutput.asText.get().trim()
    
    // Set base image name based on branch
    val baseImage = if (branchName == "master") {
        "ghcr.io/xtclang/xvm"
    } else {
        "ghcr.io/xtclang/xvm_${branchName}"
    }
    
    commandLine(
        "docker", "buildx", "build",
        "--platform", platform,
        "--tag", "${baseImage}:latest-$arch",
        "--tag", "${baseImage}:${currentVersion}-$arch",
        "--load",
        "."
    )
    
    workingDir = projectDir
    
    doFirst {
        logger.lifecycle("Building Docker image for $platform platform (branch: $branchName)...")
        logger.lifecycle("Tags: ${baseImage}:latest-$arch")
    }
}

val dockerBuildAmd64 by createDockerBuildTask("linux/amd64", "amd64")
val dockerBuildArm64 by createDockerBuildTask("linux/arm64", "arm64")

val dockerBuildMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build multi-platform Docker images for both amd64 and arm64 (local only)"
    
    val currentVersion = project.version.toString()
    
    doFirst {
        val gitCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val gitShort = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val buildDate = java.time.Instant.now().toString()
        
        // Always use local cache for build-only tasks
        java.io.File("/tmp/.buildx-cache").mkdirs()
        val cacheArgs = listOf("--cache-from", "type=local,src=/tmp/.buildx-cache", "--cache-to", "type=local,dest=/tmp/.buildx-cache,mode=max")
        
        commandLine(
            listOf(
                "docker", "buildx", "build",
                "--platform", "linux/amd64,linux/arm64",
                "--build-arg", "VERSION=${gitCommit}",
                "--build-arg", "BUILD_DATE=${buildDate}",
                "--build-arg", "VCS_REF=${gitCommit}"
            ) + cacheArgs + listOf(
                "--tag", "ghcr.io/xtclang/xvm:latest",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}",
                "--tag", "ghcr.io/xtclang/xvm:${gitShort}",
                "--tag", "ghcr.io/xtclang/xvm:${gitCommit}",
                "--load",
                "."
            )
        )
        
        logger.lifecycle("Building multi-platform Docker images for linux/amd64 and linux/arm64...")
        logger.lifecycle("Using commit SHA as VERSION: ${gitCommit}")
        logger.lifecycle("Build date: ${buildDate}")
    }
    
    workingDir = projectDir
}

val dockerPushMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    
    val currentVersion = project.version.toString()
    
    doFirst {
        val gitCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val gitShort = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val buildDate = java.time.Instant.now().toString()
        
        val isCI = System.getenv("CI") == "true"
        val cacheArgs = if (isCI) {
            listOf("--cache-from", "type=gha", "--cache-to", "type=gha,mode=max")
        } else {
            java.io.File("/tmp/.buildx-cache").mkdirs()
            listOf("--cache-from", "type=local,src=/tmp/.buildx-cache", "--cache-to", "type=local,dest=/tmp/.buildx-cache,mode=max")
        }
        
        commandLine(
            listOf(
                "docker", "buildx", "build",
                "--platform", "linux/amd64,linux/arm64",
                "--build-arg", "VERSION=${gitCommit}",
                "--build-arg", "BUILD_DATE=${buildDate}",
                "--build-arg", "VCS_REF=${gitCommit}"
            ) + cacheArgs + listOf(
                "--tag", "ghcr.io/xtclang/xvm:latest",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}",
                "--tag", "ghcr.io/xtclang/xvm:${gitShort}",
                "--tag", "ghcr.io/xtclang/xvm:${gitCommit}",
                "--push",
                "."
            )
        )
        
        logger.lifecycle("Building and pushing multi-platform Docker images...")
        logger.lifecycle("Environment: ${if (isCI) "CI" else "Local"} (will push to registry)")
        logger.lifecycle("Cache type: ${if (isCI) "GitHub Actions" else "Local filesystem (/tmp/.buildx-cache)"}")
        logger.lifecycle("Using commit SHA as VERSION: ${gitCommit}")
        logger.lifecycle("Build date: ${buildDate}")
    }
    
    workingDir = projectDir
}

// Helper function to create platform-specific Docker push tasks
fun createDockerPushTask(arch: String, buildTask: TaskProvider<Exec>) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Push $arch Docker image to GitHub Container Registry"
    
    dependsOn(buildTask)
    
    // Get current branch name
    val branchName = providers.exec {
        commandLine("git", "branch", "--show-current")
    }.standardOutput.asText.get().trim()
    
    // Set base image name based on branch
    val baseImage = if (branchName == "master") {
        "ghcr.io/xtclang/xvm"
    } else {
        "ghcr.io/xtclang/xvm_${branchName}"
    }
    
    commandLine(
        "docker", "push", "${baseImage}:latest-$arch"
    )
    
    doFirst {
        logger.lifecycle("Pushing $arch Docker image to GitHub Container Registry...")
        logger.lifecycle("Branch: $branchName, Image: ${baseImage}:latest-$arch")
        logger.lifecycle("Make sure you're logged in with: docker login ghcr.io")
    }
}

val dockerPushAmd64 by createDockerPushTask("amd64", dockerBuildAmd64)
val dockerPushArm64 by createDockerPushTask("arm64", dockerBuildArm64)

val dockerPushAll by tasks.registering {
    group = "docker"
    description = "Push all platform-specific Docker images to GitHub Container Registry"
    
    dependsOn(dockerPushAmd64, dockerPushArm64)
}

val dockerBuild by tasks.registering {
    group = "docker"
    description = "Build Docker images for both platforms (amd64 and arm64)"
    
    dependsOn(dockerBuildAmd64, dockerBuildArm64)
}

val dockerBuildAndPush by tasks.registering {
    group = "docker"
    description = "Build and push Docker images for both platforms"
    
    dependsOn(dockerPushAll)
}

val dockerCreateManifest by tasks.registering(Exec::class) {
    group = "docker"
    description = "Create and push multi-platform Docker manifests (like CI workflow)"
    
    doFirst {
        val currentVersion = project.version.toString()
        val gitCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val gitShort = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        
        val branchName = providers.exec {
            commandLine("git", "branch", "--show-current")
        }.standardOutput.asText.get().trim()
        
        val baseImage = if (branchName == "master") {
            "ghcr.io/xtclang/xvm"
        } else {
            "ghcr.io/xtclang/xvm_${branchName}"
        }
        
        logger.lifecycle("Creating manifests for: ${baseImage} (version: ${currentVersion})")
        
        // Create and push latest manifest
        exec {
            commandLine("docker", "manifest", "create", "${baseImage}:latest", 
                       "${baseImage}:latest-amd64", "${baseImage}:latest-arm64")
        }
        exec {
            commandLine("docker", "manifest", "push", "${baseImage}:latest")
        }
        
        // Create and push version manifest
        exec {
            commandLine("docker", "manifest", "create", "${baseImage}:${currentVersion}",
                       "${baseImage}:${currentVersion}-amd64", "${baseImage}:${currentVersion}-arm64")
        }
        exec {
            commandLine("docker", "manifest", "push", "${baseImage}:${currentVersion}")
        }
        
        // Create and push commit manifest
        exec {
            commandLine("docker", "manifest", "create", "${baseImage}:${gitShort}",
                       "${baseImage}:${gitShort}-amd64", "${baseImage}:${gitShort}-arm64")
        }
        exec {
            commandLine("docker", "manifest", "push", "${baseImage}:${gitShort}")
        }
    }
    
    workingDir = projectDir
}

val dockerBuildAndPushMultiPlatform by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images"
    
    dependsOn(dockerPushMultiPlatform)
}

val dockerBuildPushAndManifest by tasks.registering {
    group = "docker"  
    description = "Build, push platform images, and create manifests (complete CI-like workflow)"
    
    dependsOn(dockerPushAll, dockerCreateManifest)
}