import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

private class XdkBuildAggregator(project: Project) : Runnable {
    companion object {
        private val lifeCycleTasks =
            listOf(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME)
    }


    override fun run() {
        gradle.includedBuilds.forEachIndexed { i, includedBuild ->
            logger.info("Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
        }

        val ignoredIncludedBuilds = gradle.includedBuilds.map { it.name }.filter {
            val attachKey = "includeBuildAttach${it.replaceFirstChar(Char::titlecase)}"
            val attach = (properties[attachKey]?.toString() ?: "true").toBoolean()
            if (!attach) {
                logger.info("Included build '$it' is explicitly configured to be outside the root lifecycle ($attachKey: false).")
            }
            !attach
        }.toSet()

        checkStartParameterState()
        aggregateLifeCycleTasks(ignoredIncludedBuilds)
    }

    private fun aggregateLifeCycleTasks(ignored: Set<String>) {
        lifeCycleTasks.forEach { taskName ->
            logger.info("Creating aggregated lifecycle task: ':$taskName' in project '${project.name}'")
            tasks.named(taskName) {
                group = BUILD_GROUP
                description = "Aggregates and executes the '$taskName' task for all included builds."
                gradle.includedBuilds.filterNot { ignored.contains(it.name) }.forEach { includedBuild ->
                    dependsOn(includedBuild.task(":$taskName"))
                    logger.info("Attaching: dependsOn(':$name' <- ':${includedBuild.name}:$name')")
                }
            }
        }
        
        // Register a custom cleanBuild task that provides clear instructions
        tasks.register("cleanBuild") {
            group = BUILD_GROUP
            description = "Safe alternative to 'clean build' - provides instructions for proper execution order."
            
            doLast {
                logger.lifecycle("")
                logger.lifecycle("=== CleanBuild Task ===")
                logger.lifecycle("This task demonstrates the proper way to run clean followed by build.")
                logger.lifecycle("")
                logger.lifecycle("To achieve clean + build safely in composite builds, run:")
                logger.lifecycle("  ./gradlew clean && ./gradlew build")
                logger.lifecycle("")
                logger.lifecycle("Or use this convenience script:")
                logger.lifecycle("  ./gradlew clean")
                logger.lifecycle("  (waiting for completion...)")
                logger.lifecycle("  ./gradlew build")
                logger.lifecycle("")
                logger.lifecycle("This avoids deadlocks in composite builds while achieving the same result.")
                logger.lifecycle("======================")
                logger.lifecycle("")
            }
            
            logger.info("Registered cleanBuild task as documentation for safe clean+build usage")
        }
    }

    private fun checkStartParameterState() {
        val startParameter = gradle.startParameter
        with(startParameter) {
            logger.info(
                """
            Start parameter tasks: $taskNames
            Start parameter init scripts: $allInitScripts
        """.trimIndent()
            )

            // Check for problematic task combinations in composite builds
            val nonOptionTasks = taskNames.filter { !it.startsWith("-") && !it.contains("taskTree") }
            val hasCleanTask = nonOptionTasks.any { it == "clean" }
            val hasConstructiveTasks = nonOptionTasks.any { it != "clean" }
            
            when {
                nonOptionTasks.size > 1 && hasCleanTask && hasConstructiveTasks -> {
                    val otherTasks = nonOptionTasks.filter { it != "clean" }.joinToString(" ")
                    val msg = "Mixing 'clean' with other tasks can cause deadlocks in composite builds. " +
                             "Use './gradlew cleanBuild' for a safe clean+build, or run separately: " +
                             "'./gradlew clean && ./gradlew $otherTasks' (task names: $taskNames)"
                    logger.error(msg)
                    throw GradleException(msg)
                }
                nonOptionTasks.size > 1 -> {
                    logger.info("Running ${nonOptionTasks.size} non-destructive tasks in composite build: ${nonOptionTasks.joinToString(", ")}")
                }
                nonOptionTasks.size == 1 -> {
                    logger.info("Running single task: ${nonOptionTasks.first()}")
                }
            }
        }

        logger.info("Start Parameter(s): $startParameter")
    }
}

XdkBuildAggregator(project).run()
