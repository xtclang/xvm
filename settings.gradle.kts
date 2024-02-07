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

includeBuild("javatools_utils")
includeBuild("javatools")
includeBuild("javatools_unicode")
includeBuild("plugin")
includeBuild("xdk")
includeBuild("manualTests")

// The manualTests project is part of this repository, but will only be included if the user asks for it.

rootProject.name = "xvm"
