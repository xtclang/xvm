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
 * launch the XTC components through standard "javaexec" specifications, so that the XTC Java code
 * runs in a different process. A similar approach in fork mode can be achieved the standard way,
 * by launching with -Xdebug, just like you would for any other Java application, like so:
 *
 *   1) export GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
 *   2) Put some breakpoints and launch the remote debug configuration inside your IDE on the port 5005
 *   (somewhat dated StackOverflow answer: https://stackoverflow.com/a/2066309/1267320)
 *
 *   If the above-mentioned approach does not let you put breakpoints in the build scripts, you
 *   can still put one in e.g. org.gradle.api.internal.project.AbstractProject#dependencies() and
 *   you will at least have the opportunity to debug the build through the Gradle classes, hooking
 *   into project configuration, dependency resolution, etc.
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

// enabled = true, externalSourceOnly = true;  Only build and run your external source.
// enabled = true, externalSourceOnly = false; Build and run both your external source and the project source set.
// enabled = false, externalSourceOnly = <any value>; Build and run just the project source set; "default Maven behavior."
private val externalSrc = getExternalSource(enabled = false, externalSourceOnly = false)

/*
 * Add a reference to an external file to compile, run and/or debug. If the file does not exist
 * (check gradle.properties for its path), the project will compile and run the default source set.
 * However, for that to happen, you need to remove the "include only the externalFile.name" statement
 * in the sourceSets configuration.
 *
 * The external file in my world is in $HOME/xtc/xtcapp.x. and defines a module called AnyAppAnywhere.
 */

sourceSets.main {
    xtc {
        if (externalSrc.enabled) {
            /*
             * If we just want to compile an arbitrary file, not necessarily in the project, we add the
             * directory where the external file resides to our main source set. So far, we keep the
             * default source set (by default under $projectDir/src/main/x) as part of the source to
             * be compiled as well. (Given that it exists, of course. you don't need to have any source
             * sets in an XTC Gradle project).
             *
             * If we only want to build this one file, and not anything from our default source set,
             * we add the include statement on the second line. This makes sure that "src" is the only
             * source file considered.
             */
            val (externalSrcFile, externalSrcDir) = externalSrc.srcFile to externalSrc.srcDir
            logger.lifecycle("$prefix Compiling external file with/instead of ${project.name} SourceSet: (${externalSrcFile.absolutePath})")
            srcDir(externalSrcDir)
            if (externalSrc.exclusive) { // should we ignore everything in the project except for the external source file?
                logger.lifecycle("$prefix External source is exclusive; we will ignore the default source set in this project.")
                include(externalSrcFile.name)
                throw GradleException("WHY!")
            }
        }
    }
}

/**
 * XTC Compile configuration block. See @XtcCompilerExtension for what's in it.
 */
xtcCompile {
    //fork = false
    forceRebuild = true
}

/**
 * XTC Runtime DSL. See @XtcRuntimeExtension class for what's in it.
 * If externalFile is null, the spec will be treated as having no modules declared.
 * This means that it will default to its behavior of running what it can find in
 * the source set. If the source set is empty, it will warn and exit.
 *
 * Here, we disable fork mode, for the XTC runner. This means that it will continue
 * to execute XTC code in the same process as it runs the build. This is faster than
 * the default behavior, which forks a new JVM process (using Gradle's JavaExec task
 * mechanism). However, if fork mode is disabled, this means that you can step in
 * and out of the build systems, and put breakpoint both here, and in the XTC
 * implementation code (currently mostly in "javatools") as well.
 */
xtcRun {
    fork = false
    if (externalSrc.enabled) {
        moduleName(externalSrc.moduleName)
    }
    if (!externalSrc.exclusive) {
        moduleName("DevExample")
    }

    // You can also use the module DSL object for more granularity:
    /*
        module {
            moduleName = "AnotherAppModuleName" // mandatory
            methodName = "run"            // not mandatory
            args("Hello", "World")        // not mandatory (add two args)
        }
        module {
            // ... another module to run ...
        }
    */

    // NOTE: You can execute > 1 modules in order of declaration with repeated
    // "moduleName(...)" statements, or module {} DSL sections. To ignore this
    // spec, just remove the xtcRun {} block and use the xtcRunAll task. This
    // will change the run behavior of 'xec' to just execute all modules on
    // the resolved module path. The order of execution is currently undefined,
    // and parallel execution is possible, but not yet guaranteed to work.
}

// *NOTE*:  TO JUST USE THE DEV PROJECT build.gradle.kts FILE AND YOUR IDE/gradlew TO RUN AND DEBUG
// YOUR EXTERNAL XTC SOURCE FILE, YOU CAN STOP READING HERE.
//
// BELOW IS JUST A HELPER SO WE HAVE A GENERIC WAY OF MODIFYING AND EXTENDING WHATEVER DEBUG SCENARIO
// WE WILL FIND THE MOST USEFUL IN THE FUTURE. YOU COULD JUST HAVE HARDCODED "externalSourceInfo"
// FIELDS ABOVE, IGNORED CREATING PROPERTIES FOR THEM AND SO ON.
/**
 * ExternalSource representation.
 *
 * Information about any external source we are to compile/debug, i.e. pointing out an x file on your hard drive.
 * The default behavior and file name are defined in the gradle.properties of this project, but you can of
 * course hardcode whatever you like anywhere you like, if your only goal is to get to debug some XTC code
 * running through the launcher and the build system.
 */
data class ExternalSource(
    val enabled: Boolean = true,
    val exclusive: Boolean = false,
    val srcFile: File,
    val moduleName: String
) {
    val srcDir: File get() = this.srcFile.parentFile

    val sourceCode: List<String> get() = srcFile.readLines()

    companion object {
        private const val DEV_PREFIX = "org.xvm.example.buildscript"
        const val PROPERTY_PATH = "${DEV_PREFIX}.sourceFile"
        const val PROPERTY_MODULE_NAME = "${DEV_PREFIX}.moduleName"
        const val PROPERTY_EXCLUSIVE = "${DEV_PREFIX}.externalSourceOnly"
        const val DEFAULT_MODULE_NAME = "DevExampleExternalSource"
        const val MODULE_NONE = DEFAULT_MODULE_NAME
    }
}

@Suppress("KotlinConstantConditions")
fun getExternalSource(enabled: Boolean = true, externalSourceOnly: Boolean? = null): ExternalSource {
    if (!enabled) {
        // Ignore external source, just build the normal source set.
        return ExternalSource(enabled, exclusive = false, File("."), ExternalSource.MODULE_NONE)
    }

    // Set exclusive to a non-null value of the overrideExclusiveParam. If the override is null,
    // read the property from gradle.properties. If the property isn't there, then default to false,
    // i.e. "compile both the external source and the default source set in the project
    val exclusive =
        externalSourceOnly ?: (findProperty(ExternalSource.PROPERTY_EXCLUSIVE)?.toString()?.toBoolean() ?: false)
    val srcFile = (property(ExternalSource.PROPERTY_PATH) as String).run { File(this) }
    val moduleName = findProperty(ExternalSource.PROPERTY_MODULE_NAME)?.toString() ?: ExternalSource.DEFAULT_MODULE_NAME
    val info = ExternalSource(true, exclusive, srcFile, moduleName)

    logger.lifecycle(
        """
                $prefix *** Compiling/running external source file: '${info.srcFile.absolutePath}'
                $prefix *** The name of the module built by the external source file: '${info.moduleName}'
                $prefix *** Use external file exclusively (ignore the default source set in this project): ${info.exclusive}
            """.trimIndent()
    )
    logger.lifecycle("$prefix The source code for '${info.srcFile}' is:")
    info.sourceCode.forEach { logger.lifecycle("$prefix    $it") }

    return info
}
