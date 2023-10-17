import org.gradle.api.Project

/**
 * Simple helper functions, mostly printing out dependencies, inputs and outputs and other
 * useful information for an XDK build, that is a bit too brief to deserve tasks of its own.
 */

fun Project.printResolvedConfigFiles(configName: String) {
    // This only works on resolved configurations, and after the configuration phase.
    val config = configurations.getByName(configName)
    if (!config.isCanBeResolved) {
        logger.warn("$prefix Configuration '$configName' is not resolvable.")
        return
    }
    val files = config.resolvedConfiguration.resolvedArtifacts.map { it.file }
    logger.lifecycle("$prefix Configuration '$configName' has ${files.size} files:")
    files.forEach {
        logger.lifecycle("$prefix    file: '$it'")
    }
}

fun Project.printTaskDependencies(taskName: String) {
    val task = project.tasks.findByName(taskName) ?: throw buildException("No task named '$taskName' found.")
    //Calling this method from a task action is not supported when configuration caching is enabled.
    val deps = task.taskDependencies.getDependencies(task).toSet()
    logger.lifecycle("$prefix Task '$taskName' depends on ${deps.size} other tasks.")
    deps.forEach {
        logger.lifecycle("$prefix    $taskName <- dependsOn: '$it.name'")
    }
    project.tasks.forEach {
        if (it.dependsOn.contains(task)) {
            logger.lifecycle("$prefix    $taskName -> isDependencyOf: '$it.name'")
        }
    }
}

fun Project.printTaskInputs(taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.inputs.files
    logger.lifecycle("$prefix Task '$taskName' has ${outputs.files.size} inputs:")
    outputs.asFileTree.forEach { println("$prefix   input : '$it'") }
}

fun Project.printTaskOutputs(taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.outputs.files
    logger.lifecycle("$prefix Task '$taskName' has ${outputs.files.size} outputs:")
    outputs.asFileTree.forEach { println("$prefix   output: '$it'") }
}
