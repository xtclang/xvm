/**
 * Docker convention plugin for XVM project.
 * Provides configuration cache compatible Docker build tasks.
 *
 * Requires palantir-git-version plugin to be applied in the consuming project
 * for git info (applied via docker/build.gradle.kts).
 */

plugins {
    id("org.xtclang.build.xdk.properties")
}

// Helper function to create docker tasks
fun createDockerBuildTask(
    taskName: String,
    platforms: List<String>,
    action: String,
    architectureCheck: String? = null
) = tasks.register(taskName, DockerTask::class) {
        group = "docker"
        description = "Build Docker image for ${platforms.joinToString("/")} using XDK distribution"

        // No longer need git dependencies since Docker build doesn't use git info

        // Use the xdkDistConsumer configuration for XDK distribution zip dependency
        val xdkDistConfiguration = project.configurations.findByName("xdkDistConsumer")

        if (xdkDistConfiguration != null) {
            // Set the DIST_ZIP_URL from the configuration's resolved files
            distZipUrl.set(providers.provider {
                val files = xdkDistConfiguration.files
                val zipFile = files.find { it.name.endsWith(".zip") }
                zipFile?.absolutePath ?: providers.environmentVariable("DIST_ZIP_URL").orNull ?: ""
            })
            // CRITICAL: Wire the configuration files as task inputs for proper up-to-date checking
            xdkDistributionFiles.from(xdkDistConfiguration)
            logger.info("Docker task $taskName will use xdkDistConsumer config for distribution zip")
        } else {
            // Fallback to environment variable only
            distZipUrl.set(providers.environmentVariable("DIST_ZIP_URL"))
            logger.warn("Could not find xdkDistConsumer configuration - Docker task $taskName will only work if DIST_ZIP_URL environment variable is set")
        }

        // No longer need git info file since Docker doesn't use it
        this.platforms.set(platforms)
        this.action.set(action)

        this.jdkVersion.set(project.xdkProperties.int("org.xtclang.java.jdk"))
        this.architectureCheck.set(architectureCheck ?: "")

        // Wire git information from Palantir plugin (available for local builds) or CI env vars
        // CI sets GH_COMMIT/GH_BRANCH, local builds use Palantir versionDetails
        // Helper to get Palantir versionDetails property using reflection (avoids compile-time dependency)
        fun getVersionDetailsProperty(methodName: String, fallback: String): String? {
            return try {
                @Suppress("UNCHECKED_CAST")
                val versionDetails = (project.extensions.extraProperties["versionDetails"] as groovy.lang.Closure<*>).call()
                versionDetails::class.java.getMethod(methodName).invoke(versionDetails) as? String ?: fallback
            } catch (e: Exception) {
                logger.warn("Failed to get $methodName from Palantir plugin: ${e.message}")
                fallback
            }
        }

        this.gitCommit.set(
            providers.environmentVariable("GH_COMMIT")
                .orElse(providers.provider { getVersionDetailsProperty("getGitHashFull", "unknown") })
        )
        this.gitBranch.set(
            providers.environmentVariable("GH_BRANCH")
                .orElse(providers.provider { getVersionDetailsProperty("getBranchName", "detached-head") })
        )

        this.projectVersion.set(providers.provider { project.version.toString() })

        // Configuration cache compatible input properties
        hostArch.set(providers.systemProperty("os.arch").map { arch ->
            XdkDistribution.normalizeArchitecture(arch)
        })
        allowEmulation.set(project.xdkProperties.boolean("org.xtclang.docker.allowEmulation", false))
        dockerProgress.set(providers.environmentVariable("DOCKER_BUILDX_PROGRESS").orElse("plain"))
        ciMode.set(providers.environmentVariable("CI").map { it == "true" }.orElse(false))
        userHome.set(providers.systemProperty("user.home"))
        dockerCommand.set(project.xdkProperties.string("org.xtclang.docker.command", "docker"))
        dockerDir.set(layout.projectDirectory)

        // Output marker file for caching
        buildMarkerFile.set(layout.buildDirectory.file("docker/${taskName}.marker"))

        // Tags will be computed at execution time in the task action
        tags.set(emptyList()) // Dummy value to satisfy the property
    }
    
    // Create the standard Docker build tasks
    createDockerBuildTask("buildAmd64", listOf("linux/amd64"), "load", "amd64")
    createDockerBuildTask("buildArm64", listOf("linux/arm64"), "load", "arm64")
    createDockerBuildTask("buildAll", listOf("linux/amd64", "linux/arm64"), "load")
    createDockerBuildTask("pushAmd64", listOf("linux/amd64"), "push", "amd64")
    createDockerBuildTask("pushArm64", listOf("linux/arm64"), "push", "arm64") 
    createDockerBuildTask("pushAll", listOf("linux/amd64", "linux/arm64"), "push")
    
    // Create the Docker cleanup taskg
    val cleanImages by tasks.registering(DockerCleanupTask::class) {
        group = "docker"
        description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"

        keepCount.set(providers.gradleProperty("keepCount").orElse("10").map { it.toInt() })
        dryRun.set(providers.gradleProperty("dryRun").orElse("false").map { it.toBoolean() })
        forced.set(providers.gradleProperty("force").orElse("false").map { it.toBoolean() })
        packageName.set("xvm")
    }
