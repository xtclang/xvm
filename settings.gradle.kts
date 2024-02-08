/**
 * Root settings file. Used for build aggregation, and configuring logic that must be ready
 * before we reach the root project build.
 */

pluginManagement {
    includeBuild("build-logic/aggregator")
    includeBuild("build-logic/settings-plugins")
    includeBuild("build-logic/common-plugins")
}

plugins {
    `gradle-enterprise`
}

gradleEnterprise {
    buildScan {
        val isCi = System.getenv("CI") != null
        val isGitHubAction = System.getenv("GITHUB_ACTIONS") == "true"
        publishAlwaysIf(isGitHubAction)
        publishOnFailure()
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        isUploadInBackground = isCi
        tag(if (isCi) "CI" else "LOCAL")
        capture {
            isTaskInputFiles = true
            isBuildLogging = true
            isTestLogging = true
        }
    }
}

includeBuild("javatools")
includeBuild("javatools_utils")
includeBuild("javatools_unicode")
includeBuild("plugin")
includeBuild("xdk")

fun includeManualTests(): Boolean {
    val shouldIncludeManualTests: String? by settings
    return (shouldIncludeManualTests?.toBoolean() == true).also {
        logger.lifecycle("[xvm] Build aggregator shouldIncludeManualTests: $shouldIncludeManualTests")
    }
}

if (includeManualTests()) {
    logger.warn("[xvm] Including 'manualTests' project in the build. This may cause additional overhead with an empty build cache, or after running a 'gradlew clean', but should not be otherwise significant.")
    System.err.println("Including manual tests.")
    includeBuild("manualTests")
}

rootProject.name = "xvm"

