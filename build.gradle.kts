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
val dockerBuildAmd64 by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for linux/amd64 platform"
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
    commandLine(
        "docker", "buildx", "build",
        "--platform", "linux/amd64",
        "--build-arg", "VERSION=local",
        "--tag", "ghcr.io/xtclang/xvm:latest-amd64",
        "--tag", "ghcr.io/xtclang/xvm:${project.version}-amd64",
        "--load",
        "."
    )
    
    workingDir = rootProject.projectDir
    
    doFirst {
        println("Building Docker image for linux/amd64 platform with VERSION=local...")
        println("Tags: ghcr.io/xtclang/xvm:latest-amd64, ghcr.io/xtclang/xvm:${project.version}-amd64")
    }
}

val dockerBuildArm64 by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build Docker image for linux/arm64 platform"
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
    commandLine(
        "docker", "buildx", "build",
        "--platform", "linux/arm64",
        "--build-arg", "VERSION=local",
        "--tag", "ghcr.io/xtclang/xvm:latest-arm64",
        "--tag", "ghcr.io/xtclang/xvm:${project.version}-arm64",
        "--load",
        "."
    )
    
    workingDir = rootProject.projectDir
    
    doFirst {
        println("Building Docker image for linux/arm64 platform with VERSION=local...")
        println("Tags: ghcr.io/xtclang/xvm:latest-arm64, ghcr.io/xtclang/xvm:${project.version}-arm64")
    }
}

val dockerBuildMultiPlatform by tasks.registering(Exec::class) {
    group = "docker"
    description = "Build multi-platform Docker images for both amd64 and arm64"
    
    // Ensure XDK is built first
    dependsOn(installDist)
    
    commandLine(
        "docker", "buildx", "build",
        "--platform", "linux/amd64,linux/arm64",
        "--build-arg", "VERSION=local",
        "--tag", "ghcr.io/xtclang/xvm:latest",
        "--tag", "ghcr.io/xtclang/xvm:${project.version}",
        "--tag", "ghcr.io/xtclang/xvm:latest-amd64",
        "--tag", "ghcr.io/xtclang/xvm:${project.version}-amd64",
        "--tag", "ghcr.io/xtclang/xvm:latest-arm64",
        "--tag", "ghcr.io/xtclang/xvm:${project.version}-arm64",
        "--push",
        "."
    )
    
    workingDir = rootProject.projectDir
    
    doFirst {
        println("Building multi-platform Docker images for linux/amd64 and linux/arm64 with VERSION=local...")
        println("Tags: ghcr.io/xtclang/xvm:latest, ghcr.io/xtclang/xvm:${project.version}")
        println("Platform-specific tags: -amd64, -arm64 suffixes")
        println("Images will be pushed directly to registry (multi-platform builds cannot use --load)")
    }
}

val dockerPushAmd64 by tasks.registering(Exec::class) {
    group = "docker"
    description = "Push AMD64 Docker image to GitHub Container Registry"
    
    dependsOn(dockerBuildAmd64)
    
    commandLine(
        "docker", "push", "ghcr.io/xtclang/xvm:latest-amd64"
    )
    
    doLast {
        exec {
            commandLine("docker", "push", "ghcr.io/xtclang/xvm:${project.version}-amd64")
        }
    }
    
    doFirst {
        println("Pushing AMD64 Docker image to GitHub Container Registry...")
        println("Make sure you're logged in with: docker login ghcr.io")
    }
}

val dockerPushArm64 by tasks.registering(Exec::class) {
    group = "docker"
    description = "Push ARM64 Docker image to GitHub Container Registry"
    
    dependsOn(dockerBuildArm64)
    
    commandLine(
        "docker", "push", "ghcr.io/xtclang/xvm:latest-arm64"
    )
    
    doLast {
        exec {
            commandLine("docker", "push", "ghcr.io/xtclang/xvm:${project.version}-arm64")
        }
    }
    
    doFirst {
        println("Pushing ARM64 Docker image to GitHub Container Registry...")
        println("Make sure you're logged in with: docker login ghcr.io")
    }
}

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
