import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CLEAN_TASK_NAME

plugins {
    base
}

class XdkBuildAggregator(project: Project) : Runnable {
    companion object {
        private val lifeCycleTasks = listOfNotNull(ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, CHECK_TASK_NAME, CLEAN_TASK_NAME)
    }

    private val prefix = "[${project.name}]"

    override fun run() {
        logger.info("$prefix Aggregating included build tasks:")
        gradle.includedBuilds.forEachIndexed { i, includedBuild ->
            logger.info("$prefix     Included build #$i: ${includedBuild.name} [project dir: ${includedBuild.projectDir}]")
        }

        checkStartParameterState()
        aggregateLifeCycleTasks()
    }

    private fun aggregateLifeCycleTasks() {
        lifeCycleTasks.forEach { taskName ->
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

    private fun checkStartParameterState() {
        val startParameter = gradle.startParameter
        with (startParameter) {
            logger.info("""
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
    }
}

XdkBuildAggregator(project).run()
