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
    id("org.xvm.build.version")
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
}

dependencies {
    xdk(libs.xdk)
}

// TODO: Add source set for negative tests.
// TODO: Add modules { } dsl section to collect single module { } configurations, instead of listing them in the config as a sequence.
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

xtc {
    printVersion()
}

xtcRun {
    verbose = true
    moduleName("TestFizzBuzz")
}
