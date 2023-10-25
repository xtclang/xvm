import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

val Project.prefix get() = "[$name]"

startBuildAggregator()

fun startBuildAggregator() {
    logger.lifecycle("$prefix Aggregating included builds:")
    gradle.includedBuilds.forEachIndexed { i, includedBuild ->
        logger.lifecycle("$prefix     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
    }

    val startParameter = gradle.startParameter
    val startParameterTasks: List<String> = startParameter.taskNames
    if (startParameterTasks.isNotEmpty()) {
        logger.lifecycle("$prefix Start parameter tasks: $startParameterTasks")
        if (startParameterTasks.size > 1) {
            logger.warn("$prefix Multiple start parameter tasks are not guaranteed to work. Please run each task individually.")
        }
    }

    logger.info("$prefix Start Parameter: $startParameter")
}

listOfNotNull(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME).forEach { taskName ->
    logger.lifecycle("$prefix Creating aggregated lifecycle task: ':$taskName' in project '${project.name}'")
    tasks.named(taskName) {
        group = BUILD_GROUP
        description = "Aggregates and executes the '$taskName' task for all included builds."
        gradle.includedBuilds.forEach { includedBuild ->
            dependsOn(includedBuild.task(":$taskName"))
            logger.info("$prefix     Attaching: dependsOn(':$name' <- ':${includedBuild.name}:$name')")
        }
    }
}

val includedBuildsWithPublications = gradle.includedBuilds.filter {
    fun hasPublishingPlugin(includedBuild: IncludedBuild): Boolean {
        val name = includedBuild.name
        val path = includedBuild.projectDir.path
        if (path.contains("build-logic")) {
            logger.info("$prefix Skipping publications for 'build-logic' project: $name")
            return false
        }
        val hasPublications = when (name) {
            "xdk", "plugin" -> {
                logger.info("$prefix Included build '$name' has publishing logic; connecting to :xvm:publish* tasks.")
                true
            }

            else -> false
        }
        return hasPublications
    }
    hasPublishingPlugin(it)
}

/*
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 */

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":publishAllPublicationsToGitHubRepository"))
    }
    doFirst {
        checkParallel(name)
    }
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":publishToMavenLocal"))
    }
    doFirst {
        checkParallel(name)
    }
}

val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
    doFirst {
        checkParallel(name)
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
        doFirst {
            checkParallel(taskName)
        }
    }
}

fun checkParallel(taskName: String): Boolean {
    if (gradle.startParameter.isParallelProjectExecutionEnabled) {
        logger.error("$prefix WARNING: Task '$taskName'; parallel project may be racy due to existing issues in Gradle. Please clean and re-run the task as './gradlew $name --no-parallel')")
        return true
    }
    return false
}



