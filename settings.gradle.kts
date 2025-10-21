/**
 * Root settings file. Used for build aggregation, and configuring logic that must be ready
 * before we reach the root project build.
 */

pluginManagement {
    includeBuild("build-logic/settings-plugins")
    includeBuild("build-logic/aggregator")
    includeBuild("build-logic/common-plugins")
}

plugins {
    id("com.gradle.develocity").version("4.2")
    id("org.gradle.toolchains.foojay-resolver").version("1.0.0")
}

toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

develocity {
    projectId = "xvm"
    buildScan {
        val isCi = providers.environmentVariable("CI").isPresent
        publishing.onlyIf {
            isCi
        }
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        uploadInBackground = isCi
        tag(if (isCi) "CI" else "LOCAL")
        capture {
            fileFingerprints = true
        }
    }
}

val xvmBuilds = listOf(
    "javatools",
    "javatools_jitbridge",
    "javatools_utils",
    "javatools_unicode",
    "plugin",
    "xdk",
    "docker"
)

xvmBuilds.forEach(::includeBuild)

// Disable problematic test distribution websocket check task
gradle.taskGraph.whenReady {
    allTasks.find { it.name == "testDistributionWebSocketCheck" }?.enabled = false
}

/**
 * Checks if the property "includeBuildManualTests" is present, which can be set in the gradle.properties
 * file, or passed on the command line with -P, or the environment variable ORG_GRADLE_PROJECT_includeBuildManualTests
 * set. The default value is true. False means that the XVM build won't even try to look for the manualTests
 * project. NOTE: This is a bad idea, since if you want to work with, or debug the manual tests inside IntelliJ,
 * the manualTests project will be completely invisible and not loaded. I would still strongly recommend
 * that the manualTests project is always included, but its tasks are not attached to the root project
 * lifecycle of the composite XDK build by default, as is currently the case. The manualTests configuration
 * does not add much to build time, and pretty much zero, if cached. When configuration caching is up and running,
 * it will be completely undetectable.
 *
 * Regardless of configuration, the manual tests can be run with ./gradlew manualTests:<task> from the command
 * line, but they may not show up in the IDE, and they will not be auto included in the build lifecycle for
 * the composite.
 */
private fun includeManualTests(): Boolean {
    val shouldInclude = providers.gradleProperty("includeBuildManualTests")
        .orElse("true")  // Default to true as per gradle.properties
        .get()
        .toBoolean()
    
    logger.info("[xvm] Build aggregator includeBuild(\"manualTests\"): $shouldInclude")
    return shouldInclude
}

if (includeManualTests()) {
    logger.info(
        """
        [xvm] Tbe XDK build includes 'manualTests'
        [xvm]
        [xvm] This may cause additional overhead with an empty build cache, or after running a 'gradlew clean',
        [xvm] but should not be otherwise significant.
        """.trimIndent(),
    )
    includeBuild("manualTests")
}

rootProject.name = "xvm"

// Note: Root build doesn't use common settings plugin to avoid version catalog conflicts
// Version catalog is automatically loaded from gradle/libs.versions.toml
// XdkPropertiesService registration happens via properties plugin in build.gradle.kts
