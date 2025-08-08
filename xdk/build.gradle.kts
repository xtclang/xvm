import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_INSTALLATION_NAME
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.File

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.publish)
    alias(libs.plugins.xtc)
    alias(libs.plugins.versions)
    alias(libs.plugins.sonatype.publish)
    distribution // TODO: If we turn this into an application plugin instead, we can automatically get third party dependency jars with e.g. javatools resolved.
    signing
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
    xtcModule(libs.xdk.convert)
    xtcModule(libs.xdk.crypto)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.jsondb)
    xtcModule(libs.xdk.net)
    xtcModule(libs.xdk.oodb)
    xtcModule(libs.xdk.sec)
    xtcModule(libs.xdk.web)
    xtcModule(libs.xdk.webauth)
    xtcModule(libs.xdk.webcli)
    xtcModule(libs.xdk.xenia)
    xtcModule(libs.xdk.xml)
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
    // TODO: Set up a hook to the maven central/ossrh/sonatype repository here, once the keys have been recovered.
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

signing {
    mavenCentralSigning()
}

distributions {
    main {
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion)
    }
    val withLaunchers by registering {
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion, xdkDist.osClassifier(), installLaunchers = true)
    }
}

// Let the Distribution plugin handle dependencies properly through the standard lifecycle
// Distribution tasks should automatically depend on processResources and other build outputs

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

/**
 * Set up signing tasks, only enabled if explicitly configured, or if not, only when we
 * are publishing a non-snapshot package.
 */
tasks.withType<Sign>().configureEach {
    val sign = getXdkPropertyBoolean("org.xtclang.signing.enabled", isRelease())
    // TODO: Before mavenCentral access tokens are sorted, signing should never be enabled:
    require(!sign) { "Signing is not enabled, and should not be enabled until we are sure default configs work." }
    logger.info("$prefix Publication for project '${project.name}' will ${if (sign) "" else "NOT "}be signed.")
    onlyIf {
        sign
    }
}

// Restore the proper distribution task dependencies using the existing utility
tasks.filter { XdkDistribution.isDistributionArchiveTask(it) }.forEach {
    it.dependsOn(tasks.named("processXtcResources"))
}

tasks.withType<Tar>().configureEach {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

val test by tasks.existing {
    doLast {
        TODO("Implement response to the check lifecycle, probably some kind of aggregate XUnit.")
    }
}


/**
 * Ensure that tags are correct. First we fetch remote tags, clobbering any locals ones,
 * the remote is always the single source of truth.
 *
 * For a snapshot release, we delete any existing tag for this version, and place a new
 * tag with hte same contents at the latest commit.
 *
 * For a normal release, we fail if there already is a tag for this version.
 */
val ensureTags by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Ensure that the current commit is tagged with the current version."
    
    // Capture values during configuration phase to avoid runtime project access
    val capturedSnapshotOnly = snapshotOnly()
    val capturedSemanticVersion = semanticVersion
    val capturedGitHubProtocol = xdkBuildLogic.gitHubProtocol()
    val capturedPrefix = prefix  // Capture prefix to avoid project access during execution
    
    if (!allowPublication()) {
        logger.lifecycle("$capturedPrefix Skipping publication task, snapshotOnly=${capturedSnapshotOnly} for version: '$capturedSemanticVersion")
    }
    onlyIf {
        allowPublication()
    }
    doLast {
        logger.lifecycle("""
            $capturedPrefix Ensuring that the current commit is tagged with version.
            $capturedPrefix     version: $capturedSemanticVersion
            $capturedPrefix     snapshotOnly: $capturedSnapshotOnly
        """.trimIndent())
        val tag = capturedGitHubProtocol.ensureTags(capturedSnapshotOnly)
        if (GitHubProtocol.tagCreated(tag)) {
            logger.lifecycle("$capturedPrefix Created or updated tag '$tag' for version: '$capturedSemanticVersion'")
        }
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
    // Override the internal directory name to be generic (without classifier)
    contents.eachFile {
        // This will be processed during archive creation, making the internal structure generic
        if (relativePath.segments.first().contains("-$distClassifier")) {
            val newFirstSegment = relativePath.segments.first().replace("-$distClassifier", "")
            relativePath = RelativePath(true, *arrayOf(newFirstSegment) + relativePath.segments.drop(1))
        }
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

// Let the Distribution plugin handle installDist dependencies according to Gradle standards
