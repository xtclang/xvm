import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

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

        checkStartParameterState()
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

    private fun checkStartParameterState() {
        val startParameter = gradle.startParameter
        with(startParameter) {
            logger.info(
                """
            [aggregator] Start parameter tasks: $taskNames
            [aggregator] Start parameter init scripts: $allInitScripts
        """.trimIndent()
            )

            // Allow multiple tasks except for clean and other lifecycle tasks that might conflict
            val conflictingTasks = taskNames.filter { 
                !it.startsWith("-") && 
                (it == "clean" || lifeCycleTasks.contains(it))
            }
            if (conflictingTasks.size > 1) {
                val msg =
                    "[aggregator] Multiple conflicting lifecycle tasks detected. Please run lifecycle tasks individually: $conflictingTasks"
                logger.error(msg)
                throw GradleException(msg)
            }
        }

        logger.info("[aggregator] Start Parameter(s): $startParameter")
    }
}

XdkBuildAggregator(project).run()
