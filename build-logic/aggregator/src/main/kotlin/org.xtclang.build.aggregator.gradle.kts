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
            listOf(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME, "installDist")
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
        aggregateLifeCycleTasks(ignoredIncludedBuilds)
    }

    private fun aggregateLifeCycleTasks(ignored: Set<String>) {
        lifeCycleTasks.forEach { taskName ->
            // Only aggregate tasks that exist in the root project
            tasks.findByName(taskName)?.let {
                logger.info("[aggregator] Creating aggregated lifecycle task: ':$taskName' in project '${project.name}'")
                tasks.named(taskName) {
                    group = BUILD_GROUP
                    description = "Aggregates and executes the '$taskName' task for all included builds."
                    gradle.includedBuilds.filterNot { ignored.contains(it.name) }.forEach { includedBuild ->
                        dependsOn(includedBuild.task(":$taskName"))
                        logger.info("[aggregator]     Attaching: dependsOn(':$name' <- ':${includedBuild.name}:$name')")
                    }
                }
            } ?: run {
                logger.info("[aggregator] Skipping aggregated lifecycle task: ':$taskName' (not found in root project)")
            }
        }
        
        // Create special tasks that handle clean + other task combinations
        val otherLifecycleTasks = lifeCycleTasks.filter { it != CLEAN_TASK_NAME }
        otherLifecycleTasks.forEach { taskName ->
            tasks.register("cleanAnd${taskName.replaceFirstChar(Char::titlecase)}") {
                group = BUILD_GROUP
                description = "Runs clean first, then $taskName to avoid task interference."
                dependsOn(CLEAN_TASK_NAME)
                finalizedBy(taskName)
                
                doLast {
                    logger.info("[aggregator] Completed clean, now running $taskName")
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

            // Check if clean is mixed with other lifecycle tasks
            val nonFlagTasks = taskNames.filter { !it.startsWith("-") }
            val hasClean = nonFlagTasks.contains("clean")
            val otherLifecycleTasks = nonFlagTasks.filter { it != "clean" && lifeCycleTasks.contains(it) }
            
            if (hasClean && otherLifecycleTasks.isNotEmpty()) {
                val cleanIndex = nonFlagTasks.indexOf("clean")
                if (cleanIndex == 0) {
                    // Clean is first - make other tasks depend on clean
                    logger.info("[aggregator] Clean task detected first in command line with other lifecycle tasks: $otherLifecycleTasks")
                    logger.info("[aggregator] Making other lifecycle tasks depend on clean to ensure proper execution order")
                    
                    // Configure dependencies after build script evaluation
                    gradle.projectsEvaluated {
                        otherLifecycleTasks.forEach { taskName ->
                            tasks.findByName(taskName)?.let { task ->
                                task.dependsOn(CLEAN_TASK_NAME)
                                logger.info("[aggregator] Made $taskName depend on clean")
                            }
                        }
                    }
                } else {
                    // Clean is not first - show warning but allow it
                    logger.warn("[aggregator] Clean task detected but not first in command line. For best results, put clean first: './gradlew clean ${otherLifecycleTasks.joinToString(" ")}'")
                }
            } else if (nonFlagTasks.filter { lifeCycleTasks.contains(it) }.size > 1 && !hasClean) {
                // Original logic for other conflicting tasks (excluding clean case)
                val conflictingTasks = nonFlagTasks.filter { lifeCycleTasks.contains(it) }
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
