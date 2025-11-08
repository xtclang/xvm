import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME
import org.gradle.kotlin.dsl.closureOf

plugins {
    base
}

private class XdkBuildAggregator(val project: Project) : Runnable {
    companion object {
        private val lifeCycleTasks =
            listOf(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME)
        // Diagnostic/help tasks that should be aggregated across all included builds
        private val diagnosticTasks = listOf("dependencies", "properties", "buildEnvironment", "versions")
    }

    override fun run() {
        gradle.includedBuilds.forEachIndexed { i, includedBuild ->
            logger.info("[aggregator]     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
        }

        val ignoredIncludedBuilds = gradle.includedBuilds.map { it.name }.filter {
            val attachKey = "includeBuildAttach${it.replaceFirstChar(Char::titlecase)}"
            val attach = (properties[attachKey]?.toString() ?: "true").toBoolean()
            if (!attach) {
                logger.info("[aggregator] Included build '$it' is explicitly configured to be outside the root lifecycle ($attachKey: false).")
            }
            !attach
        }.toSet()

        aggregateTasks(lifeCycleTasks, BUILD_GROUP, "lifecycle", ignoredIncludedBuilds)
        aggregateTasks(diagnosticTasks, "help", "diagnostic", ignoredIncludedBuilds)
    }

    private fun aggregateTasks(taskNames: List<String>, group: String, taskType: String, ignored: Set<String>) {
        taskNames.forEach { taskName ->
            logger.info("[aggregator] Creating aggregated $taskType task: ':$taskName' in project '${project.name}'")
            // Use findByName first, then create or configure
            val task = tasks.findByName(taskName) ?: tasks.register(taskName).get()
            task.apply {
                this.group = group
                description = "Aggregates and executes the '$taskName' task for all included builds."

                // Special check for 'clean' task - it cannot run with other lifecycle tasks
                if (taskName == CLEAN_TASK_NAME) {
                    gradle.taskGraph.whenReady(closureOf<TaskExecutionGraph> {
                        // Only check if clean is actually in the task graph
                        val cleanInGraph = allTasks.any { it.project == project && it.name == CLEAN_TASK_NAME }
                        if (!cleanInGraph) {
                            return@closureOf
                        }

                        val requestedTasks = gradle.startParameter.taskRequests.flatMap { it.args }
                            .filter { !it.startsWith("-") }
                        val otherLifecycleTasks = requestedTasks.filter { it != CLEAN_TASK_NAME && lifeCycleTasks.contains(it) }

                        logger.info("[aggregator] Clean task in graph. Requested tasks: $requestedTasks")

                        if (otherLifecycleTasks.isNotEmpty()) {
                            val msg = """

                                ================================================================================
                                [aggregator] FORBIDDEN: 'clean' cannot run with other tasks: $otherLifecycleTasks

                                The 'clean' task conflicts with other lifecycle tasks in composite builds.
                                Run tasks individually:
                                  ./gradlew clean
                                  ./gradlew build
                                ================================================================================

                            """.trimIndent()
                            logger.error(msg)
                            throw GradleException(msg)
                        }
                    })
                }

                // Filter included builds: exclude explicitly ignored builds and all build-logic projects
                val buildsToAggregate = gradle.includedBuilds
                    .filterNot { ignored.contains(it.name) }
                    .filterNot {
                        // Never aggregate build-logic projects - they're infrastructure, not application code
                        it.projectDir.absolutePath.contains("/build-logic/")
                    }

                buildsToAggregate.forEach { includedBuild ->
                    try {
                        dependsOn(includedBuild.task(":$taskName"))
                        logger.info("[aggregator]     Attaching: dependsOn(':$taskName' <- ':${includedBuild.name}:$taskName')")
                    } catch (e: Exception) {
                        logger.info("[aggregator]     Skipping: '${includedBuild.name}' doesn't have ':$taskName'")
                    }
                }
            }
        }
    }

}

XdkBuildAggregator(project).run()
