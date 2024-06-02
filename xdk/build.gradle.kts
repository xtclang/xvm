import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_INSTALLATION_NAME
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.jreleaser.model.Active
import org.jreleaser.model.Distribution.DistributionType
import org.jreleaser.model.Http.Authorization
import org.jreleaser.model.api.common.Activatable
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.File

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.publish)
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
    alias(libs.plugins.versions)
    //alias(libs.plugins.sonatype.publish)
    alias(libs.plugins.jreleaser)
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
        contentSpec(xdkDist.distributionName, xdkDist.distributionVersion, XdkDistribution.osClassifier(), installLaunchers = true)
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
    if (!allowPublication()) {
        logger.lifecycle("$prefix Skipping publication task, snapshotOnly=${snapshotOnly()} for version: '$semanticVersion")
    }
    onlyIf {
        allowPublication()
    }
    doLast {
        val snapshotOnly = snapshotOnly()
        logger.lifecycle("""
            $prefix Ensuring that the current commit is tagged with version.
            $prefix     version: $semanticVersion
            $prefix     snapshotOnly: $snapshotOnly
        """.trimIndent())
        val tag = xdkBuildLogic.gitHubProtocol().ensureTags(snapshotOnly)
        if (GitHubProtocol.tagCreated(tag)) {
            logger.lifecycle("$prefix Created or updated tag '$tag' for version: '$semanticVersion'")
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

val withLaunchersDistZip by tasks.existing(Zip::class) {
    doLast {
        logger.lifecycle("$prefix Creating distribution with launchers: ${outputs.files.singleFile.absolutePath}")
        archiveFile
    }
}

//val platformZip: Provider<RegularFile> = withLaunchersDistZip.map { it.archiveFile }

val installWithLaunchersDist by tasks.existing(Sync::class) {
    doLast {
        logger.lifecycle("$prefix Installing distribution with launchers: ${destinationDir.absolutePath}")
    }
}

// Keep this as git assets or git releases.
// Github artifact - release artifact as asset, add it a default setting. In the release step - every distribution and file will be pushed as a git release asset
//
// How do I consume this from a build? That is weird.
// repositories {
//   theAssetMagically()
// }
val platformPlatformIndependentZip: Provider<RegularFile> = distZip.flatMap { it.archiveFile }

val platformZip: Provider<RegularFile> = withLaunchersDistZip.flatMap { it.archiveFile }

// Build everything in github workflows instead



/**
 * Functionality wanted:
 *
 *    1) Publish snapshot package artifacts to GitHub Maven Packages repo
 *
 * Use cases:
 *    1) Supply releases and any other possible GitHub asset artifacts to the plugin, so we
 *       can POC an xtc(...) configuration that does not require a GitHub token in the system
 *       or config anywhere, just for read only.
 *
 *
 */

jreleaser {
    dryrun = true
    gitRootSearch = true

    /**
     * Project configuration.
     *    Sets up minimally required license information. Apache-2.0 is preferred for the remote validation sides, but may need to change.
     */
    project {
        description = "XDK Platform"
        copyright = "(C) xtclang.org 2024"
        license = "Apache-2.0"
        authors = listOf("xtclang.org")
        links {
            homepage = "https://xtclang.org"
        }
        snapshot {
            // XTC uses semver versioning. The Git tags that correspond to a version are the default for
            // releases (i.e. "vx.y.z"), but snapshot tags are prefixed with snapshot/
            label = "snapshot/v{{projectVersionMajor}}.{{projectVersionMinor}}.{{projectVersionPatch}}"
            fullChangelog = false
        }
    }

    // The deploy section is used to publish package artifacts to the GitHub Maven Packages.
    //   We have the "xdk" maven artifact, the "xtc-plugin" Gradle artifact, and its Maven version (Gradle adds some extra meta info in pseudo artifact)
    deploy {
        maven {
            github {
                val xdk by registering {
                    // TODO: There has to be a way to publish snapshots.
                    active = Active.ALWAYS
                    //prerelease = true
                    //overwrite = false
                    url = "https://maven.pkg.github.com/xtclang/xvm"
                    username = "xtclang-bot"
                    password = System.getenv("GITHUB_TOKEN")
                    authorization = Authorization.BEARER

                    localStagingRepoDirectory
                    // TODO there needs to be a staging repository that takes a provider or we have to build lots of stuff during config
                    //stagingRepository("localStagingRepoDirectory.get().asFile.absolutePath)
                    // The defaults for the below config is already false/disabled, unless applyMavenCentralRules are in place.
                    //stagingRepositories =  = listOf("localStagingRepoDirectory.get().asFile.absolutePath")
                    sign = false
                    sourceJar = false
                    javadocJar = false
                    verifyPom = false
                    applyMavenCentralRules = false
                }
            }
            //pomchecker {
            //    enabled = false
            //    version = libs.versions.kordamp
            //}
        }
    }
}

/*

            // TODO: Enable
            // https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html
            //mavenCentral {
            //    enabled = false
            //}

            // https://jreleaser.org/guide/latest/reference/deploy/maven/github.html
/*            github {
                val xdk by registering {
                    // TODO: There has to be a way to publish snapshots.
                    active = Active.ALWAYS
                    //prerelease = true
                    //overwrite = false
                    url = "https://maven.pkg.github.com/xtclang/xvm"
                    username = "xtclang-bot"
                    password = System.getenv("GITHUB_TOKEN")
                    authorization = Authorization.BEARER

                    // TODO there needs to be a staging repository that takes a provider or we have to build lots of stuff during config
                    //stagingRepository("localStagingRepoDirectory.get().asFile.absolutePath)
                    // The defaults for the below config is already false/disabled, unless applyMavenCentralRules are in place.
                    sign = false
                    sourceJar = false
                    javadocJar = false
                    verifyPom = false
                    applyMavenCentralRules = false
                }
            }
        }
    }*/
 */
/*
    // This is where github publications on commits to master go (as maven artifact) https://maven.pkg.github.com/xtclang/xvm
    // Any such thing like mavenCentral too needs deployments
    deploy {
        // "deployment is not enabled" The github deployer does not allow SNAPSHOTs to be deployed?
        maven {
            github {
                // Run the build to stage the artifacts.
                // Then run the release to publish the artifacts.
                // Depending on the maven deployer, use kordamp pomchecker, otherwise take them as they are
                // for the github maven package repository.
                val xdk by creating {
                    enabled = true // the xdk deployer regardless of snapshot or not should always be enabled.
                    // TODO: Andres - want a special state for snapshots explicitly?
                    url = "https://maven.pkg.github.com/xtclang/xvm"
                    username = "xtclang-bot"
                    password = "token"//xdkBuildLogic.getXtclangGitHubMavenPackageRepositoryToken()
                    // TODO: This must be lazy
                    stagingRepository(localStagingRepoDirectory.get().asFile.absolutePath)
                }
            }
        }
    }

    // E.g. v0.4.5 of the XDK, placed on GitHub as a Release, possibly.
    release {
        github {
            skipTag = false // This assumes I do all tagging manually and that the current commit during a jreleaser relase execution
            // is the one its built from, but nothing is tagged in github by jreleaser .
            // [INFO]  HEAD is at c1d5c3e
            // overwrite is false means that the tagging is moved

            // overwrite is set to true automatically if it's natural release, i.e. not a final release "full stuff"
            // overwrite = true //
            //tagName.set(xdkBuildLogic.gitHubProtocol().localTag)  // "For a snapshot snapshot/v0.1.2,for a release the standard v0.1.2"
        }
    }

    // The platform independent zip (currently a github package publication on xtclang/xdk), will
    // be a files block, not a distribution. We do use it, e.g. for XTC dsl like xdk(libs.xdk).version("1.0.0")
    // but we don't want to release it as a platform independent github distribution.

    // Both files and artifacts can be published as github release assets.
    // The difference is that the only files and artifacts in the distribution section only have access to package management
    // In the future - e.g. brew support goes into the distribution section.

    // jreleaser assemble step creates binaries.
    // dryrun lets you read from remote services.

    // jreleaser: release command.
    //    package managers, anouncements etc is full-release
    //
    /*
    # These are binaries created using jpackages.
    jreleaser-installer:
    type: NATIVE_PACKAGE
    winget:
    active: RELEASE
    continueOnError: true
    package:
    name: jreleaser
    repository:
    active: ALWAYS
    name: jreleaser-winget
    commitMessage: 'jreleaser {{tagName}}'
    executable:
    name: jreleaser
    windowsExtension: exe
    artifacts:
    - path: '{{jpackageDir}}/JReleaser-{{projectVersionNumber}}-osx-x86_64.pkg'
    transform: '{{distributionName}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-x86_64.pkg'
    platform: 'osx-x86_64'
    - path: '{{jpackageDir}}/JReleaser-{{projectVersionNumber}}-osx-aarch64.pkg'
    transform: '{{distributionName}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-aarch64.pkg'
    platform: 'osx-aarch_64'
    - path: '{{jpackageDir}}/jreleaser_{{projectVersionNumber}}-1_amd64.deb'
    transform: '{{distributionName}}/{{distributionName}}_{{projectEffectiveVersion}}-1_amd64.deb'
    platform: 'linux-x86_64'
    - path: '{{jpackageDir}}/jreleaser-{{projectVersionNumber}}-1.x86_64.rpm'
    transform: '{{distributionName}}/{{distributionName}}-{{projectEffectiveVersion}}-1.x86_64.rpm'
    platform: 'linux-x86_64'
    - path: '{{jpackageDir}}/jreleaser-{{projectVersionNumber}}-windows-x86_64.msi'
    transform: '{{distributionName}}/{{distributionName}}-{{projectEffectiveVersion}}-windows-x86_64.msi'
    platform: 'windows-x86_64'

    jreleaser-native:
    # This are my three github assets - platform dependent zips for windows, linux and mac
    artifacts:
    - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-aarch64.zip'
    platform: 'osx-aarch_64'
    - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-x86_64.zip'
    platform: 'osx-x86_64'
    - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-linux-x86_64.zip'
    platform: 'linux-x86_64'
    - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-windows-x86_64.zip'
    platform: 'windows-x86_64'

    files:
    artifacts:
    - path: VERSION
    extraProperties:
    skipChecksum: true
    skipSigning: true
    skipSbom: true
    - path: plugins/jreleaser-ant-tasks/build/distributions/jreleaser-ant-tasks-{{projectVersion}}.zip
    transform: 'jreleaser-ant-tasks/jreleaser-ant-tasks-{{projectEffectiveVersion}}.zip'

    files {
        artifact {
            path = platformPlatformIndependentZip
        }
    }

    /*
    platform:
    replacements:
    aarch_64: aarch64
*/
    // The extra properties section under project (and other places) allows you to define template values
    // For example
    // also see https://jreleaser.org/guide/latest/reference/name-templates.html
    /*
    project.extraProperties {
        someMajorVersion: 0  -> I can use as a template {{projectSomeMajorVersion}}
    }
     */

    /*platform {
        // Verify that windows and linux builds actually have the correct from name pattern.
        // The to pattern is the mandated JReleaser platform description and MUST be exactly
        // those strings.
        replacements {
            "macos_aarch64" to "osx-x86_64"
            "macos_x86_64" to "osx-x86_64"
            "linux_x86_64" to "linux-x86_64"
            "win_amd64" to "windows-x86_64"
        }
    }*/

    // This filters out everything that is not my current platform.
    // ./gradlew xdk:jreleaserConf --select-current-platform
    //gradlew xdk:jreleaserConf --select-platform=osx-x86_64
    // We can also publish the github snapshot packages
    //val org.gradle.api.Project.localStagingRepoDirectory get() = compositeRootBuildDirectory.dir("repo-staging")

    // Assemble step is not necessary, because I already have a distribution (and platform specific versions for win, linux, mac with binary launchers added)

    distributions {
        // TODO - plugin?
        val xdk by creating {
        //create("xdk") {
            distributionType = DistributionType.BINARY
            // This is the full all-platforms release config. It needs the pd independent archive as an artifact
            // and all three specific ones.
            // This means we know that we can only build any given platform, but we still have to list the artifacts for all platforms.
            artifacts {
                // In each of these, the platformZip path only exists for the current platform running ./gradlew jreleaser
                // Github workflows will run for all three platforms, but that doesn't work locally.
                artifact {
                    // ANy artifactblock has a transform property for renaming as well. It's a string, not a path.
                    // transform = "xdk/xdk-{{projectEffectiveVersion}}-osx-x86_64.zip"
                    path = platformZip // really the mandated mac name, has to end with mac-aarch.zip something
                    platform = "osx-x86_64"
                }
                /*
                artifact {
                    path = platformZip // really the mandated mac name, has to end with mac-aarch.zip something
                    platform = "linux-x86_64"  // These platforms have to be exactly these. This is the only valid platform description
                }
                artifact {
                    path = platformZip // really the mandated mac name, has to end with mac-aarch.zip something
                    platform = "windows-x86_64"
                }*/
            }
            /*
            jreleaser-native:
            artifacts:
            - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-aarch64.zip'
            platform: 'osx-aarch_64'
            - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-osx-x86_64.zip'
            platform: 'osx-x86_64'
            - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-linux-x86_64.zip'
            platform: 'linux-x86_64'
            - path: '{{nativeImageDir}}/{{distributionName}}-{{projectEffectiveVersion}}-windows-x86_64.zip'
            platform: 'windows-x86_64'
        }
    }
}
*/
