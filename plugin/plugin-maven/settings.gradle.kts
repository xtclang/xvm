pluginManagement {
    includeBuild("../../build-logic/settings-plugins")
    includeBuild("../../build-logic/common-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "xtc-plugin-maven"
