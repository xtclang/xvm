/*
 * This is a subproject templated to compile, run, and debug the XTC compiler and runtimes.
 * By default, the source set of this project contains a simple HelloWorld.x program. Do
 * debug an XTC program/module of your choice, just change the sourceSet to include whatever
 * source directory you have for your module.
 *
 * The project is part of the XDK, and uses its included builds, so that it looks like a normal Gradle XTC
 * project, that just uses the XDK as a dependency. It doesn't "know" it's part of the XTC. Please see the
 * GitHub repo "xtc-app-template" on GitHub in the XtcLang organization for a standalone example.
 *
 * Behind the scenes, the project will, thanks to the included build set, refresh and recompile any
 * changes made to the XDK in the same repository, whenever the project is build and "xtc" or "xec"
 * is executed through the compile and run configurations.
 *
 * The configs in this project use the "fork = false" option, for launching the XTC tools. This means
 * that the plugin dependencies are expected to be on the classpath of the same classloader as the
 * one building this project. This makes it possible for us to step directly into the XDK code,
 * or to hit breakpoints in the same process as the one running the XTC launchers. For a "sharp"
 * development project, that is XTC only, it is recommended to not override "fork", and let Gradle
 * launch the XTC components through standard "javaexec" specfications, so that the XTC Java code
 * runs in a different process. A similar approach in fork mode can be achieved the standard way,
 * by launching with -Xdebug, just like you would for any other Java application, like so:
 *
 *   1) export GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
 *   2) Put some breakpoints and launch the remote debug configuration inside your IDE on the port 5005
 *   (somewhat dated StackOverflow answer: https://stackoverflow.com/a/2066309/1267320)
 *
 *   If the abovementioned approach does not let you put breakpoints in the build scripts, you
 *   can still put one in e.g. org.gradle.api.internal.project.AbstractProject#dependencies() and
 *   you will at least have the opportunity to debug the build through the Gradle classes, hooking
 *   into project confiugration, dependency resolution, etc.
 *
 *   For some scenarios, it's also probably a good idea to run Gradle with --no-daemon mode.
 *
 * The master reference to debugging a Gradle build, and applications launched from it, can be
 * found here: https://docs.gradle.org/current/userguide/troubleshooting.html
 */

plugins {
    id("org.xvm.build.version")
    alias(libs.plugins.xtc)
}

// TODO: Add an assert:debug in our test program.

dependencies {
    xdk(libs.xdk)
}

sourceSets {
    main {
        xtc {
        }
    }
}

xtc {
    printVersion()
}

xtcCompile {
    fork = true
    forceRebuild = true
}

// TODO Add the possibility to run a module with hard coded path
xtcRun {
    version = true
    fork = true // Run the app in the build process.
    module {
        moduleName = "AppUnderTest"
        method = "run"
        args("some", "arg")
    }

    // Other shorthand and DSL:
    /*

    moduleName("Someting")

    moduleNames("Something", "Other", "...")

    // Or a list of modules work, i.e. repeated module {} blocks. Executed in order.
    module {
        moduleName = "mandatoryName"
        method = "optionalMethodToRun" // default is "run"
        args("arguments", "to", "add") // default is empty list.
    }
        ... other modules ...

    module {
        ...
    }

    debugEnabled = false

    // Not supported yet. You can get parallelism if you add another project. Gradle will build everything that's
    // independent at the same time. TODO: This will be improved with the Worker API.
    allowParallel = true

    jvmArgs = ..

    verbose = true // default is false

    */
}
