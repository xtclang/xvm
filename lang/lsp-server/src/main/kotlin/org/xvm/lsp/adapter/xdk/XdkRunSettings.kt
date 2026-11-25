package org.xvm.lsp.adapter.xdk

/**
 * What a source module's Gradle project runs it with, from the imported build model: the [method] and [args] that the
 * project's `xtcRun` runs the module with, both null where `xtcRun` does not name it, and the JVM options of the
 * project's `xtcRun`, [jvmArgs], and of its `xtcTest`, [testJvmArgs]; and what of each the model's export could not
 * read, and left out, [problems] and [testProblems]. `runXtc` and `testXtc` take these unless their own configuration
 * changes them, which the model does not see. [project] is the project's path in the build tree, null from a model
 * without one.
 */
internal data class XdkRunSettings(
    val project: String?,
    val method: String?,
    val args: List<String>?,
    val jvmArgs: List<String>,
    val testJvmArgs: List<String>,
    val problems: List<String> = emptyList(),
    val testProblems: List<String> = emptyList(),
) {
    /** The project's [extension], `xtcRun` or `xtcTest`, as a run's output names it: `Gradle project :app's xtcRun`. */
    fun source(extension: String): String =
        when (project) {
            null -> "Gradle's $extension"
            ":" -> "the Gradle root project's $extension"
            else -> "Gradle project $project's $extension"
        }
}
