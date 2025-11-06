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
        logger.info(
            """
            [aggregator] Start parameter tasks: ${startParameter.taskNames}
            [aggregator] Start parameter init scripts: ${startParameter.allInitScripts}
        """.trimIndent()
        )

        // Use Gradle's TaskExecutionRequest API to extract task names
        // Since Gradle doesn't expose a clean API to distinguish task names from option values,
        // we use a heuristic: skip arguments that start with '-' AND skip the next argument
        // if the previous one was a long option (--xxx) without '='
        val actualTasks = startParameter.taskRequests.flatMap { request ->
            val tasks = mutableListOf<String>()
            var skipNext = false

            for (arg in request.args) {
                when {
                    skipNext -> {
                        // This is the value for a previous --option, skip it
                        skipNext = false
                    }
                    arg.startsWith("--") -> {
                        // Long option: if it doesn't contain '=', the next arg might be its value
                        skipNext = !arg.contains("=")
                    }
                    arg.startsWith("-") -> {
                        // Short option (like -x): these typically don't take separate value args
                        // or use attached format (-Pkey=value), so don't skip next
                    }
                    else -> {
                        // Not an option, this is a task name
                        tasks.add(arg)
                    }
                }
            }
            tasks
        }

        logger.info("[aggregator] Resolved actual tasks from TaskExecutionRequest API: $actualTasks")

        if (actualTasks.size > 1) {
            val conflictingTasks = actualTasks.filter { it == "clean" || lifeCycleTasks.contains(it) }
            val allowMultipleTasks = (project.properties["allowMultipleTasks"]?.toString() ?: "false").toBoolean()

            when {
                conflictingTasks.size > 1 -> {
                    val msg = "[aggregator] Multiple conflicting lifecycle tasks detected. Please run lifecycle tasks individually: $conflictingTasks"
                    logger.error(msg)
                    throw GradleException(msg)
                }
                !allowMultipleTasks -> {
                    val msg = """
                        [aggregator] Multiple tasks detected.
                        Please run tasks individually or use -PallowMultipleTasks=true if you know exactly what you are doing: $actualTasks
                    """.trimIndent().replace("\n", " ")
                    logger.error(msg)
                    throw GradleException(msg)
                }
                else -> logger.info("[aggregator] Multiple tasks allowed via -PallowMultipleTasks=true: $actualTasks")
            }
        }

        logger.info("[aggregator] Start Parameter(s): $startParameter")
    }
}

XdkBuildAggregator(project).run()
