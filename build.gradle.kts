import org.jetbrains.gradle.ext.*
import org.xvm.CleanIdeTask
import org.xvm.CleanGitTask
import java.nio.file.Paths

/**
 * Main build file for the XVM project, producing the XDK.
 *
 * Gradle best practice is to put only accessing, not mutating state outside the buildSrc directory.
 * We are gradually migrating over mutating logic common to several parts of the build to buildSrc.
 * There are a few issues with the Kotlin DSL for Gradle and buildSrc, but we can work around most
 * of the existing ones. We would prefer the Kotlin DSL language for our build before Groovy, because
 * Jetbrains and third party developers encourage Kotlin DSL to be the new standard.
 */

group = "org.xvm"
version = "0.4.3"

plugins {
    id("java-library")
    id("kotlin-common-conventions")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
}

/*
 * Best practice way of specifying Java runtime for subprojects. No subproject that applies the IntellIJ
 * We could also specify an exact JDK distribution URL per version, and forbid the build to use any other
 * auto-detection / auto download mechanisms. This is a good step towards bit identical builds. The
 * toolchain plugin will cache any downloaded JDK just as it does with all other dependent artifacts.
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

/*
 * Set the default behavior for Gradle running with no task name to clean and re-resolve and display the
 * available tasks.
 */
defaultTasks("clean", "tasks")

/*
 * Constants used by the build, e.g. paths (versions should be handled in a version catalog)
 */
val manualTestsDir = Paths.get(rootProject.projectDir.absolutePath, "manualTests").toAbsolutePath().toString()

val cleanTask: Task = tasks["clean"]
val buildTask: Task = tasks["build"]

/*
 * Mechanisms that are applied to all projects, including the root project. We should avoid
 * putting logic in here, favoring more modular ways, wherever possible.
 */
allprojects { // TODO: Should subprojects be enough here. That may also be considered bad Gradle practice. Revisit later.
    apply(plugin="org.jetbrains.gradle.plugin.idea-ext")

    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils")).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools")).using(project(":javatools"))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // TODO: -Xlint:all in pedantic mode
    }
}

/*
 * Augment any Gradle wrapper upgrade to use DistributionType.ALL, to ensure optimum debuggability.
 * To create more compact environments, we can remove this or change it to BIN. The ALL field bundles
 * the Gradle source code and documentation, and all major IDEs can make use of it, when debugging
 * or developing build system logic, to get an environment where all parts of the Gradle API
 * autocomplete, plug into intelligent suggestions in an IDE, and so on.
 */
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

/*
 * The config "idea" represents all changes to settings that IntelliJ needs to build, run and debug
 * XTC code. It also provides sample configurations for a debuggable HelloWorld.x program with a
 * breakpoint, as well as a configuration that can be used to debug and/or test§ the build process
 * with breakpoints.
 *
 * The goal with this configuration is to make it unnecessary to ever manually change or add
 * settings in IntelliJ for a fully configured "get started immediately" dev environment for XTC.
 * This will soon be complemented by a complete language plugin for XTC, which will ingrate
 * IntelliJ with XTC development. This should include IntelliJ debugger support, language analysis,
 * and a dependency for requesting and installing the correct XDK binary artifact from a repository.
 * When complete, a new potential XTC developer need only create a new Gradle project and add the XVM
 * as a dependency to the xtc-language-plugin (the language plugin will also provide an auto generate
 * templates for both the Gradle build file and the needed XDK dependency). Futhermore, the XTC
 * compiler, and most of the stuff in Java tools will also be integrated with the IDE. For example,
 * lazy compilation requests for new source code, if it has not been built, just like Java in
 * IntelliJ works today.
 *
 * It should now be possible to add a simple .x suffixed source code snippet to the project, modify the
 * already imported Run/Debug Configuration to execute the file. write a small ".x" source code file
 * and add it to the project in the .idea directory. Right now, the current master branch shouldn't
 * need any more configuration than what is given below. The configuration also provides a
 * simple HelloWorld.x program and a breakpoint, so that just by executing it in Debug Mode will
 * bootstrap the system and leave the user in the debugger at the predefined breakpoint.
 *
 * The IDEA andIDEA.ext plugin have predefined sections:
 *    + module settings (extended by ext plugin)
 *    + project settings (extended by ext plugin)
 *    + workspace settings (likely not needed with .idea directory format, and handover to IntelliJ with the ext plugin)
 *
 * We might have to handle raw XML to do things like setting predefined breakpoints and so on.
 * Most of the time, it's enough to mess around with the xml files in the .idea folder. However, some of the
 * state, which seems it should belong there, is derived from other places in the file system. As there is
 * no clear boundary, it is confusing. Here are the system paths that IntelliJ also uses to recreate
 *
 * (where "2022.2" is replaced by the version number of the IntelliJ being used.
 *
 * Windows:
 *     Configuration (idea.config.path): %APPDATA%\JetBrains\IntelliJIdea2022.2
 *     Plugins (idea.plugins.path): %APPDATA%\JetBrains\IntelliJIdea2022.2\plugins
 *     System (idea.system.path): %LOCALAPPDATA%\JetBrains\IntelliJIdea2022.2
 *     Logs (idea.log.path): %LOCALAPPDATA%\JetBrains\IntelliJIdea2022.2\log
 *
 * macOS:
 *     Configuration (idea.config.path): ~/Library/Application Support/JetBrains/IntelliJIdea2022.2
 *     Plugins (idea.plugins.path): ~/Library/Application Support/JetBrains/IntelliJIdea2022.2/plugins
 *     System (idea.system.path): ~/Library/Caches/JetBrains/IntelliJIdea2022.2
 *     Logs (idea.log.path): ~/Library/Logs/JetBrains/IntelliJIdea2022.2
 *
 * Linux:
 *     Configuration (idea.config.path): ~/.config/JetBrains/IntelliJIdea2022.2
 *     Plugins (idea.plugins.path): ~/.local/share/JetBrains/IntelliJIdea2022.2
 *     System (idea.system.path): ~/.cache/JetBrains/IntelliJIdea2022.2
 *     Logs (idea.log.path): ~/.cache/JetBrains/IntelliJIdea2022.2/log
 *
 * Workspaces that cache runConfiguration and similar items end up with hash values
 * under the idea.config.path/workspace.
 */
idea {
    /**
     * IdeaModule configuration:
     *
     *   + inheritOutputDirs (make sure output directories for compiling module will be located below output directory for project,
     *       otherwise they will be available through getOutputDir() and getTestOutputDir(). Set this property to 'true' to make
     *       IntelliJ use the same output paths as Gradle does, to avoid confusion mixing two different build styles, the one
     *       when handling the build over to IntelliJ being different and contains "out" directories as default. There were
     *       issues here in the past, but now, it should be OK to end up with a project with some modules compiled by Gradle,
     *       and others by IntelliJ. The output will look the same. If there are any glitches, they likely stem from artifact
     *       caching, configuration caching, or similar things. We have not seen that this is an issue. Especially since we
     *       tend to work on one module at a time in the IDE, with the rest being already compiled, or start out with Gradle
     *       dependencies being initialized, so that the entire project is rebuilt by Gradle or IntelliJ anyway. At least if
     *       we start out with a "./gradlew clean".
     *   + "packagePrefix": allows configuring package prefixes for module source directories
     *   + "facets": allows configuring module facets (only supported for Spring right now)
     *
     */
    module {
        inheritOutputDirs = true
        println("idea.module.inheritOutputDirs=$inheritOutputDirs (Gradle and IntelliJ should now use the same build directories.)")
    }

    project {
        settings {
            runConfigurations {
                /*
                 * Test run harness with a hello world example.
                 *
                 * Application config options:
                 *    + java version (from toolchain of current project)
                 *    + jvm flags: -ea -DNoDEBUG=1 -Dxvm.parallelism=1
                 *    + main class: org.xvm.runtime.TestConnector
                 *    + working directory: (for this example, a manual test: rootProjectDir/manualTests
                 *    + jvm args: src/main/x/HelloWorld.x
                 *    + module: (-cp) xvm.javatools.test
                 *    + shortenCommandLine: (read command line arguments of different types from files)
                 *    + includeProvidedDependencies: (maven style include dependencies for scope in app)
                 *
                 *    In the IDE:
                 *      + Prebuild task: "1. Run Gradle task 'xvm.javatools: build'
                 *      + Open run/debug window when started.
                 *
                 *    TODO: Would be cool to add a breakpoint at the println so we can demo the debug
                 *      configuration with one click on Run/Debug this config. Can be done with XML
                 *      transforms built into the plugin.
                 */
                create("HelloWorld", org.jetbrains.gradle.ext.Application::class) {
                    moduleName = "xvm.javatools.test"
                    workingDirectory = manualTestsDir
                    jvmArgs = "-ea -Dfile.encoding=UTF-8 -DNoDEBUG=1 -Dxvm.parallelism=1"
                    mainClass = "org.xvm.runtime.TestConnector"
                    programParameters = "src/main/x/HelloWorld.x"
                    includeProvidedDependencies
                    //val beforeRunTask = beforeRun.create<GradleTask>("xvm.javatools:build")
                    //println("beforeRunTask $beforeRunTask")
                }

                /*
                 * Run and debug the Gradle tasks for clean, build for the root project
                 * (also set as the default tasks in the root project).
                 *
                 * Gradle Task config options:
                 *    + "projectPath": absolute path to project directory with the task to execute
                 *    + "taskNames": list of Gradle tasks names to execute
                 *    + "scriptParameters": Gradle scripts flags, e.g. log level and warning detail
                 *    + "jvmArgs": Gradle JVM args (should automatically be taken from rootProject/gradle.properties)
                 *    + "envs": String->String map of environment variables.
                 *
                 * In the IDE:
                 *    + Open run/debug tool window when started
                 *    + Enable Gradle Script debugging
                 *    + Debug forked tasks in the same process
                 */
                create("GradleBuild", org.jetbrains.gradle.ext.Gradle::class) {
                    taskNames = listOf("clean", "build")
                    projectPath = rootProject.path
                    scriptParameters = "--info --warning-mode=all"
                    jvmArgs = "-Dfile.encoding=UTF-8"
                }
            }

            /*
             * IDEA Compiler settings for the Gradle build task / daemon or single executions,
             * and other settings we typically use to make a Gradle build faster.
             */
            compiler {
                processHeapSize = 1024 // Gradle daemon max heap size, default is 700
                autoShowFirstErrorInEditor = true
                enableAutomake = true
                parallelCompilation = true
                rebuildModuleOnDependencyChange = true
                javac {
                    javacAdditionalOptions = "-encoding UTF-8 -deprecation" // TODO: -Xlint:all in pedantic mode
                    generateDeprecationWarnings = true

                }

                /**
                 * Always delegate "Build and Run" from Gradle to IDEA
                 * Always use the same mechanism as the build to run every test (in this case IDEA too).
                 */
                delegateActions {
                    delegateBuildRunToGradle = false
                    testRunner = ActionDelegationConfig.TestRunner.PLATFORM
                }

                /*
                 * This takes care of encodings in the IntelliJ compilers, but we need to fix the Gradle ones too.
                 * We currenly pass it to all JVM and Javac execution sites, which may be unnecessary. This will be
                 * addressed later, as there is no harm except larger changes keeping them here for now.
                 *
                 *   + encoding: (name of encoding, e.g. "UTF-8" or "Windows-1251"
                 *   + bomPolicy: org.jetbrains.gradle.ext.EncodingConfigation.BomPolicy enum
                 *   + properties: map of encoding behaviors, e.g. "encoding => '<System Default>" and "transparentAsciiConversion => false"
                 *   + mapping: map of module names, recursively from module name if there are submodules, to encoding names
                 */
                encodings {
                    encoding = "UTF-8"
                }

                /*
                 * Here we put hooks used to manipulate the raw XML generated into the .idea directory by IntelliJ
                 * before or after it is written. This can be used to change things that should go in the configs,
                 * but that don't yet have abstraction in the "idea.ext" plugin.
                 */
                withIDEADir {
                    assert(this is File)
                    println("Callback withIDEADir executed with: ${this.absolutePath}")
                    println("Modify XML here.")
                }

                /*
                 * .idea directory per-config-file manipulation logic. In Kotlin, the best way to use this is
                 * to go through the w3c.dom framework to manipulate XML configs. In Groovy, there are better
                 * suited groovy.util.Node methods to manipulate the XML information.
                 *
                 * In all DSLs, the XmlProvider sent to this callback by IntelliJ points to an XML file whose
                 * relativePath is relative to the ".idea" config directory
                 *
                 * We can use it to modify values in the IntelliJ config files that aren't explicitly modelled by
                 * the "idea.ext" plugin, for example, adding a breakpoint to a certain line number in a certain file.
                 * The "idea.ext" plugin will likely be more complete in the future, but we already have pretty much
                 * all the support we need to set up a starting state from scratch.
                 */
                /*
                withIDEAFileXml(".idea/vcs.xml") {
                    val moduleRoot = asElement() // this is an org.w3c.dom.Element, preferred in the Kotlin DSL. Otherwise use asNode() (groovy.util.Node)
                    val doc = moduleRoot.ownerDocument

                    println("ModuleRoot: $moduleRoot")
                    println("ownerDocument: $doc")
                    // groovy example: asNode().component.find { it.@name == 'VcsDirectoryMappings' }.mapping.@vcs = 'Git'
                }
                 */
            }
        }
    }
}

println("Module name from config: " + project.idea.module.name)

/*
 * Add tasks that clean the IDE config/state, and files not under source control.
 * Also add a full rebuild task, similar to the script in project.rootDir/bin/clean.sh
 */
tasks.register<CleanIdeTask>("cleanIde") {
    doLast {
        println("Finished cleaning IDE config and state.")
    }
}

tasks.register<CleanGitTask>("cleanGit") {
    doLast {
        println("Finished cleaning files not under source control")
    }
}

val cleanDeleteGitTask = tasks.register<CleanIdeTask>("cleanGitDelete") {
    delete = true

}
val cleanDeleteIdeTask = tasks.register<CleanIdeTask>("cleanIdeDelete") {
    delete = true
}

val cleanAllTask = tasks.register("cleanAll") {
    dependsOn(cleanDeleteGitTask, cleanDeleteIdeTask, cleanTask)
    doLast {
        println("Finished cleaning files not under source control and IDE state.")
    }
}

tasks.register("rebuildAll") {
    dependsOn(cleanAllTask, buildTask)
    doLast {
        println("Finished rebuild.")
    }
}
