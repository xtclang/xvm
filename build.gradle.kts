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

// Hacky due to bootstrapping reasons. On the other hand, I realise we can probably very much
// simplify the version catalog and project version logic. We could have a pre pass and turn a
// libs.versions.toml.template (or something) into the real libs toml instead. That would simplify
// quite a bit, so sadly it would still have to be done at the settings level, though.
version = file("VERSION").readText().trim()
group = file("GROUP").readText().trim()
// we should probably just marshall a build jreleaser releases tasks here into the
// publishable sub projects.
//xdkBuildLogic.versions().assignSemanticVersionFromCatalog()
//val semanticVersion: SemanticVersion by extra

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOf(xdk, plugin)

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
    XdkDistribution.distributionTasks.forEach {
        dependsOn(xdk.task(":$it"))
    }
    dependsOn(xdk.task(":$name"))
}

/*
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 *
 * If the snapshot=true property is set, we will build a snapshot release.
 *   However, if the VERSION file does not contain a snapshot version, we either fail or skip depending on another property
 *
 * If the snapshot=false property is set, we will build a normal release. Requires a tag.
 *   We should never attempt to build an existing release, it will fail on the GitHub side.
 */
val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregate) all artifacts in the XDK to the remote repositories."
    val versionMatches = checkSnapshot()
    if (versionMatches) {
        includedBuildsWithPublications.forEach {
            dependsOn(it.task(":$name"))
        }
    }
    onlyIf {
        if (!versionMatches) {
            logger.warn("$prefix Skipping snapshot publication. VERSION is not a snapshot.");
        }
        versionMatches
    }
}

/*
 * Publish local publications, which are the mavenLocal repositry, and the build repo, as part of
 * an XTC distribution archive. The former is on by default, the latter is off.
 */
val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregated) all artifacts in the XDK to the local Maven repository."
    val versionMatches = checkSnapshot()
    if (versionMatches) {
        includedBuildsWithPublications.forEach {
            dependsOn(it.task(":publishAllPublicationsToMavenLocalRepository"))
        }
    }
    onlyIf {
        versionMatches
    }
}

val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal)
    dependsOn(publishRemote)
}

listOf("list", "delete").forEach { prefix ->
    listOf("AllLocalPublications", "AllRemotePublications").forEach { suffix ->
        val taskName = "$prefix$suffix"
        tasks.register(taskName) {
            group = PUBLISH_TASK_GROUP
            description = "Task that aggregates '$taskName' tasks for builds with publications."
            includedBuildsWithPublications.forEach {
                dependsOn(it.task(":$taskName"))
            }
        }
    }
}

