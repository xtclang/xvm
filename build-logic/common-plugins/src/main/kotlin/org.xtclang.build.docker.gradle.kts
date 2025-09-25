/**
 * Docker convention plugin for XVM project.
 * Provides configuration cache compatible Docker build tasks.
 */

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

        this.jdkVersion.set(providers.provider { project.extra["jdkVersion"] as Int })
        this.architectureCheck.set(architectureCheck ?: "")

        // Wire git information: prefer environment variables (from CI), fall back to git commands at execution time
        this.gitCommit.set(providers.environmentVariable("GH_COMMIT").orElse("auto-detect"))
        this.gitBranch.set(providers.environmentVariable("GH_BRANCH").orElse("auto-detect"))

        this.projectVersion.set(providers.provider { project.version.toString() })

        // Configuration cache compatible input properties
        hostArch.set(providers.systemProperty("os.arch").map { arch ->
            when (arch) {
                "amd64", "x86_64" -> "amd64"
                "aarch64", "arm64" -> "arm64"
                else -> "unknown"
            }
        })
        allowEmulation.set(providers.systemProperty("org.xtclang.docker.allowEmulation").map { it.toBoolean() }.orElse(false))
        dockerProgress.set(providers.environmentVariable("DOCKER_BUILDX_PROGRESS").orElse("plain"))
        ciMode.set(providers.environmentVariable("CI").map { it == "true" }.orElse(false))
        userHome.set(providers.systemProperty("user.home"))
        dockerDir.set(layout.projectDirectory)

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
    
    // Create the Docker cleanup task
    val cleanImages by tasks.registering(DockerCleanupTask::class) {
        group = "docker"
        description = "Clean up old Docker package versions (default: keep 10 most recent, protect master images)"

        keepCount.set(providers.gradleProperty("keepCount").orElse("10").map { it.toInt() })
        dryRun.set(providers.gradleProperty("dryRun").orElse("false").map { it.toBoolean() })
        forced.set(providers.gradleProperty("force").orElse("false").map { it.toBoolean() })
        packageName.set("xvm")
    }
