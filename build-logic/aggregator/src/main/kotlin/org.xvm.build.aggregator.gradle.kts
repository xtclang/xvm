import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

internal val Project.prefix get() = "[$name]"
private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOfNotNull(xdk, plugin)

startBuildAggregator()

fun startBuildAggregator() {
    logger.lifecycle("$prefix Aggregating included build tasks:")
    gradle.includedBuilds.forEachIndexed { i, includedBuild ->
        logger.lifecycle("$prefix     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
    }

    val startParameter = gradle.startParameter
    val startParameterTasks: List<String> = startParameter.taskNames
    if (startParameterTasks.isNotEmpty()) {
        logger.info("$prefix Start parameter tasks: $startParameterTasks")
        if (startParameterTasks.size > 1 && startParameterTasks.find { it.contains("taskTree") }.isNullOrEmpty()) { // exemption granted for taskTree, which needs another tasks to instrument, and that is not part of the build.
            val msg = "$prefix Multiple start parameter tasks are not guaranteed to work. Please run each task individually."
            logger.error(msg)
            throw GradleException(msg)
        }
    }

    logger.info("$prefix Start Parameter: $startParameter")
}

listOfNotNull(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME).forEach { taskName ->
    logger.info("$prefix Creating aggregated lifecycle task: ':$taskName' in project '${project.name}'")
    tasks.named(taskName) {
        group = BUILD_GROUP
        description = "Aggregates and executes the '$taskName' task for all included builds."
        gradle.includedBuilds.forEach { includedBuild ->
            dependsOn(includedBuild.task(":$taskName"))
            logger.info("$prefix     Attaching: dependsOn(':$name' <- ':${includedBuild.name}:$name')")
        }
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
    }
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregated) all artifacts in the XDK to the local Maven repository."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
    }
}

val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
}

listOfNotNull("list", "delete").forEach { taskPrefix ->
    val taskName = "${taskPrefix}GitHubPublications"
    tasks.register(taskName) {
        group = PUBLISH_TASK_GROUP
        description = "Task that aggregates '$taskName' tasks for builds with publications."
        includedBuildsWithPublications.forEach {
            dependsOn(it.task(":$taskName"))
        }
    }
}

val install by tasks.registering {
    group = "distribution"
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    dependsOn(xdk.task(":install"))
    doLast {
        logger.lifecycle("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

val installLocalDist by tasks.registering {
    group = "distribution"
    description = "Build and overwrite any local distribution with the new distribution produced by the build."
    dependsOn(gradle.includedBuild("xdk").task(":installLocalDist"))
    doLast {
        logger.lifecycle("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

val importUnicodeFiles by tasks.registering {
    group = BUILD_GROUP
    description = "Download and regenerate the unicode file as resources."
    dependsOn(gradle.includedBuild("xdk").task(":lib-ecstasy:importUnicodeFiles"))
    doLast {
        logger.lifecycle("$prefix Finished $name (generated and imported new unicode files)")
    }
}
