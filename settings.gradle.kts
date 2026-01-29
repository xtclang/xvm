@file:Suppress("UnstableApiUsage")

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

/**
 * Core XVM builds - always included.
 */
val coreBuilds = listOf(
    "javatools",
    "javatools_jitbridge",
    "javatools_utils",
    "javatools_unicode",
    "plugin",
    "xdk",
    "docker"
)

coreBuilds.forEach(::includeBuild)

/**
 * Optional builds controlled by gradle properties.
 *
 * Each entry maps a build name to its property name. All default to true.
 * Properties can be set in gradle.properties, via -P on command line,
 * or via environment variable (e.g., ORG_GRADLE_PROJECT_includeBuildLang).
 *
 * Setting a property to false excludes the build entirely, which means:
 * - The build won't be visible in the IDE
 * - Tasks can't be run via ./gradlew buildName:task
 *
 * It's generally recommended to keep these enabled - the overhead is minimal
 * and negligible with configuration caching.
 */
val optionalBuilds = mapOf(
    "lang" to "includeBuildLang",
    "manualTests" to "includeBuildManualTests"
)

optionalBuilds.forEach { (buildName, propertyName) ->
    val shouldInclude = providers.gradleProperty(propertyName)
        .orElse("true")
        .get()
        .toBoolean()

    logger.info("[xvm] Build aggregator includeBuild(\"$buildName\"): $shouldInclude")

    if (shouldInclude) {
        includeBuild(buildName)
    }
}

// Disable problematic test distribution websocket check task
gradle.taskGraph.whenReady {
    allTasks.find { it.name == "testDistributionWebSocketCheck" }?.enabled = false
}

rootProject.name = "xvm"

// Note: Root build doesn't use common settings plugin to avoid version catalog conflicts
// Version catalog is automatically loaded from gradle/libs.versions.toml
// XdkPropertiesService registration happens via properties plugin in build.gradle.kts
