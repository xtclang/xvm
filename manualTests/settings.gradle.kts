pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
    includeBuild("../plugin")
}

plugins {
    id("org.xvm.build.common")
}

includeBuild("../xdk")

rootProject.name = "manualTests"
