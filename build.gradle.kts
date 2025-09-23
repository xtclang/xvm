import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xdk.build.aggregator)
    id("org.xtclang.build.publishing")
}

// Use the centralized credential management from the publishing convention

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
val unpublishGradlePlugin by tasks.registering(UnpublishGradlePluginTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Unpublish/delete a specific version from Gradle Plugin Portal (use -PunpublishGradlePlugin=version)"
    dependsOn(validateCredentials)

    pluginId.set(getXdkProperty("org.xtclang.plugin.id"))
    unpublishVersion.set(project.findProperty("unpublishGradlePlugin")?.toString() ?: "")

    // Use centralized credential management
    hasCredentials.set(xdkPublishingCredentials.enablePluginPortal.zip(xdkPublishingCredentials.gradlePublishKey) { enabled, key ->
        enabled && key.isNotEmpty() && xdkPublishingCredentials.gradlePublishSecret.get().isNotEmpty()
    })
}

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to remote repositories (GitHub Packages, Gradle Plugin Portal)."
    dependsOn(validateCredentials)
    dependsOn(
        plugin.task(":publishAllPublicationsToGitHubRepository"),
        xdk.task(":publishMavenPublicationToGitHubRepository")
    )
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to local Maven repository."
    // Publish to local Maven repository for both projects
    dependsOn(
        plugin.task(":publishToMavenLocal"),
        xdk.task(":publishToMavenLocal")
    )
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
private val publishTaskPrefixes = listOf("list", "delete")
private val publishTaskSuffixesRemote = listOf("RemotePublications")
private val publishTaskSuffixesLocal = listOf("LocalPublications")


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

// Task classes are now extracted to separate files in build-logic/common-plugins/src/main/kotlin/


// list|deleteLocalPublicatiopns/remotePublications.
publishTaskPrefixes.forEach { prefix ->
    publishTaskSuffixesLocal.forEach { suffix ->
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


// Validate credentials are available for publishing (GitHub + optional Plugin Portal)
val validateCredentials by tasks.registering(ValidateCredentialsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Validate GitHub and Plugin Portal credentials are available for publishing"

    // Use centralized credential management
    gitHubUsername.set(xdkPublishingCredentials.gitHubUsername)
    gitHubPassword.set(xdkPublishingCredentials.gitHubPassword)
    enableGitHub.set(xdkPublishingCredentials.enableGitHub)
    enablePluginPortal.set(xdkPublishingCredentials.enablePluginPortal)
    gradlePublishKey.set(xdkPublishingCredentials.gradlePublishKey)
    gradlePublishSecret.set(xdkPublishingCredentials.gradlePublishSecret)
}

// Special handling for remote publication listing - use GitHub API integration instead of delegation
val listRemotePublications by tasks.registering(ListRemotePublicationsFromApiTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "List remote GitHub and Plugin Portal publications using API integration"
    dependsOn(validateCredentials)

    // Use centralized credential management
    gitHubToken.set(xdkPublishingCredentials.gitHubPassword)
    enablePluginPortal.set(xdkPublishingCredentials.enablePluginPortal)
    gradlePublishKey.set(xdkPublishingCredentials.gradlePublishKey)
    pluginId.set(getXdkProperty("org.xtclang.plugin.id"))
}

// Handle deleteRemotePublications with delegation
val deleteRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates 'deleteRemotePublications' tasks for builds with publications."
    includedBuildsWithPublications.forEach { it ->
        dependsOn(it.task(":deleteRemotePublications"))
    }
}
