import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */
//group = libs.versions.group.xdk.get()
//version = libs.versions.xdk.get()

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xdk.build.aggregator)
    alias(libs.plugins.tasktree)
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOfNotNull(xdk, plugin)

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */

val install by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    doLast {
        logger.info("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

install {
    XdkDistribution.distributionTasks.forEach {
        dependsOn(xdk.task(":$it"))
    }
    dependsOn(xdk.task(":installDist"))
}

val installLocalDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Build and overwrite any local distribution with the new distribution produced by the build."
    dependsOn(xdk.task(":$name"))
    doLast {
        logger.lifecycle("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

fun getTaskStateDescriptor(state: TaskState): Map<String, Any> {
    return buildMap {
        put("didWork", state.didWork)
        put("executed", state.executed)
    }
}

val installInitScripts by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Install bootstrapping scripts for the XTC Organization GitHub Maven package registry."
    dependsOn(xdk.task(":$name"))
    doLast {
        logger.lifecycle("$prefix Finished '$name' [task state: ${getTaskStateDescriptor(state)}]")
    }
}

/*
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 *
 * Publishing tasks can be racy, but it seems that Gradle serializes tasks that have a common
 * output directory, which should be the case here. If not, we will have to put back the
 * parallel check/task failure condition.
 */

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregate) all artifacts in the XDK to the remote repositories."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":publishAllPublicationsToGitHubRepository"))
        // TODO: Add gradlePluginPortal() and mavenCentral() here, when we have an official release to publish (will be done immediately efter plugin branch gets merged to master)
    }
    doLast {
        logger.lifecycle("$prefix Finished '$name'.")
    }
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregated) all artifacts in the XDK to the local Maven repository."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
    }
    doLast {
        logger.lifecycle("$prefix Finished '$name'.")
    }
}

val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
    doLast {
        logger.lifecycle("$prefix Finished '$name'.")
    }
}

GitHubPackages.publishTaskPrefixes.forEach { prefix ->
    buildList {
        addAll(GitHubPackages.publishTaskSuffixesLocal)
        addAll(GitHubPackages.publishTaskSuffixesRemote)
    }.forEach { suffix ->
        val taskName = "$prefix$suffix"
        tasks.register(taskName) {
            group = PUBLISH_TASK_GROUP
            description = "Task that aggregates '$taskName' tasks for builds with publications."
            includedBuildsWithPublications.forEach {
                dependsOn(it.task(":$taskName"))
            }
            doLast {
                logger.lifecycle("$prefix Finished '$name'.")
            }
        }
    }
}
