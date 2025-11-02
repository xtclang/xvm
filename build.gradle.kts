import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    idea
    alias(libs.plugins.xdk.build.aggregator)
    alias(libs.plugins.xdk.build.properties)
}

// Root aggregator: version set automatically by properties plugin
group = xdkProperties.stringValue("xdk.group")
version = xdkProperties.stringValue("xdk.version")

logger.info("[xvm] Root aggregator version: $group:$name:$version")

// Configure IntelliJ IDEA to use correct Java version
// This ensures IntelliJ doesn't default to Java 6 when importing the project
val jdkVersion = xdkProperties.int("org.xtclang.java.jdk")
idea {
    project {
        jdkName = jdkVersion.get().toString()
        languageLevel = org.gradle.plugins.ide.idea.model.IdeaLanguageLevel(jdkVersion.get())

        // CRITICAL: Configure compiler settings
        // IntelliJ overwrites these when switching build modes, so we need to enforce them
        ipr {
            withXml {
                val compilerConfig = asNode().appendNode("component").apply {
                    attributes()["name"] = "CompilerConfiguration"
                }
                compilerConfig.appendNode("bytecodeTargetLevel").apply {
                    attributes()["target"] = jdkVersion.get().toString()
                }
            }
        }
    }

    module {
        isDownloadJavadoc = false
        isDownloadSources = true
    }
}

// Task to validate and fix IntelliJ settings after preference changes
// Configuration-cache compatible version
abstract class FixIdeaSettingsTask : DefaultTask() {
    @get:InputFile
    abstract val compilerXml: RegularFileProperty

    @get:InputFile
    abstract val gradleXml: RegularFileProperty

    @get:Input
    abstract val jdkVersion: Property<Int>

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun fix() {
        val jdk = jdkVersion.get()
        val compiler = compilerXml.get().asFile
        val gradle = gradleXml.get().asFile

        if (compiler.exists()) {
            var content = compiler.readText()
            val wrongTarget = Regex("""<bytecodeTargetLevel target="1\.6" />""")
            if (wrongTarget.containsMatchIn(content)) {
                logger.warn("❌ IntelliJ overwrote bytecode target to 1.6! Fixing to $jdk...")
                content = content.replace(wrongTarget, """<bytecodeTargetLevel target="$jdk" />""")
                compiler.writeText(content)
                logger.lifecycle("✅ Fixed compiler.xml bytecode target to $jdk")
            }
        }

        if (gradle.exists()) {
            var content = gradle.readText()
            var changed = false

            if (content.contains("""<option name="delegatedBuild" value="false" />""")) {
                logger.warn("❌ IntelliJ switched to IDEA build mode! Switching back to Gradle mode...")
                content = content.replace(
                    """<option name="delegatedBuild" value="false" />""",
                    """<option name="delegatedBuild" value="true" />"""
                )
                changed = true
            }

            if (content.contains("""<option name="testRunner" value="PLATFORM" />""")) {
                logger.warn("❌ IntelliJ switched to IDEA test runner! Switching back to Gradle...")
                content = content.replace(
                    """<option name="testRunner" value="PLATFORM" />""",
                    """<option name="testRunner" value="GRADLE" />"""
                )
                changed = true
            }

            if (changed) {
                gradle.writeText(content)
                logger.lifecycle("✅ Fixed gradle.xml to use Gradle build mode")
                logger.lifecycle("⚠️  RESTART IntelliJ for changes to take effect!")
            }
        }
    }
}

tasks.register<FixIdeaSettingsTask>("fixIdeaSettings") {
    val buildJdkVersion = jdkVersion
    group = "ide"
    description = "Fix IntelliJ settings that get overwritten when changing preferences"
    compilerXml.set(layout.projectDirectory.file(".idea/compiler.xml"))
    gradleXml.set(layout.projectDirectory.file(".idea/gradle.xml"))
    jdkVersion.set(buildJdkVersion)
}

/**
 * Print version information for the root aggregator and all included builds.
 * The aggregator plugin creates this task and adds dependencies to all included builds.
 * We configure it here to also print the root aggregator's version.
 */
val versions by tasks.existing {
    // Capture values during configuration for configuration cache compatibility
    val projectName = project.name
    val projectGroup = project.group
    val projectVersion = project.version

    doFirst {
        logger.lifecycle("\n📦 Root Aggregator: $projectName")
        logger.lifecycle("   $projectGroup:$projectName:$projectVersion")
        logger.lifecycle("")
    }
}

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */

val distZip by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Build the XDK distribution zip in the xdk/build/distributions directory."
    dependsOn(xdk.task(":$name"))
}

val installDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    dependsOn(xdk.task(":$name"))
}

val installWithNativeLaunchersDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution with native launchers in the xdk/build/install directory."
    dependsOn(xdk.task(":$name"))
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val publishedBuilds = listOf(xdk, plugin)

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to local Maven repository."

    // Publish to local Maven repository for all included builds with publications
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":publishToMavenLocal"))
    }
}

/**
 * Publish XDK and plugin artifacts to both local Maven and remote repositories.
 *
 * Publishes to both local Maven and enabled remote repositories
 * (GitHub Packages, Maven Central, Gradle Plugin Portal).
 *
 * Options:
 * - Use -Porg.xtclang.allowRelease=true to allow publishing release versions (required for non-SNAPSHOT versions)
 */
val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to both local Maven and remote repositories."

    // Capture version and allowRelease as Providers for configuration cache compatibility
    val versionProvider = xdkProperties.string("xdk.version")
    val allowReleaseProvider = xdkProperties.boolean("org.xtclang.allowRelease", false)

    doFirst {
        // Safety check: prevent accidental release publishing
        val currentVersion = versionProvider.get()
        val isSnapshot = currentVersion.endsWith("-SNAPSHOT")
        val allowRelease = allowReleaseProvider.getOrElse(false)

        if (!isSnapshot && !allowRelease) {
            throw GradleException(
                """
                |❌ Cannot publish release version without explicit approval!
                |
                |Current version: $currentVersion
                |
                |This is a RELEASE version (no -SNAPSHOT suffix).
                |To publish a release, you must explicitly set -Porg.xtclang.allowRelease=true
                |
                |Example: ./gradlew publish -Porg.xtclang.allowRelease=true
                |
                |This safety check prevents accidental release publishing.
                """.trimMargin()
            )
        }
        logger.lifecycle("${if (isSnapshot) "📦" else "⚠️ "} Publishing ${if (isSnapshot) "SNAPSHOT" else "RELEASE"} version: $currentVersion (allowRelease=$allowRelease)")
    }

    // Validate credentials before attempting remote publishing (use xdk's validateCredentials task)
    dependsOn(xdk.task(":validateCredentials"))

    // Always publish to both local and remote
    dependsOn(publishLocal)

    // Publish to all enabled remote repositories for all included builds with publications
    // The :publish task will publish to all repositories enabled via properties
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":publish"))
    }
}

/**
 * Aggregate validateCredentials task that runs validation in all publishable projects.
 */
val validateCredentials by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Validate all publishing credentials across all projects without publishing"

    // Run validateCredentials in all projects with publications
    publishedBuilds.forEach { build ->
        dependsOn(build.task(":validateCredentials"))
    }
}

/**
 * Docker tasks - forwarded to docker subproject
 * TODO: Skip this and resolve the dist some other way.
 */

private val dockerSubproject = gradle.includedBuild("docker")
private val dockerTaskNames = listOf(
    "dockerBuildAmd64", "dockerBuildArm64", "dockerBuild",
    "dockerBuildMultiPlatform", "dockerPushMultiPlatform",
    "dockerPushAmd64", "dockerPushArm64", "dockerPushAll",
    "dockerBuildAndPush", "dockerBuildAndPushMultiPlatform",
    "dockerCreateManifest", "dockerBuildPushAndManifest"
)

// Forward all docker tasks to the docker subproject
dockerTaskNames.forEach { taskName ->
    tasks.register(taskName) {
        group = "docker"
        description = "Forward to docker subproject task: $taskName"
        dependsOn(dockerSubproject.task(":$taskName"))

        // Ensure XDK is built first for tasks that need it
        if (taskName.contains("Build") || taskName.contains("Push")) {
            dependsOn(installDist)
        }
    }
}
