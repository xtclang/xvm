import org.jetbrains.gradle.ext.*
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.ShortenCommandLine.*
import org.xvm.BuildHelpers
//import org.jetbrains.gradle.ext.SerializationUtil.prettyPrintJSON

/*
 * Main build file for the XVM project, producing the XDK.
 */

/*
* TODO root level settings.gradle.kts:
* for (project in rootProject.children) {
*    project.apply {
*        projectDir = file("subprojects/$name")
*        buildFileName = "$name.gradle.kts"
*        require(projectDir.isDirectory) { "Project '${project.path} must have a $projectDir directory" }
*        require(buildFile.isFile) { "Project '${project.path} must have a $buildFile build script" }
*    }
* }
*/

plugins {
    id("java-library")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7" apply true
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "org.xvm"
version = "0.4.3"

val manualTestDir = java.io.File(rootDir, "manualTests")

// TODO move this to the xtc plugin configuration or something.

val enablePreview = false

// Display all Java warnings, warnings as error?
val pedantic = false

defaultTasks("clean", "build")

subprojects.forEach {
    println("subproject: $it")
    // todo print dependencies.
    println("** " + it.configurations)
}

// .editorconfig replaces the Code Style xml file import
//

/* Example how the IDEA plugin extends the idea plugin that is to be replaced

def extend(Project project) {
    def ideaModel = project.extensions.findByName('idea') as IdeaModel
    if (!ideaModel) { return }
 */

/*
If you simply want to load a Gradle project into IntelliJ IDEA, then use the IDE’s
       import facility. You do not need to apply this plugin to import your project into NOTE IDEA,
       although if you do, the import will take account of any extra IDEA configuration you have that doesn’t
        directly modify the generated files — see the
       Configuration section for more details.

       idea (module file generation for project), openIdea, cleanIdea,
       cleanIdeaWorkspace (clean does not depend on this to avoid wiping e.g. .idea
       ideaProject, ideaModule, ideaWorkspace (idea depends on these)
        */

// TODO: WHY DOES CODE STYLE IN KTS SCRIPTS REFUSE TO BREAK LINES. EditorConfig seems right?

//runConfigurations
/*val ProjectSettings.runConfigurations: RunConfigurationContainer
    get() = (this as ExtensionAware).the()

fun ProjectSettings.runConfigurations(configure: RunConfigurationContainer.() -> Unit) {
    configure(this.runConfigurations)
}*/


idea {
    module {
        inheritOutputDirs = true
        println("Path variables: " + pathVariables)
        println(project.name + " project has module flag $inheritOutputDirs")
    }

    // Configurations needed:
    //   clean build, release and debug + breakpoint, simple app
    //   run tests?
    //   run and debug build with breakpoints?
    project {
        // TODO set example breakpoint
        // TODO - a way to execute a RunConfiguration to the breakpoint?

        /*
        interface ExtensionAware
        Objects that can be extended at runtime with other objects.

        // Extensions are just plain objects, there is no interface/type class MyExtension { String foo MyExtension(String foo) { this.foo = foo } } // Add new extensions via the extension container project.extensions.create('custom', MyExtension, "bar") // («name», «type», «constructor args», …) // extensions appear as properties on the target object by the given name assert project.custom instanceof MyExtension assert project.custom.foo == "bar" // also via a namespace method project.custom { assert foo == "bar" foo = "other" } assert project.custom.foo == "other" // Extensions added with the extension container's create method are themselves extensible assert project.custom instanceof ExtensionAware project.custom.extensions.create("nested", MyExtension, "baz") assert project.custom.nested.foo == "baz" // All extension aware objects have a special “ext” extension of type ExtraPropertiesExtension assert project.hasProperty("myProperty") == false project.ext.myProperty = "myValue" // Properties added to the “ext” extension are promoted to the owning object assert project.myProperty == "myValue"
        Many Gradle objects are extension aware. This includes; projects, tasks, configurations, dependencies etc.
        For more on adding & creating extensions, see ExtensionContainer.

        Could also do extensionaware inline in the script. Probably good for various XTC tasks
        idea {
            project {
                (this as ExtensionAware)
                 configure<ProjectSettings> {
                    doNotDetectFrameworks("android", "web")
         */
        settings {
            runConfigurations {
                val app = create("MyApp", Application::class).apply {
                    mainClass = "org.xvm.runtime.TestConnector"
                    moduleName = "xvm.javatools.test"
                    String mainClass - class to run
                            String workingDirectory - working directory
                            String jvmArgs - jvm arguments string
                    String moduleName - name of Idea module to collect runtime classpath
                            String programParameters - program arguments string
                    Map<String, String> envs - environment variables map
                }
                println(app)
                //prettyPrintJSON(app)
            }

            /*
            withIDEADir {
                File ideDir ->
                println("heja")
            }

            withIDEAFileXml("vcs.xml") {
                XmlProvider p ->
                p.asNode().component
                    .find { it.@ name == 'VcsDirectoryMappings' }
                    .mapping.@ vcs = 'Git'
            }*/

            compiler {
                processHeapSize = 2048 // Gradle daemon max heap size, default is 700
                autoShowFirstErrorInEditor = true
                enableAutomake = false
                parallelCompilation = true
                rebuildModuleOnDependencyChange = true
                javac {
                    javacAdditionalOptions = "-encoding UTF-8 -deprecation -Xlint:all"
                    generateDeprecationWarnings = true
                }

                /*            runConfigurations {
                                                // TODO: Run build task java_tools before, or at least be a dependency
                                                "HelloWorld"(org.jetbrains.gradle.ext.Application) {

                                                }
                                           }
                            //settings.generateImlFiles*/
                delegateActions {
                    // Always delegate "Build and Run" and "Test" actions from Gradle to IntelliJ.
                    delegateBuildRunToGradle = false
                    testRunner = ActionDelegationConfig.TestRunner.PLATFORM
                    defaultTasks("clean", "build")
                }
                encodings {
                    // This takes care of encodings in the IntelliJ compilers, but we need to fix the Gradle ones too.
                    encoding = "UTF-8"
                }/* Project level settings *//*                compiler
                                groovyCompiler
                                copyright
                                runConfigurations
                                doNotDetectFrameworks
                                taskTriggers
                                delegateActions
                                ideArtifacts
                                encodings*/
            }
        }
        workspace {

        }
    }
}

// DO not do this or subproject.
// Add tasks and stuff to all subprojects.
allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils")).using(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).using(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools")).using(project(":javatools"))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
// TODO all these options and similar ones should live in various global
//  configurations in buildSrc
        options.encoding = "UTF-8"
//        options.warnings = true
        options.debug()
//        options.debugOptions()
//        val op
// TODO for --add-exports or similar things that may be needed to reach preview JDK code,
//  we need to do options.isFork = true && options.forkOptions.executale = "javac"
//  This is because for any non standard options, e.g. -XDignore.symbol.file, we need to fork.
        options.compilerArgs = listOf("-Xlint:all", "-deprecation", "-Xmaxwarns", "10000")
        if (enablePreview) {
            options.compilerArgs.add("--enable-preview")
        }
        doLast {
            println(name + " running for " + project.name)
        }
    }
}

//A project is essentially a collection of Task objects.Each task performs some basic piece of work, such as compiling
//classes, or running unit tests, or zipping up a WAR file . You add tasks to a project using one of the create ()
//methods on TaskContainer, such as TaskContainer.create(java.lang.String).You can locate existing tasks using one of
//the lookup methods on TaskContainer, such as TaskCollection.getByName(java.lang.String)
// We must ensure gradle builds use the same config as the one in the idea section, and gradle
// needs to tell javac stuff like flags, use java 19 etc... Reuse these in idea config.


//tasks.forEach {
//}

/*
tasks.register("build") {
    group       = "Build"
    description = "Build all projects"

    dependsOn(project("xdk:").tasks["build"])
}
*/

// Example of a task wrapper to logic elsewhere.
// TODO: This is probably how to subclass flavors of an XtcCompileTask for
//  xdk JavaExec->Task.

task("extendedBuildSrcTestTask") {
    doLast {
        println(project.name)
        BuildHelpers.sayHello()
    }
}

// TODO: Why does this file not resolve project extension methods in the buildSrc dir. This seems wrong?

tasks.create("extendedBuildSrcTestTask2", org.xvm.XvmTask::class) {
    doLast {
        println("We have executed xvm task.")
    }
}


task("gitClean") {
    group = "other"
    description = "Runs git clean, recursively from the repo root. Default is dry run."

    doLast {
        exec {
            val dryRun = !"false".equals((project.findProperty("gitCleanDryRun") ?: "true").toString(), ignoreCase = true)
            logger.lifecycle("Running gitClean task...")
            if (dryRun) {
                logger.warn("WARNING: gitClean is in dry run mode. To explicitly run gitClean, use '-PgitCleanDryRun=false'.")
            }
            commandLine("git", "clean", if (dryRun) "-nfxd" else "-fxd", "-e", ".idea")
        }
    }
}
