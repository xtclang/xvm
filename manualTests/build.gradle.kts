import org.xtclang.plugin.tasks.XtcCompileTask
import org.xtclang.plugin.tasks.XtcRunAllTask
import org.xtclang.plugin.tasks.XtcRunTask

/**
 * This is the manualTests project.
 *
 * Test utilities.  This is a standalone XTC project, which should only depend on the XDK.
 * If we want to use it to debug the XDK, that is also fine, as it will do dependency
 * substitution on the XDK and XTC Plugin (and Javatools and other dependencies) correctly,
 * through included builds, anyway.
 *
 * This is compiled as part of the XDK build, in order to ensure that the build DSL work as
 * expected, and that we can resolve modules only with external dependencies to repository
 * artifacts for the XTC Gradle plugin and the XDK.
 */

/**
 * Plugins required. This is done through the Gradle version catalog mechanism. A version catalog
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
 * settings (for a best-practise example, please see the XTC Platform project and its XTC Plugin branch,
 * which is either up for review as a pull request or already merged to master), the build will look
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
 * Dependencies required. The xdk cam be retrieved with the "xdk" consumer configuration, which
 * will look for a file/dir hierarchy that contains the modules from the javatools and lib directories
 * of an XTC installation. We currently publish XDK releases as zipped artifacts in our GitHub Maven
 * repositoru (and soon also on mavenCentral after the next official release). If you want to use
 * a zipped XDK artifact, use the "xdkDistribution" depeendency instead. Again, we recommend you
 * look at the XTC platform repository, or the XTC template app, that is a simple Hello World, to
 * understand how to build a project with XTC and Gradle.
 */
dependencies {
    xdk(libs.xdk)
}

/**
 * This configured a source set, which makes the compiler build all of the included modules.
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
            include(
                "**/annos.x",
                "**/array.x",
                "**/collections.x",
                "**/defasn.x",
                "**/exceptions.x",
                "**/FizzBuzz.x",
                "**/generics.x",
                "**/innerouter.x",
                "**/files.x",
                "**/IO.x",
                "**/lambda.x",
                "**/literals.x",
                "**/loop.x",
                "**/nesting.x",
                "**/numbers.x",
                "**/prop.x",
                "**/maps.x",
                "**/misc.x",
                "**/queues.x",
                "**/services.x",
                "**/reflect.x",
                "**/regex.x",
                "**/tuple.x"
            )
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
 * 2) The run tasks for each source set.
 *
 * A compile task is added for each source set. Just like for other language plugins that use source sets,
 * we have a default source set for xtc code called "main" and one called "test" (currently not
 * used for much). The name of a compile task is derived from the name of the source set it will
 * compile, and upon detecting changes, recompile. Hence, if you have a "test" source set, you
 * will be given a "compileTextXtc" task, just as it would work with Java, Scala, Clojure etc.
 * For the default source set (by convention always renamed "main"), the task will just be called
 * "compileXtc", just like the default Java plugin compile task for the main source set is called
 * "compileJava".  Since the plugin currently creates XTC main and test source sets by default, you
 * will get compile tasks for both of these, even if they are empty. If you declare your own
 * source sets above, e.g. "negativeTests", you will get a compile task and run tasks for that too.
 *
 * Run tasks work similarly, and are similarly named. At the moment, we also add a "runAll<SourceSetName>Xtc"
 * per source set. This may not be how the finished DSL will look.
 *
 * Your XDK application can be executed by calling any of the run tasks. The run task will make sure
 * that all dependencies it needs are compiled, resolved from repositories and on the module path
 * when any module is run.
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
 *
 */
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
     * Compile in build process thread. Enables seamless IDE debugging in the Gradle build, with breakpoints
     * in e.g. Javatools classes, but is brittle, and should not be used for production use, for example
     * if the launched app does System.exit, this will kill the build job too.
     *
     * Javatools launchers should be debuggable through a standard Run/Debug Configuration (for example in IntelliJ)
     * where the Javatools project is added as a Java Application (and not a Gradle job).
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
     * If fork is "true", the runner will run in the build process thread. This enables seamless IDE debugging of the
     * Gradle build, with breakpoints in Java classes (e.g. Javatools). Unfortunately, running in the build process
     * thread is brittle (e.g. a System.exit will kill the build job), so is only used when debugging the build.
     *
     * The Javatools command line tools should be debuggable through a standard Run/Debug Configuration (e.g. in
     * IntelliJ IDEA) by adding the Javatools project as a Java Application (not as a Gradle job).
     *
     * The default is true.
     */
    fork = true

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

    /*
     * Execute TestFizzBuzz with the Hello World arguments. We support providers, as well
     * as Strings, as per the common Gradle API conventions. For example, you can do
     * moduleArgs(<collection of string providers>) or moduleArg(<string provider>) for
     * lazy evaluation, too.
     *
     * Currently, all module configurations in the xtcRun DSL will be executed in sequence,
     * as their order declared.
     *
     * The project-global run configuration is mostly modelled on the Java Application plugin,
     * which has a single configuration with mainClass, and a single run task. This makes sense,
     * because if you added individual run tasks for individual modules, they would have to
     * accept inputs (from where?), which we sort of do when it comes to the simple test
     * default, but they would also not be a standard "here is my outputs" Gradle task. For
     * a new task, registered without any outputs in the configuration, Gradle will always treat
     * it as stale (does the equivalent of our Tasks.considerNeverUpToDate()), so out of the box
     * a run task (or any Gradle task) will not be cached. Of course XTC run tasks can do anything
     * as well, and they don't declare any outputs for the same reason.
     */
    val moduleTestName = getTestModuleName() // module name on command line or 'TestSimple' as default
    // Special case - use the Runner to run all tests in parallel, as long as the xtcRunAll tasks does not
    // automatically do this with everything in the module path of the source set. Also modify that task
    // after bugfixing to have a parallel option, through the XTC runner, or whatever it may be.
    module {
        if (!shouldRunAllTestsInParallel()) {
            moduleName = moduleTestName
        } else {
            moduleName = "Runner"
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
}

/**
 * The task configuration below can be used to modify the behavior of the XTC compile task
 * As-is, it is a no-op except for the logger output.
 */
tasks.withType<XtcCompileTask>().configureEach {
    doLast {
        assert(prefix.contains(project.name) && prefix.contains(name)) { "Task prefix and name should contain the project name and task name." }
        logger.lifecycle("$prefix (compile task) executed.")
    }
}

/**
 * The task configuration below can be used to modify the behavior of the XTC "run"" task
 * As-is, it is a no-op except for the logger output.
 */
tasks.withType<XtcRunTask>().configureEach {
    doLast {
        logger.lifecycle("$prefix XTC manualTests 'xtcRun' task executed.")
    }
}

/**
 * The task configuration below can be used to modify the behavior of the XTC "run all" task.
 * As-is, it is a no-op except for the logger output.
 *
 *  TODO: We should also create a configuration containing all compiled modules from a source set, that
 *        can be used to generate this list of module names. In fact, such a configuration is already created
 *        internally, or at least componenets enough to weave it, by the plugin, when it processes source sets,
 *        so it should be a very small enhancement in code size.
 *
 *   TODO: We should also support something like module groups, and be able to specify e.g. run all modules
 *        sin a module group by group name, by passing properties on the command lines with -P or one of the other
 *        supported property resolution mechanisms for Gradle and/or the Gradle XTC Plugin.
 */

tasks.withType<XtcRunAllTask>().configureEach {
    doLast {
        logger.lifecycle("$prefix XTC manualTests 'xtcRunAll' task executed.")
    }
}
/**
 * Makes it possible to pass -PtestName=<name of a test module> on the ./gradlew command line, e.g.
 *   ./gradlew -PtestName=TestFizzBuzz
 *
 * If the property is not set, it will default to TestSimple
 *
 * If it's set to all, we should run all tests in parallel.
 */
fun getTestModuleName(defaultTestName: String = "TestSimple"): String {
    return if (hasProperty("testName")) (properties["testName"] as String) else defaultTestName
}

fun shouldRunAllTestsInParallel(): Boolean {
    return getTestModuleName() == "all"
}
