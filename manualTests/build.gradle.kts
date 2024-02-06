import org.xtclang.plugin.tasks.XtcCompileTask

/*
 * Test utilities.  This is a standalone XTC project, which should only depend on the XDK.
 * If we want to use it to debug the XDK, that is also fine, as it will do dependency
 * substitution on the XDK and XTC Plugin (and Javatools and other dependencies) correctly,
 * through included builds, anyway.
 *
 * This is compiled as part of the XDK build, in order to ensure that the build DSL work as
 * expected, and that we can resolve modules only with external dependencies to repository
 * artifacts for the XTC Gradle plugin and the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xtc)
}

val sanityCheckRuntime = getXdkPropertyBoolean("org.xtclang.build.sanityCheckRuntime", false)

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
                "**/innerouter.x", // TODO @lagergren case sensitivity issue; this does not work: "**/innerOuter.x",
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
 * It's important to understand what causes caching problems and unnecessary rebuilds in Gradle.
 * One of these things is tasks depending on System environment variables.
 * To fix that particular issue, an input of the form inputs.property("langEnvironment") { System.getenv(ENV_VAR) }
 * needs to be added to the task configuration or any plugin that uses is must be aware of it.
 * <p>
 * Also Using doFirst and doLast from a build script on a cacheable task ties you to build script changes since
 * the implementation of the closure comes from the build script. If possible, you should use separate tasks instead.
 * <p>
 * @see <a href="https://docs.gradle.org/current/userguide/common_caching_problems.html">Gradle User Guide on caching</a>
 */
fun alwaysRebuild(): Boolean {
    val rebuild = (System.getenv("ORG_XTCLANG_BUILD_SANITY_CHECK_RUNTIME_FORCE_REBUILD") ?: "false").toBoolean()
    if (rebuild) {
        logger.warn("$prefix manualTest compile configuration is set to force rebuild (forceRebuild: true)")
    }
    return rebuild
}

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
     * generation or execution of an XTC app.
     */
    forceRebuild = alwaysRebuild()

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
     * We suspect that the pre-generated run tasks from the XTC Plugin are not the most optimal
     * and intuitive way of running XTC modules. It's probably cleaner for the user to modify
     * the configuration on task level for any runtime task, and add many simple custom run tasks
     * for clarity. However, we haven't ahd the cycles to support the standard overrides for
     * modules to run (all other DSL can be overridden) at task level. It is easy and encouraged
     * to contribute to the XTC Plugin build DSL, so that we get more functionality, clearer
     * and shorter syntax, and new features that we feel we require. It's not clear what all
     * of these are, but working with an XTC project that applies the XTC Plugin will likely
     * discover most of the shortcomings quickly, so that we can file enhancement requests.
     *
     * TODO: Add parallelism, and a simpler way to work with this.
     * TODO: Add a nicer DSL syntax with a nested modules section.
     */
    module {
        moduleName = "TestFizzBuzz" // Will add other ways to resolve modules too.
        showVersion = true // Overrides env showVersion flag.
        moduleArgs("Hello, ", "World!")
    }
}

tasks.withType<XtcCompileTask>().configureEach {
    enabled = true // TODO @lagergren sanityCheckRuntime
    doLast {
        logger.lifecycle("$prefix *** RECOMPILING: $name")
    }
}
