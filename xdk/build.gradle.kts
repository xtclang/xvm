import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_INSTALLATION_NAME
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.xtclang.plugin.tasks.XtcCompileTask

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.publish)
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
    alias(libs.plugins.versions)
    distribution // TODO: If we turn this into an application plugin instead, we can automatically get third party dependency jars with e.g. javatools resolved.
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

distributions {
    main {
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion)
    }
    val withLaunchers by registering {
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion, xdkDist.osClassifier(), installLaunchers = true)
    }
}

tasks.filter { XdkDistribution.isDistributionArchiveTask(it) }.forEach {
    // Add transitive dependency to the process resource tasks. There might be something brokemn with those dependencies,
    // but it's more likely that since the processXtcResources task needs to be run before compileXtc, and the Java one does
    // not, this somehow confuses the life cycle. TODO: This is another argument to remove and duplicate what is needed of the
    // Java plugin functionality for the XTC Plugin, but we haven't had time to neither do that, nor work on build speedups
    // through configuration caching and other dependencies.
    it.dependsOn(tasks.compileXtc)
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

tasks.withType<Tar>().configureEach {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

val distZip by tasks.existing(Zip::class) {
    dependsOn(tasks.compileXtc) // And by transitive dependency, processResources
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
private fun Distribution.contentSpec(distName: String, distVersion: String, distClassifier: String = "", installLaunchers: Boolean = false) {
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
            exclude(JAVATOOLS_PREFIX_PATTERN) // *.xtc, but not javatools_*.xtc
        }
        from(configurations.xtcModule) {
            into("javatools")
            include(JAVATOOLS_PREFIX_PATTERN) // only javatools_*.xtc
        }
        from(configurations.xdkJavaTools) {
            rename("javatools-${project.version}.jar", JAVATOOLS_INSTALLATION_NAME)
            into("javatools")
        }
        from(tasks.xtcVersionFile)
        if (installLaunchers) {
            // Do we want to install launchers that work on the host system?
            assert(distClassifier.isNotEmpty()) { "No distribution given for host specific distribution, OS: ${XdkDistribution.currentOs}" }
            XdkDistribution.binaryLauncherNames.forEach {
                val launcher = xdkDist.launcherFileName()
                from(xtcLauncherBinaries) {
                    include(launcher)
                    rename(launcher, it)
                    into("bin")
                }
            }
        }
    }
}

val installDist by tasks.existing {
    dependsOn(tasks.compileXtc)
}

/*
 *
 *
 * val jreleaserAssemble by tasks.existing {
 *     dependsOn(tasks.distZip, tasks.installDist)
 *     doFirst {
 *         logger.lifecycle("$prefix Executing jreleaser assemble doFirst")
 *     }
 * }
 *
 * jreleaser {
 * //    configFile = file("jreleaser.yml")
 *     gitRootSearch = true
 *
 *     environment {
 *         // TODO: Artifacts dir.
 *     }
 *
 *     project {
 *         authors = listOf("xtclang.org")
 *         license = "Apache-2.0"
 *         snapshot {
 *             enabled = true
 *             fullChangelog = false
 *         }
 *         links {
 *             homepage = "https://acme.com/app"
 *         }
 *         inceptionYear = "2024"
 *         stereotype = Stereotype.CLI
 *     }
 *
 *     release {
 *         github {
 *             // RepoOwner should auto configure to the github root found.
 *             overwrite = true
 *         }
 *     }
 *
 *     assemble {
 *         val xdk by archive.registering {
 *             // TODO: ALWAYS, NEVER, RELEASE, PRERELEASE, RELEASE_PRERELEASE, SNAPSHOT
 *             active = Active.ALWAYS
 *             formats = setOf(Format.ZIP)
 *             attachPlatform = true
 *             fileSet {
 *                 input = installDirFile.absolutePath
 *             }	            }
 *         }	        }
 *         from(tasks.xtcVersionFile)
 *         checksum {
 *             name = "checksums.txt"
 *             individual = false
 *             algorithms = setOf(Algorithm.SHA_256)
 *             artifacts = true
 *             files = true
 *         }
 *     }
 *
 *     // TODO: Command and script hooks, maybe only on the CI side.
 *     // TODO: Snapshot release
 *     // TODO: Publish snapshot distributions, Gradle user guide. Publish every push.
 *     val xdk by distributions.registering {
 *         artifact {
 *             // TODO it would be great being to take providers here, now we are forced to code the
 *             // path as a string, or force execute the distZip to determine outputs.
 *             path = layout.buildDirectory.dir("distributions/xdk-$releaseVersion.zip").get().asFile
 *         }
 *     }	    }
 * }	}
 *
 * /*
 * assemble {
 *     this.
 *     this.archive
 *     directory = installDir.get().asFile
 *     archiveFormats = setOf(Format.TAR, Format.ZIP)
 *     checksum {
 *         name = "checksums.txt"
 *         individual = false
 *         algorithms = setOf("SHA_256")
 *         artifacts = true
 *         files = true
 *     }
 * }
 * // A binary distribution is basically just a directory tree with a bin/executable in it
 * // This is exactly what we want for the output of installDistForBuildHost
 * // The file structure needs to contain:
 * //    LICENSE and README in the root
 * //    bin directory in the root, and an executable (hopefully two) in it.
 * // The final distribution artifact is packaged as tar or zip. The archive must contain
 * // a root entry followed by the name of the archive. Thus, if the archive is named
 * // app-1.2.3.zip, the root entry should be app-1.2.3/.
 * //
 * // We can also use an archive assembler, to create an archive, e.g. Docker, Sdkman,
 * // Homebrew etc. We probably want to do that as part of the release.
 * /*val xdk by distributions.registering {
 *     distributionType = DistributionType.BINARY
 *     // We can also configure
 * }*/
 * }
 */