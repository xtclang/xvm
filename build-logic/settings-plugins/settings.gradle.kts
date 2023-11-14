plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("latest.release")
    `gradle-enterprise`
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        isUploadInBackground = System.getenv("CI") != null
        // Always publish buildScan if GITHUB_ACTIONS is set to true (for any running GitHub Action)
        publishAlwaysIf(System.getenv("GITHUB_ACTIONS") == "true")
        publishOnFailure()
        capture {
            isTaskInputFiles = System.getProperty("slow.internet.connection", "false").toBoolean()
        }
    }
}

rootProject.name = "settings-plugins"
