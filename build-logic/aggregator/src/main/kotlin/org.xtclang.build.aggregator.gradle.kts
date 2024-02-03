import org.gradle.api.logging.configuration.ShowStacktrace.ALWAYS
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
                val msg = "$prefix Multiple start parameter tasks are not guaranteed to in order/in parallel. Please run each task individually."
                logger.error(msg)
                throw GradleException(msg)
            }

            if (isBuildScan) {
                // Make sure we always put stack traces if a build scan is enabled.
                logger.lifecycle("$prefix Build scans are enabled, current stack trace setting: $showStacktrace")
                val scanShowStacktrace = if (showStacktrace.ordinal < ALWAYS.ordinal) ALWAYS else showStacktrace
                if (showStacktrace != scanShowStacktrace) {
                    logger.lifecycle("$prefix     Enabling more verbose stack traces for build scan forensics: $showStacktrace -> $scanShowStacktrace")
                    showStacktrace = scanShowStacktrace
                }
            }

            if ((isBuildScan || System.getenv("CI") != null) && logLevel == LogLevel.DEBUG) {
                logger.lifecycle("$prefix CI environment detected, reducing DEBUG log level to INFO to be less susceptible to security leaks.")
                logLevel = LogLevel.INFO
            }
        }

        logger.info("$prefix Start Parameter(s): $startParameter")
    }
}

XdkBuildAggregator(project).run()
