import org.xtclang.plugin.launchers.ExecutionMode
import org.xtclang.plugin.tasks.XtcRunTask
import org.xtclang.plugin.tasks.XtcTestTask

/**
 * This is the manualTests project.
 *
 * PLEASE NOTE: This is not your "Hello world" app playground.
 *
 * Please use another project directory and/or GitHub repository for that, with the XDK as either
 * an included build, or as published artifacts.
 *
 * ManualTests is a standalone XTC project, which should only depend on the XDK.
 *
 * Depending on the "manualTests" properties set in ../gradle.properties, this project
 * will either be an includedBuild in the XDK build, but will only configure, not resolve or
 * compile anything, if not connected to the lifecycle, which cached is pretty much zero overhead.
 * Excluding manual tests completely from the composite build will make it invisible in an IDE,
 * (not just for the build lifecycle)
 *
 * Either way, you can always run these tasks from the CLI (or if you have the build included
 * from inside IntelliJ, and actually debug and test these tasks and the XDK implementation code
 * in the IDE).
 *
 *   ./gradlew build should be used to compile this project, not the explicit compile task.
 *
 *   ./gradlew :manualTests:compile<SourceSet>Xtc (will compile the source set "SourceSet",
 *              for main, compileXtc is the task by convention)
 *   ./gradlew :manualTests:runXtc (runs any modules in the global xtcRun extension, no-op if none)
 *   ./gradlew :manualTests:<other run tasks> (shows how to override configuration in xtcRun with
 *              your own modules to run)
 */

/**
 * PLUGIN SECTION.
 *
 * These are resolved through the Gradle version catalog mechanism. A version catalog
 * is a single source of truth for dependent artifacts for a Gradle project. For the XVM repo,
 * there is a "global" version catalog under $compositeBuildRootDirectory/gradle/libs/libs.versions.html.
 * This makes it possible to alias artifacts to type safe (compile time knowable) hierarchical names.
 * For example, "libs.plugins.xtc" will resolve to "org.xtclang:xtc-plugin:<xvm version>".
 * This, in turn, means that the build should install that artifact with that version. It will look
 * in any repositories you have specified. For example, if you have a section like
 *
 * repositories {
 *    mavenCentral()
 * }
 *
 * in your project settings, or defined in the build file (if you can, it is recommended to keep it in
 * settings (for a best-practise example, please see the xtc-template-app project)), the build will look
 * for the artifact in any local caches, and if not found, then ask the mavenCentral artifact repo
 * to if it knows about it and downloads/caches it. Basically, for any artifact on which you depend,
 * you need to know group, id and version, to fully resolve it. Sometimes you are only interested in
 * resolving any version, or latest version, or a version range, or a version with a specific Git tag, etc.
 * To see how that works, please refer to the Maven or Gradle user guides.
 *
 * Gradle also supports "included builds". This means that any artifact identifier can be substituted
 * with source control tree for a project that builds that artifact. This makes it possible for the XVM
 * to partially build and verify itself, and it decouples paths, project modes and other things from
 * configuration. Any change you will do in the XVM will "pretend" to be an artifact to its dependencies,
 * if their settings contain that particular project as an includedBuild. Hence, building the manualTests
 * module, will ensure that the plugin and the XDK are versioned, built and resolved from their code trees
 * in the XVM repo. But the build doesn't have to know about that. For all it knows, it just resolves
 * a named and versioned artifact. This build could be torn out and turn into a separate project and
 * still resolve the artifacts for any configured repository, without changing a line of code.
 *
 */

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.xtc)
}

/**
 * DEPENDENCIES SECTION
 *
 * The xdk cam be retrieved with the "xdk" consumer configuration, which will look for a file/dir
 * hierarchy that contains the modules from the javatools and lib directories of an XTC installation.
 * We currently publish XDK releases as zipped artifacts in our GitHub Maven
 * repository (and soon also on mavenCentral after the next official release). If you want to use
 * a zipped XDK artifact, use the "xdkDistribution" dependency instead. Again, we recommend you
 * look at the XTC platform repository, or the XTC template app, that is a simple Hello World, to
 * understand how to build a project with XTC and Gradle.
 */
dependencies {
    xdk(libs.xdk)
}

/**
 * SOURCE SETS SECTION
 *
 * This configures source sets, which makes the compiler build all the included modules.
 * in that source set, in tasks containing that source set name (except for Main which is
 * a blank string in its tasks), e.g. compileXtc compiles the main source set, compileTestXtc
 * compiles the test source set and so on.
 * <p>
 * There are several negative "should fail" source files in this subproject as well. but
 * these are filtered out through the standard Gradle source set mechanism. This repo
 * is not really meant to be used as a unit test. It merely sits on top of everything to
 * ensure that we don't accidentally break external dependencies to the XDK artifacts
 * for the world outside the XDK repo, and that the build lifecycle works as it should,
 * and we don't push any broken changes to XTC language support, that won't be discovered
 * until several commits later, or worse, by some developer not working on the XDK itself
 * who has no interest in building their own XDK internals or modifying the plugin.
 */
sourceSets {
    main {
        xtc {
             // TODO: tests below are meant to be compiled and run manually; consider moving them
             //       somewhere else and filter out the negative tests
            exclude("**/archive/**")
            exclude("**/dbTests/**")
            exclude("**/json/**")
            exclude("**/jsondb/**")
            exclude("**/multiModule/**")
            exclude("**/webTests/**")
            exclude(
                "**/TestSimple.x",
                "**/ConstOrdinalListTest.x",
                "**/NumericConversions.x",
                "**/contained.x",
                "**/container.x",
                "**/Dec28.x",
                "**/errors.x")
        }
    }
}

/**
 * Applying the XTC plugin, adds a couple of default extensions to the project: xtcCompile and xtcRun
 * are the only ones that may be necessary to care about, if you are working an XTC project build.
 * The "xtcCompile" extension contains common configuration for all XTC compilation tasks in
 * the current project. The "xtcRun" extensions contains common configuration for all XTC runner tasks
 * in a project. Every XtcCompileTask and XtcRunTask will inherit these configurations, but it's possible
 * to override any value in the extension on a per-task basis in the task configuration.
 *
 * Applying the XTC plugin to a Gradle project creates a few tasks by default. This is the pattern used
 * by most Gradle language plugins for compiled languages. These are the once that the XTC programmer
 * may be interested in:
 *
 * 1) The compile tasks for each source set
 * 2) The run tasks for all known modules.
 *
 * A compile task is added for each source set. Just like for other language plugins that use source sets,
 * we have a default source set for xtc code called "main" and one called "test" (currently not
 * used for much). The name of a compile task is derived from the name of the source set it will
 * compile, and upon detecting changes, recompile. Hence, if you have a "test" source set, you
 * will be given a "compileTestXtc" task, just as it would work with Java, Scala, Clojure etc.
 * For the default source set (by convention always renamed "main"), the task will just be called
 * "compileXtc", just like the default Java plugin compile task for the main source set is called
 * "compileJava".  Since the plugin currently creates XTC main and test source sets by default, you
 * will get compile tasks for both of these, even if they are empty. If you declare your own
 * source sets above, e.g. "negativeTests", you will get a compile task and run tasks for that too.
 *
 * Compile and run tasks can be created as you wish, like the examples below. The xtcCompile tasks
 * and xtcRun tasks can override any property from the extensions.
 *
 * NOTE: In Gradle, properly set up, any task and configuration in a build relies on implicitly
 * or explicitly defined inputs and outputs. Should they no longer hash to the same values, the task
 * cannot be cached. Otherwise, a task may be skipped and use its cached values. This also means
 * that you should never have to call any cleanup tasks, or any tasks that create dependencies of
 * another task in a correctly implemented Gradle build. This may confuse some users that come from
 * other languages. For example, in C/C++, a "clean" task in a Makefile will delete everything that
 * has be built, and a following build will start from scratch. The "clean" task in the Gradle build
 * lifecycle does the same, i.e. by default deletes everything a project has built, but AS LONG AS
 * YOU HAVE THE BUILD CACHE ENABLED YOUR NEXT BUILD WILL STILL REUSE CACHED ARTIFACTS, IF THEIR
 * INPUTS AND OUTPUTS ARE UNCHANGED. This is the whole point of the Gradle build cache, and XTC
 * respects those semantics, as a Gradle citizen should. So, for example, to create a distribution
 * of the XDK (./gradlew installDist), this is the only task you should need to code. If any
 * of its dependencies are gone, or have changed so that their inputs and outputs now differ,
 * it will be re-run, but otherwise, its build data will just be retrieved from the build cache.
 * "./gradlew clean" amd then "./gradlew build" is an antipattern, and if your build requires
 * these to work properly, that is a bug that should be reported to the build script author.
 *
 * To see more information on why a task is re-run, skipped or retrieved from the build cache,
 * you can run Gradle with the --info flag.
 */

// Defaults inherited and overridable by all xtcCompile tasks
xtcCompile {
    /*
     * Execution mode controls how the compiler runs:
     *   - DIRECT: In-process via ServiceLoader (fastest, shares JVM)
     *   - ATTACHED: Forked JVM with inherited I/O (default, isolated)
     *   - DETACHED: Not supported for compile tasks
     *
     * Override per-task:
     *   executionMode = ExecutionMode.DIRECT
     *
     * Default is ATTACHED (forked JVM with console output).
     */

    /*
     * Displays XTC runtime version (should be semanticVersion of this XDK), default is "false"
     */
    showVersion = false

    /*
     * Run the XTC command in its built-in verbose mode (default: false).
     */
    verbose = false

    /*
     * To debug the XTC compiler, use standard JDWP arguments via jvmArgs:
     *
     * jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
     *
     * Then attach your debugger to port 5005. For more information on debugging,
     * see plugin/README.md in the XVM repository.
     */

    /*
     * Should all compilations be forced to rerun every time this build is performed? This is not
     * the same thing as touching all source, and the default is "true". Rebuild means that if the
     * compiler is called, XTC cannot ignore the compile request. XCC may choose to ignore a compile
     * request when .x source code is unchanged, but the javatools.jar (i.e. the Compiler internals
     * themselves) have been changed, unless the rebuild flag is set. This also means that for
     * the default value "true", rebuild means that any change to the Launcher/javatools code will
     * cause all xtc modules in the XDK to be rebuilt. For a lot of cases, a developer modding
     * javatools does not need or what this, but at the moment we have no finer grained dependency
     * mechanism to detect if any changes affect the compiler or runtime alone, or if it requires
     * actually regenerating the XTC modules from unchanged source. For any one not working on the
     * actual XDK, this is not a problem. For the XDK itself, the "true" default may cause longer
     * rebuilds, but you can override the default value of the rebuild flag with the
     * -PxtcDefaultRebuild=false" on the "gradlew" command line, or with the equivalent system
     * variable ORG_GRADLE_PROJECT_xtcDefaultRebuild=false" exported or passed on the "gradlew"
     * command line.
     *
     * Default is true (basically only meaning that if javatools.jar has changed, we need to
     * rerun every job that depends on it, but NOT meaning that all .x source files are touched
     * and updated, or anything like that).
     */
    rebuild = false

    /*
     * ============================================================================
     * STDOUT/STDERR REDIRECTION (Configuration Cache Compatible)
     * ============================================================================
     *
     * By default, forked processes inherit I/O from the parent (console output).
     * You can redirect stdout/stderr to files using several approaches:
     *
     * 1. Simple string path with timestamp placeholder (= for simple types):
     *    stdoutPath = "build/logs/compiler-%TIMESTAMP%.log"
     *    stderrPath = "build/logs/compiler-errors-%TIMESTAMP%.log"
     *
     *    The %TIMESTAMP% placeholder expands to yyyyMMddHHmmss format at execution time.
     *
     * 2. Provider with .set() (recommended - lazy and configuration cache compatible):
     *    stdoutPath.set(layout.buildDirectory.file("logs/compiler.log").map { it.asFile.absolutePath })
     *    stderrPath.set(layout.buildDirectory.file("logs/errors.log").map { it.asFile.absolutePath })
     *
     * 3. Provider with timestamp in path:
     *    stdoutPath.set(layout.buildDirectory.file("logs/compile-%TIMESTAMP%.log")
     *        .map { it.asFile.absolutePath })
     *
     * 4. File-based (convenience method):
     *    stdoutPath(project.file("my-output.log"))
     *    stderrPath(project.file("my-errors.log"))
     *
     * 5. Dynamic provider logic:
     *    stdoutPath.set(provider {
     *        val env = findProperty("buildEnv") ?: "dev"
     *        "build/logs/${env}/compiler-%TIMESTAMP%.log"
     *    })
     *
     * Convention: Use = for simple types, .set() for Property/Provider types
     *
     * Example usage (commented out):
     */
    // stdoutPath.set(layout.buildDirectory.file("logs/compiler-%TIMESTAMP%.log").map { it.asFile.absolutePath })
    // stderrPath.set(layout.buildDirectory.file("logs/compiler-errors-%TIMESTAMP%.log").map { it.asFile.absolutePath })
}

// Defaults inherited and overridable by all runXtc tasks you chose to create. The default xtcRun task
// will execute any modules defined in xtcRun.
xtcRun {
    /*
     * Equivalent to the "--version" flag for the launcher (default: false).
     */
    showVersion = false

    /*
     * Run the XTC command in its built-in verbose mode (default: false).
     */
    verbose = true

    /*
    * Add a JVM argument to the defaults. Will be ignored if the launch does not spawn a forked JVM for its run.
    */
    jvmArgs("-showversion", "--enable-preview")

    /*
     * ============================================================================
     * EXECUTION MODE AND STDOUT/STDERR REDIRECTION FOR RUN TASKS
     * ============================================================================
     *
     * Execution modes:
     *   - DIRECT: In-process via ServiceLoader
     *   - ATTACHED: Forked JVM with inherited I/O (default)
     *   - DETACHED: Forked JVM running in background with file redirects
     *
     * ATTACHED MODE (default):
     *   Default: stdout/stderr inherit from console
     *   Override: Use any of the approaches shown in xtcCompile section
     *
     * DETACHED MODE (executionMode = ExecutionMode.DETACHED):
     *   Default: stdout/stderr redirect to "{toolname}_pid_{timestamp}.log"
     *   Override: Specify custom paths to change the default
     *
     * Example for detached mode with simple string path:
     *   executionMode = ExecutionMode.DETACHED
     *   stdoutPath = "build/logs/server-%TIMESTAMP%.log"
     *   stderrPath = "build/logs/server-errors-%TIMESTAMP%.log"
     *
     * Example for detached mode using layout provider:
     *   executionMode = ExecutionMode.DETACHED
     *   stdoutPath.set(layout.buildDirectory.file("logs/server-%TIMESTAMP%.log")
     *       .map { it.asFile.absolutePath })
     *
     * Note: In detached mode, the process continues running after Gradle exits.
     *       Use 'kill <PID>' to stop it (PID is logged on startup).
     */

    /**
     * The default runXtc task is configured to run what is here in the xtcRun configuration.
     * If you do not define any modules here, the default runXtc task will do nothing.
     *
     * The runXtc task uses all source set outputs in the module path, so even though
     * you can compile tests as a separate source set, they will know about the main modules
     */
    module {
        moduleName = "EchoTest"
        methodName = "run" // default
        moduleArgs("Hello", "World")
    }
}

/**
 * XTC TEST CONFIGURATION
 *
 * The xtcTest extension configures the default testXtc task and provides defaults
 * for any custom XtcTestTask instances. XtcTestTask extends XtcRunTask, so all
 * run configuration options are available, plus test-specific options.
 *
 * Test tasks are wired into the Gradle 'check' lifecycle, so running:
 *   ./gradlew check
 * will execute all configured tests.
 */
xtcTest {
    /*
     * Whether to fail the build if any test fails. Default is true.
     * Set to false to continue the build even when tests fail.
     */
    failOnTestFailure = true

    /*
     * Verbose output for test execution
     */
    verbose = true

    /*
     * Test modules to run. Uses the same module { } DSL as xtcRun.
     * If no modules are configured, the testXtc task will be a no-op.
     */
    // Example: Run xunit tests from a test module
    // module {
    //     moduleName = "MyTestModule"
    //     moduleArgs("--verbose")
    // }
}

// This shows how to add a custom run task that overrides the global xtcRun config.
//
// To debug, use standard JDWP arguments:
// jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
// See plugin/README.md for more information on debugging.
//
val runTwoTestsInSequence by tasks.registering(XtcRunTask::class) {
    group = "application"
    verbose = true // override a default from xtcRun
    module {
        moduleName = "EchoTest"
        moduleArg("Hello")
        moduleArg(provider { System.getProperty("user.name") ?: "unknown user" })
    }
    moduleName("TestArray")
}

// Generate map of execution modes to task names
val executionModeTasks = ExecutionMode.entries.associateWith { mode ->
    val taskName = "runTestWith${mode.name.lowercase().replaceFirstChar { it.uppercase() }}"
    tasks.register<XtcRunTask>(taskName) {
        group = "application"
        executionMode = mode
        verbose = true
        if (mode == ExecutionMode.DETACHED) {
            logger.lifecycle("Running $taskName (detached), will redirect output.")
        }
        module {
            // TODO: We may want sugar for parallel flag and execution mode specialization in individual modules later.
            moduleName = "EchoTest"
            moduleArg("Testing Execution Mode:")
            moduleArg("  $mode")
        }
        // TODO: POC and make this work
        // TODO: commit.yml test for running the xtc-app-template
        //val redirect = stdoutPath.getOrElse("stdout")
        //println("REDIRECTED: $redirect")
        //stdoutPath.set(layout.buildDirectory.file("logs/runOne-stdout-%TIMESTAMP%.log").map { it.asFile.absolutePath })
        //stderrPath.set(layout.buildDirectory.file("logs/runOne-stderr-%TIMESTAMP%.log").map { it.asFile.absolutePath })
    }
}

val runTestAllExecutionModes by tasks.registering {
    group = "application"
    description = "Run EchoTest in all execution modes to verify that they work."
    dependsOn(executionModeTasks.values)
    doLastTask {
        logger.lifecycle("Finished testing all execution modes.")
    }
}

// This allows running a single test, e.g.: ./gradlew manualTests:runOne -PtestName="TestArray"
val runOne by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = """
        Runs one test as given by the property 'testName' (has a hardcoded default test name)
        Arguments are in 'testArgs'. (no arguments if property not defined)
    """.trimIndent()

    module {
        moduleName = resolveTestNameProperty() // NOTE: this syntax also has the moduleName("...") shorthand
        moduleArgs(provider { resolveTestArgumentsProperty() })
    }
    doFirstTask {
        logger.lifecycle("manualTests:runOne: $name")
    }
}

// Shared list of test module names for both parallel and sequential runners
val testModuleNames = listOf(
    "TestAnnotations",
    "TestArray",
    "TestCollections",
    "TestDefAsn",
    "TestFiles",
    "TestGenerics",
    "TestInnerOuter",
    "TestIO",
    "TestLambda",
    "TestLiterals",
    "TestLoops",
    "TestMaps",
    "TestMisc",
    "TestNesting",
    "TestNumbers",
    "TestProps",
    "TestQueues",
    "TestReflection",
    "TestRegularExpressions",
    "TestServices",
    "TestTry",
    "TestTuples"
)

/**
 * Run all tests in parallel using the Runner module, which spawns all test modules concurrently.
 */
val runParallel by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Run all known tests in parallel through the parallel test runner."
    module {
        verbose = false
        moduleName = "Runner"
        // TODO: If the runner took the file names instead of module names, we could just pass in
        //   exactly the outgoing source sets, and we wouldn't have to know their names, and could
        //   have them compiled by xcc (for example calling moduleArgs(testModuleProvider))
        //   Now instead we have to explicitly specify the module names. IMPLEMENT THIS!
        //
        // TODO: CI integration test  for third party xdk dependency
        moduleArgs(testModuleNames)
    }
}

/**
 * Run all tests sequentially, one after another. Each test module runs to completion before
 * the next one starts. This is useful for debugging or when parallel execution causes issues.
 */
val runSequential by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Run all known tests sequentially, one after another."
    // TODO: TestAnnotations is currently failing - fix the test and remove this exclusion
    // TODO: The runner.x in parallel tests apparently just swallows and prints exceptions WTF?
    // TODO: We should integrate this with xUnit instead maybe? OR finally implement negative and positive tests.
    val excludedModules = setOf("TestAnnotations")
    testModuleNames.filter { it !in excludedModules }.forEach { moduleName(it) }
}

val runAllTestTasks by tasks.registering {
    group = "application"
    description = "Run all test tasks."
    dependsOn(runOne, runTwoTestsInSequence, runTestAllExecutionModes, runSequential)
}

val runAllTestTasksParallel by tasks.registering {
    group = "application"
    description = "Run all test tasks."
    dependsOn(runOne, runTwoTestsInSequence, runTestAllExecutionModes, runParallel)
}

/**
 * Custom XtcTestTask example.
 *
 * This demonstrates creating a custom test task that overrides the default xtcTest configuration.
 * Test tasks inherit from XtcRunTask, so all run configuration options are available.
 */
val runXunitTests by tasks.registering(XtcTestTask::class) {
    group = "verification"
    description = "Run xunit tests using the xunit framework."

    // Override test-specific settings
    failOnTestFailure = true
    verbose = true

    // Configure test modules (same DSL as XtcRunTask)
    // module {
    //     moduleName = "xunit_demo"
    //     moduleArgs("--verbose")
    // }
}

fun resolveTestNameProperty(defaultTestName: String = "EchoTest"): String {
    return if (hasProperty("testName")) (properties["testName"] as String) else defaultTestName
}

fun resolveTestArgumentsProperty(defaultTestArguments: String = ""): List<String> {
    val argsString = if (hasProperty("testArgs")) (properties["testArgs"] as String) else defaultTestArguments
    return if (argsString.isEmpty()) emptyList() else argsString.split(",").map { it.trim() }
}

/**
 * Lazy way of getting the filenames of all the modules we want to send in as arguments to the parallel Runner
 * module, demonstrating how to resolve these things lazy-only, i.e. when the Runner is about to start.
 * Since these modules depend on compiling the entire project, and resolving actual file locations (that don't
 * exist at configuration time), not implementing this as a provider, would trigger a full compile immediately
 * when someone wants to access source set output that doesn't exist yet. It would also likely not work, because
 * the configuration don't know everything about the world yet.
 *
 * This shows the power of Gradle/Maven; anyone asking for the contents of this provider, i.e. anyone who
 * asks what's in the source set, will trigger the source set being built to return its outputs, and it will
 * happen iff those data are really required (such as during the execution phase of the testParallel task).
 * At configuration time, the testParallel task will validate the DSL, but all it sees is a provider in the
 * module's definition. It's first when that task is executed (if every) that we trigger a build. You can
 * see this if you exclude manual tests from the composite build lifecycle (see root/gradle.properties) and
 * just execute ./gradlew manualTests:runParallel. Only when we are ready to launch the runner, is the
 * cascade of operations leading the source set output occur. We also know exactly which source sets
 * we have, so we don't have to maintain a list of module names, which we don't know if they exist or not.
 * This is also brittle, because the runner may in turn trigger the compiler, after searching through
 * the directory space for something corresponding to the module name.
 */
val testModuleProvider: Provider<List<String>> = provider {
    // TODO: If we put JavaTools on the compile classpath for this project, as a one-line dependency, we could directly
    //   call the XTC FileRepresentation logic that determines both if an XTC binary is a valid such file, and the
    //   actual module name of this binary. This is potentially very powerful, the build use parts of its target.
    //   Try that out yourself, if you want!
    fun isValidXtcModule(file: File): Boolean {
        return file.extension == "xtc"
    }
    sourceSets.main.get().output.asFileTree.filter { isValidXtcModule(it) }.map { it.absolutePath }.toList()
}

val printTestModules by tasks.registering {
    group = "help"
    description = "Print the output of provideTestModules."
    doLastTask {
        val resolved = testModuleProvider.get()
        if (resolved.isEmpty()) {
            logger.lifecycle("No test modules found.")
        } else {
            logger.lifecycle("Test modules:")
            resolved.forEach { logger.lifecycle("  $it") }
        }
    }
}
