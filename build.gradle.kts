import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import com.diffplug.gradle.spotless.SpotlessCheck
import com.diffplug.spotless.LineEnding
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.aggregator)
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.spotless)
}

// Repository code standards. Spotless attaches spotlessCheck to check.
// Add future language-specific standards here as they are adopted.
spotless {
    format("javaAndXtc") {
        // Cover the complete source tree, even when optional composite builds are disabled.
        // New files are checked before staging; generated output and local worktrees are excluded.
        target(fileTree(layout.projectDirectory) {
            include("**/*.java", "**/*.x")
            exclude(
                "**/build/**", "**/out/**", "**/node_modules/**",
                "**/.*/**"
            )
        })
        // Accept LF and CRLF without imposing a repository-wide line-ending convention.
        lineEndings = LineEnding.PRESERVE
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        // A remaining tab may be literal data: require an explicit repair rather than alter it.
        forbidRegex("noTabs", "\t", "Literal tabs are forbidden; use spaces or a \\t escape in string literals.")
        replaceRegex("consecutiveBlankLines", "(?m)^(?:\\h*\\n){2,}", "\n")
        endWithNewline()
    }
}

// Local check/build repairs formatting first; CI only verifies committed source.
// An explicit spotlessCheck stays read-only in both environments.
if (!providers.environmentVariable("CI").isPresent) {
    val spotlessApply = tasks.named("spotlessApply")
    tasks.named("check") {
        dependsOn(spotlessApply)
    }
    // Order the actual per-format checks, not just their aggregate lifecycle task.
    tasks.withType<SpotlessCheck>().configureEach {
        mustRunAfter(spotlessApply)
    }
}

// Root aggregator: version set automatically by properties plugin
group = xdkProperties.stringValue("xdk.group")
version = xdkProperties.stringValue("xdk.version")

logger.info("[xvm] Root aggregator version: $group:$name:$version")

/**
 * Print version information for the root aggregator and all included builds.
 * The aggregator plugin creates this task and adds dependencies to all included builds.
 * We configure it here to also print the root aggregator's version.
 */
val versions = tasks.named("versions") {
    // Capture values during configuration for configuration cache compatibility
    val projectName = project.name
    val projectGroup = project.group
    val projectVersion = project.version

    doFirst {
        logger.lifecycle("\n📦 Root Aggregator: $projectName")
        logger.lifecycle("   $projectGroup:$projectName:$projectVersion")
        logger.lifecycle("")
    }
}

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */

val distZip = tasks.register("distZip") {
    group = DISTRIBUTION_TASK_GROUP
    description = "Build the XDK distribution zip in the xdk/build/distributions directory."
    dependsOn(xdk.task(":$name"))
}

val installDist = tasks.register("installDist") {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    dependsOn(xdk.task(":$name"))
}

val installWithNativeLaunchersDist = tasks.register("installWithNativeLaunchersDist") {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution with native launchers in the xdk/build/install directory."
    dependsOn(xdk.task(":$name"))
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val publishedBuilds = listOf(xdk, plugin)

val publishLocal = tasks.register("publishLocal") {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to local Maven repository."

    // Publish to local Maven repository for all included builds with publications
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":publishToMavenLocal"))
    }
}

val publishSnapshotBundle = tasks.register("publishSnapshotBundle") {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin snapshot artifacts to an isolated file-backed Maven repository."

    val hasDestination = !xdkProperties.string("org.xtclang.publish.snapshotBundleRepo", "")
        .get().isBlank()
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":validateSnapshotBundle"))
        if (hasDestination) {
            dependsOn(build.task(":publishAllPublicationsToSnapshotBundleRepository"))
        }
    }
}

/**
 * Publish XDK and plugin artifacts to both local Maven and remote repositories.
 *
 * Publishes to both local Maven and enabled remote repositories
 * (GitHub Packages, Maven Central, Gradle Plugin Portal).
 *
 * Options:
 * - Use -Porg.xtclang.allowRelease=true to allow publishing release versions (required for non-SNAPSHOT versions)
 */
val publish = tasks.register("publish") {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to both local Maven and remote repositories."

    // Each remote publishing task validates release approval and credentials before its action.
    // Always publish to both local and remote
    dependsOn(publishLocal)

    // Publish to all enabled remote repositories for all included builds with publications
    // The :publish task will publish to all repositories enabled via properties
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":publish"))
    }
}

/**
 * Aggregate validateCredentials task that runs validation in all publishable projects.
 */
val validateCredentials = tasks.register("validateCredentials") {
    group = PUBLISH_TASK_GROUP
    description = "Validate all publishing credentials across all projects without publishing"

    // Run validateCredentials in all projects with publications
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":validateCredentials"))
    }
}

/**
 * Docker aliases forwarded to the included build. Its distribution input carries the producer dependency.
 */

private val dockerSubproject = gradle.includedBuild("docker")
private val dockerTaskAliases = mapOf(
    "dockerBuildAmd64" to "buildAmd64",
    "dockerBuildArm64" to "buildArm64",
    "dockerBuild" to "buildAll",
    "dockerBuildMultiPlatform" to "buildAll",
    "dockerPushAmd64" to "pushAmd64",
    "dockerPushArm64" to "pushArm64",
    "dockerPushAll" to "pushAll",
    "dockerPushMultiPlatform" to "pushAll",
    "dockerBuildAndPush" to "pushAll",
    "dockerBuildAndPushMultiPlatform" to "pushAll",
    // buildx --push publishes the multi-platform manifest with the images.
    "dockerBuildPushAndManifest" to "pushAll"
)

// There is no standalone manifest-creation task in the included build.
dockerTaskAliases.forEach { (alias, target) ->
    tasks.register(alias) {
        group = "docker"
        description = "Forward to docker task: $target"
        dependsOn(dockerSubproject.task(":$target"))
    }
}
