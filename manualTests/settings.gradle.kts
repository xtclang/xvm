pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
    includeBuild("../plugin")
}

plugins {
    id("org.xtclang.build.common")
}

// NOTE: Do NOT include ../xdk here - it creates circular dependency
// The root project already includes both xdk and manualTests
// And xdk includes manualTests, so xdk dependencies are available

rootProject.name = "manualTests"
