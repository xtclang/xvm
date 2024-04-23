import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xdk.build.aggregator)
    alias(libs.plugins.tasktree)
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOf(xdk, plugin)

/**
 * Ensure that the remote publication (on GitHub) has the correct tag.
 * This may mutate the Git repository under source control (tags only)
 *
 * The tag is used to determine an unambiguous code point from where we
 * can publish packages (and later releases).
 *
 * Semantics:
 *
 *   SNAPSHOT versions:
 *
 *       Anytime a commit gets pushed to master, we check the VERSION file for the current
 *       version of the project. If the VERSION has a SNAPSHOT suffix, this means that we
 *       are publish a SNAPSHOT release. A snapshot release can be published several
 *       times with the identical version number (that is the definition), and those are
 *       all stored in the package repository. There will be a cron job running on GitHub
 *       that prunes older snapshot publications, so that we don't have hundreds of
 *       published artifacts side by side for the same SNAPSHOT number. The common use case
 *       is, after all, that you want the SNAPSHOT to be a hardcoded/fixed dependency, and
 *       you want the latest version of that.
 *    
 *       If you have a dependency to an XDK or XDK plugin artifact in your project, your
 *       project will be rebuilt every time the publication has been overwritten by a later
 *       change having been committed to 'master'. This is typically exactly what you want.
 *    
 *       If you want snapshot or bleeding edge versions of XDK and the plugin, i.e. published
 *       from the code at the latest commit in master, and do not care about a specific version
 *       number, you can use the magic string "latest.integration" for the Gradle version of
 *       the artifact.
 *    
 *       Snapshots are currently only published to our GitHub Maven repository, owned by
 *       the xtclang organization. It is bad form to clutter up public repositories with
 *       lots and lots of SNAPSHOT releases.
 *    
 *       The SNAPSHOT artifacts are created with exactly the same semantics for occasion and
 *       timeline, as are the xdk-latest brew installations.
 *
 *       Tagging: When a SNAPSHOT publication should take place, i.e. when any new commit
 *       gets pushed to master, the build system will tag that commit with a Git tag. It
 *       will have the format "snapshot/vX.Y.Z-SNAPSHOT", where the contents of the string
 *       is derived from the VERSION file in the repository that was checked in. Whenever
 *       a commit goes into mater, and there already is an existing snapshot publication for
 *       it, an earlier tag for the last version of this snapshot publication exists in
 *       the Git history, at the commit from which this snapshot was last published. Typically,
 *       this is the previous commit pushed to master. If the tag exists, we move it to the
 *       latest commit, and publish a new version of the same snapshot package.
 *       If the tag does not exist, we assume this is the first time this snapshot is
 *       published, a new snapshot version will show up under publications in GitHub when
 *       the publication has finished.
 *
 *   "Release versions":
 *
 *       If the VERSION file does not contain a SNAPSHOT suffix, this is assumed to be
 *       a release version. When you commit a change to master, in which the VERSION file
 *       points out a non-snapshot version, it is expected to be a change used to publish
 *       a _release_. A "real" release basically depends on the packages built by the last
 *       commit pushed to master, just like a snapshot release, and can be automated. However,
 *       we will start out with releasing being a manual process (i.e. a workflow_dispatch job
 *       that can be triggered manually).
 *
 *       A "release" as per the GitHub/Maven definition is some kind of archive, installer or
 *       executable that can be downloaded by a user, or referred to as a dependency in a
 *       Gradle or Maven build script. For Gradle, to always use the latest release and not
 *       a specific version number, it's possible to use the version string "latest.release"
 *       for a build dependency to the XDK.
 *
 *       Releases are rare and far between, so this starts out as a manual project, by triggering
 *       an explicit release job. The release job expects the commit the release is built from
 *       to be tagged with a tag on the format: X.Y.Z, where X, Y and Z are from the VERSION file.
 *       The version specified the VERSION file should NOT have a "-SNAPSHOT" suffix. The tag
 *       for a release is expected to be "vX.Y.Z". This is already the case for our historic
 *       releases. IF WE PUSH A CHANGE TO MASTER WHERE THE VERSION FILE REFERS TO A RELEASE
 *       THAT ALREADY EXISTS, THE BUILD WILL FAIL. This is to ensure that releases can never be
 *       overwritten, and this is also how anyone using Maven/Gradle dependencies with a specified
 *       non-snapshot version expects things to work. Depending on a Maven artifact with a
 *       specific and non-snapshot version string, means that we expect the bits downloaded
 *       representing that artifact should NEVER EVER change. Forever.
 *
 *       When a release publication is (manually) triggered, we will plug into JReleaser, which
 *       very helpfully can easily be configured to create various platform dependent installers,
 *       CHANGELOG files, and other information about the release. The packages with both be
 *       published to the GitHub Maven repository for the xtclang.org, and to well known public
 *       artifact repositories, Maven Central (and in the case of the XTC Gradle Plugin, Gradle
 *       Plugin Portal). We will likely upload only packages to those repositories, but we will
 *       provide the releases with full installers and packaging on GitHub.
 *
 *       Later, with the help of JReleaser, we also want to support other package managers such
 *       as Brew, SDKMAN, etc. The first thing we want to do there, is to port the existing
 *       Jenkins job that builds our xdk-latest releases to do exactly the same thing, but with GitHub
 *       workflows, and using JReleaser.  JReleaser can produce native installers for all supported
 *       platforms, as long as you are running JReleaser on that particular platform. Luckily, Github
 *       Workflow actions are available to run (for free) on Linux, Windows and Mac OS, to such an
 *       extent that that suits our purposes, and we do not need to rely on any CPU/OS specific
 *       mechanism to cross compile executable for another platform, which otherwise would have
 *       been a mess to maintain.
 *
 *       After a release has been published, it is impossible to push a new change that has that
 *       commit in its history, with a non snapshot version number. The next push to master after
 *       a release has been created must have a new version number. We will likely automatically
 *       update the VERSION file to be the next patch version number in sequence, and append
 *       a snapshot suffix as the next change to master. This can be automated, and usually is.
 *
 *       I.e. If the last commit contained a release tag, i.e. "vX.Y.Z", the VERSION file of
 *       that commit would contain the same tag. If we push another commit to master, where
 *       the VERSION hasn't been updated, this will break the build, as releases are unique
 *       and cannot be overwritten. The release process likely modifies the VERSION file
 *       so that it reads "X.Y.(Z+1)-SNAPSHOT" instead of "X.Y.Z", as it refers to what the
 *       next release will be, when enough commits have been added, and product management
 *       decides it's time to release a new "official" version.
 *
 */

val ensureTag by tasks.registering {
    delegateTo(PUBLISH_TASK_GROUP, "Ensure that the current commit is tagged with a tag based on the current VERSION file.", xdk to name)
}

/**
 * List existing tags and deduce which tags would need to be moved and/or created to publish
 * package artifacts for this version of the XDK.
 */
val listTags by tasks.registering {
    delegateTo(PUBLISH_TASK_GROUP, "Fetch and list all local and remote Git tags, used for package publication and releases.", xdk to name)
}

/**
 * Lists all existing published packages, their version strings, and in the case of snapshots,
 * which can have multiple packages with the same version number, the creation times for
 * the respective snapshots. Of course, a non-snapshot package cannot contain several creation
 * dates - it can never be overwritten, just deleted (which is also a bad idea).
 */
val listPackages by tasks.registering {
    delegateTo(PUBLISH_TASK_GROUP, "Fetch and list all local and remote Git tags, used for package publication and releases.", xdk to name)
}

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */
val installDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    XdkDistribution.distributionTasks.forEach {
        dependsOn(xdk.task(":$it"))
    }
    dependsOn(xdk.task(":$name"))
}

val installLocalDist by tasks.registering {
    delegateTo(DISTRIBUTION_TASK_GROUP,  "Build and overwrite any local distribution with the new distribution produced by the build.", xdk to name)
}

/**
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 *
 * Publishing tasks can be racy, but it seems that Gradle serializes tasks that have a common
 * output directory, which should be the case here. If not, we will have to put back the
 * parallel check/task failure condition.
 */
val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregate) all artifacts in the XDK to the remote repositories."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
        // TODO: Add gradlePluginPortal() and mavenCentral() here, when we have an official release to publish (will be done immediately after plugin branch gets merged to master)
    }
}

/**
 * Publish local publications to the mavenLocal repository. This is useful for testing
 * that a dependency to a particular XDK version of an XDK or XTC package works with
 * another application before you push it, and cause a "remote" publication to be made.
 */
val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish (aggregated) all artifacts in the XDK to the local Maven repository."
    includedBuildsWithPublications.forEach {
        dependsOn(it.task(":$name"))
    }
}

/**
 * Publish both local (mavenLocal) and remote (GitHub, and potentially mavenCentral, gradlePluginPortal)
 * packages for the current code.
 */
val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
}

private val publishTaskPrefixes = listOf("list", "delete")
private val publishTaskSuffixesRemote = listOf("AllRemotePublications")
private val publishTaskSuffixesLocal = listOf("AllLocalPublications")

publishTaskPrefixes.forEach { prefix ->
    buildList {
        addAll(publishTaskSuffixesLocal)
        addAll(publishTaskSuffixesRemote)
    }.forEach { suffix ->
        val taskName = "$prefix$suffix"
        tasks.register(taskName) {
            group = PUBLISH_TASK_GROUP
            description = "Task that aggregates '$taskName' tasks for builds with publications."
            includedBuildsWithPublications.forEach {
                dependsOn(it.task(":$taskName"))
            }
        }
    }
}

private fun Task.delegateTo(groupName: String, taskDescription: String = "", vararg delegates: Pair<IncludedBuild, String>) {
    group = groupName
    description = taskDescription
    delegates.forEach {
        val (includedBuild, taskName) = it
        val delegateTask = includedBuild.task(":$taskName")
        dependsOn(delegateTask)
        logger.info("$prefix Registered task delegate: $name -> ${includedBuild.name}:$taskName ($delegateTask)")
    }
}