import org.xtclang.plugin.tasks.XtcRunTask

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
 * is a single source of truth for dependent artifacts for a Gradle oroject. For the XVM repo,
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
 * settings (for a best-practise example, please see the xtc-template-app project), the build will look
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
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xtc)
}

/**
 * DEPENDENCIES SECTION
 *
 * The xdk cam be retrieved with the "xdk" consumer configuration, which will look for a file/dir
 * hierarchy that contains the modules from the javatools and lib directories of an XTC installation.
 * We currently publish XDK releases as zipped artifacts in our GitHub Maven
 * repositoru (and soon also on mavenCentral after the next official release). If you want to use
 * a zipped XDK artifact, use the "xdkDistribution" depeendency instead. Again, we recommend you
 * look at the XTC platform repository, or the XTC template app, that is a simple Hello World, to
 * understand how to build a project with XTC and Gradle.
 */
dependencies {
    xdk(libs.xdk)
}

/**
 * SOURCE SETS SECTION
 *
 * This configures source sets, which makes the compiler build all of the included modules.
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
 * in a project. Every XtcCompleTask and XtcRunTask will inherit these configurations, but it's possible
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
 * "./gradlew clean" amd then "./gradlew build" is an anti-pattern, and if your build requires
 * these to work properly, that is a bug that should be reporoted to the build script author.
 *
 * To see more information on why a task is re-run, skipped or retrieved from the build cache,
 * you can run Gradle with the --info flag.
 */

// Defaults inherited and overridable by all xtcCompile tasks
xtcCompile {
    /*
     * Displays XTC runtime version (should be semanticVersion of this XDK), default is "false"
     */
    showVersion = false

    /*
     * Run the XTC command in its built-in verbose mode (default: false).
     */
    verbose = false

    /*
     * Use the debug = true flag, either here, or on a per-task level, to suspend and allow you to attach a debugger
     * after launcher has been spawned in a child process. You can also use the system variable XTC_DEBUG=true
     * (and optionally, XTC_DEBUG_PORT=<int>"
     */
    debug = false

    /*
     * Compile in build process thread. Enables seamless IDE debugging in the Gradle build, with breakpoints
     * in e.g. Javatools classes, but is brittle, and should not be used for production use, for example
     * if the launched app does System.exit, this will kill the build job too.
     *
     * Javatools launchers should be debuggable through a standard Run/Debug Configuration (for example in IntelliJ)
     * where the Javatools project is added as a Java Application (and not a Gradle job). So just set the debug
     * flag instead, for most common cases.
     *
     * Default is true.
     */
    fork = true

    /*
     * Should all compilations be forced to rerun every time this build is performed? This is NOT recommended,
     * as it removes pretty much every advantage that Gradle with dynamic dependency management gives you. It
     * should be used only for testing purposes, and never for anything else, in a typical build, distribution
     * generation or execution of an XTC app. You should never have to enable forceRebuild unless you are doing
     * something like functionality testing during XVM compiler development.
     */
    forceRebuild = false

    /*
     * By default, a Gradle task swallows stdin, but it's possible to override standard input and
     * output for any XTC launcher task.
     *
     * To redirect any I/O stream, for example if you want to input data to the XTC debugger or
     * to the console, such as credentials/interactive prompts, the error, output and input streams
     * can be redirected to a custom source.
     *
     * This should at least enable the "ugly" use case of breaking into the debugger on the
     * console when an "assert:debug" statement is evaluated.
     */
    // stdin = System.`in`
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
     * Use an XTC native launcher (requires a local XDK installation on the test machine.)
     *
     * The default is false.
     */
    useNativeLauncher = false

    /*
    * Add a JVM argument to the defaults. Will be ignored if the launch does not spawn a forked JVM for its run.
    */
    jvmArgs("-showversion")

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

// This shows how to add a custom run task that overrides the global xtcRun config.
val runTwoTestsInSequence by tasks.registering(XtcRunTask::class) {
    group = "application"

    // The default debugger settings are debug = false, debugPort = 4711 and debugSuspend = true
    // If you run with debugSuspend = false, you can attacha devbugger to the debugPort at any time.
    //debug = true
    //debugPort = 5005
    //debugSuspend = false

    verbose = true // override a default from xtcRun
    module {
        moduleName = "EchoTest"
        moduleArg("Hello")
        moduleArg(provider { System.getProperty("user.name") ?: "unknown user" })
    }
    moduleName("TestArray")
}

// This allows running a single test, e.g.: ./gradlew manualTests:runOne -PtestName="TestArray"
val runOne by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Runs one test as given by the property 'testName', or a default test if not set."
    // Override debug flag from xtcRun extension here to suspend the process launched for this task, and allow attach.
    //debug = true
    module {
        moduleName = resolveTestNameProperty() // this syntax also has the moduleName("...") shorthand
    }
}

/**
 * The default behavior right now, is to run multiple tests in a sequence, one after each other.
 * This mostly has to do with Gradle assuming single thread-ness unless we add a worker API.
 * This task uses the Ecstasy Runner module, taking the module names as arguments.
 */
val runParallel by tasks.registering(XtcRunTask::class) {
    group = "application"
    description = "Run all known tests in parallel through the parallel test runner."
    module {
        moduleName = "Runner"
        verbose = false

        // TODO: If the runner took the file names instead of module names, we could just pass in
        //   exactly the outgoing source sets, and we wouldn't have to know their names, and could
        //   have them compiled by xcc (for example calling moduleArgs(provideTestModules))
        //   Now instead we have to explicitly specify the module names.
        moduleArgs(
            "TestAnnotations",
            "TestArray",
            "TestCollections",
            "TestDefAsn",
            "TestTry",
            "TestGenerics",
            "TestInnerOuter",
            "TestFiles",
            "TestIO",
            "TestLambda",
            "TestLiterals",
            "TestLoops",
            "TestNesting",
            "TestNumbers",
            "TestProps",
            "TestMaps",
            "TestMisc",
            "TestQueues",
            "TestServices",
            "TestReflection",
            "TestRegularExpressions",
            "TestTuples"
        )
    }
}

val runAllTestTasks by tasks.registering {
    group = "application"
    description = "Run all test tasks."
    dependsOn(runTwoTestsInSequence, runParallel, runOne, tasks.runXtc)
}

fun resolveTestNameProperty(defaultTestName: String = "EchoTest"): String {
    return if (hasProperty("testName")) (properties["testName"] as String) else defaultTestName
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
 * we have, so we don't have to maintain a list of module names, which we don't now if they exist or not.
 * This is also brittle, because the runner may in turn trigger the compiler, after searching through
 * the directory space for something corresponding to the module name.
 */
val provideTestModules: Provider<List<String>> = provider {
    // TODO: If we put JavaTools on the compile classpath for this project, as a one-line dependency, we could directly
    //   call the XTC FileRepresentation logic that determines both if an XTC binary is a valid such file, and the
    //   actual module name of this binary. This is potentially very powerful, the build use parts of its target.
    //   Try that out yourself, if you want!
    fun isValidXtcModule(file: File): Boolean {
        return file.extension == "xtc"
    }
    logger.lifecycle("[manualTests] Resolving source set output, to use as arguments for the runParallel task. If you see this message at config time, something is wrong.")
    val list: List<String> = sourceSets.main.get().output.asFileTree.filter { isValidXtcModule(it) }.map { it.absolutePath }.toList()
    list.forEach { logger.lifecycle("[manualTests]     Resolved test module: $it") }
    list
}
