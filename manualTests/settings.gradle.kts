pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
    includeBuild("../plugin")
}

plugins {
    id("org.xtclang.build.common")
}

includeBuild("../xdk")

rootProject.name = "manualTests"
