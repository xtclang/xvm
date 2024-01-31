import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import XdkDistribution.Companion.JAVATOOLS_JARFILE_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.ByteArrayOutputStream
import java.nio.file.Files

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

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.cli)
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

private fun distContents(): CopySpec {
    return copySpec {
        val resources = tasks.processResources.get().outputs.files.asFileTree
        logger.info("$prefix Distribution contents need to use lazy resources.")
        from(resources) {
            eachFile {
                includeEmptyDirs = false
            }
        }
        from(xtcLauncherBinaries) {
            into("bin")
        }
        from(configurations.xtcModule) {
            into("lib")
            exclude("**/javatools*")
        }
        from(configurations.xtcModule) {
            into("javatools")
            include("**/javatools*")
        }
        from(configurations.xdkJavaTools) {
            rename {
                assert(it.endsWith(".jar"))
                it.replace(Regex("-.*.jar"), ".jar")
            }
            into("javatools") // should just be one file with corrected dependencies, assert?
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

distributions {
    main {
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion)
    }
    // The contents logic is broken out so we can use it in multiple distributions, should we want
    // to build e.g. linux/windows/mac zip files. It's still not perfectly aligned with any release
    // pipeline, or tools like JReleaser, but its a potential way to create more than one distribution and
    // use them as the basis for any framework that works with Gradle installs and distros.
    //create("xdk-macos") {
    //    contentSpec("xdk", xdkDist.distributionVersion, "macosx")
    //}
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
    dependsOn(tasks.compileXtc) // And by transitive dependency, processResources
}

val distZip by tasks.existing(Zip::class) {
    dependsOn(tasks.compileXtc) // And by transitive dependency, processResources
}

val distExe by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Use an NSIS compatible plugin to create the Windows .exe installer."

    dependsOn(distZip)

    val nsi = file("src/main/nsi/xdkinstall.nsi")
    val makensis = XdkBuildLogic.findExecutableOnPath(XdkDistribution.MAKENSIS)
    onlyIf {
        makensis != null && xdkDist.shouldCreateWindowsDistribution()
    }

    val outputFile = layout.buildDirectory.file("distributions/xdk-$version.exe")
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
        val distributionDir = layout.buildDirectory.dir("tmp-archives")
        copy {
            from(zipTree(distZip.get().archiveFile))
            into(distributionDir)
        }
        exec {
            environment(
                "NSIS_SRC" to distributionDir.get(),
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


val assembleDist by tasks.existing {
    // Implicitly depends on distTar and distZip. Should also depend on distExe where relevant:
    if (xdkDist.shouldCreateWindowsDistribution()) {
        logger.warn("$prefix Task '$name' is configured to build a Windows installer. Environment needs '${XdkDistribution.MAKENSIS}' and the EnVar plugin.")
        dependsOn(distExe)
    }
}

/**
 * Take the output of assembleDist and put it in an installation directory.
 */
val installDist by tasks.existing {
    inputs.files(tasks.processXtcResources, tasks.processResources)
    dependsOn(tasks.assembleDist)
    doLast {
        logger.info("$prefix '$name' Installed distribution to '${project.layout.buildDirectory.get()}/install/' directory.")
        logger.info("$prefix Installation files:")
        printTaskOutputs(LogLevel.INFO)
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
            Files.createLink(symLink.toPath(), launcherExe.asFile.toPath())
        }
    }
}

val test by tasks.existing {
    doLast {
        TODO("Implement response to the check lifecycle, probably some kind of aggregate XUnit.")
    }
}

/**
 * Creates distribution contents based on a distribution name, version and classifier.
 * This logic is used for the nain distribution artifact (named "xdk"), and the contents
 * has been broken out into this function so that we can easily generate ore installations
 * and distributions with slightly different contents, for example, based o OS, and with
 * an OS specific launcher already in "bin".
 */
private fun Distribution.contentSpec(distName: String, distVersion: String, distClassifier: String = "") {
    distributionBaseName = distName
    version = distVersion
    if (distClassifier.isNotEmpty()) {
        @Suppress("UnstableApiUsage")
        distributionClassifier = distClassifier
    }

    contents {
        val xdkTemplate = tasks.processResources.map {
            logger.info("$prefix Resolving processResources output (this should be during the execution phase).");
            File(it.outputs.files.singleFile, "xdk")
        }
        from(xdkTemplate) {
            eachFile {
                includeEmptyDirs = false
            }
        }
        from(xtcLauncherBinaries) {
            into("bin")
        }
        from(configurations.xtcModule) {
            into("lib")
            exclude(JAVATOOLS_JARFILE_PATTERN)
        }
        from(configurations.xtcModule) {
            into("javatools")
            include(JAVATOOLS_JARFILE_PATTERN)
        }
        from(configurations.xdkJavaTools) {
            rename {
                assert(it.endsWith(".jar"))
                it.replace(Regex("-.*.jar"), ".jar")
            }
            into("javatools") // should just be one file with corrected dependencies, assert?
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
