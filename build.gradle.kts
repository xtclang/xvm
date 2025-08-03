import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xdk.build.aggregator)
    alias(libs.plugins.tasktree)
}

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */
val installDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    dependsOn(xdk.task(":$name"))
}

/**
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 *
 * Publishing tasks can be racy, but it seems that Gradle serializes tasks that have a common
 * output directory, which should be the case here. If not, we will have to put back the
 * parallel check/task failure condition.
 *
 * Publish remote - one way to do it is to only allow snapshot publications in GitHub, otherwise
 * we need to do it manually. "publishRemoteRelease", in which case we will also feed into
 * jreleaser.
 */
val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregate) all artifacts in the XDK to the remote repositories."
    // Call publishRemote in xdk and plugin projects.
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
    }
}

/**
 * Publish local publications to the mavenLocal repository. This is useful for testing
 * that a dependency to a particular XDK version of an XDK or XTC package works with
 * another application before you push it, and cause a "remote" publication to be made.
 */
val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregated) all artifacts in the XDK to the local Maven repository."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
    }
}

/**
 * Publish both local (mavenLocal) and remote (GitHub, and potentially mavenCentral, gradlePluginPortal)
 * packages for the current code.
 */
val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOf(xdk, plugin)
private val distributionTaskNames = XdkDistribution.distributionTasks
private val publishTaskPrefixes = listOf("list", "delete")
private val publishTaskSuffixesRemote = listOf("RemotePublications")
private val publishTaskSuffixesLocal = listOf("LocalPublications")

/**
 * Docker tasks for building and pushing multi-platform container images
 */

// Helper function to create platform-specific Docker build tasks
fun createDockerBuildTask(platform: String, arch: String) = tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for linux/$arch platform"
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
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
        "--load",
        "."
    )
    
    workingDir = rootProject.projectDir
    
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
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
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
                "--build-arg", "BUILD_DATE=${buildDate}",
                "--build-arg", "VCS_REF=${gitCommit}"
            ) + cacheArgs + listOf(
                "--tag", "ghcr.io/xtclang/xvm:latest",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}",
                "--tag", "ghcr.io/xtclang/xvm:latest-amd64",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}-amd64",
                "--tag", "ghcr.io/xtclang/xvm:latest-arm64",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}-arm64",
                "--tag", "ghcr.io/xtclang/xvm:${gitShort}",
                "--tag", "ghcr.io/xtclang/xvm:${gitCommit}",
                "--load",
                "."
            )
        )
        
        logger.lifecycle("Building multi-platform Docker images for linux/amd64 and linux/arm64...")
        logger.lifecycle("Source: Latest master from GitHub (no local code used)")
        logger.lifecycle("Environment: Local build (will load to Docker)")
        logger.lifecycle("Cache type: Local filesystem (/tmp/.buildx-cache)")
        logger.lifecycle("Git commit: ${gitCommit}")
        logger.lifecycle("Build date: ${buildDate}")
        logger.lifecycle("Tags include commit-specific: ${gitShort}, ${gitCommit}")
        logger.lifecycle("Note: Multi-platform builds with --load may not work on all Docker setups")
    }
    
    workingDir = rootProject.projectDir
}

val dockerPushMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build and push multi-platform Docker images to registry"
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
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
                "--build-arg", "BUILD_DATE=${buildDate}",
                "--build-arg", "VCS_REF=${gitCommit}"
            ) + cacheArgs + listOf(
                "--tag", "ghcr.io/xtclang/xvm:latest",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}",
                "--tag", "ghcr.io/xtclang/xvm:latest-amd64",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}-amd64",
                "--tag", "ghcr.io/xtclang/xvm:latest-arm64",
                "--tag", "ghcr.io/xtclang/xvm:${currentVersion}-arm64",
                "--tag", "ghcr.io/xtclang/xvm:${gitShort}",
                "--tag", "ghcr.io/xtclang/xvm:${gitCommit}",
                "--push",
                "."
            )
        )
        
        logger.lifecycle("Building and pushing multi-platform Docker images...")
        logger.lifecycle("Source: Latest master from GitHub (no local code used)")
        logger.lifecycle("Environment: ${if (isCI) "CI" else "Local"} (will push to registry)")
        logger.lifecycle("Cache type: ${if (isCI) "GitHub Actions" else "Local filesystem (/tmp/.buildx-cache)"}")
        logger.lifecycle("Git commit: ${gitCommit}")
        logger.lifecycle("Build date: ${buildDate}")
        logger.lifecycle("Tags include commit-specific: ${gitShort}, ${gitCommit}")
    }
    
    workingDir = rootProject.projectDir
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

val dockerBuildAndPushMultiPlatform by tasks.registering {
    group = "docker"
    description = "Build and push multi-platform Docker images"
    
    dependsOn(dockerPushMultiPlatform)
}

// list|deleteLocalPublicatiopns/remotePublications.
publishTaskPrefixes.forEach { prefix ->
    buildList {
        addAll(publishTaskSuffixesLocal)
        addAll(publishTaskSuffixesRemote)
    }.forEach { suffix ->
        val taskName = "$prefix$suffix"
        tasks.register(taskName) {
            group = PUBLISH_TASK_GROUP
            description = "Task that aggregates '$taskName' tasks for builds with publications."
            includedBuildsWithPublications.forEach { it ->
                dependsOn(it.task(":$taskName"))
            }
        }
    }
}
