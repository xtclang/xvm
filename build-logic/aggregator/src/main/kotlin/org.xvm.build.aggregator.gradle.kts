import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

fun logAggregation() {
    logger.lifecycle("[xvm] Aggregating included builds:")
    gradle.includedBuilds.forEachIndexed { i, includedBuild ->
        logger.lifecycle("[xvm]     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
    }
}

logAggregation()

val startParameterTasks: List<String> = project.gradle.startParameter.taskNames
if (startParameterTasks.isNotEmpty()) {
    logger.lifecycle("[xvm] Start parameter tasks: $startParameterTasks")
}

listOfNotNull(
    BUILD_TASK_NAME,
    CHECK_TASK_NAME,
    CLEAN_TASK_NAME,
    TEST_TASK_NAME
).forEach {
    fun Task.attachDependencies() {
        logger.lifecycle("[xvm] Creating aggregated lifecycle task: ':xvm:$name' in project '${project.name}'")
        gradle.includedBuilds.forEach { includedBuild ->
            dependsOn(includedBuild.task(":$name"))
            logger.info("[xvm]     Attaching: dependsOn(':xvm:$name' <- ':${includedBuild.name}:$name')")
        }
    }

    val existingTask = tasks.findByName(it)
    if (existingTask != null) {
        tasks.named(it) {
            attachDependencies()
        }
    } else {
        tasks.register(it) {
            attachDependencies()
        }
    }
}

/*
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 */

mapOf(
    "publishTo" to "MavenLocal",
    "publishAllPublicationsTo" to "GitHubRepository"
).forEach { (taskPrefix, repo) ->
    val prefix = "[${project.name}]"
    val taskName = "$taskPrefix$repo" // e.g. publishToMavenLocal

    val task = project.tasks.findByName(taskName)
    logger.info("$prefix Checking if we should add an inter-dependency task '$taskName' in ${project.name}")

    if (task != null) {
        logger.info("$prefix Skipping publishing task dependency extension. Publication logic already exists: '$taskName'")
        return@forEach
    }

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

    /*
     * For all included builds that have publications (the XTC plugin, and the XDK root project), register
     * identically named tasks to their publication tasks in the XVM root repo, so that we can do
     * things like ./gradlew publishToMavenLocal from the command line, without qualifying a project.
     * A publish method in the XVM root project, should depend on the corresponding publish tasks in the
     * included builds, so that all of them will be executed when running the XDK root version of the task.
     */
    val xvmRootPublishSubcomponentTask = tasks.register(taskName) {
        group = PUBLISH_TASK_GROUP
        description = "Publishes all XTC/XTC Plugin artifacts to the '$repo'"
        val isLocalRepo = repo.endsWith("Local")
        logger.info("$prefix Registering aggregated includedBuild dependent publication task: $taskName (isLocalRepo: $isLocalRepo).")
        gradle.includedBuilds
            .filter { hasPublishingPlugin(it) }
            .forEach { includedBuild ->
                assert(!includedBuild.projectDir.path.contains("build-logic"))
                logger.info("$prefix    Publication dependency: ':${project.name}:$taskName' <- dependsOn ':${includedBuild.name}:$taskName'")
                dependsOn(includedBuild.task(":$taskName"))
            }
    }

    /*
     * Create the aggregated xvm:publish task.
     */
    val xvmRootPublish = tasks.findByName(PUBLISH_LIFECYCLE_TASK_NAME)
    if (xvmRootPublish == null) {
        logger.info("$prefix Creating aggregated publish task.")
        tasks.register(PUBLISH_LIFECYCLE_TASK_NAME) {
            group = PUBLISH_TASK_GROUP
            description = "Aggregates and publishes all XTC/XTC Plugin artifacts to the '$repo'"
        }
    }
    tasks.named(PUBLISH_LIFECYCLE_TASK_NAME) {
        dependsOn(xvmRootPublishSubcomponentTask)
        logger.lifecycle("$prefix Publication (master) dependency: dependsOn(':${project.name}':$name' <- ':${project.name}:$taskName')")
    }
}
