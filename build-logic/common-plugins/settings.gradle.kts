buildCache {
    local {
        // As in the root build: each checkout keeps its own cache, and CI keeps it in the Gradle user home.
        if (!providers.environmentVariable("CI").isPresent) {
            directory = file("../../.gradle/build-cache")
        }
    }
}

pluginManagement {
    includeBuild("../settings-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "common-plugins"
