import org.gradle.internal.logging.console.UserInputConsoleRenderer
import org.xvm.plugin.tasks.XtcCompileTask
import java.nio.file.Paths
import kotlin.io.path.isDirectory

/*
 * This is a subproject templated to compile, run, and debug the XTC compiler and runtimes.
 * By default, the source set of this project contains a simple HelloWorld-ish program. Do
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
 * The configs in this project may use the "fork = false" option, for launching the XTC tools. This means
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
    alias(libs.plugins.tasktree)
}

dependencies {
    xdk(libs.xdk)
}

/*
 * Add a reference to an external file to compile, run and/or debug. If the file does not exist
 * (check gradle.properteis for its path), the project will compile and run the default source set.
 * However, for that to happen, you need to remove the "include only the externalFile.name" statement
 * in the sourceSets configuration.
 *
 * The external file in my world is in $HOME/xtc/xtcapp.x. and defines a module called AnyAppAnywhere.
 */

val externalFile = runCatching { File(property("org.xvm.path.singleSourceFile") as String) }.getOrNull()
val externalModuleName = "AnyAppAnywhere"

if (externalFile != null) {
    logger.lifecycle("$prefix External source file given: ${externalFile.absolutePath} (exists: ${externalFile.exists()})")

    sourceSets {
        main {
            xtc {
                // Add directory where the external file lives as a source set.
                srcDir(externalFile.parentFile)
                // Ignore any other file in the source set, like those in this project. Remove the include line to
                // compile both the main source set of this project and the external file to XTC modules.
                include(externalFile.name)
            }
        }
    }
}

// XTC Compile DSL. See the XtcCompilerExtension class for what's in it.
xtcCompile {
    //fork = false
    forceRebuild = true
}

// XTC Run DSL. See the XtcRuntimeExtension class for what's in it.
xtcRun {
    fork = false
    moduleName(externalModuleName)

    // You can also use the module DSL object for more granularity:
    /*
    module {
        moduleName = "AnyAppAnywhere" // mandatory
        methodName = "run"            // not mandatory
        args("Hello", "World")        // not mandatory (add two args)
    }
    */

    // You can execute > 1 modules in order of declaration with repeated
    // "moduleName(...)" statements, or module {} DSL sections.
    // To ignore this spec, use the xtcRun<SourceSet>All task, to just
    // execute all modules on the resolved module path.
}
