/*
 * Test utilities.  This is a standalone XTC project, which should only depend on the XDK.
 * If we want to use it to debug the XDK, that is also fine, as it will do dependency
 * substitution on the XDK and XTC Plugin (and Javatools and other dependencies) correctly,
 * through included builds, anyway.
 *
 * We can use the xtcRun method, that is configured in the closure below,
 * or we can use the xtcRunAll method to resolve amd run everything runnable in the source set.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
}

dependencies {
    xdk(libs.xdk)
}

// TODO: Add source set for negative tests.
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
                "**/innerOuter.x",
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

private val forceRecompile = (System.getenv("ORG_XTCLANG_BUILD_SANITY_CHECK_RUNTIME_FORCE_REBUILD") ?: "false").toBoolean()
if (forceRecompile) {
    logger.warn("$prefix manualTest compile configuration is set to force rebuild (forceRebuild=$forceRecompile)")
}

xtcCompile {
    /*
     * Displays XTC runtime version (should be semanticVersion of this XDK), default is "false"
     */
    showVersion = true

    /*
     * Run the XTC command in its built-in verbose mode (default: false).
     */
    verbose = true

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
    fork = false

    /*
     * Should all compilations be forced to rerun every time this build is performed? This is NOT recommended,
     * as it removes pretty much every advantage that Gradle with dynamic dependency management gives you. It
     * should be used only for testing purposes, and never for anything else, in a typical build, distribution
     * generation or execution of an XTC app.
     */
    forceRebuild = forceRecompile
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
     * Run in build process thread. Enables seamless IDE debugging in the Gradle build, with breakpoints
     * in e.g. Javatools classes, but is brittle, and should not be used for production use, for example
     * if the launched app does System.exit, this will kill the build job too.
     *
     * Javatools lanchers should be debuggable through a standard Run/Debug Configuration (for example in IntelliJ)
     * where the Javatools project is added as a Java Application (and not a Gradle job).
     *
     * Default is true.
     */
    fork = true

    /*
     * Use an XTC native launcher (requires a local XDK installation on the test machine.)
     * The default is "false".
     */
    useNativeLauncher = false

    /*
     * By default, a Gradle task swallows stdin, but it's possible to override standard input and
     * output for any XTC launcher task.
     *
     * To redirect any I/O stream, for example if you want to input data to the XTC debugger or
     * to the console, such as credentials/interactive prompts, the error, output and input streams
     * can be redirected to a custom source.
     */
    // stdin = System.`in`

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
        // Just as with all standardized Gradle argument APIs, we can use an argument provider too, of course:
        // methodName = "run" // Leave this commented out to keep "run" as the default method to call in the module.
    }
}
