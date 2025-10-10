import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.aggregator)
    id("org.xtclang.build.xdk.properties")
}

// Root aggregator: set version directly from xdkProperties (special case, not using versioning plugin)
group = xdkProperties.string("xdk.group").get()
version = xdkProperties.string("xdk.version").get()

logger.info("[xvm] Root aggregator version: $group:$name:$version")

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

val installWithNativeLaunchersDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution with native launchers in the xdk/build/install directory."
    dependsOn(xdk.task(":$name"))
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOf(xdk, plugin)

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to local Maven repository."

    // Publish to local Maven repository for all included builds with publications
    includedBuildsWithPublications.forEach { build ->
        dependsOn(build.task(":publishToMavenLocal"))
    }
}

/**
 * Publish XDK and plugin artifacts to both local Maven and remote repositories.
 *
 * Publishes to both local Maven and enabled remote repositories
 * (GitHub Packages, Maven Central, Gradle Plugin Portal).
 *
 * Options:
 * - Use -PallowRelease=true to allow publishing release versions (required for non-SNAPSHOT versions)
 */
val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to both local Maven and remote repositories."

    // Capture version and allowRelease as Providers for configuration cache compatibility
    val versionProvider = xdkProperties.string("xdk.version")
    val allowReleaseProvider = providers.gradleProperty("allowRelease").map { it.toBoolean() }

    doFirst {
        // Safety check: prevent accidental release publishing
        val currentVersion = versionProvider.get()
        val isSnapshot = currentVersion.endsWith("-SNAPSHOT")
        val allowRelease = allowReleaseProvider.getOrElse(false)

        if (!isSnapshot && !allowRelease) {
            throw GradleException(
                """
                |❌ Cannot publish release version without explicit approval!
                |
                |Current version: $currentVersion
                |
                |This is a RELEASE version (no -SNAPSHOT suffix).
                |To publish a release, you must explicitly set -PallowRelease=true
                |
                |Example: ./gradlew publish -PallowRelease=true
                |
                |This safety check prevents accidental release publishing.
                """.trimMargin()
            )
        }

        if (!isSnapshot) {
            logger.lifecycle("⚠️  Publishing RELEASE version: $currentVersion (allowRelease=true)")
        } else {
            logger.lifecycle("📦 Publishing SNAPSHOT version: $currentVersion")
        }
    }

    // Validate credentials before attempting remote publishing (use xdk's validateCredentials task)
    dependsOn(xdk.task(":validateCredentials"))

    // Always publish to both local and remote
    dependsOn(publishLocal)

    // Publish to all enabled remote repositories for all included builds with publications
    // The :publish task will publish to all repositories enabled via properties
    includedBuildsWithPublications.forEach { build ->
        dependsOn(build.task(":publish"))
    }
}

/**
 * Aggregate validateCredentials task that runs validation in all publishable projects.
 */
val validateCredentials by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Validate all publishing credentials across all projects without publishing"

    // Run validateCredentials in all projects with publications
    includedBuildsWithPublications.forEach { build ->
        dependsOn(build.task(":validateCredentials"))
    }
}

/**
 * Docker tasks - forwarded to docker subproject
 * TODO: Skip this and resolve the dist some other way.
 */

private val dockerSubproject = gradle.includedBuild("docker")
private val dockerTaskNames = listOf(
    "dockerBuildAmd64", "dockerBuildArm64", "dockerBuild",
    "dockerBuildMultiPlatform", "dockerPushMultiPlatform",
    "dockerPushAmd64", "dockerPushArm64", "dockerPushAll",
    "dockerBuildAndPush", "dockerBuildAndPushMultiPlatform",
    "dockerCreateManifest", "dockerBuildPushAndManifest"
)

// Forward all docker tasks to the docker subproject
dockerTaskNames.forEach { taskName ->
    tasks.register(taskName) {
        group = "docker"
        description = "Forward to docker subproject task: $taskName"
        dependsOn(dockerSubproject.task(":$taskName"))

        // Ensure XDK is built first for tasks that need it
        if (taskName.contains("Build") || taskName.contains("Push")) {
            dependsOn(installDist)
        }
    }
}
