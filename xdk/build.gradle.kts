import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.logging.LogLevel.INFO
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP

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

val xtcUnicodeConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    // TODO: Can likely remove these.
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("unicodeDir"))
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

internal val xdkDist = xdkBuildLogic.xdkDistribution()
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

/**
 * Run once, to create templates in GRADLE_USER_HOME/init.d/ for an XTC Org user with
 * just read:package access (in a safe token). This is a workaround for GitHub requiring
 * authentication for package access, even for public packages in public repos. People
 * have asked about this feature for almost five years now.
 *
 * However, as soon as we have changed artifact groups for our publications to "org.xtclang"
 * instead of "org.xvm", we can prove domain ownership of the former with gradlePluginPortal()
 * and mavenCentral(), and provide packages that are *really* public. In the meantime, in order
 * to get everyone up and running as quickly as possible, this task is the bootstrap mechanism
 * to work with the GitHub Package Repo, but not having to add various tokens and credentials.
 *
 * Security review completed satisfactorily.
 */
val installInitScripts by tasks.registering(Copy::class) {
    group = PUBLISH_TASK_GROUP
    description =
        "Writes the init script to GRADLE_USER_HOME/init.d, providing GitHub credentials for the package repo."
    from(compositeRootProjectDirectory.dir("gradle/config/repos")) {
        eachFile {
            // TODO: decide if "must be online" trumps "install once", as to which script template
            //   should be default. We copy all the files to GRADLE_USER_HOME/init.d, though, but we do
            //   not rename the non-default version.
            if (!name.contains("minimal")) {
                name = name.removeSuffix(".template")
            }
        }
    }
    into(userInitScriptDirectory)
    doLast {
        printAllTaskInputs()
        printAllTaskOutputs()
    }
}

val pluginPublication by tasks.registering {
    group = BUILD_GROUP
    // TODO: includeBuild dependency; Slightly hacky - use a configuration from the plugin project instead.
    dependsOn(gradle.includedBuild("plugin").task(":publishAllPublicationsToBuildRepository"))
    outputs.dir(buildRepoDirectory)
    doLast {
        // TODO: Right now this is always executed, as we declare an outgoing artifact for it in plugin/build.gradle.kts
        //   May not be necessary. The reason it's there is because the installLocalDist wants it, but it should really
        //   only try to resolve that at runtime. However, the dependency will still be resolved, and can probably be
        //   more lazy.
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
        distributionBaseName =
            xdkDist.distributionName // TODO: Should really rename the distribution to "xdk" explicitly.
        assert(distributionBaseName.get() == "xdk")
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
                // TODO consider breaking out javatools_bridge.xtc, javatools_turtle.xtc into a separate configuration.
                exclude("**/javatools*")
            }
            from(configurations.xtcModule) {
                into("libexec/javatools")
                // TODO consider breaking out javatools_bridge.xtc, javatools_turtle.xtc into a separate configuration.
                include("**/javatools*")
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
            val subProjectBuildDir = it.layout.buildDirectory.get().asFile
            delete(it.layout.buildDirectory)
            logger.lifecycle("$prefix $name Cleaned subproject ${it.name} build directory (buildDir: '$subProjectBuildDir')")
        }
        delete(compositeRootBuildDirectory)
        logger.lifecycle("$prefix $name Cleaned composite build common build directory: ${compositeRootBuildDirectory.get()}")
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
    doLast {
        logger.lifecycle("$prefix $name: Assembled distribution.")
        printTaskInputs(INFO)
        printTaskOutputs(INFO)
    }
}

val processResources by tasks.existing(Copy::class)

val distExe by tasks.registering {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Use an NSIS compatible plugin to create the Windows .exe installer."

    dependsOn(installDist)

    val nsi = file("src/main/nsi/xdkinstall.nsi")
    val makensis = xdkBuildLogic.findExecutableOnPath("makensis")
    onlyIf {
        makensis != null
    }

    val inputDir = project.layout.buildDirectory.dir("install/xdk")
    inputs.dir(inputDir)
    val outputFile = project.layout.buildDirectory.file("distributions/xdk-$version.exe")
    outputs.file(outputFile)

    // notes:
    // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
    // - requires the "makensis" command to be in the path
    // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS
    // - requires the x.ico file to be in the same directory as the nsi file
    doLast {
        if (makensis == null) {
            throw buildException("Cannot find 'makensis' in PATH.")
        }
        if (!nsi.exists()) {
            throw buildException("Cannot find 'nsi' file: ${nsi.absolutePath}")
        }
        exec {
            environment(
                "NSIS_SRC" to inputDir.get(),
                "NSIS_ICO" to xtcIconFile,
                "NSIS_OUT" to outputFile,
                "NSIS_VER" to xdkDist.distributionVersion
            )
            commandLine(makensis.toFile().absolutePath, nsi.absolutePath, "-NOCD")
        }
    }
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
        logger.lifecycle("$prefix '$name' Installed distribution to {project.layout.buildDirectory}/install/ directory.")
        logger.info("$prefix Installation files:")
        printTaskOutputs()
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
        XdkBuildLogic.listDirWithTimestamps(localDistDir!!).lines().forEach {
            logger.lifecycle("$prefix   $it")
        }
    }
}

val backupLocalDist by tasks.registering(Copy::class) {
    group = XdkDistribution.DISTRIBUTION_GROUP
    description = "Backs up a local installation to build dir before starting to overwrite."
    val localDist = xdkBuildLogic.resolveLocalXdkInstallation()
    val dest = xdkDist.getLocalDistBackupDir(localDist.name)
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
        XdkBuildLogic.listDirWithTimestamps(localDistDir).lines().forEach {
            logger.lifecycle("$prefix   $it")
        }
    }
}


/**
 * This task can update the Unicode data files, if a Unicode release has occurred and provided
 * a new `ucd.all.flat.zip`; that is the only time that the Unicode data files have to be updated.
 *
 * This task is used to force rebuild and input unicode files into our build. This is not part of the
 * common build, but there is no reason to not make it that, now that we have caching parallel
 * Gradle/Maven build infrastructure.
 */

// TODO: We do NOT want the unicode consumer as a project dependency. We must resolve it in-task somehow.

val importUnicodeFiles by tasks.registering {
    group = BUILD_GROUP
    description = "Copy the various Unicode data files from :javatools_unicode to :lib_ecstasy project."

    logger.warn("$prefix '$name' is currently broken. There is a reported issue, and we will get to this very soon.")

    alwaysRerunTask()
    dependsOn(gradle.includedBuild("javatools_unicode").task(":run"))
    dependsOn(xtcUnicodeConsumer)
    inputs.files(xtcUnicodeConsumer)

    val libEcstasy = project(XdkVersionHandler.semanticVersionFor(libs.xdk.ecstasy).artifactId)
    val outputDir = File(libEcstasy.projectDir, "src/main/resources/ecstasy/text2")
    outputs.dir(outputDir)

    doLast {
        logger.lifecycle("$prefix Trying to write unicode tables to ${outputDir.absolutePath}...")
        logger.lifecycle("$prefix Provider files: ${xtcUnicodeConsumer.get().resolve()}")
        fileTree(xtcUnicodeConsumer).forEach {
            println(" *** TODO: WORK : $it")
        }

        copy {
            from(fileTree(xtcUnicodeConsumer)) {
                into(outputDir)
            }
            includeEmptyDirs = false
            eachFile {
                relativePath = RelativePath(true, name)
                logger.lifecycle("$prefix Copying unicode file: ${file.absolutePath} to ${outputDir.absolutePath}.")
            }
        }
        logger.lifecycle("$prefix Unicode files copied to ${outputDir.absolutePath}. Please verify your git diff, test and commit.")
        fileTree(outputDir).forEach {
            logger.lifecycle("$prefix     Destination: $it")
        }
    }
}
