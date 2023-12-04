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
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        isUploadInBackground = System.getenv("CI") != null
        publishAlwaysIf(System.getenv("GITHUB_ACTIONS") == "true")
        publishOnFailure()
        capture {
            isTaskInputFiles = System.getProperty("slow.internet.connection", "false").toBoolean()
        }
    }
}

includeBuild("javatools_utils")
includeBuild("javatools")
includeBuild("javatools_unicode")
includeBuild("plugin")
includeBuild("xdk")
includeBuild("manualTests")
includeBuild("manualTests/webapp")
//includeBuild("dev")

rootProject.name = "xvm"
