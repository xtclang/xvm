import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

// TODO get around to using a custom logger for annotated log entries. We postpone until the XTC plugin is merged though.
private val Project.prefix get() = "[$name]"
private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOfNotNull(xdk, plugin)

internal val startBuildAggregator = Runnable {
    logger.lifecycle("$prefix Aggregating included build tasks:")
    gradle.includedBuilds.forEachIndexed { i, includedBuild ->
        logger.lifecycle("$prefix     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
    }

    val startParameter = gradle.startParameter
    with (startParameter) {
        logger.lifecycle("""
            $prefix Start parameter tasks: $taskNames
            $prefix Start parameter init scripts: $allInitScripts
        """.trimIndent())

        if (taskNames.count { !it.startsWith("-") && !it.contains("taskTree") } > 1) {
            val msg = "$prefix Multiple start parameter tasks are not guaranteed to work. Please run each task individually."
            logger.error(msg)
            throw GradleException(msg)
        }
    }

    logger.info("$prefix Start Parameter(s): $startParameter")

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
}

startBuildAggregator.run()

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
    doLast {
        logger.lifecycle("$prefix Finished $name.")
    }
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
    dependsOn(xdk.task(":installDist"))
    doLast {
        logger.lifecycle("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

val installLocalDist by tasks.registering {
    group = "distribution"
    description = "Build and overwrite any local distribution with the new distribution produced by the build."
    dependsOn(xdk.task(":$name"))
    doLast {
        logger.lifecycle("$prefix Finished $name (overwrote existing XDK distribution).")
    }
}

val installInitScripts by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Build and overwrite any local distribution with the new distribution produced by the build."
    dependsOn(xdk.task(":$name"))
    doLast {
        logger.lifecycle("$prefix Finished '$name' (task state: $state.)")
    }
}

val importUnicodeFiles by tasks.registering {
    group = BUILD_GROUP
    description = "Download and regenerate the unicode file as resources."
    dependsOn(xdk.task(":$name"))
    doLast {
        logger.lifecycle("$prefix Finished '$name' (generated and imported new unicode files)")
    }
}
