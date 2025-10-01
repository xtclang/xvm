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
        private val diagnosticTasks = listOf("dependencies", "properties", "buildEnvironment")
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
            tasks.named(taskName) {
                this.group = group
                description = "Aggregates and executes the '$taskName' task for all included builds."
                gradle.includedBuilds.filterNot { ignored.contains(it.name) }.forEach { includedBuild ->
                    dependsOn(includedBuild.task(":$taskName"))
                    logger.info("[aggregator]     Attaching: dependsOn(':$name' <- ':${includedBuild.name}:$name')")
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
