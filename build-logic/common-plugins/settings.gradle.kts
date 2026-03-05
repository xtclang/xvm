buildCache {
    local {
        directory = file("../../.gradle/build-cache")
    }
}

pluginManagement {
    includeBuild("../settings-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "common-plugins"
