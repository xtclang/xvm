import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_INSTALLATION_NAME
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.jreleaser.gradle.plugin.tasks.AbstractJReleaserTask
import org.jreleaser.gradle.plugin.tasks.JReleaserConfigTask
import org.jreleaser.model.Active
import org.jreleaser.model.Active.ALWAYS
import org.jreleaser.model.Distribution.DistributionType.BINARY
import org.jreleaser.model.Http.Authorization
import org.jreleaser.model.Stereotype
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
    alias(libs.plugins.jreleaser)
    distribution // TODO: If we turn this into an application plugin instead, we can automatically get third party dependency jars with e.g. javatools resolved.
    signing
    `maven-publish`
}

val githubToken = getXtclangGitHubMavenPackageRepositoryToken(true)

publishing {
    repositories {
        mavenLocal()
        mavenGitHubPackages(githubToken)
        mavenLocalStagingDeploy(project)
    }
    configureMavenPublications(project)
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
                packaging = "zip"
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
        logger.lifecycle(
            """
            $prefix Ensuring that the current commit is tagged with version.
            $prefix     version: $semanticVersion
            $prefix     snapshotOnly: $snapshotOnly
        """.trimIndent()
        )
        val tag = xdkBuildLogic.gitHubProtocol().ensureTags(snapshotOnly)
        if (GitHubProtocol.tagCreated(tag)) {
            logger.lifecycle("$prefix Created or updated tag '$tag' for version: '$semanticVersion'")
        }
    }
}

val writeVersionFile by tasks.registering {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Write XDK version file to build directory (VERSION)."
    val versionFile = layout.buildDirectory.file("VERSION")
    outputs.file(versionFile)
    /*    doLast {
            val contents = buildString {
                val (branch, commit) = xdkBuildLogic.gitHubProtocol().resolveBranch()
                appendLine(semanticVersion)
                appendLine("branch: $branch:$commit")
            }
            versionFile.get().asFile.writeText(contents)
        }*/
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
        //from(tasks["writeVersionFile"])

        //.get().outputs.files.singleFile)
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
    dependsOn(installDist) // ensure we also always have a platform independent distribution/install
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

tasks.withType<AbstractJReleaserTask>().configureEach {
    dependsOn(installDist)
    dependsOn(installWithLaunchersDist)
    dependsOn(tasks.named("publishXdkArchivePublicationToLocalStagingRepository"))
    logger.info("$prefix JReleaser task'$name' configured to depend on installDist and installWithLaunchersDist.")
}

val jreleaserConfig by tasks.existing(JReleaserConfigTask::class)

val cleanStagingRepo by tasks.registering {
    doLast {
        logger.lifecycle("$prefix Cleaning local staging repository at: '${localStagingRepoDirectory.get().asFile.absolutePath}'.")
        delete(localStagingRepoDirectory)
    }
}

val publishXdkArchivePublicationToLocalStagingRepository by tasks.existing {
    // We explicitly delete any existing publications in the staging repo, as to not accumulate
    // unncessary numbered SNAPSHOTS. We just want a new / single publication to deploy and to use
    // as deployment or release input.
    dependsOn(cleanStagingRepo)
}

val deploy by tasks.registering {
    // sync build-repo
    dependsOn(jreleaserConfig)
    dependsOn(tasks.named("jreleaserDeploy"))
}

jreleaser {
    //dryrun = true
    gitRootSearch = true

    // TODO: Command line argument: --select-current-platform, or --select-platform osx-aarch_64
    val releaseTag = "v{{projectVersionMajor}}.{{projectVersionMinor}}.{{projectVersionPatch}}"
    //val snapshotTag = "snapshot/$releaseTag" // TODO: "early-access" for all snapshots
    val snapshotTag = "early-access"
    val tag = if (isSnapshot()) snapshotTag else releaseTag

    environment {
        properties = mapOf("artifactsDir" to layout.buildDirectory.file("distributions"))
    }

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
            label = tag
            fullChangelog = false
        }
        stereotype = Stereotype.NONE
    }

    platform {
        replacements = mapOf("osx-aarch_64" to "osx-x86_64")
    }

    // TODO Use early-access as snapshot tag.
    System.err.println("Change the snapshot tag to 'early-access' for all snapshots")
    System.err.println("Build the XDK distribution as a JAR not a ZIP file. Tweak the extractor a bit.")
    // https://www.baeldung.com/maven-artifact
    deploy {
        maven {
            active = ALWAYS
            github {
                val xdk by registering {
                    active = ALWAYS
                    snapshotSupported = true
                    url = "https://maven.pkg.github.com/xtclang/xvm"
                    username = "xtclang-bot"
                    password = githubToken
                    authorization = Authorization.BEARER
                    stagingRepository(localStagingRepoDirectory.get().asFile.absolutePath)
                    applyMavenCentralRules = false
                }
            }
            /*
            https://jreleaser.org/guide/latest/examples/maven/maven-central.html
            nexus2 {
                val `maven-central` by registering {
                    active = ALWAYS
                    url = "https://s01.oss.sonatype.org/service/local"
                    snapshotUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    closeRepository = true
                    releaseRepository = false
                    stagingRepository(localStagingRepoDirectory.get().asFile.absolutePath)
                    applyMavenCentralRules = true
                }
            }*/
        }
    }

    // No uploaders. They are used to uopload assets eleswhere than GH releses such as AWS.
    upload {
        active = Active.NEVER
    }

    // xdk-0.4.4-SNAPSHOT-osx-x86_64.tar.gz  xdk-0.4.4-SNAPSHOT-osx-x86_64.zip     xdk-0.4.4-SNAPSHOT.tar.gz             xdk-0.4.4-SNAPSHOT.zip
    // Just set the classpath to the inner jars.
    distributions {
        val xdk by registering {
            distributionType = BINARY
            stereotype = Stereotype.CLI
            active = ALWAYS
            artifacts {
                artifact {
                    path = layout.buildDirectory.file("distributions/xdk-$version.zip")
                    extraProperties = mapOf(
                        "universal" to true,
                        "graalVMNativeImage" to false
                    )
                }
                artifact {
                    path = layout.buildDirectory.file("distributions/xdk-$version-osx-x86_64.zip")
                    platform = "osx-x86_64"
                    extraProperties = mapOf("graalVMNativeImage" to false)
                }
                artifact {
                    path = layout.buildDirectory.file("distributions/xdk-$version-linux-x86_64.zip")
                    platform = "linux-x86_64"
                    extraProperties = mapOf("graalVMNativeImage" to false)
                }
                artifact {
                    path = layout.buildDirectory.file("distribution/xdk-$version-windows-x86_64.zip")
                    platform = "windows-x86_64"
                    extraProperties = mapOf("graalVMNativeImage" to false)
                }
            }
        }
    }

    release {
        // TODO prerelease
        github {
            // TODO: Make it possible to override draft releases, but it's mostly going to be a manual process to un-draft them.
            draft = true
            tagName = tag
            overwrite = true
            changelog {
                formatted = ALWAYS
                preset = "conventional-commits"
                contributors {
                    format = "- {{contributorName}}{{#contributorUsernameAsLink}} ({{.}}){{/contributorUsernameAsLink}}"
                }
                contentTemplate = compositeRootProjectDirectory.file("gradle/jreleaser/changelog.tpl")
            }
        }
    }
}


/*
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

    // This filters out everything that is not my current platform.
    // ./gradlew xdk:jreleaserConf --select-current-platform
    //gradlew xdk:jreleaserConf --select-platform=osx-x86_64
    // We can also publish the github snapshot packages
    //val org.gradle.api.Project.localStagingRepoDirectory get() = compositeRootBuildDirectory.dir("repo-staging")

    // Assemble step is not necessary, because I already have a distribution (and platform specific versions for win, linux, mac with binary launchers added)

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
