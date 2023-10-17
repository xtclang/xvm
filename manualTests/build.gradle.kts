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
    alias(libs.plugins.xdk.build.version)
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

xtcRun {
    /*
     * Run the XTC command in its built-in verbose mode.
     */
    verbose = true

    /*
     * Change this to false to run module from the build thread to enable more seamless debugging. Not recommended for production.
     */
    fork = true

    /*
     * Use an XTC native launcher (requires a local XDK installation on the test machine.)
     */
    useNativeLauncher = false

    /*
     * Do or do not swallow and redirect execution output to the logging framework. If true (default),
     * any output to stdout or stderr will simply be sent to the console, i.e. the default behavior for
     * an XTC run.
     */
    logOutputs = false

    /*
     * By default, a Gradle task swallows stdin, but it's possible to override standard input and
     * output for any XTC launcher task.
     *
     * To redirect any I/O stream, for example if you want to input data to the XTC debugger or
     * to the console, such as credentials/interactive prompts, the error, output and input streams
     * can be redirected to a custom source. Note that with logOutputs = true, there might be
     * surprising behavior, but it's not wrong per-se. It's probably just something that's good to
     * avoid.
     */
    // stdin = System.`in`

    /*
     * Add a JVM argument to the defaults. Will be ignored if the launch does not spawn a forked JVM for its run.
     */
    jvmArgs("-showversion")

    /*
     * Execute TestFizzBuzz with the Hello World arguments.
     *
     * Note that the second arg is passed as a provider, which means that it will not be evaluated
     * anywhere in the Gradle build/run lifecycle until it is actually needed, which is just before
     * the Runner executes. This is the preferred way of passing arguments to things in a Gradle build,
     * and we will incrementally add support for any argument being either a direct value or a
     * provider. Typically, everything in Gradle has build DSL methods on the form "method(Object... args)",
     * which is rather ugly, but that is the standard way of allowing different types of arguments to be
     * passed. This is because some may be lazy and some may not. There are likely more examples.
     */
    module {
        moduleName = "TestFizzBuzz" // Will add other ways to resolve modules too.

        moduleArgs("Hello, ", "World!")
        // Just as with all standardized Gradle argument APIs, we can use an argument provider too, of course:
        // moduleArgs(provider { listOfNotNull(helloArg, worldArg) })

        // methodName = "run" // Leave this commented out to keep "run" as the default method to call in the module.
    }
}
