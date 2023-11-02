import XdkBuildLogic.Companion.XDK_TASK_GROUP_DEBUG

/**
 * Utilities for debugging the build. Please note that some of these tasks extract contents from
 * Providers, which means that it may slow the build down, both configuring and/or running the tasks.
 *
 * Use with caution. In general; just remember the caveat: this logic may slow the build down,
 * by evaluating lazy information too early.
 */

val printResolvedConfigFiles by tasks.registering {
    group = XDK_TASK_GROUP_DEBUG
    description = "Prints the files in a resolved configuration."
    doLast {
        printAllResolvedConfigFiles()
    }
}

val printTaskDependencies by tasks.registering {
    group = XDK_TASK_GROUP_DEBUG
    description = "Prints the files in a resolved configuration."
    doLast {
        printAllTaskDependencies()
    }
}

val printPublications by tasks.registering {
    group = XDK_TASK_GROUP_DEBUG
    description = "Prints the declared publications in a project"
    doLast {
        printPublications()
    }
}

val printTaskInputs by tasks.registering {
    group = XDK_TASK_GROUP_DEBUG
    description = "Prints the inputs for all tasks."
    doLast {
        printAllTaskInputs()
    }
}

val printTaskOutputs by tasks.registering {
    group = XDK_TASK_GROUP_DEBUG
    description = "Prints the outputs for all tasks."
    doLast {
        printAllTaskOutputs()
    }
}