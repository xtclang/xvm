import org.xvm.plugin.tasks.XtcCompileTask
import org.xvm.plugin.tasks.XtcRunTask

// TODO: The build repo publication is pretty annoying, as it rebuilds every time, even though nothing has changed.
// Hence, it would be nice to just be able to refer to a standard repository in some normal way.
// e.g. by extending a pluginSpec for xtcLocalDistRepo() that does this better, or by getting rid of the GitHub
// credentials stuff.

plugins {
    /**
     * This line just ties the dev project into the versioned XDK build as one of its components.
     * This means we have the same wiring for using the xtc plugin and xdk libs from the XDK repo,
     * and using any changed versions as the "official" ones, enabling incremental rebuild and testing
     * with semantics just as if we were using "real" published artifacts. For an XTC third party app,
     * this line would not be needed.
     *
     * TODO: A standalone "third party app" template repo is almost finished.
     */
    id("org.xvm.build.version")

   /**
    * This is the XTC plugin. An external user would have a version catalog of their own, default location
    * would be in repo root/gradle/libs.versions.toml. Note that any dependency resolution works, so you
    * could just put:
    *
    *    id("org.xvm:xtc-plugin:$version") (or a hard coded version string after the last colon, of course)
    *
    * (But this is just what the version catalog extension provides shorthand for. Third party users
    *  will probably put the xtc plugin and xdk with versions and identifiers in their own version catalogs,
    *  together with the versions and id:s of any other external projects they depend on, at least when
    *  their projects start to grow. So the "alias(libs.plugins.xtc)" or something almost identical will
    *  most likely pop up in that format in all kinds of projects, so it's not unrealistic to use this
    *  shorthand for both the XDK and for a third party XDK dependency project, but that is up to its
    *  implementor.)
    *
    * The XDK build builds the XDK libs and the XTC plugin and assigns them the same version, taken
    * from the version catalog, e.g. "0.4.4".
    * version catalogs, see https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalogs
    */
   alias(libs.plugins.xtc)
}

/**
 * The XTC plugin teaches the project about the "xdk" dependency, which basically means "I want all
 * modules in the XDK to go into any module path we use for compilation and execution of the modules
 * build by this project. The notation in the parenthesis, is shorthand to refer to an artifact that
 * is in our TOML version catalog description, but the user can use any dependency notation that Gradle
 * supports, of course. For example 'xdk("org.xvm:xdk:0.4.4")'.
 */
dependencies {
    xdk(libs.xdk)
}

/**
 * The source set section below is currently commented out. It illustrates how you can add source
 * with any kind of URI, anywhere (not just in the source tree of this project) to the build.
 */
//val genesLocalXtcSourceFile = File(System.getProperty("user.home") + "/ggleyzer-xtc-code/GenesHelloWorld.x")

/**
 * Add a source directory with the file to be compiled to this project. Exclude everything else from this dir or
 * any other default dir. To not exclude anything except the external source file, but also build the default
 * source sets (if they exist), just remove the "include" line or comment it out. "srcDir" means ADD a source
 * set directory to any existing ones.
 *
 * Again: this is commented out, and would complement the "execute only this compilation/run" above.
 * The default action is to compile and run modules defined under the main source set directory of this
 * project (or under the test source set, if we are executing compileXtcTest or runXtcTest).
 */
//sourceSets.main {
//    xtc {
//        srcDir(genesLocalXtcSourceFile.parentFile) // Adds a source directory to the XTC source set.
//        include(genesLocalXtcSourceFile.name) // Include Gene's XTC source file and absolutely nothing else.
//    }
//}

/**
 * XTC Compile configuration block. See @XtcCompilerExtension for what's in it.
 * As we have not stated otherwise, we leave it to the plugin's discretion to put the built xtc
 * artifact anywhere it wants (default is build/xdk/main/lib). Gradle and the plugin handles
 * the module paths for us, so there is really no nead to change this, but we can.
 */
xtcCompile {
    fork = false

    /** forceRebuild flag:
     *     forceRebuild = false is the default, and should really always be enough, if you don't have a very
     *     good reason to be recompiling already compiled and cached source code over and over again.
     *     forceRebuild = true is ABSOLUTELY not recommended for production builds, but can be useful for
     *     development and testing e.g. parts of the build lifecycle.
     *
     *     You can do the same thing in the XDK build in a more generic way for any task, by using the
     *     extension function Task.alwaysRerunTask() from the build logic.
     */
    //forceRebuild = true
}

/**
 * XTC Runtime DSL. See @XtcRuntimeExtension class for what's in it.
 */
xtcRun {
    fork = true // fork = false, may be desirable to execute the runner inside the build process so breakpoint works without connectors and stuff
    module {
        moduleName = "DevExample"
        methodName = "run"
        args("one", "two", "three")
    }
    //moduleName("DevExample")  // alternative shorthand with only module name, default run method and no args (can also be an of module names)
}

/**
 * Helper logs. Print the inputs, outputs etc, of any task in the project that is executed:
 */

tasks.withType<XtcCompileTask>().configureEach {
    doLast {
       printTaskInputs()
       printTaskOutputs()
    }
}

tasks.withType<XtcRunTask>().configureEach {
    /**
     * An XtcRun task declares no explicit outputs, which means that Gradle should always automatically treat
     * it as non-cachable, and rerun it. If not, we can force it with the XDK build logic function
     * Task.alwaysRerunTask() (see above), or its Gradle equivalent configuration. This should not be necessary,
     * though, so if the run does not seem to execute again in an unmodified build, when rerun, please debug
     * that.
     */
    //alwaysRerunTask()
    doLast {
       printTaskInputs()
       printTaskOutputs()
    }
}
