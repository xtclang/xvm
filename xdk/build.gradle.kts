import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    id("org.xvm.build.publish")
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
    alias(libs.plugins.versions)
    distribution // TODO: Create our own XDK distribution plugin, or put it in the XTC plugin
}

val xtcLauncherBinaries by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

/**
 * Local configuration to provide an xdk-distribution, which contains versioned zip and tar.gz XDKs.
 */
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

// TODO: Add a plugin handler that scans the local build dir? Bootstrapping? Worth it?
val xtcPluginRepoConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val xtcJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
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

internal val xvmDist = xdkBuildLogic.xdkDistribution()
logger.lifecycle("$prefix *** Building XDK; semantic version: '${property("semanticVersion")}' ***")

publishing {
    publications {
        create<MavenPublication>("xdkArchive") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            logger.lifecycle("$prefix Publication '$name' configured for '$groupId:$artifactId:$version'")
            artifact(tasks.distZip) {
                extension = "zip"
            }
        }
    }
}

val pluginPublication by tasks.registering {
    group = LifecycleBasePlugin.BUILD_GROUP
    // TODO: includeBuild dependency; Slightly hacky - use a configuration from the plugin project instead.
    dependsOn(gradle.includedBuild("plugin").task(":publishAllPublicationsToBuildRepository"))
    outputs.dir(buildRepoDirectory)
    doLast {
        logger.lifecycle("$prefix Published plugin to build repository: ${buildRepoDirectory.get()}")
        //printTaskOutputs()
    }
}

/**
 * Set up the distribution layout. This is executed during the config phase, which means that we can't
 * resolve outputs to other tasks to their explicit destination files yet, unless they have run.
 * However, we _can_ use a from spec that refers to a task, which then becomes a dependency.
 */
distributions {
    main {
        distributionBaseName = xvmDist.distributionName // TODO: Should really rename the distribution to "xdk" explicitly.
        contents {
            val resources = tasks["processResources"].outputs.files.asFileTree
            val published = tasks["pluginPublication"].outputs.files
            // 1) copy build plugin repository publication of the XTC plugin to install/xdk/repo
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
            from(published) {
                into("repo")
            }
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

val clean by tasks.existing {
    doLast {
        subprojects.forEach {
            // Hack to handle subprojects clean, not includedBuilds, where dependencies are auto-resolved by the aggregator.
            logger.lifecycle("$prefix $name Cleaning subproject ${it.name} build directory.")
            delete(it.layout.buildDirectory)
            logger.lifecycle("$prefix $name Done.")
        }
        logger.lifecycle("$prefix $name Cleaning composite build common build directory: ${compositeRootBuildDirectory.get()}")
        delete(compositeRootBuildDirectory)
        logger.lifecycle("$prefix $name Done.")
    }
}

val distTar by tasks.existing(Tar::class) {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

val assembleDist by tasks.existing {
    doFirst {
        logger.lifecycle("$prefix $name; Assembling distribution...")
    }
}

val processResources by tasks.existing(Copy::class)

val distExe by tasks.registering {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Use an NSIS compatible plugin to create the Windows .exe installer."
    doLast {
        throw GradleException("TODO: distExe needs to be implemented and verified.")
    }
    // TODO("$prefix distExe needs to be ported to the new build system.")
    /*
    val makensis = xdkBuildLogic.findExecutableOnPath("makensis")
    val nsi = file("src/main/nsi/xdkinstall.nsi")
    val outputDir = xvmDist.getDistributionDir()

    outputs.dir(outputDir)

    onlyIf {
        makensis != null
    }

    // notes:
    // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
    // - requires the "makensis" command to be in the path
    // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS
    // - requires the x.ico file to be in the same directory as the nsi file
    doLast {
        if (makensis == null) {
            throw buildException("Cannot find makensis on the path.")
        }
        if (!nsi.exists()) {
            throw buildException("Cannot find nsi file: ${nsi.absolutePath}")
        }
        val exe = File(outputDir.get().asFile, "xdk-$version.exe")
        //val ico = "${launcherMain}/c/x.ico"

        exec {
            environment("NSIS_SRC", xvmDist.getInstallDir().get().asFile.absolutePath)
            environment("NSIS_VER", xvmDist.getName())
            environment("NSIS_OUT", exe.absolutePath)
            //environment("NSIS_ICO", ico)
            commandLine(makensis.toFile().absolutePath, nsi.absolutePath, "-NOCD")
        }
    }*/
}

val test by tasks.existing {
    // TODO: Test task should be automatically configured by XTC the plugin in the future
    //   This is just a sanity check to make sure we can plug in the XDK distro we are
    //   building, and compile and run a small XTC program.
    dependsOn(gradle.includedBuild("manualTests").task(":runXtc"))
}

/**
 * Take the output of assembleDist and put it in an installation directory.
 *
 * TODO: This task should create symlinks for the launcher names to the right binaries.
 * TODO better copying, even if it means hardcoding directory names.
 */
val installDist by tasks.existing {
    doLast {
        logger.lifecycle("$prefix Installed distribution to build directory.")
        logger.info("$prefix Installation files:")
        outputs.files.asFileTree.forEach {
            logger.info("$prefix   ${it.absolutePath}")
        }
    }
}

val findLocalDist by tasks.registering {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Find and add any local XDK installation from the PATH."
    val localDistDir = xdkBuildLogic.findLocalXdkInstallation() // done during config
    if (localDistDir == null) {
        logger.error("$prefix No local XTC installation found.")
    } else {
        outputs.dir(localDistDir)
    }
    doLast {
        logger.lifecycle("$prefix Detected existing local XTC installation at: '$localDistDir'")
        XdkBuildLogic.walkDir(project, localDistDir!!, LogLevel.INFO)
    }
}

val backupLocalDist by tasks.registering(Copy::class) {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Backs up a local installation to build dir before starting to overwrite."
    val localDist = xdkBuildLogic.resolveLocalXdkInstallation()
    val dest = xvmDist.getLocalDistBackupDir(localDist.name)
    from(localDist).into(dest)
    doLast {
        logger.lifecycle("$prefix Backed up local installation at $localDist to ${dest.get()}")
    }
}

val installLocalDist by tasks.registering {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Creates an XDK installation, and overwrites any local installation with it."

    dependsOn(installDist, backupLocalDist)

    // This task is always stale and not cached. Rerun it every time it is called. This makes it important that
    // it's not included as part of future normal build jobs. If this is the case, we need to change to a cached
    // configurable approach.
    alwaysRerunTask()

    doLast {
        val localDistDir = xdkBuildLogic.resolveLocalXdkInstallation() // throws exception if file not found.
        val localDistVersion = localDistDir.name
        logger.lifecycle("$prefix $name Overwriting local installation: $localDistVersion (path: '${localDistDir.absolutePath}')...")
        copy {
            from(project.layout.buildDirectory.dir("install/xdk"))
            into(localDistDir)
        }
        logger.lifecycle("$prefix $name Finished.")
        XdkBuildLogic.walkDir(project, localDistDir)
    }
}
