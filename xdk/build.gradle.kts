import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.logging.LogLevel.INFO
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.FileAttribute

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.publish)
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
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE))
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

dependencies {
    xdkJavaTools(libs.javatools)
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

private val semanticVersion: SemanticVersion by extra

private val xdkDist = xdkBuildLogic.distro()

/**
 * Propagate the "version" part of the semanticVersion to all XTC compilers in all subprojects (the XDK modules
 * will get stamped with the Gradle project version, as defined in VERSION in the repo root).
 */

subprojects {
    tasks.withType<XtcCompileTask>().configureEach {
        /*
         * Add version stamp to XDK module from the XDK build global semantic version single source of truth.
         */
        assert(version == semanticVersion.artifactVersion)
        xtcVersion = semanticVersion.artifactVersion
    }
}

publishing {
    // TODO: Use the Nexus publication plugin and
    //    ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
    //    (incorporate that in aggregate publish task in xvm/build.gradle.kts)
    publications {
        val xdkArchive by registering(MavenPublication::class) {
            with(project) {
                groupId = group.toString()
                artifactId = project.name
                version = version.toString()
            }
            pom {
                name = "xdk"
                description = "XTC Language Software Development Kit (XDK) Distribution Archive"
                url = "https://xtclang.org"
            }
            logger.info("$prefix Publication '$name' configured for '$groupId:$artifactId:$version'")
            artifact(tasks.distZip) {
                extension = "zip"
            }
        }
    }
}

private fun shouldPublishPluginToLocalDist(): Boolean {
    return project.getXdkPropertyBoolean("org.xtclang.publish.localDist", false)
}

val publishPluginToLocalDist by tasks.registering {
    group = BUILD_GROUP
    // TODO: includeBuild dependency; Slightly hacky - use a configuration from the plugin project instead.
    if (shouldPublishPluginToLocalDist()) {
        dependsOn(gradle.includedBuild("plugin").task(":publishAllPublicationsToBuildRepository"))
        outputs.dir(buildRepoDirectory)
        doLast {
            logger.info("$prefix Published plugin to build repository: ${buildRepoDirectory.get()}")
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
        distributionBaseName = xdkDist.distributionName
        assert(distributionBaseName.get() == "xdk") // TODO: Should really rename the distribution to "xdk" explicitly per convention.
        contents {
            // TODO: Why do we need the indirect - likely change these to lazy properties through map format.
            // TODO WE should really not do get() here.
            val resources = tasks.processResources.get().outputs.files.asFileTree
            logger.info("$prefix Distribution contents need to use lazy resources.")
            /*
             * 1) copy build plugin repository publication of the XTC plugin to install/xdk/repo
             * 2) copy xdk resources/main/xdk to install/xdk/libexec
             * 3) copy javatools_launcher/bin/\* to install/xdk/libexec/bin/
             * 4) copy XDK modules to install/xdk/libexec/lib
             * 5) copy javatools.jar, turtle and bridge to install/xdk/libexec/javatools
             */
            from(resources) {
                eachFile {
                    path = path.replace("xdk", "libexec")
                    path = path.replace("libexec-", "xdk-") // TODO: Hacky.
                    includeEmptyDirs = false
                }
            }
            from(xtcLauncherBinaries) {
                into("libexec/bin")
            }
            from(configurations.xtcModule) {
                // This copies everything not a javatools jar into libexec/javatools, which is where XTC wants the
                // javatools_turtle.xtc and javatools_bridge.xtc modules.
                // TODO consider breaking out javatools_bridge.xtc, javatools_turtle.xtc into a separate configuration.
                into("libexec/lib")
                exclude("**/javatools*")
            }
            from(configurations.xtcModule) {
                into("libexec/javatools")
                include("**/javatools*")
            }
            from(configurations.xdkJavaTools) {
                rename {
                    assert(it.endsWith(".jar"))
                    it.replace(Regex("-.*.jar"), ".jar")
                }
                into("libexec/javatools") // should just be one file with corrected dependencies, assert?
            }
            if (shouldPublishPluginToLocalDist()) {
                val published = publishPluginToLocalDist.get().outputs.files
                from(published) {
                    into("repo")
                }
            }
            from(tasks.xtcVersionFile)
        }
    }
}

val cleanXdk by tasks.registering(Delete::class) {
    subprojects.forEach {
        delete(it.layout.buildDirectory)
    }
    delete(compositeRootBuildDirectory)
}

val clean by tasks.existing {
    dependsOn(cleanXdk)
    doLast {
        logger.info("$prefix WARNING: Note that running 'clean' is often unnecessary with a properly configured build cache.")
    }
}

val distTar by tasks.existing(Tar::class) {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

val distZip by tasks.existing(Zip::class)

val assembleDist by tasks.existing {
    if (xdkDist.shouldCreateWindowsDistribution()) {
        logger.warn("$prefix Task '$name' is configured to build a Windows installer. Environment needs '${XdkDistribution.MAKENSIS}' and the EnVar plugin.")
        dependsOn(distExe)
    }
}

val distExe by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Use an NSIS compatible plugin to create the Windows .exe installer."

    onlyIf {
        xdkDist.shouldCreateWindowsDistribution()
    }

    // TODO: Why do we need this dependency? Likely just remove it.
    dependsOn(installDist)

    val nsi = file("src/main/nsi/xdkinstall.nsi")
    val makensis = XdkBuildLogic.findExecutableOnPath(XdkDistribution.MAKENSIS)
    onlyIf {
        makensis != null
    }

    val inputDir = layout.buildDirectory.dir("install/xdk")
    val outputFile = layout.buildDirectory.file("distributions/xdk-$version.exe")
    inputs.dir(inputDir)
    outputs.file(outputFile)

    // notes:
    // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
    // - requires the "makensis" command to be in the path
    // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS
    // - requires the x.ico file to be in the same directory as the nsi file
    doLast {
        if (makensis == null) {
            throw buildException("Cannot find '${XdkDistribution.MAKENSIS}' in PATH.")
        }
        if (!nsi.exists()) {
            throw buildException("Cannot find 'nsi' file: ${nsi.absolutePath}")
        }
        logger.info("$prefix Writing Windows installer: ${outputFile.get()}")
        val stdout = ByteArrayOutputStream()
        exec {
            environment(
                "NSIS_SRC" to inputDir.get(),
                "NSIS_ICO" to xdkIconFile,
                "NSIS_OUT" to outputFile.get(),
                "NSIS_VER" to xdkDist.distributionVersion
            )
            commandLine(makensis.toFile().absolutePath, nsi.absolutePath, "-NOCD")
            standardOutput = stdout
        }
        makensis.toFile().setExecutable(true)
        logger.info("$prefix Finished building distribution: '$name'")
        stdout.toString().lines().forEach { logger.info("$prefix     $it") }
    }
}

val test by tasks.existing {
    System.err.println("gradle.includedBUilds: " + gradle.includedBuilds.map { it.name } )
    if (gradle.includedBuilds.map { it.name }.contains("manualTests")) {
        System.err.println("Here I am!")
//        dependsOn(gradle.includedBuild("manualTests").task(":runXtc"))
    }
}

/**
 * Take the output of assembleDist and put it in an installation directory.
 */
val installDist by tasks.existing {
    doLast {
        logger.info("$prefix '$name' Installed distribution to '${project.layout.buildDirectory.get()}/install/' directory.")
        logger.info("$prefix Installation files:")
        printTaskOutputs(INFO)
    }
}

/**
 * Overwrite or install a local distribution of the XDK under rootProjectDir/build/dist, which
 * you could add to your path, for example
 *
 * TODO: @aalmiray recommends application plugin run script generation, and that makes sense to me.
 *   It should be possible to hook up JReleaser and/or something like launch4j to cross compile
 *   binary launchers for different platforms, if that is what we want instead of the current
 *   solution.
 */
val installLocalDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Creates an XDK installation in root/build/dist, for the current platform."

    val localDistDir = compositeRootBuildDirectory.dir("dist")

    dependsOn(installDist)
    inputs.files(installDist)
    outputs.dir(localDistDir)

    doLast {
        // Sync, not copy, so we can do this declaratively, Gradle input/output style, without horrible file system logic.
        sync {
            from(project.layout.buildDirectory.dir("install/xdk"))
            into(localDistDir)
        }

        // Create symlinks for launcher.
        val binDir = mkdir(localDistDir.get().dir("bin"))
        val launcherExe = xdkDist.resolveLauncherFile(localDistDir)

        // TODO: The launchers should just be application plugin scripts, this is kind of ridiculous.
        listOf("xcc", "xec", "xtc").forEach {
            val symLink = File(binDir, it)
            logger.info("$prefix Creating symlink for launcher '$it' -> '${launcherExe.asFile}' (on Windows, this may require developer mode settings).")
            Files.createSymbolicLink(symLink.toPath(), launcherExe.asFile.toPath())
        }
    }
}
