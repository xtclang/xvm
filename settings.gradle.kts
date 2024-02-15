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

/**
 * Checks if the property "shouldIncludeManualTests" is present, which can be set in the gradle.properties
 * file, or passed on the command line with -P (or set in an environment variable with the user guide defined
 * name). The default is false, which means that the XVM build won't even try to look for the manualTests
 * project. In Gradle, this should also be equivalent to having the environment variable
 * ORG_GRADLE_PROJECT_shouldIncludeManualTests set to true.
 *
 * TODO: This is a bad idea, since if you want to work with, or debug the manual tests inside IntelliJ,
 * the manualTests project will be completely invisible and not loaded. I would still strongly recommend
 * that the manualTests project is always included, but its tasks are disabled if we want. Its configuration
 * does not add much to build time, especially not cached, and when configuration caching is up and running,
 * it will probably be completely undetectable.
 *
 * Regardless of configuration, the manual tests can be run with ./gradlew manualTests:
 */
private fun includeManualTests(): Boolean {
    val includeBuildManualTests: String? by settings
    val shouldInclude = includeBuildManualTests?.toBoolean() ?: false
    System.err.println("includeManualTests: $shouldInclude")
    return shouldInclude.also {
        logger.info("[xvm] Build aggregator includeBuild(\"manualTests\"): $shouldInclude")
    }
}

if (includeManualTests()) {
    logger.info("[xvm] The XDK build includes 'manualTests'. This may cause additional overhead with an empty build cache, or after running a 'gradlew clean', but should not be otherwise significant.")
    includeBuild("manualTests")
}

rootProject.name = "xvm"
