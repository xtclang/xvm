package org.xvm.lsp.protocol

/**
 * `xtc/run` parameters. [module] is a module name or root URI; a client that sends a root URI also sends the
 * module's name as [moduleName], which identifies the module when the URI no longer does, as after its root file
 * was renamed or moved. The client chooses [runId] so it can attach its console before any output arrives;
 * [args] are passed to the entry method as given, never shell-parsed. Without a [method], or with no [args], the run
 * takes those that the module's Gradle project's `xtcRun` runs it with, where the imported build model has them, and
 * `run` and none otherwise; it runs with `xtcRun`'s JVM options. With [tests], xunit runs the module's tests that they
 * select instead, all of them for an empty list, as `xtc test` does, and the server reports their execution as
 * `xtc/testEvent` notifications ([TestEventParams]), which are not in the output; the entry method and its arguments
 * are not used, and the tests run with the JVM options of the project's `xtcTest`.
 */
data class RunParams(
    val module: String? = null,
    val method: String? = null,
    val args: List<String>? = null,
    val workingDirectory: String? = null,
    val runId: String? = null,
    val moduleName: String? = null,
    val tests: List<TestSelector>? = null,
)

/** `xtc/run` result: the build outcome, and the run's ID when the build succeeded and the run started. */
data class RunResponse(
    val runId: String? = null,
    val build: BuildResponse = BuildResponse(),
)

/** A Run lens of a document: the module it runs and the zero-based line it is on. */
data class RunLens(
    val module: String = "",
    val line: Int = 0,
)

data class StopRunParams(
    val runId: String? = null,
)

/**
 * `xtc/runInput` parameters: [text] for the program's console, sent as typed, with line ends; [end] ends
 * the input, after which a program waiting for a line gets an empty one.
 */
data class RunInputParams(
    val runId: String? = null,
    val text: String? = null,
    val end: Boolean? = null,
)

data class RunOutputParams(
    val runId: String = "",
    val text: String = "",
)

/** `xtc/testEvent` parameters: an [event] of the tests that the run [runId] runs, as xunit reported it. */
data class TestEventParams(
    val runId: String = "",
    val event: TestEvent? = null,
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String = "",
    val exitCode: Long? = null,
    val error: String? = null,
)

/**
 * `xtc/replacedLibraries` parameters: the bundled libraries that the workspace's own modules replace, by name; the
 * workspace [folders], as URIs, whose modules the server does not build or run, which then build and run with Gradle;
 * and, by folder URI, why, as the editors say it after the folder's name, its [reasons]: the folder builds with another
 * XDK than the bundled one, which it names, as in `builds with XDK 0.5.0, not the bundled XDK 0.4.4`, or replaces
 * bundled libraries with its own modules. A folder without a reason, as from an older server, is one whose modules are
 * built against the replaced [libraries].
 */
data class ReplacedLibrariesParams(
    val libraries: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val reasons: Map<String, String> = emptyMap(),
)

/** `xtc/gradleProjects` parameters: the Gradle project that owns each source module, by module name, as its path in the build tree. */
data class GradleProjectsParams(
    val projects: Map<String, String> = emptyMap(),
)
