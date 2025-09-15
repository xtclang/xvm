/**
 * Docker convention plugin for XVM project.
 * Provides configuration cache compatible Docker build tasks.
 */


// Import task classes
// (Tasks are defined in separate files to avoid non-static inner class issues)

// Make sure this plugin depends on the git plugin
plugins.withId("org.xtclang.build.git") {
    val jdkVersion = project.extra["jdkVersion"] as Int
    
    // Helper function to create docker tasks
    fun createDockerBuildTask(
        taskName: String,
        platforms: List<String>,
        action: String,
        architectureCheck: String? = null
    ) = tasks.register(taskName, DockerTask::class) {
        group = "docker"
        description = "Build Docker image for ${platforms.joinToString("/")} (use DIST_ZIP_URL env var for snapshot builds, or GH_COMMIT/GH_BRANCH for source builds)"
        
        dependsOn(tasks.named("resolveGitInfo"))
        
        gitInfoFile.set(tasks.named<ResolveGitInfoTask>("resolveGitInfo").flatMap { it.outputFile })
        this.platforms.set(platforms)
        this.action.set(action)
        distZipUrl.set(providers.environmentVariable("DIST_ZIP_URL"))
        this.jdkVersion.set(jdkVersion)
        this.architectureCheck.set(architectureCheck ?: "")
        
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
}


