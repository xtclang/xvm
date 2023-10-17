import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.internal.os.OperatingSystem
import java.nio.file.Paths

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    id("org.xvm.build.publish")
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
    distribution // TODO: Create our own XDK distribution plugin, or put it in the XTC plugin
}

val xtcLauncherBinaries by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    xtcJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.crypto)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.jsondb)
    xtcModule(libs.xdk.net)
    xtcModule(libs.xdk.oodb)
    xtcModule(libs.xdk.web)
    xtcModule(libs.xdk.webauth)
    xtcModule(libs.xdk.xenia)
    xtcModule(libs.javatools.bridge)
    @Suppress("UnstableApiUsage")
    xtcLauncherBinaries(project(path = ":javatools-launcher", configuration = "xtcLauncherBinaries"))
}

val xvmDist = XvmDistribution(project)

// Local configuration to provide an xdk-distribution, which contains versioned zip and tar.gz XDKs.
val xdkProvider by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    // TODO these are added twice to the archive configuration. We probably don't want that.
    outgoing.artifact(tasks.distZip) {
        extension = "zip"
    }
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("xdk-distribution-archive"))
    }
}

tasks.named("clean") {
    doLast {
        subprojects.forEach {
            // Hack to handle subprojects clean, not includedBuilds, where dependencies are auto-resolved by the aggregator.
            logger.lifecycle("$prefix Cleaning subproject ${it.name} build directory.")
            delete(it.layout.buildDirectory)
        }
    }
}

val distTar by tasks.getting(Tar::class) {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

var assembleDist = tasks.assembleDist {
    dependsOn(installDist)
}

val distExe by tasks.registering {
    group = xvmDist.distributionGroup()
    description = "Use an NSIS compatible plugin to create the Windows .exe installer."
    doFirst {
        TODO("$prefix distExe needs to be ported to the new build system.")
        /*
        val distEXE = tasks.register("distEXE") {
            group = xtcDist.distributionGroup()
            description = "Create the XDK .exe file (Windows installer)"

            dependsOn(build)
            dependsOn(prepareDirs)

            doLast {
                val output = java.io.ByteArrayOutputStream()
                project.exec {
                    commandLine("which", "makensis")
                    standardOutput = output
                    setIgnoreExitValue(true)
                }
                if (output.toString().trim().length > 0) {
                    // notes:
                    // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
                    // - requires the "makensis" command to be in the path
                    // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS

                    val src = file("src/main/nsi/xdkinstall.nsi")
                    val dest = "${distDir}/xdk-${distName}.exe"
                    val ico = "${launcherMain}/c/x.ico"

                    project.exec {
                        environment("NSIS_SRC", "${xdkDir}")
                        environment("NSIS_ICO", "${ico}")
                        environment("NSIS_VER", "${distName}")
                        environment("NSIS_OUT", "${dest}")
                        commandLine("makensis", "${src}", "-NOCD")
                    }
                } else {
                    logger.error("*** Failure building \"distEXE\": Missing \"makensis\" command")
                }
            }
        }*/
    }
}

val test by tasks.named("test") {
    dependsOn(gradle.includedBuild("manualTests").task(":runXtc"))
}

/**
 * Take the output of assembleDist and put it in an installation directory.
 *
 * TODO: This task should create symlinks for the launcher names to the right binaries.
 * TODO better copying, even if it means hardcoding directory names.
 */
val installDist = tasks.installDist {
    doLast {
        logger.lifecycle("$prefix Installed distribution to build directory.")
        logger.info("$prefix Installation files:")
        outputs.files.asFileTree.forEach {
            logger.info("$prefix   ${it.absolutePath}")
        }
    }
}

val installLocalDist by tasks.registering {
    // TODO: This can be done a lot more nicely with relative path copy/renaming as
    //   described in Chapter 1 of Gradle Beyond The Basics by O'Reilly.
    group = xvmDist.distributionGroup()
    description = "Creates an XDK installation, and overwrites any local installation with it."
    dependsOn(installDist) // needs distTar, and distZip executed before this can run
    doLast {
        val xecPath = executeCommand("which", "xec")
            ?: throw buildException("$prefix Cannot find a local installation of the XDK on the system path.")
        val xecFile = Paths.get(xecPath).toRealPath()
        val libExecDir = file(xecFile.parent.parent)
        val backup = project.layout.buildDirectory.dir("xdkLocalDistCopy")
        copy {
            from(libExecDir)
            into(backup)
        }
        copy {
            from("${xvmDist.installDir}/libexec/bin")
            into("$libExecDir/bin")
        }
        copy {
            from("${xvmDist.installDir}/libexec/lib")
            into("$libExecDir/lib")
        }
        copy {
            from("${xvmDist.installDir}/libexec/javatools")
            into("$libExecDir/javatools")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("xdkArchive") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            logger.lifecycle("$prefix Publication '$groupId:$artifactId:$version.$name' configured.")
            artifact(tasks.distZip) {
                //classifier = "xdk"
                extension = "zip"
            }
        }
    }
}

/**
 * Set up the distribution layout. This is executed during the config phase, which means that we can't
 * resolve outputs to other tasks to their explicit destination files yet, unless they have run.
 * However, we _can_ use a from spec that refers to a task, which then becomes a dependency.
 */
distributions {
    main {
        distributionBaseName = xvmDist.name
        contents {
            val resources = tasks["processResources"].outputs.files
            // 1) copy xdk resources/main/xdk to install/xdk/libexec
            // 2) copy javatools_launcher/bin/* to install/xdk/libexec/bin/
            // 3) copy xdk modules to install/xdk/libexec/lib
            // 4) copy javatools.jar, turtle and bridge to install/xdk/libexec/javatools
            from(resources) {
                eachFile {
                    path = path.replace("xdk", "libexec")
                    path = path.replace("libexec-", "xdk-") // TODO: Hacky.
                    includeEmptyDirs = false
                }
            }
            from()
            from(xtcLauncherBinaries) {
                into("libexec/bin")
            }
            from(configurations.xtcModule) {
                into("libexec/lib")
                exclude("**/javatools*") // TODO consider breaking out javatools_bridge.xtc, javatools_turtle.xtc into a seprate configuration.
            }
            from(configurations.xtcModule) {
                into("libexec/javatools")
                include("**/javatools*") // TODO consider breaking out javatools_bridge.xtc, javatools_turtle.xtc into a seprate configuration.
            }
            from(configurations.xtcJavaTools) {
                rename {
                    assert(it.endsWith(".jar"))
                    it.replace(Regex("-.*.jar"), ".jar")
                }
                into("libexec/javatools") // should just be one file with corrected dependencies, assert?
            }
            from(tasks.versionFile)
        }
    }
}

// TODO: Add a sanity check, that makes sure all files in the distribution are there,
//   and perhaps things like the right binaries having been resolved/copied. And maybe
//   even some kind of signature/hash check.

// TODO - Keep dir patterns here too, or have it return copy specs based on resources
class XvmDistribution @Inject constructor(private val project: Project) {
    companion object {
        const val DISTRIBUTION_GROUP = "distribution"
        private val CURRENT_OS = OperatingSystem.current()
    }

    private val build = project.layout.buildDirectory
    val name: String = project.name
    val distributionDir = build.dir("distributions/")
    val installDir = build.dir("install/$name")
    private val version: String = buildString {
        fun isCiBuild(): Boolean {
            return System.getenv("CI") != null
        }

        fun getBuildNumber(): String? {
            return System.getenv("BUILD_NUMBER")
        }

        fun getLatestGitCommit(): String? {
            return executeCommand("git", "rev-parse", "HEAD")
        }

        fun getCiTag(): String {
            if (!isCiBuild()) {
                return ""
            }
            val buildNumber = getBuildNumber()
            val gitCommit = getBuildNumber()
            if (buildNumber == null || gitCommit == null) {
                logger.error("$prefix Cannot resolve CI build tag (buildNumber=$buildNumber, commit=$gitCommit)")
                return ""
            }
            return "-ci-$buildNumber+$gitCommit".also {
                logger.lifecycle("$prefix Configuration XVM distribution for CI build: '$it'")
            }
        }

        append(project.version)
        append(getCiTag())
    }

    fun distributionGroup(): String {
        return DISTRIBUTION_GROUP
    }

    override fun toString(): String {
        return "$name-$version"
    }
}
